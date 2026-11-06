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
        // The database lower-cases some characters differently from Java, so a spelling with
        // non-ASCII letters could find an account that the login limiter counts under another key.
        if (!email.chars().allMatch(character -> character > 0x20 && character < 0x7f)) {
            throw new UsernameNotFoundException("Unknown employee");
        }
        return employees.findByEmailIgnoreCase(email.trim())
                .map(EmployeePrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown employee"));
    }
}
