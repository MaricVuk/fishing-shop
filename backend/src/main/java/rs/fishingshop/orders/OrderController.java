package rs.fishingshop.orders;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
class OrderController {

    private static final String KEY_HEADER = "Idempotency-Key";
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{16,100}");

    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(name = KEY_HEADER, required = false) String idempotencyKey) {
        if (idempotencyKey != null && !KEY.matcher(idempotencyKey).matches()) {
            throw invalidKey();
        }
        OrderService.Placement placed = orders.place(request, idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED);
        if (placed.replayed()) {
            response.header("Idempotency-Replayed", "true");
        }
        return response.body(placed.order());
    }

    private static ErrorResponseException invalidKey() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", List.of(Map.of("field", KEY_HEADER, "code", "Pattern")));
        return new ErrorResponseException(HttpStatus.BAD_REQUEST, problem, null);
    }
}
