package rs.fishingshop.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import rs.fishingshop.employees.Employee;
import rs.fishingshop.employees.EmployeeRepository;
import rs.fishingshop.employees.EmployeeRole;

@Component
public class OwnerBootstrap implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;

    private static final Logger log = LoggerFactory.getLogger(OwnerBootstrap.class);

    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;
    private final OwnerBootstrapProperties properties;

    public OwnerBootstrap(EmployeeRepository employees, PasswordEncoder passwordEncoder, OwnerBootstrapProperties properties) {
        this.employees = employees;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (employees.count() > 0) {
            return;
        }
        String email = blankToNull(properties.ownerEmail());
        String password = properties.ownerPassword();
        if (email == null || password == null || password.isBlank()) {
            log.warn("No employees exist and SHOP_OWNER_EMAIL / SHOP_OWNER_PASSWORD are not set, nobody can log in");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("SHOP_OWNER_PASSWORD must have at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        String name = blankToNull(properties.ownerName());
        employees.save(new Employee(email, name == null ? "Vlasnik" : name, passwordEncoder.encode(password),
                EmployeeRole.OWNER));
        log.info("Created the first owner account {}", email);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
