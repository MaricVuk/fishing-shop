package rs.fishingshop.stock;

import java.util.Collection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class InsufficientStockException extends ErrorResponseException {

    InsufficientStockException(Collection<Long> variantIds) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, problem(variantIds), null);
    }

    private static ProblemDetail problem(Collection<Long> variantIds) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT, "Some items are not in stock in the requested quantity");
        problem.setProperty("insufficientStockVariantIds", variantIds);
        return problem;
    }
}
