package rs.fishingshop.reports;

import org.springframework.jdbc.core.JdbcTemplate;

final class ReportSession {

    private ReportSession() {
    }

    // Measured on 5 years of orders (docs/perf): the planner badly misjudges the size of a date range, so
    // (1) a generic prepared-statement plan, which the JDBC driver switches to after a few executions, picks an
    // index loop per order (1.3 s instead of 0.4 s), and (2) JIT compilation costs more than it saves.
    // SET LOCAL keeps both settings inside the report transaction.
    static void tune(JdbcTemplate jdbc) {
        jdbc.execute("SET LOCAL plan_cache_mode = force_custom_plan");
        jdbc.execute("SET LOCAL jit = off");
    }
}
