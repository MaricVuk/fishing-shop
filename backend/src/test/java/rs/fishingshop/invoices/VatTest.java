package rs.fishingshop.invoices;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class VatTest {

    private static final BigDecimal RATE = new BigDecimal("20");

    @Test
    void splitsAGrossTotalIntoBaseAndVat() {
        Vat vat = Vat.ofGross(new BigDecimal("9000.00"), RATE);

        assertThat(vat.base()).isEqualByComparingTo("7500.00");
        assertThat(vat.amount()).isEqualByComparingTo("1500.00");
    }

    @Test
    void roundsTheVatHalfUpAndKeepsBasePlusVatEqualToTheTotal() {
        Vat vat = Vat.ofGross(new BigDecimal("1250.50"), RATE);

        assertThat(vat.amount()).isEqualByComparingTo("208.42");
        assertThat(vat.base()).isEqualByComparingTo("1042.08");
        assertThat(vat.base().add(vat.amount())).isEqualByComparingTo("1250.50");
    }

    @Test
    void aZeroTotalHasNoVat() {
        Vat vat = Vat.ofGross(new BigDecimal("0.00"), RATE);

        assertThat(vat.amount()).isEqualByComparingTo("0");
        assertThat(vat.base()).isEqualByComparingTo("0");
    }
}
