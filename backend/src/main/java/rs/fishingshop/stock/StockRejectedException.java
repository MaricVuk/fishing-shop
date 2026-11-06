package rs.fishingshop.stock;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class StockRejectedException extends ErrorResponseException {

    private StockRejectedException(ProblemDetail problem) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, problem, null);
    }

    static StockRejectedException limitExceeded(Long variantId, int maxQuantity) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT, "Stock of one variant cannot exceed " + maxQuantity);
        problem.setProperty("variantId", variantId);
        problem.setProperty("maxQuantity", maxQuantity);
        return new StockRejectedException(problem);
    }

    static StockRejectedException belowReserved(Long variantId, int reserved) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT, "The count is lower than what open orders still hold");
        problem.setProperty("variantId", variantId);
        problem.setProperty("reserved", reserved);
        return new StockRejectedException(problem);
    }
}
