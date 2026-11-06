package rs.fishingshop.stock;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class StockVariantNotFoundException extends ErrorResponseException {

    public StockVariantNotFoundException(Long variantId) {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Variant " + variantId + " not found"), null);
    }
}
