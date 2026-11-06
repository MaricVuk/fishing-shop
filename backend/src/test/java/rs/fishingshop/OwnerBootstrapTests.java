package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import rs.fishingshop.auth.OwnerBootstrap;
import rs.fishingshop.auth.OwnerBootstrapProperties;
import rs.fishingshop.employees.EmployeeRepository;

class OwnerBootstrapTests extends EmployeeTestSupport {

    @Autowired
    EmployeeRepository employees;

    @Test
    void createsTheFirstOwnerOnlyWhenThereAreNoEmployees() {
        OwnerBootstrap bootstrap = bootstrap(" prvi@fishing-shop.test ", "a-long-enough-password");

        bootstrap.run(new DefaultApplicationArguments());
        bootstrap.run(new DefaultApplicationArguments());

        assertThat(jdbc.queryForList("SELECT email FROM employees WHERE role = 'OWNER'", String.class))
                .containsExactly("prvi@fishing-shop.test");
        String hash = jdbc.queryForObject("SELECT password_hash FROM employees", String.class);
        assertThat(passwordEncoder.matches("a-long-enough-password", hash)).isTrue();
    }

    @Test
    void doesNothingWhenEmployeesAlreadyExist() {
        employee("postoji@fishing-shop.test", "SELLER", true);

        bootstrap("prvi@fishing-shop.test", "a-long-enough-password").run(new DefaultApplicationArguments());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM employees", Integer.class)).isEqualTo(1);
    }

    @Test
    void doesNothingWithoutCredentials() {
        bootstrap("", "").run(new DefaultApplicationArguments());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM employees", Integer.class)).isZero();
    }

    @Test
    void refusesAShortPassword() {
        OwnerBootstrap bootstrap = bootstrap("prvi@fishing-shop.test", "short");

        assertThatThrownBy(() -> bootstrap.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM employees", Integer.class)).isZero();
    }

    private OwnerBootstrap bootstrap(String email, String password) {
        return new OwnerBootstrap(employees, passwordEncoder, new OwnerBootstrapProperties(email, password, null));
    }
}
