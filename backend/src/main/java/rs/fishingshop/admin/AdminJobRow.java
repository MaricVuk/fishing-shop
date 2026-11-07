package rs.fishingshop.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import rs.fishingshop.jobs.JobStatus;
import rs.fishingshop.jobs.JobType;

record AdminJobRow(
        long id,
        JobType type,
        long orderId,
        JobStatus status,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        Instant createdAt,
        Instant finishedAt) {

    static AdminJobRow from(ResultSet rs, int row) throws SQLException {
        return new AdminJobRow(
                rs.getLong("id"),
                JobType.valueOf(rs.getString("type")),
                rs.getLong("order_id"),
                JobStatus.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                instant(rs, "next_attempt_at"),
                rs.getString("last_error"),
                instant(rs, "created_at"),
                instant(rs, "finished_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
