package rs.fishingshop.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import rs.fishingshop.employees.Employee;
import rs.fishingshop.employees.EmployeeRepository;
import rs.fishingshop.employees.EmployeeRole;

@Component
public class OwnerBootstrap implements ApplicationRunner {

    private static final int MAX_EMAIL_LENGTH = 100;

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
        email = email.toLowerCase(Locale.ROOT);
        if (email.length() > MAX_EMAIL_LENGTH || !email.matches("[\\x21-\\x7e]+@[\\x21-\\x7e]+\\.[\\x21-\\x7e]+")) {
            throw new IllegalStateException("SHOP_OWNER_EMAIL must be an ASCII email address of at most "
                    + MAX_EMAIL_LENGTH + " characters");
        }
        if (password.length() < PasswordRules.MIN_LENGTH || password.length() > PasswordRules.MAX_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > PasswordRules.MAX_BYTES) {
            throw new IllegalStateException("SHOP_OWNER_PASSWORD must have " + PasswordRules.MIN_LENGTH + " to "
                    + PasswordRules.MAX_LENGTH + " characters and at most " + PasswordRules.MAX_BYTES + " bytes");
        }
        String name = blankToNull(properties.ownerName());
        try {
            employees.saveAndFlush(new Employee(email, name == null ? "Vlasnik" : name,
                    passwordEncoder.encode(password), EmployeeRole.OWNER));
            log.info("Created the first owner account {}", email);
        } catch (DataIntegrityViolationException createdByAnotherInstance) {
            log.info("The first owner account was created by another instance");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
