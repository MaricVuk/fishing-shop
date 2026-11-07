package rs.fishingshop.events;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import rs.fishingshop.employees.EmployeeRole;

@Component
class AdminEventBroadcaster {

    static final int MAX_SUBSCRIBERS = 200;
    // A stream ends after a few minutes and the browser reconnects with Last-Event-ID. Every reconnect is a
    // new request, so a deactivated account or a changed role takes effect within this window.
    static final Duration STREAM_LIFETIME = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(AdminEventBroadcaster.class);

    private final AdminEventFeed feed;
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    private final Supplier<SseEmitter> emitters;

    @Autowired
    AdminEventBroadcaster(AdminEventFeed feed) {
        this(feed, () -> new SseEmitter(STREAM_LIFETIME.toMillis()));
    }

    AdminEventBroadcaster(AdminEventFeed feed, Supplier<SseEmitter> emitters) {
        this.feed = feed;
        this.emitters = emitters;
    }

    SseEmitter subscribe(EmployeeRole role, String lastEventId) {
        if (subscribers.size() >= MAX_SUBSCRIBERS) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Too many open event streams");
        }
        EventCursor resumeFrom = EventCursor.parse(lastEventId);
        Subscriber subscriber = new Subscriber(emitters.get(), role,
                resumeFrom != null ? resumeFrom : feed.latest());
        SseEmitter emitter = subscriber.emitter;
        emitter.onCompletion(() -> subscribers.remove(subscriber));
        emitter.onTimeout(() -> subscribers.remove(subscriber));
        emitter.onError(failure -> subscribers.remove(subscriber));
        // Holding the subscriber while replaying makes a live event that commits meanwhile wait, so it cannot
        // move the cursor past older events that are still being replayed.
        synchronized (subscriber) {
            subscribers.add(subscriber);
            subscriber.comment("connected");
            if (resumeFrom != null) {
                feed.since(resumeFrom).forEach(subscriber::deliver);
            }
        }
        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void orderPlaced(OrderPlaced placed) {
        broadcast(feed.newOrder(placed.orderId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void variantSoldOut(VariantSoldOut soldOut) {
        broadcast(feed.soldOut(soldOut.movementId()));
    }

    @Scheduled(fixedRateString = "${shop.events.heartbeat}")
    void heartbeat() {
        subscribers.forEach(subscriber -> subscriber.comment("heartbeat"));
    }

    int subscriberCount() {
        return subscribers.size();
    }

    private void broadcast(Optional<AdminEvent> event) {
        event.ifPresent(found -> subscribers.forEach(subscriber -> subscriber.deliver(found)));
    }

    private final class Subscriber {

        private final SseEmitter emitter;
        private final EmployeeRole role;
        private EventCursor cursor;

        Subscriber(SseEmitter emitter, EmployeeRole role, EventCursor cursor) {
            this.emitter = emitter;
            this.role = role;
            this.cursor = cursor;
        }

        synchronized void deliver(AdminEvent event) {
            if (!cursor.isNew(event)) {
                return;
            }
            cursor = cursor.after(event);
            if (!event.type().isFor(role)) {
                return;
            }
            send(SseEmitter.event().id(cursor.id()).name(event.type().eventName()).data(event));
        }

        synchronized void comment(String text) {
            send(SseEmitter.event().id(cursor.id()).comment(text));
        }

        // One closed browser must never stop the event from reaching the others: this handles every failure
        // of this connection itself, including the one Tomcat throws when the request is completed from a
        // thread that is not its own after the client has gone.
        private void send(SseEmitter.SseEventBuilder event) {
            try {
                emitter.send(event);
            } catch (IOException | RuntimeException closed) {
                log.debug("Event stream closed: {}", closed.getMessage());
                subscribers.remove(this);
                try {
                    emitter.completeWithError(closed);
                } catch (RuntimeException alreadyClosed) {
                    log.debug("Event stream was already closed: {}", alreadyClosed.getMessage());
                }
            }
        }
    }
}
