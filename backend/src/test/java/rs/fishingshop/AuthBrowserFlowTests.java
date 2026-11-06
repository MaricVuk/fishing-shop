package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.HttpCookie;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthBrowserFlowTests extends EmployeeTestSupport {

    private static final String OWNER = "browser@fishing-shop.test";

    @LocalServerPort
    int port;

    private final Map<String, String> cookies = new LinkedHashMap<>();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aBrowserGetsACsrfTokenLogsInUsesTheSessionAndLogsOut() throws Exception {
        employee(OWNER, "OWNER", true);

        HttpResponse<String> csrf = send(HttpRequest.newBuilder(url("/api/auth/csrf")).GET());
        assertThat(csrf.statusCode()).isEqualTo(200);
        assertThat(csrf.headers().allValues("Set-Cookie")).anySatisfy(cookie ->
                assertThat(cookie).startsWith("XSRF-TOKEN=").contains("Secure").contains("SameSite=Lax")
                        .doesNotContain("HttpOnly"));
        assertThat(cookies).doesNotContainKey("SESSION");
        String headerName = JsonPath.read(csrf.body(), "$.headerName");
        String token = cookies.get("XSRF-TOKEN");
        assertThat(headerName).isEqualTo("X-XSRF-TOKEN");

        HttpResponse<String> login = send(HttpRequest.newBuilder(url("/api/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header(headerName, token)
                .POST(HttpRequest.BodyPublishers.ofString(form(OWNER, PASSWORD))));
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().allValues("Set-Cookie")).anySatisfy(cookie ->
                assertThat(cookie).startsWith("SESSION=").contains("HttpOnly").contains("Secure")
                        .contains("SameSite=Lax"));
        assertThat((String) JsonPath.read(login.body(), "$.role")).isEqualTo("OWNER");

        HttpResponse<String> me = send(HttpRequest.newBuilder(url("/api/auth/me")).GET());
        assertThat(me.statusCode()).isEqualTo(200);

        HttpResponse<String> logoutWithoutToken = send(HttpRequest.newBuilder(url("/api/auth/logout"))
                .POST(HttpRequest.BodyPublishers.noBody()));
        assertThat(logoutWithoutToken.statusCode()).isEqualTo(403);

        send(HttpRequest.newBuilder(url("/api/auth/csrf")).GET());
        String freshToken = cookies.get("XSRF-TOKEN");
        assertThat(freshToken).isNotEqualTo(token);
        HttpResponse<String> logout = send(HttpRequest.newBuilder(url("/api/auth/logout"))
                .header(headerName, freshToken)
                .POST(HttpRequest.BodyPublishers.noBody()));
        assertThat(logout.statusCode()).isEqualTo(204);

        assertThat(send(HttpRequest.newBuilder(url("/api/auth/me")).GET()).statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        if (!cookies.isEmpty()) {
            request.header("Cookie", cookies.entrySet().stream()
                    .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                    .collect(Collectors.joining("; ")));
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        for (String header : response.headers().allValues("Set-Cookie")) {
            for (HttpCookie cookie : HttpCookie.parse(header)) {
                if (cookie.getMaxAge() == 0 || cookie.getValue().isEmpty()) {
                    cookies.remove(cookie.getName());
                } else {
                    cookies.put(cookie.getName(), cookie.getValue());
                }
            }
        }
        return response;
    }

    private URI url(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String form(String email, String password) {
        return "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
    }
}
