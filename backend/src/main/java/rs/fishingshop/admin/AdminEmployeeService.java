package rs.fishingshop.admin;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.fishingshop.audit.AuditAction;
import rs.fishingshop.audit.AuditActor;
import rs.fishingshop.audit.AuditEntityType;
import rs.fishingshop.audit.AuditService;
import rs.fishingshop.auth.EmployeeSessions;
import rs.fishingshop.employees.Employee;
import rs.fishingshop.employees.EmployeeRepository;
import rs.fishingshop.employees.EmployeeRole;

@Service
class AdminEmployeeService {

    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeSessions sessions;
    private final AuditService audit;

    AdminEmployeeService(EmployeeRepository employees, PasswordEncoder passwordEncoder, EmployeeSessions sessions,
            AuditService audit) {
        this.employees = employees;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    List<EmployeeResponse> list() {
        return employees.findAllForAdmin().stream().map(EmployeeResponse::from).toList();
    }

    @Transactional
    EmployeeResponse create(AuditActor actor, CreateEmployeeRequest request) {
        String hash = passwordEncoder.encode(request.password());
        requireActiveOwner(actor);
        String email = request.email().toLowerCase(Locale.ROOT);
        if (employees.findByEmailIgnoreCase(email).isPresent()) {
            throw EmployeeRejectedException.emailTaken();
        }
        Employee employee;
        try {
            employee = employees.saveAndFlush(new Employee(email, request.fullName().trim(), hash, request.role()));
        } catch (DataIntegrityViolationException failure) {
            if (failure.getMessage() != null && failure.getMessage().contains("uq_employees_email_lower")) {
                throw EmployeeRejectedException.emailTaken();
            }
            throw failure;
        }
        audit.record(actor, AuditAction.EMPLOYEE_CREATED, AuditEntityType.EMPLOYEE, employee.getId(), null,
                Map.of("email", employee.getEmail(), "fullName", employee.getFullName(), "role", employee.getRole()));
        return EmployeeResponse.from(employee);
    }

    @Transactional
    EmployeeResponse changeRole(AuditActor actor, Long id, EmployeeRole role) {
        Employee target = lockTarget(actor, id);
        EmployeeRole before = target.getRole();
        if (before == role) {
            return EmployeeResponse.from(target);
        }
        if (before == EmployeeRole.OWNER && target.isActive()) {
            requireAnotherActiveOwner(target);
        }
        target.changeRole(role);
        sessions.endAll(target.getEmail());
        audit.record(actor, AuditAction.EMPLOYEE_ROLE_CHANGED, AuditEntityType.EMPLOYEE, id,
                Map.of("role", before), Map.of("role", role));
        return EmployeeResponse.from(target);
    }

    @Transactional
    EmployeeResponse deactivate(AuditActor actor, Long id) {
        Employee target = lockTarget(actor, id);
        if (!target.isActive()) {
            return EmployeeResponse.from(target);
        }
        if (target.getRole() == EmployeeRole.OWNER) {
            requireAnotherActiveOwner(target);
        }
        target.deactivate();
        sessions.endAll(target.getEmail());
        audit.record(actor, AuditAction.EMPLOYEE_DEACTIVATED, AuditEntityType.EMPLOYEE, id,
                Map.of("active", true), Map.of("active", false));
        return EmployeeResponse.from(target);
    }

    @Transactional
    EmployeeResponse activate(AuditActor actor, Long id) {
        Employee target = lockTarget(actor, id);
        if (target.isActive()) {
            return EmployeeResponse.from(target);
        }
        target.activate();
        audit.record(actor, AuditAction.EMPLOYEE_ACTIVATED, AuditEntityType.EMPLOYEE, id,
                Map.of("active", false), Map.of("active", true));
        return EmployeeResponse.from(target);
    }

    @Transactional
    void resetPassword(AuditActor actor, Long id, String password) {
        String hash = passwordEncoder.encode(password);
        Employee target = lockTarget(actor, id);
        target.replacePasswordHash(hash);
        sessions.endAll(target.getEmail());
        audit.record(actor, AuditAction.EMPLOYEE_PASSWORD_RESET, AuditEntityType.EMPLOYEE, id, null, null);
    }

    // Every change locks all active owners first, in id order. Two owners changing each other at
    // the same moment then run one after the other, so the shop can never end up without an owner.
    private Employee lockTarget(AuditActor actor, Long id) {
        requireActiveOwner(actor);
        if (id.equals(actor.id())) {
            throw EmployeeRejectedException.ownAccount();
        }
        return employees.findForUpdate(id).orElseThrow(() -> EmployeeRejectedException.notFound(id));
    }

    private void requireActiveOwner(AuditActor actor) {
        List<Employee> owners = employees.lockActiveOwners();
        if (owners.stream().noneMatch(owner -> owner.getId().equals(actor.id()))) {
            throw new AccessDeniedException("Actor is no longer an active owner");
        }
    }

    private void requireAnotherActiveOwner(Employee target) {
        boolean another = employees.lockActiveOwners().stream()
                .anyMatch(owner -> !owner.getId().equals(target.getId()));
        if (!another) {
            throw EmployeeRejectedException.lastOwner();
        }
    }
}
