package rs.fishingshop.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

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
}
