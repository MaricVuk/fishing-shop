package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "shop.orders.max-per-address=2")
class RateLimitBypassTests extends EmployeeTestSupport {

    private static final String OWNER = "probe@fishing-shop.test";

    @LocalServerPort
    int port;

    private final Map<String, String> cookies = new LinkedHashMap<>();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void anEncodedLoginPathIsLimitedLikeThePlainOne() throws Exception {
        employee(OWNER, "OWNER", true);
        send("GET", "/api/auth/csrf", null, null);
        String token = cookies.get("XSRF-TOKEN");

        for (int i = 0; i < 5; i++) {
            assertThat(send("POST", "/api/auth/login", form(OWNER, "wrong-" + i), token)).isEqualTo(401);
        }

        assertThat(send("POST", "/api/auth/login", form(OWNER, PASSWORD), token)).isEqualTo(429);
        assertThat(send("POST", "/api/auth/%6Cogin", form(OWNER, PASSWORD), token)).isEqualTo(429);
        assertThat(send("POST", "/api/auth/%6cogin", form(OWNER, "x"), token)).isEqualTo(429);
    }

    @Test
    void anEncodedOrdersPathIsLimitedLikeThePlainOne() throws Exception {
        send("GET", "/api/auth/csrf", null, null);

        assertThat(send("POST", "/api/orders", "{}", null, "application/json")).isEqualTo(400);
        assertThat(send("POST", "/api/%6Frders", "{}", null, "application/json")).isEqualTo(400);
        assertThat(send("POST", "/api/orders", "{}", null, "application/json")).isEqualTo(429);
        assertThat(send("POST", "/api/%6Frders", "{}", null, "application/json")).isEqualTo(429);
    }

    @Test
    void credentialsInTheQueryStringAreRefused() throws Exception {
        employee(OWNER, "OWNER", true);
        send("GET", "/api/auth/csrf", null, null);
        String token = cookies.get("XSRF-TOKEN");

        int status = send("POST", "/api/auth/login?" + form(OWNER, PASSWORD), "", token);

        assertThat(status).isEqualTo(400);
        assertThat(send("GET", "/api/auth/me", null, null)).isEqualTo(401);
    }

    private int send(String method, String path, String body, String token) throws Exception {
        return send(method, path, body, token, "application/x-www-form-urlencoded");
    }

    private int send(String method, String path, String body, String token, String contentType) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (!cookies.isEmpty()) {
            request.header("Cookie", cookies.entrySet().stream()
                    .map(c -> c.getKey() + "=" + c.getValue()).collect(Collectors.joining("; ")));
        }
        if (token != null) {
            request.header("X-XSRF-TOKEN", token);
        }
        if (body != null) {
            request.header("Content-Type", contentType);
            request.method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        for (String header : response.headers().allValues("Set-Cookie")) {
            String[] pair = header.split(";", 2)[0].split("=", 2);
            if (pair.length == 2 && !pair[1].isEmpty()) {
                cookies.put(pair[0], pair[1]);
            }
        }
        return response.statusCode();
    }

    private static String form(String email, String password) {
        return "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
    }
}
