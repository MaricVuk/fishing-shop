package rs.fishingshop.orders;

import java.util.Collection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class OrderRejectedException extends ErrorResponseException {

    private OrderRejectedException(ProblemDetail problem) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, problem, null);
    }

    static OrderRejectedException unavailable(Collection<Long> variantIds) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT, "Some items are no longer available");
        problem.setProperty("unavailableVariantIds", variantIds);
        return new OrderRejectedException(problem);
    }

    static OrderRejectedException totalTooLarge() {
        return new OrderRejectedException(
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, "Order total is too large"));
    }

    static OrderRejectedException quantityTooLarge(Long variantId, int maxQuantity) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT, "Quantity of one item cannot exceed " + maxQuantity);
        problem.setProperty("variantId", variantId);
        return new OrderRejectedException(problem);
    }
}
