package rs.fishingshop.jobs;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobQueue {

    private final JdbcTemplate jdbc;

    JobQueue(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueForOrder(JobType type, long orderId) {
        jdbc.update("INSERT INTO background_jobs (type, order_id) VALUES (?, ?)", type.name(), orderId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueForOrderUnlessQueued(JobType type, long orderId) {
        jdbc.update("""
                INSERT INTO background_jobs (type, order_id) VALUES (?, ?)
                ON CONFLICT ON CONSTRAINT uq_background_jobs_order_type DO NOTHING
                """, type.name(), orderId);
    }
}
