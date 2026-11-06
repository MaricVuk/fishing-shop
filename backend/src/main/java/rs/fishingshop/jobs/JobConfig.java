package rs.fishingshop.jobs;

import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(JobProperties.class)
class JobConfig {

    @Bean
    JobWorker jobWorker(JdbcTemplate jdbc, TransactionTemplate transactions, List<JobHandler> handlers,
            JobProperties properties) {
        return new JobWorker(jdbc, transactions, handlers, properties);
    }
}
