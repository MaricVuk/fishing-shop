package rs.fishingshop.audit;

import java.time.Instant;
import java.util.Map;

public record AuditEntry(
        Long id,
        String actorEmail,
        AuditAction action,
        AuditEntityType entityType,
        String entityId,
        Map<String, Object> oldValue,
        Map<String, Object> newValue,
        Instant createdAt) {
}
