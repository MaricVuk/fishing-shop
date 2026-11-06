package rs.fishingshop.auth;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import rs.fishingshop.employees.Employee;
import rs.fishingshop.employees.EmployeeRole;

public final class EmployeePrincipal implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String email;
    private final String fullName;
    private final EmployeeRole role;
    private final boolean active;
    private String passwordHash;

    EmployeePrincipal(Employee employee) {
        this.id = employee.getId();
        this.email = employee.getEmail();
        this.fullName = employee.getFullName();
        this.role = employee.getRole();
        this.active = employee.isActive();
        this.passwordHash = employee.getPasswordHash();
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public EmployeeRole getRole() {
        return role;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
