package rs.fishingshop.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import rs.fishingshop.orders.OrderStatus;

class OrderStatusConflictException extends ErrorResponseException {

    OrderStatusConflictException(OrderStatus current, OrderStatus requested) {
        super(HttpStatus.CONFLICT, problem(current, requested), null);
    }

    private static ProblemDetail problem(OrderStatus current, OrderStatus requested) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Order cannot go from " + current + " to " + requested);
        problem.setProperty("currentStatus", current);
        problem.setProperty("requestedStatus", requested);
        problem.setProperty("allowedStatuses", current.nextStatuses());
        return problem;
    }
}
