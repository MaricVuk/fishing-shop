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
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminEventStreamTests extends CommittedOrderTestSupport {

    private static final String PASSWORD = "correct-horse-battery";
    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final Duration QUIET = Duration.ofMillis(1500);

    @LocalServerPort
    int port;

    @Autowired
    PasswordEncoder passwordEncoder;

    private final HttpClient http = HttpClient.newHttpClient();
    private final List<Stream<String>> openStreams = new ArrayList<>();

    @BeforeEach
    void addEmployees() {
        removeEmployees();
        for (String role : List.of("OWNER", "SELLER", "WAREHOUSE")) {
            jdbc.update("""
                    INSERT INTO employees (email, full_name, password_hash, role, active)
                    VALUES (?, ?, ?, ?, TRUE)
                    """, email(role), "Test " + role, passwordEncoder.encode(PASSWORD), role);
        }
    }

    @AfterEach
    void closeStreamsAndRemoveEmployees() {
        openStreams.forEach(Stream::close);
        removeEmployees();
    }

    @Test
    void theOwnerSeesANewOrderAndTheLastUnitSoldRightAfterCommit() throws Exception {
        long variant = variantWithStock("EVT-LAST", 2);
        EventStream owner = connect("OWNER", null);

        long order = placeOrder(variant, 2);

        SseFrame placed = owner.nextEvent();
        assertThat(placed.event()).isEqualTo("order-placed");
        assertThat((Integer) JsonPath.read(placed.data(), "$.orderId")).isEqualTo((int) order);
        assertThat((String) JsonPath.read(placed.data(), "$.total")).isEqualTo("9800.00");
        assertThat((Integer) JsonPath.read(placed.data(), "$.items")).isEqualTo(1);
        SseFrame soldOut = owner.next("variant-sold-out");
        assertThat((Integer) JsonPath.read(soldOut.data(), "$.variantId")).isEqualTo((int) variant);
        assertThat((String) JsonPath.read(soldOut.data(), "$.sku")).isEqualTo(SKU_PREFIX + "EVT-LAST");
        assertThat(soldOut.id()).matches("\\d+-\\d+");
    }

    @Test
    void eachRoleGetsOnlyItsOwnEvents() throws Exception {
        long variant = variantWithStock("EVT-ROLE", 1);
        EventStream seller = connect("SELLER", null);
        EventStream warehouse = connect("WAREHOUSE", null);

        placeOrder(variant, 1);

        assertThat(seller.next("order-placed")).isNotNull();
        assertThat(warehouse.next("variant-sold-out")).isNotNull();
        assertThat(seller.nothingMore()).isTrue();
        assertThat(warehouse.nothingMore()).isTrue();
    }

    @Test
    void anOrderThatIsRolledBackSendsNothing() throws Exception {
        long variant = variantWithStock("EVT-ROLLBACK", 1);
        EventStream owner = connect("OWNER", null);
        // Hibernate writes the decreased stock only when the transaction commits, after the events were
        // published, so this constraint fails the commit itself.
        jdbc.execute("ALTER TABLE inventory ADD CONSTRAINT test_reject_empty_shelf CHECK (quantity > 0) NOT VALID");
        try {
            HttpResponse<String> response = postOrder(variant, 1);
            assertThat(response.statusCode()).isEqualTo(500);
        } finally {
            jdbc.execute("ALTER TABLE inventory DROP CONSTRAINT test_reject_empty_shelf");
        }

        assertThat(stockOf(variant)).isEqualTo(1);
        assertThat(owner.nothingMore()).isTrue();
    }

    @Test
    void aReconnectWithLastEventIdGetsWhatWasMissed() throws Exception {
        long variant = variantWithStock("EVT-REPLAY", 3);
        EventStream first = connect("OWNER", null);
        String lastSeen = first.next(null).id();
        first.close();

        long missed = placeOrder(variant, 3);
        EventStream again = connect("OWNER", lastSeen);

        SseFrame replayed = again.next("order-placed");
        assertThat((Integer) JsonPath.read(replayed.data(), "$.orderId")).isEqualTo((int) missed);
        assertThat(again.next("variant-sold-out")).isNotNull();
    }

    @Test
    void aNewStreamCanResumeFromAParameterWhenTheBrowserCannotSendTheHeader() throws Exception {
        long variant = variantWithStock("EVT-PARAM", 2);
        EventStream first = connect("OWNER", null);
        String lastSeen = first.next(null).id();
        first.close();

        long missed = placeOrder(variant, 1);
        EventStream again = connect("OWNER", null, "/api/admin/events?lastEventId=" + lastSeen);

        SseFrame replayed = again.next("order-placed");
        assertThat((Integer) JsonPath.read(replayed.data(), "$.orderId")).isEqualTo((int) missed);
    }

    @Test
    void aFreshConnectionDoesNotReplayHistory() throws Exception {
        long variant = variantWithStock("EVT-OLD", 1);
        placeOrder(variant, 1);

        EventStream owner = connect("OWNER", null);

        assertThat(owner.nothingMore()).isTrue();
    }

    @Test
    void theStreamIsForEmployeesOnlyAndIsNeverCompressedOrCached() throws Exception {
        HttpResponse<String> anonymous = http.send(HttpRequest.newBuilder(url("/api/admin/events")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(anonymous.statusCode()).isEqualTo(401);

        EventStream owner = connect("OWNER", null);
        assertThat(owner.response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("text/event-stream"));
        assertThat(owner.response.headers().firstValue("Cache-Control")).hasValue("no-cache, no-transform");
    }

    private EventStream connect(String role, String lastEventId) throws Exception {
        return connect(role, lastEventId, "/api/admin/events");
    }

    private EventStream connect(String role, String lastEventId, String path) throws Exception {
        Map<String, String> cookies = logIn(role);
        HttpRequest.Builder request = HttpRequest.newBuilder(url(path))
                .header("Accept", "text/event-stream")
                .header("Cookie", cookies.entrySet().stream()
                        .map(cookie -> cookie.getKey() + "=" + cookie.getValue())
                        .collect(Collectors.joining("; ")))
                .GET();
        if (lastEventId != null) {
            request.header("Last-Event-ID", lastEventId);
        }
        HttpResponse<Stream<String>> response = http.send(request.build(), HttpResponse.BodyHandlers.ofLines());
        assertThat(response.statusCode()).isEqualTo(200);
        openStreams.add(response.body());
        return new EventStream(response);
    }

    private Map<String, String> logIn(String role) throws Exception {
        Map<String, String> cookies = new LinkedHashMap<>();
        HttpResponse<String> csrf = http.send(HttpRequest.newBuilder(url("/api/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        remember(csrf, cookies);
        HttpResponse<String> login = http.send(HttpRequest.newBuilder(url("/api/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"))
                .header("Cookie", "XSRF-TOKEN=" + cookies.get("XSRF-TOKEN"))
                .POST(HttpRequest.BodyPublishers.ofString("email=" + URLEncoder.encode(email(role),
                        StandardCharsets.UTF_8) + "&password=" + PASSWORD))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);
        remember(login, cookies);
        return cookies;
    }

    private static void remember(HttpResponse<?> response, Map<String, String> cookies) {
        for (String header : response.headers().allValues("Set-Cookie")) {
            for (HttpCookie cookie : HttpCookie.parse(header)) {
                cookies.put(cookie.getName(), cookie.getValue());
            }
        }
    }

    private long placeOrder(long variant, int quantity) throws Exception {
        HttpResponse<String> response = postOrder(variant, quantity);
        assertThat(response.statusCode()).isEqualTo(201);
        return OrderNumbers.idOf(jdbc, response.body());
    }

    private HttpResponse<String> postOrder(long variant, int quantity) throws Exception {
        String body = """
                {"customer":{"name":"Događaj Test","email":"dogadjaj@example.com","shippingAddress":"Ulica 1, Beograd"},
                 "items":[{"variantId":%d,"quantity":%d}]}""".formatted(variant, quantity);
        return http.send(HttpRequest.newBuilder(url("/api/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private void removeEmployees() {
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM employees WHERE email LIKE '%@events.test'");
    }

    private static String email(String role) {
        return role.toLowerCase() + "@events.test";
    }

    private URI url(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    record SseFrame(String id, String event, String data, boolean commentOnly) {
    }

    private static final class EventStream {

        private final HttpResponse<Stream<String>> response;
        private final BlockingQueue<SseFrame> frames = new LinkedBlockingQueue<>();
        private final List<SseFrame> skipped = new ArrayList<>();
        private final Thread reader;

        EventStream(HttpResponse<Stream<String>> response) {
            this.response = response;
            this.reader = Thread.ofVirtual().start(this::read);
        }

        private void read() {
            Map<String, String> fields = new HashMap<>();
            boolean[] comment = {false};
            try {
                response.body().forEach(line -> {
                    if (line.isEmpty()) {
                        if (!fields.isEmpty() || comment[0]) {
                            frames.add(new SseFrame(fields.get("id"), fields.get("event"), fields.get("data"),
                                    !fields.containsKey("data")));
                        }
                        fields.clear();
                        comment[0] = false;
                    } else if (line.startsWith(":")) {
                        comment[0] = true;
                    } else {
                        int colon = line.indexOf(':');
                        fields.merge(line.substring(0, colon), line.substring(colon + 1), (a, b) -> a + "\n" + b);
                    }
                });
            } catch (RuntimeException closed) {
                // the test closed the stream
            }
        }

        SseFrame next(String event) throws InterruptedException {
            for (SseFrame frame : skipped) {
                if (event == null || event.equals(frame.event())) {
                    skipped.remove(frame);
                    return frame;
                }
            }
            long deadline = System.nanoTime() + WAIT.toNanos();
            while (System.nanoTime() < deadline) {
                SseFrame frame = frames.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
                if (frame != null && (event == null || event.equals(frame.event()))) {
                    return frame;
                }
                if (frame != null && !frame.commentOnly()) {
                    skipped.add(frame);
                }
            }
            throw new AssertionError("No '" + event + "' event within " + WAIT);
        }

        SseFrame nextEvent() throws InterruptedException {
            long deadline = System.nanoTime() + WAIT.toNanos();
            while (System.nanoTime() < deadline) {
                SseFrame frame = frames.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
                if (frame != null && !frame.commentOnly()) {
                    return frame;
                }
            }
            throw new AssertionError("No event within " + WAIT);
        }

        boolean nothingMore() throws InterruptedException {
            if (!skipped.isEmpty()) {
                return false;
            }
            long deadline = System.nanoTime() + QUIET.toNanos();
            while (System.nanoTime() < deadline) {
                SseFrame frame = frames.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
                if (frame != null && !frame.commentOnly()) {
                    return false;
                }
            }
            return true;
        }

        void close() {
            response.body().close();
            reader.interrupt();
        }
    }
}
