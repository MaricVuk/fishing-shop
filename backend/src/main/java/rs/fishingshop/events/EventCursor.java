package rs.fishingshop.events;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

record EventCursor(long orderId, long movementId) {

    private static final Pattern FORMAT = Pattern.compile("(\\d{1,18})-(\\d{1,18})");

    static EventCursor parse(String lastEventId) {
        if (lastEventId == null) {
            return null;
        }
        Matcher matcher = FORMAT.matcher(lastEventId);
        return matcher.matches()
                ? new EventCursor(Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2)))
                : null;
    }

    boolean isNew(AdminEvent event) {
        return switch (event.type()) {
            case ORDER_PLACED -> event.sourceId() > orderId;
            case VARIANT_SOLD_OUT -> event.sourceId() > movementId;
        };
    }

    EventCursor after(AdminEvent event) {
        return switch (event.type()) {
            case ORDER_PLACED -> new EventCursor(Math.max(orderId, event.sourceId()), movementId);
            case VARIANT_SOLD_OUT -> new EventCursor(orderId, Math.max(movementId, event.sourceId()));
        };
    }

    String id() {
        return orderId + "-" + movementId;
    }
}
