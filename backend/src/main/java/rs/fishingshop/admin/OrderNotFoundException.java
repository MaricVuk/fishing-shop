package rs.fishingshop.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class OrderNotFoundException extends ErrorResponseException {

    OrderNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Order " + id + " not found"),
                null);
    }
}
