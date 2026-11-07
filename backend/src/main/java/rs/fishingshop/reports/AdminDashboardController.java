package rs.fishingshop.reports;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rs.fishingshop.auth.EmployeePrincipal;

@RestController
@RequestMapping("/api/admin/dashboard")
class AdminDashboardController {

    private final DashboardService dashboard;

    AdminDashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping
    ResponseEntity<Dashboard> dashboard(@AuthenticationPrincipal EmployeePrincipal employee) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(dashboard.dashboard(employee.getRole()));
    }
}
