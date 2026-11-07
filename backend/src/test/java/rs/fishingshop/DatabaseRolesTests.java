package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DatabaseRolesTests {

    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @BeforeAll
    static void migrateAsMigrator() {
        MigrateResult result = RolesDatabase.migrate();
        assertThat(result.success).isTrue();
    }

    @Test
    void migratorOwnsEveryTableAndAppOwnsNothing() throws SQLException {
        try (Connection c = RolesDatabase.app()) {
            assertThat(strings(c, "SELECT DISTINCT tableowner FROM pg_tables WHERE schemaname = 'public'"))
                    .containsExactly(RolesDatabase.MIGRATOR);
            assertThat(strings(c, """
                    SELECT rolname FROM pg_roles
                    WHERE rolname = 'shop_app' AND (rolsuper OR rolcreaterole OR rolcreatedb OR rolbypassrls OR rolreplication)
                    """)).isEmpty();
        }
    }

    @Test
    void appCannotUpdateDeleteOrTruncateTheAppendOnlyTables() throws SQLException {
        try (Connection c = RolesDatabase.app()) {
            for (String table : List.of("audit_log", "invoices", "credit_notes", "stock_movements")) {
                assertDenied(c, "UPDATE " + table + " SET id = id WHERE FALSE");
                assertDenied(c, "DELETE FROM " + table + " WHERE FALSE");
                assertDenied(c, "TRUNCATE " + table);
            }
        }
    }

    @Test
    void appCannotSwitchOffTheTriggersThatProtectAuditLogAndInvoices() throws SQLException {
        try (Connection c = RolesDatabase.app()) {
            assertDenied(c, "ALTER TABLE audit_log DISABLE TRIGGER USER");
            assertDenied(c, "ALTER TABLE audit_log DISABLE TRIGGER trg_audit_log_no_update_delete");
            assertDenied(c, "ALTER TABLE invoices DISABLE TRIGGER USER");
            assertDenied(c, "DROP TRIGGER trg_audit_log_no_update_delete ON audit_log");
            assertDenied(c, "SET session_replication_role = replica");
            assertDenied(c, "ALTER TABLE audit_log DISABLE TRIGGER ALL");
        }
    }

    @Test
    void appCannotChangeTheSchemaOrTheRoles() throws SQLException {
        try (Connection c = RolesDatabase.app()) {
            assertDenied(c, "CREATE TABLE intruder (id INT)");
            assertDenied(c, "DROP TABLE orders");
            assertDenied(c, "ALTER TABLE orders ADD COLUMN intruder INT");
            assertDenied(c, "CREATE INDEX intruder_idx ON orders (id)");
            assertDenied(c, "CREATE ROLE intruder LOGIN");
            assertDenied(c, "ALTER ROLE shop_app SUPERUSER");
            assertDenied(c, "SELECT * FROM flyway_schema_history");
            assertDenied(c, "INSERT INTO flyway_schema_history (installed_rank, description, type, script, checksum, installed_by, execution_time, success) VALUES (999, 'x', 'SQL', 'x', 0, 'x', 0, TRUE)");
        }
    }

    @Test
    void appCanUpdateOnlyTheTablesTheApplicationChanges() throws SQLException {
        try (Connection c = RolesDatabase.app()) {
            assertThat(tablesWithPrivilege(c, "UPDATE")).containsExactlyInAnyOrder(
                    "background_jobs", "credit_note_counters", "employees", "inventory", "invoice_counters", "orders",
                    "spring_session", "spring_session_attributes");
            assertThat(tablesWithPrivilege(c, "DELETE"))
                    .containsExactlyInAnyOrder("spring_session", "spring_session_attributes");
            assertThat(tablesWithPrivilege(c, "TRUNCATE")).isEmpty();
            assertThat(tablesWithPrivilege(c, "TRIGGER")).isEmpty();
            assertThat(tablesWithPrivilege(c, "REFERENCES")).isEmpty();
        }
    }

    @Test
    void aTableAddedLaterStartsAsInsertAndSelectOnly() throws SQLException {
        try (Connection migrator = RolesDatabase.migrator(); Statement s = migrator.createStatement()) {
            s.execute("CREATE TABLE added_later (id BIGSERIAL PRIMARY KEY, note TEXT)");
            try (Connection app = RolesDatabase.app(); Statement a = app.createStatement()) {
                a.execute("INSERT INTO added_later (note) VALUES ('first')");
                a.execute("INSERT INTO added_later (note) VALUES ('second')");
                try (ResultSet rs = a.executeQuery("SELECT count(*) FROM added_later")) {
                    rs.next();
                    assertThat(rs.getInt(1)).isEqualTo(2);
                }
                assertDenied(app, "UPDATE added_later SET note = 'changed'");
                assertDenied(app, "DELETE FROM added_later");
            } finally {
                s.execute("DROP TABLE added_later");
            }
        }
    }

    @Test
    void appRoleStartsWithTimeoutsThatStopRunawayStatementsAndAbandonedTransactions() throws SQLException {
        try (Connection c = RolesDatabase.app()) {
            assertThat(show(c, "statement_timeout")).isEqualTo("30s");
            assertThat(show(c, "idle_in_transaction_session_timeout")).isEqualTo("2min");
        }
    }

    @Test
    void anAbandonedTransactionLosesItsRowLockSoAnotherWorkerCanTakeTheRow() throws Exception {
        try (Connection admin = RolesDatabase.admin(); Statement s = admin.createStatement()) {
            s.execute("DELETE FROM invoice_counters WHERE year = 2098");
            s.execute("INSERT INTO invoice_counters (year, last_number) VALUES (2098, 0)");
        }
        try (Connection stuck = RolesDatabase.app(); Connection other = RolesDatabase.app()) {
            stuck.setAutoCommit(false);
            other.setAutoCommit(false);
            try (Statement s = stuck.createStatement()) {
                s.execute("SET idle_in_transaction_session_timeout = '2s'");
                s.execute("SELECT year FROM invoice_counters WHERE year = 2098 FOR UPDATE");
            }
            assertThat(skipLockedYears(other)).isEmpty();

            Awaitility.await().atMost(10, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS)
                    .untilAsserted(() -> assertThat(skipLockedYears(other)).containsExactly(2098));

            assertThatThrownBy(() -> {
                try (Statement s = stuck.createStatement()) {
                    s.execute("SELECT 1");
                }
            }).isInstanceOf(SQLException.class);
        }
    }

    private static List<Integer> skipLockedYears(Connection c) throws SQLException {
        List<Integer> years = new ArrayList<>();
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT year FROM invoice_counters WHERE year = 2098 FOR UPDATE SKIP LOCKED")) {
            while (rs.next()) {
                years.add(rs.getInt(1));
            }
        }
        c.rollback();
        return years;
    }

    private static void assertDenied(Connection c, String sql) {
        assertThatThrownBy(() -> {
            try (Statement s = c.createStatement()) {
                s.execute(sql);
            }
        }).as(sql)
                .isInstanceOfSatisfying(SQLException.class,
                        e -> assertThat(e.getSQLState()).as(sql).isEqualTo(INSUFFICIENT_PRIVILEGE));
    }

    private static Set<String> tablesWithPrivilege(Connection c, String privilege) throws SQLException {
        return new TreeSet<>(strings(c, """
                SELECT DISTINCT table_name FROM information_schema.role_table_grants
                WHERE grantee = 'shop_app' AND table_schema = 'public' AND privilege_type = '%s'
                """.formatted(privilege)));
    }

    private static List<String> strings(Connection c, String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
    }

    private static String show(Connection c, String setting) throws SQLException {
        return strings(c, "SHOW " + setting).get(0);
    }
}
