package rs.fishingshop.admin;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import rs.fishingshop.auth.EmployeePrincipal;

@RestController
@RequestMapping("/api/admin/employees")
@PreAuthorize("hasRole('OWNER')")
class AdminEmployeeController {

    private final AdminEmployeeService employees;

    AdminEmployeeController(AdminEmployeeService employees) {
        this.employees = employees;
    }

    @GetMapping
    List<EmployeeResponse> list() {
        return employees.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    EmployeeResponse create(@AuthenticationPrincipal EmployeePrincipal owner,
            @Valid @RequestBody CreateEmployeeRequest request) {
        return employees.create(owner.toAuditActor(), request);
    }

    @PostMapping("/{id}/role")
    EmployeeResponse changeRole(@AuthenticationPrincipal EmployeePrincipal owner, @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest request) {
        return employees.changeRole(owner.toAuditActor(), id, request.role());
    }

    @PostMapping("/{id}/deactivate")
    EmployeeResponse deactivate(@AuthenticationPrincipal EmployeePrincipal owner, @PathVariable Long id) {
        return employees.deactivate(owner.toAuditActor(), id);
    }

    @PostMapping("/{id}/activate")
    EmployeeResponse activate(@AuthenticationPrincipal EmployeePrincipal owner, @PathVariable Long id) {
        return employees.activate(owner.toAuditActor(), id);
    }

    @PostMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@AuthenticationPrincipal EmployeePrincipal owner, @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request) {
        employees.resetPassword(owner.toAuditActor(), id, request.password());
    }
}
