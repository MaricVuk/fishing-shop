package rs.fishingshop.invoices;

import java.math.BigDecimal;
import java.math.RoundingMode;

record Vat(BigDecimal base, BigDecimal amount) {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    static Vat ofGross(BigDecimal total, BigDecimal ratePercent) {
        BigDecimal amount = total.multiply(ratePercent)
                .divide(HUNDRED.add(ratePercent), 2, RoundingMode.HALF_UP);
        return new Vat(total.subtract(amount), amount);
    }
}
