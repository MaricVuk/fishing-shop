package rs.fishingshop.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AccountService accounts;
    private final LoginAttemptLimiter limiter;

    AuthController(AccountService accounts, LoginAttemptLimiter limiter) {
        this.accounts = accounts;
        this.limiter = limiter;
    }

    record CsrfResponse(String headerName, String cookieName) {
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        // Reading the token makes the deferred token load, which writes the XSRF-TOKEN cookie. The
        // browser sends the cookie value back in the header; the value in the body would be masked.
        token.getToken();
        return new CsrfResponse(token.getHeaderName(), "XSRF-TOKEN");
    }

    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal EmployeePrincipal principal) {
        return MeResponse.from(principal);
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@AuthenticationPrincipal EmployeePrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest http) {
        Duration blocked = limiter.blockedFor(principal.getUsername(), http.getRemoteAddr());
        if (blocked.isPositive()) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.TOO_MANY_REQUESTS, "Too many failed attempts, try again later");
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, blocked.toSeconds())));
            throw new ErrorResponseException(HttpStatus.TOO_MANY_REQUESTS, problem, null) {
                @Override
                public HttpHeaders getHeaders() {
                    return headers;
                }
            };
        }
        if (!accounts.changePassword(principal, request)) {
            limiter.recordPasswordFailure(principal.getUsername(), http.getRemoteAddr());
            throw AccountService.wrongCurrentPassword();
        }
        limiter.recordSuccess(principal.getUsername(), http.getRemoteAddr());
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
