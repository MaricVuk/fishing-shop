package rs.fishingshop.audit;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository entries;

    AuditService(AuditLogRepository entries) {
        this.entries = entries;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditActor actor, AuditAction action, AuditEntityType entityType, Object entityId,
            Map<String, Object> oldValue, Map<String, Object> newValue) {
        entries.saveAndFlush(new AuditLog(actor, action, entityType, String.valueOf(entityId), oldValue, newValue));
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> history(AuditEntityType entityType, Object entityId) {
        return entries.findByEntityTypeAndEntityIdOrderByIdDesc(entityType, String.valueOf(entityId)).stream()
                .map(AuditLog::toEntry)
                .toList();
    }
}
