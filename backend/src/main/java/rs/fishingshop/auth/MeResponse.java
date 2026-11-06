package rs.fishingshop.auth;

import rs.fishingshop.employees.EmployeeRole;

public record MeResponse(Long id, String email, String fullName, EmployeeRole role) {

    static MeResponse from(EmployeePrincipal principal) {
        return new MeResponse(principal.getId(), principal.getUsername(), principal.getFullName(), principal.getRole());
    }
}
