package rs.fishingshop.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByIdDesc(AuditEntityType entityType, String entityId);
}
