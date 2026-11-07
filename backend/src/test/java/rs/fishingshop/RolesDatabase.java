package rs.fishingshop;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

final class RolesDatabase {

    static final String APP = "shop_app";
    static final String APP_PASSWORD = "app-password-for-tests";
    static final String MIGRATOR = "shop_migrator";
    static final String MIGRATOR_PASSWORD = "migrator-password-for-tests";

    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withEnv("SHOP_DB_APP_PASSWORD", APP_PASSWORD)
            .withEnv("SHOP_DB_MIGRATOR_PASSWORD", MIGRATOR_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("../docker/postgres/init-roles.sh"), 0755),
                    "/docker-entrypoint-initdb.d/init-roles.sh");

    static {
        postgres.start();
    }

    private RolesDatabase() {
    }

    static synchronized MigrateResult migrate() {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), MIGRATOR, MIGRATOR_PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    static Connection connect(String user, String password) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), user, password);
    }

    static Connection app() throws SQLException {
        return connect(APP, APP_PASSWORD);
    }

    static Connection migrator() throws SQLException {
        return connect(MIGRATOR, MIGRATOR_PASSWORD);
    }

    static Connection admin() throws SQLException {
        return connect(postgres.getUsername(), postgres.getPassword());
    }
}
