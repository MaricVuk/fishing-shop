package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
class ActuatorPortTests extends PostgresIntegrationTest {

    @LocalServerPort
    int appPort;

    @LocalManagementPort
    int managementPort;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    @AfterEach
    void removeJobs() {
        jdbc.update("DELETE FROM background_jobs");
        jdbc.update("DELETE FROM orders WHERE customer_email = 'actuator@example.com'");
    }

    @Test
    void theApplicationPortServesNoActuatorEndpoint() throws Exception {
        for (String path : List.of("/actuator", "/actuator/health", "/actuator/info", "/actuator/metrics")) {
            assertThat(get(appPort, path).statusCode()).as(path).isIn(401, 403, 404);
        }
    }

    @Test
    void healthOnTheManagementPortShowsTheQueueWithoutASession() throws Exception {
        deadJob();

        HttpResponse<String> response = get(managementPort, "/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(response.body(), "$.status")).isEqualTo("UP");
        assertThat(JsonPath.<Integer>read(response.body(), "$.components.jobs.details.dead")).isEqualTo(1);
    }

    @Test
    void jobMetricsOnTheManagementPortNeedNoSession() throws Exception {
        deadJob();

        HttpResponse<String> response = get(managementPort, "/actuator/metrics/shop.jobs?tag=status:DEAD");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Double>read(response.body(), "$.measurements[0].value")).isEqualTo(1.0);
    }

    @Test
    void endpointsOtherThanHealthInfoAndMetricsAreNotServed() throws Exception {
        for (String path : List.of("/actuator/env", "/actuator/loggers", "/actuator/beans", "/actuator/heapdump",
                "/actuator/configprops", "/actuator/mappings", "/actuator/threaddump")) {
            assertThat(get(managementPort, path).statusCode()).as(path).isIn(401, 403, 404);
        }
    }

    @Test
    void theManagementPortListensOnlyOnTheLoopbackAddress() throws Exception {
        InetAddress other = Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                .flatMap(nic -> Collections.list(nic.getInetAddresses()).stream())
                .filter(address -> address instanceof Inet4Address && !address.isLoopbackAddress())
                .findFirst().orElse(null);
        assumeThat(other).as("a non-loopback address to connect to").isNotNull();

        assertThatThrownBy(() -> client.send(
                HttpRequest.newBuilder(URI.create("http://" + other.getHostAddress() + ":" + managementPort
                        + "/actuator/health")).build(), HttpResponse.BodyHandlers.ofString()))
                .isInstanceOf(IOException.class);
        assertThat(get(managementPort, "/actuator/health").statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void deadJob() {
        long order = jdbc.queryForObject("""
                INSERT INTO orders (customer_name, customer_email, shipping_address, total)
                VALUES ('Actuator Test', 'actuator@example.com', 'Ulica 1', 100.00) RETURNING id
                """, Long.class);
        jdbc.update("""
                INSERT INTO background_jobs (type, order_id, status, next_attempt_at, last_error, finished_at)
                VALUES ('ORDER_CONFIRMATION_EMAIL', ?, 'DEAD', now(), 'SMTP down', now())
                """, order);
    }
}
