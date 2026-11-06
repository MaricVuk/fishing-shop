package rs.fishingshop.orders;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

public final class RsdFormat {

    private RsdFormat() {
    }

    public static String amount(BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        return new DecimalFormat("#,##0.00", symbols).format(value);
    }

    public static String withCurrency(BigDecimal value) {
        return amount(value) + " RSD";
    }
}
