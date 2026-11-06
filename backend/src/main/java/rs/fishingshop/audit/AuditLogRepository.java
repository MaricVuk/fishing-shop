package rs.fishingshop.audit;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByIdDesc(AuditEntityType entityType, String entityId);

    @Query("""
            select a from AuditLog a
            where (:entityType is null or a.entityType = :entityType)
              and (:entityId is null or a.entityId = :entityId)
              and (:action is null or a.action = :action)
            order by a.id desc
            """)
    Page<AuditLog> search(@Param("entityType") AuditEntityType entityType, @Param("entityId") String entityId,
            @Param("action") AuditAction action, Pageable pageable);
}
