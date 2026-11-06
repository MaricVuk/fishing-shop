package rs.fishingshop.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class JobNotFoundException extends ErrorResponseException {

    JobNotFoundException(long id) {
        super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Job " + id + " not found"),
                null);
    }
}
