package rs.fishingshop.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class EmployeeRejectedException extends ErrorResponseException {

    private EmployeeRejectedException(HttpStatus status, String detail) {
        super(status, ProblemDetail.forStatusAndDetail(status, detail), null);
    }

    static EmployeeRejectedException notFound(Long id) {
        return new EmployeeRejectedException(HttpStatus.NOT_FOUND, "Employee " + id + " not found");
    }

    static EmployeeRejectedException emailTaken() {
        return new EmployeeRejectedException(HttpStatus.CONFLICT, "An employee with this email already exists");
    }

    static EmployeeRejectedException ownAccount() {
        return new EmployeeRejectedException(HttpStatus.CONFLICT, "Owners cannot change their own role or access");
    }

    static EmployeeRejectedException lastOwner() {
        return new EmployeeRejectedException(HttpStatus.CONFLICT, "The shop must keep at least one active owner");
    }
}
