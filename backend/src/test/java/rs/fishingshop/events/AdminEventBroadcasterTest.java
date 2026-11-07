package rs.fishingshop.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.web.server.ResponseStatusException;
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
        broadcaster = new AdminEventBroadcaster(feed, () -> handedOut.remove(0), Runnable::run);
    }

    @Test
    void aClosedBrowserNeverStopsTheEventFromReachingTheOthers() {
        DeadClient closedTab = new DeadClient();
        RecordingClient openTab = new RecordingClient();
        handedOut.addAll(List.of(closedTab, openTab));
        broadcaster.subscribe(1, EmployeeRole.OWNER, null);
        broadcaster.subscribe(1, EmployeeRole.OWNER, null);
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
        broadcaster.subscribe(1, EmployeeRole.OWNER, null);
        broadcaster.subscribe(1, EmployeeRole.OWNER, null);
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

        broadcaster.subscribe(1, EmployeeRole.OWNER, null);

        assertThat(broadcaster.subscriberCount()).isZero();
    }

    @Test
    void ordersThatCommitOutOfIdOrderAreAllDelivered() {
        RecordingClient owner = new RecordingClient();
        handedOut.add(owner);
        when(feed.newOrder(100)).thenReturn(Optional.of(newOrder(100)));
        when(feed.newOrder(101)).thenReturn(Optional.of(newOrder(101)));
        broadcaster.subscribe(1, EmployeeRole.OWNER, null);

        broadcaster.orderPlaced(new OrderPlaced(101));
        broadcaster.orderPlaced(new OrderPlaced(100));

        assertThat(owner.eventNames()).containsExactly("order-placed", "order-placed");
    }

    @Test
    void anOrderCommittedWhileTheStreamOpensIsNotLost() {
        RecordingClient owner = new RecordingClient();
        handedOut.add(owner);
        when(feed.latest()).thenReturn(new EventCursor(99, 0));
        when(feed.since(new EventCursor(99, 0))).thenReturn(List.of(newOrder(100)));

        broadcaster.subscribe(1, EmployeeRole.OWNER, null);

        assertThat(owner.eventNames()).containsExactly("order-placed");
    }

    @Test
    void theSameEventIsSentOnlyOnce() {
        RecordingClient owner = new RecordingClient();
        handedOut.add(owner);
        when(feed.newOrder(100)).thenReturn(Optional.of(newOrder(100)));
        broadcaster.subscribe(1, EmployeeRole.OWNER, null);

        broadcaster.orderPlaced(new OrderPlaced(100));
        broadcaster.orderPlaced(new OrderPlaced(100));

        assertThat(owner.eventNames()).containsExactly("order-placed");
    }

    @Test
    void writesToBrowsersNeverRunOnTheCommittingThread() {
        List<Runnable> queued = new ArrayList<>();
        AdminEventBroadcaster queuedBroadcaster =
                new AdminEventBroadcaster(feed, () -> handedOut.remove(0), queued::add);
        RecordingClient owner = new RecordingClient();
        handedOut.add(owner);
        when(feed.newOrder(100)).thenReturn(Optional.of(newOrder(100)));
        queuedBroadcaster.subscribe(1, EmployeeRole.OWNER, null);

        queuedBroadcaster.orderPlaced(new OrderPlaced(100));
        queuedBroadcaster.heartbeat();

        assertThat(owner.eventNames()).isEmpty();
        int connectedOnly = owner.sent.size();
        queued.forEach(Runnable::run);
        assertThat(owner.eventNames()).containsExactly("order-placed");
        assertThat(owner.sent.size()).isEqualTo(connectedOnly + 2);
    }

    @Test
    void oneEmployeeCannotTakeEveryStream() {
        for (int i = 0; i < AdminEventBroadcaster.MAX_PER_EMPLOYEE + 1; i++) {
            handedOut.add(new RecordingClient());
        }
        handedOut.add(new RecordingClient());
        for (int i = 0; i < AdminEventBroadcaster.MAX_PER_EMPLOYEE; i++) {
            broadcaster.subscribe(1, EmployeeRole.SELLER, null);
        }

        assertThatThrownBy(() -> broadcaster.subscribe(1, EmployeeRole.SELLER, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429");
        broadcaster.subscribe(2, EmployeeRole.OWNER, null);
        assertThat(broadcaster.subscriberCount()).isEqualTo(AdminEventBroadcaster.MAX_PER_EMPLOYEE + 1);
    }

    private static AdminEvent newOrder(long id) {
        return new AdminEvent.NewOrder(id, new java.math.BigDecimal("10.00"), 1, java.time.Instant.now());
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
