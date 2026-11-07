package rs.fishingshop.auth;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({AuthProperties.class, OwnerBootstrapProperties.class, OrderRateLimitProperties.class})
class SecurityConfig {

    static final String LOGIN = "/api/auth/login";
    static final String LOGOUT = "/api/auth/logout";
    private static final String INVALID_LOGIN = "Invalid email or password";

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, ProblemWriter problems, LoginAttemptLimiter limiter, AuthProperties properties,
            OrderRateLimitProperties orderLimits, JdbcTemplate jdbc) {
        http
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/*", "/api/categories",
                                "/api/variants").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(LOGIN).permitAll()
                        .requestMatchers("/api/auth/me", "/api/auth/password", LOGOUT, "/api/admin/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/metrics", "/actuator/metrics/*").hasRole("OWNER")
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll())
                .csrf(csrf -> csrf.spa()
                        .csrfTokenRepository(csrfTokenRepository(properties))
                        .ignoringRequestMatchers(
                                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/orders")))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form
                        .loginPage(LOGIN)
                        .loginProcessingUrl(LOGIN)
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) -> {
                            EmployeePrincipal principal = (EmployeePrincipal) authentication.getPrincipal();
                            limiter.recordSuccess(principal.getUsername(), request.getRemoteAddr());
                            problems.writeJson(response, MeResponse.from(principal));
                        })
                        .failureHandler((request, response, exception) -> {
                            limiter.recordFailure(request.getParameter("email"), request.getRemoteAddr());
                            problems.write(request, response, HttpStatus.UNAUTHORIZED, INVALID_LOGIN);
                        }))
                .logout(logout -> logout
                        .logoutUrl(LOGOUT)
                        .invalidateHttpSession(true)
                        .deleteCookies("SESSION")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                problems.write(request, response, HttpStatus.UNAUTHORIZED, "Login required"))
                        .accessDeniedHandler((request, response, exception) ->
                                problems.write(request, response, HttpStatus.FORBIDDEN, "Access denied")))
                .addFilterBefore(new LoginRateLimitFilter(limiter, problems), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new OrderRateLimitFilter(orderLimits, problems), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new SessionAgeFilter(properties.sessionMaxAge()), SecurityContextHolderFilter.class)
                .addFilterAfter(new EmployeeStateFilter(jdbc), SecurityContextHolderFilter.class);
        return http.build();
    }

    private static CookieCsrfTokenRepository csrfTokenRepository(AuthProperties properties) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.secure(properties.secureCookies()).sameSite("Lax"));
        return repository;
    }

    @Bean
    CookieSerializer sessionCookieSerializer(AuthProperties properties) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(properties.secureCookies());
        serializer.setSameSite("Lax");
        return serializer;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    AuthenticationManager authenticationManager(EmployeeUserDetailsService users, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwordEncoder);
        // The active flag is checked after the password, so a deactivated account answers exactly
        // like a wrong password and takes as long.
        provider.setPreAuthenticationChecks(user -> { });
        provider.setPostAuthenticationChecks(user -> {
            if (!user.isEnabled()) {
                throw new DisabledException("Employee is not active");
            }
        });
        return new ProviderManager(provider);
    }
}
