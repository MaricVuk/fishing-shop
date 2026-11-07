package rs.fishingshop.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import rs.fishingshop.employees.EmployeeRole;

class AdminEventBroadcasterTest {

    private final AdminEventFeed feed = mock(AdminEventFeed.class);
    private final List<SseEmitter> handedOut = new ArrayList<>();
    private AdminEventBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        when(feed.latest()).thenReturn(new EventCursor(0, 0));
        when(feed.soldOut(7)).thenReturn(Optional.of(
                new AdminEvent.SoldOut(7, 3, 5, "Daiwa Ninja X", "NIX-270", Map.of("length", "2.7m"))));
        broadcaster = new AdminEventBroadcaster(feed, () -> handedOut.remove(0));
    }

    @Test
    void aClosedBrowserNeverStopsTheEventFromReachingTheOthers() {
        DeadClient closedTab = new DeadClient();
        RecordingClient openTab = new RecordingClient();
        handedOut.addAll(List.of(closedTab, openTab));
        broadcaster.subscribe(EmployeeRole.OWNER, null);
        broadcaster.subscribe(EmployeeRole.OWNER, null);
        closedTab.dead.set(true);

        broadcaster.variantSoldOut(new VariantSoldOut(7));

        assertThat(openTab.eventNames()).containsExactly("variant-sold-out");
        assertThat(broadcaster.subscriberCount()).isEqualTo(1);
    }

    @Test
    void aClosedBrowserNeverStopsTheHeartbeatForTheOthers() {
        DeadClient closedTab = new DeadClient();
        RecordingClient openTab = new RecordingClient();
        handedOut.addAll(List.of(closedTab, openTab));
        broadcaster.subscribe(EmployeeRole.OWNER, null);
        broadcaster.subscribe(EmployeeRole.OWNER, null);
        int before = openTab.sent.size();
        closedTab.dead.set(true);

        broadcaster.heartbeat();

        assertThat(openTab.sent.size()).isEqualTo(before + 1);
        assertThat(broadcaster.subscriberCount()).isEqualTo(1);
    }

    @Test
    void aStreamThatFailsEvenWhileConnectingIsDroppedWithoutAnError() {
        DeadClient brokenFromTheStart = new DeadClient();
        brokenFromTheStart.dead.set(true);
        handedOut.add(brokenFromTheStart);

        broadcaster.subscribe(EmployeeRole.OWNER, null);

        assertThat(broadcaster.subscriberCount()).isZero();
    }

    private static final class RecordingClient extends SseEmitter {

        private final List<String> sent = new ArrayList<>();

        @Override
        public void send(SseEventBuilder builder) {
            StringBuilder text = new StringBuilder();
            builder.build().forEach(part -> text.append(part.getData()));
            sent.add(text.toString());
        }

        List<String> eventNames() {
            return sent.stream()
                    .filter(text -> text.contains("event:"))
                    .map(text -> text.substring(text.indexOf("event:") + 6, text.indexOf('\n', text.indexOf("event:"))))
                    .toList();
        }
    }

    // A browser that went away: the write fails, and Tomcat then refuses to complete the request from a thread
    // that is not its own, as it did in production-like runs.
    private static final class DeadClient extends SseEmitter {

        private final AtomicBoolean dead = new AtomicBoolean();

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (dead.get()) {
                throw new IOException("Broken pipe");
            }
        }

        @Override
        public void completeWithError(Throwable failure) {
            throw new IllegalStateException(
                    "A non-container (application) thread attempted to use the AsyncContext after an error");
        }
    }
}
