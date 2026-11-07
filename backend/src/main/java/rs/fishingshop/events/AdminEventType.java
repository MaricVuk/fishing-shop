package rs.fishingshop.events;

import java.util.Set;
import rs.fishingshop.employees.EmployeeRole;

enum AdminEventType {
    ORDER_PLACED("order-placed", Set.of(EmployeeRole.OWNER, EmployeeRole.SELLER)),
    VARIANT_SOLD_OUT("variant-sold-out", Set.of(EmployeeRole.OWNER, EmployeeRole.WAREHOUSE));

    private final String eventName;
    private final Set<EmployeeRole> audience;

    AdminEventType(String eventName, Set<EmployeeRole> audience) {
        this.eventName = eventName;
        this.audience = audience;
    }

    String eventName() {
        return eventName;
    }

    boolean isFor(EmployeeRole role) {
        return audience.contains(role);
    }
}
