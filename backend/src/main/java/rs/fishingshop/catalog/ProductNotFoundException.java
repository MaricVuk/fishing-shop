package rs.fishingshop.catalog;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ProductNotFoundException extends ErrorResponseException {

    public ProductNotFoundException(Long id) {
        super(
                HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Product " + id + " not found"),
                null);
    }
}
