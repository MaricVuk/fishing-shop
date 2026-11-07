package rs.fishingshop.events;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
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
    static final int MAX_PER_EMPLOYEE = 10;
    static final int REMEMBERED_EVENTS = 500;
    // A stream ends after a few minutes and the browser reconnects with Last-Event-ID. Every reconnect is a
    // new request, so a deactivated account or a changed role takes effect within this window.
    static final Duration STREAM_LIFETIME = Duration.ofMinutes(5);

    private static final Logger log = LoggerFactory.getLogger(AdminEventBroadcaster.class);

    private final AdminEventFeed feed;
    private final Supplier<SseEmitter> emitters;
    // Writes to browsers never run on the thread that committed an order or on the scheduler thread: a slow
    // or half-open admin connection must not delay a customer's checkout or the background job worker.
    private final Executor fanOut;
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    @Autowired
    AdminEventBroadcaster(AdminEventFeed feed) {
        this(feed, () -> new SseEmitter(STREAM_LIFETIME.toMillis()),
                Executors.newSingleThreadExecutor(Thread.ofPlatform().name("admin-events").daemon().factory()));
    }

    AdminEventBroadcaster(AdminEventFeed feed, Supplier<SseEmitter> emitters, Executor fanOut) {
        this.feed = feed;
        this.emitters = emitters;
        this.fanOut = fanOut;
    }

    SseEmitter subscribe(long employeeId, EmployeeRole role, String lastEventId) {
        EventCursor resumeFrom = EventCursor.parse(lastEventId);
        EventCursor start = resumeFrom != null ? resumeFrom : feed.latest();
        Subscriber subscriber = new Subscriber(emitters.get(), employeeId, role, start);
        SseEmitter emitter = subscriber.emitter;
        emitter.onCompletion(() -> subscribers.remove(subscriber));
        emitter.onTimeout(() -> subscribers.remove(subscriber));
        emitter.onError(failure -> subscribers.remove(subscriber));
        // The replay also covers a fresh connection: an order that commits between reading the cursor and
        // registering the subscriber is found by the query instead of being missed by both paths. Holding the
        // subscriber makes a live event that arrives meanwhile wait until the replay is sent.
        synchronized (subscriber) {
            register(subscriber);
            subscriber.comment("connected");
            feed.since(start).forEach(subscriber::deliver);
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
        submit(() -> subscribers.forEach(subscriber -> subscriber.comment("heartbeat")));
    }

    int subscriberCount() {
        return subscribers.size();
    }

    @PreDestroy
    void stop() {
        if (fanOut instanceof ExecutorService service) {
            service.shutdownNow();
        }
    }

    private void register(Subscriber subscriber) {
        synchronized (subscribers) {
            if (subscribers.size() >= MAX_SUBSCRIBERS) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Too many open event streams");
            }
            long own = subscribers.stream().filter(each -> each.employeeId == subscriber.employeeId).count();
            if (own >= MAX_PER_EMPLOYEE) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many open event streams");
            }
            subscribers.add(subscriber);
        }
    }

    private void broadcast(Optional<AdminEvent> event) {
        event.ifPresent(found -> submit(() -> subscribers.forEach(subscriber -> subscriber.deliver(found))));
    }

    private void submit(Runnable task) {
        try {
            fanOut.execute(task);
        } catch (RejectedExecutionException shuttingDown) {
            log.debug("Admin event dropped while shutting down");
        }
    }

    private final class Subscriber {

        private final SseEmitter emitter;
        private final long employeeId;
        private final EmployeeRole role;
        private final Set<String> delivered = new LinkedHashSet<>();
        private EventCursor cursor;

        Subscriber(SseEmitter emitter, long employeeId, EmployeeRole role, EventCursor cursor) {
            this.emitter = emitter;
            this.employeeId = employeeId;
            this.role = role;
            this.cursor = cursor;
        }

        // Orders commit in a different order than their ids are handed out, so a newer id can arrive before
        // an older one. Repeats (live and replay of the same event) are recognised by the event itself; the
        // cursor only tells the browser where to resume.
        synchronized void deliver(AdminEvent event) {
            if (!firstTime(event)) {
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

        private boolean firstTime(AdminEvent event) {
            if (!delivered.add(event.type() + ":" + event.sourceId())) {
                return false;
            }
            if (delivered.size() > REMEMBERED_EVENTS) {
                Iterator<String> oldest = delivered.iterator();
                oldest.next();
                oldest.remove();
            }
            return true;
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
