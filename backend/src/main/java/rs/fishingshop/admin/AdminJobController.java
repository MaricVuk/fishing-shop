package rs.fishingshop.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rs.fishingshop.auth.EmployeePrincipal;
import rs.fishingshop.jobs.JobStatus;
import rs.fishingshop.jobs.JobType;

@RestController
@RequestMapping("/api/admin/jobs")
@PreAuthorize("hasRole('OWNER')")
class AdminJobController {

    private final AdminJobService jobs;

    AdminJobController(AdminJobService jobs) {
        this.jobs = jobs;
    }

    @GetMapping
    AdminJobOverview list(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) JobType type,
            @RequestParam(defaultValue = "0") @Min(0) @Max(9999) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return jobs.overview(status, type, page, size);
    }

    @GetMapping("/summary")
    AdminJobSummary summary() {
        return jobs.summary();
    }

    @PostMapping("/{id}/resolve")
    AdminJobRow resolve(@AuthenticationPrincipal EmployeePrincipal employee, @PathVariable long id,
            @Valid @RequestBody ResolveJobRequest request) {
        return jobs.resolve(employee.toAuditActor(), id, request);
    }

    @PostMapping("/{id}/retry")
    AdminJobRow retry(@AuthenticationPrincipal EmployeePrincipal employee, @PathVariable long id) {
        return jobs.retry(employee.toAuditActor(), id);
    }
}
