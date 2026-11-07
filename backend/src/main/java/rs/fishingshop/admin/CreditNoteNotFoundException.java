package rs.fishingshop.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

class CreditNoteNotFoundException extends ErrorResponseException {

    CreditNoteNotFoundException(Long orderId) {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Order " + orderId + " has no credit note"),
                null);
    }
}
