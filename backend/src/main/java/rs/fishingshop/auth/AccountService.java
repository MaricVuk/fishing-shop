package rs.fishingshop.auth;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;
import rs.fishingshop.audit.AuditAction;
import rs.fishingshop.audit.AuditEntityType;
import rs.fishingshop.audit.AuditService;
import rs.fishingshop.employees.Employee;
import rs.fishingshop.employees.EmployeeRepository;

@Service
class AccountService {

    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeSessions sessions;
    private final AuditService audit;

    AccountService(EmployeeRepository employees, PasswordEncoder passwordEncoder, EmployeeSessions sessions,
            AuditService audit) {
        this.employees = employees;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.audit = audit;
    }

    @Transactional
    boolean changePassword(EmployeePrincipal principal, ChangePasswordRequest request, String currentSessionId) {
        Employee employee = employees.findForUpdate(principal.getId())
                .orElseThrow(() -> new IllegalStateException("Logged in employee is missing"));
        if (!passwordEncoder.matches(request.currentPassword(), employee.getPasswordHash())) {
            return false;
        }
        employee.replacePasswordHash(passwordEncoder.encode(request.newPassword()));
        sessions.endAllExcept(employee.getEmail(), currentSessionId);
        audit.record(principal.toAuditActor(), AuditAction.EMPLOYEE_PASSWORD_CHANGED, AuditEntityType.EMPLOYEE,
                employee.getId(), null, null);
        return true;
    }

    static ErrorResponseException wrongCurrentPassword() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", List.of(new FieldError("currentPassword", "Mismatch")));
        return new ErrorResponseException(HttpStatus.BAD_REQUEST, problem, null);
    }

    record FieldError(String field, String code) {
    }
}
