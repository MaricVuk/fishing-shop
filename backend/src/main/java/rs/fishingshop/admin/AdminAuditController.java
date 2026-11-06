package rs.fishingshop.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rs.fishingshop.audit.AuditAction;
import rs.fishingshop.audit.AuditEntityType;
import rs.fishingshop.audit.AuditEntry;
import rs.fishingshop.audit.AuditService;
import rs.fishingshop.catalog.PageResponse;

@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('OWNER')")
class AdminAuditController {

    private final AuditService audit;

    AdminAuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    PageResponse<AuditEntry> list(
            @RequestParam(required = false) AuditEntityType entityType,
            @RequestParam(required = false) @Pattern(regexp = "[0-9]{1,19}") String entityId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(defaultValue = "0") @Min(0) @Max(9999) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return PageResponse.from(audit.search(entityType, entityId, action, page, size));
    }
}
