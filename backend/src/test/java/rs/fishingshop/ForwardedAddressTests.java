package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "shop.orders.max-per-address=3"})
class ForwardedAddressTests extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void valuesAnAttackerPutInFrontOfTheRealAddressDoNotGiveANewBudget() throws Exception {
        for (int i = 1; i <= 3; i++) {
            assertThat(postOrder("198.51.100." + i + ", 203.0.113.7")).isEqualTo(400);
        }

        assertThat(postOrder("198.51.100.99, 203.0.113.7")).isEqualTo(429);
        assertThat(postOrder("203.0.113.7")).isEqualTo(429);
    }

    @Test
    void anotherRealAddressHasItsOwnBudget() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(postOrder("203.0.113.20")).isEqualTo(400);
        }

        assertThat(postOrder("203.0.113.20")).isEqualTo(429);
        assertThat(postOrder("203.0.113.21")).isEqualTo(400);
    }

    @Test
    void proxiesInThePrivateRangeAreSkippedWhenFindingTheClient() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(postOrder("203.0.113.30, 10.1.2.3, 172.20.0.5")).isEqualTo(400);
        }

        assertThat(postOrder("203.0.113.30")).isEqualTo(429);
    }

    private int postOrder(String forwardedFor) throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/orders"))
                        .header("Content-Type", "application/json")
                        .header("X-Forwarded-For", forwardedFor)
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }
}
