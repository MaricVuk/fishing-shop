package rs.fishingshop.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.fishingshop.employees.EmployeeRepository;

@Service
class EmployeeUserDetailsService implements UserDetailsService {

    private final EmployeeRepository employees;

    EmployeeUserDetailsService(EmployeeRepository employees) {
        this.employees = employees;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return employees.findByEmailIgnoreCase(email.trim())
                .map(EmployeePrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown employee"));
    }
}
