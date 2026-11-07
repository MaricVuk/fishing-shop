package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import rs.fishingshop.audit.AuditAction;
import rs.fishingshop.audit.AuditEntityType;
import rs.fishingshop.employees.EmployeeRole;
import rs.fishingshop.jobs.JobStatus;
import rs.fishingshop.jobs.JobType;
import rs.fishingshop.orders.OrderStatus;
import rs.fishingshop.stock.StockMovementType;

class EnumConstraintsTests extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void everyEnumValueIsAcceptedByItsCheckConstraintAndNothingElseIsListed() {
        assertMatches("chk_audit_log_action", AuditAction.values());
        assertMatches("chk_audit_log_entity_type", AuditEntityType.values());
        assertMatches("chk_stock_movements_type", StockMovementType.values());
        assertMatches("chk_employees_role", EmployeeRole.values());
        assertMatches("orders_status_check", OrderStatus.values());
        assertMatches("chk_background_jobs_type", JobType.values());
        assertMatches("chk_background_jobs_status", JobStatus.values());
    }

    private void assertMatches(String constraint, Enum<?>[] values) {
        String definition = jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?", String.class, constraint);
        List<String> listed = java.util.regex.Pattern.compile("'([A-Z_]+)'").matcher(definition).results()
                .map(match -> match.group(1))
                .distinct()
                .toList();
        List<String> names = Arrays.stream(values).map(Enum::name).toList();

        assertThat(listed).as(constraint).containsExactlyInAnyOrderElementsOf(names);
    }
}
