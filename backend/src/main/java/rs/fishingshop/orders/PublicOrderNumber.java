package rs.fishingshop.orders;

import java.util.Locale;
import java.util.regex.Pattern;

public final class PublicOrderNumber {

    private static final Pattern TOKEN = Pattern.compile("[0-9A-F]{12}");

    private PublicOrderNumber() {
    }

    public static String format(String token) {
        return token.substring(0, 4) + "-" + token.substring(4, 8) + "-" + token.substring(8);
    }

    public static String tokenFrom(String text) {
        String token = text.trim().replace("-", "").toUpperCase(Locale.ROOT);
        return TOKEN.matcher(token).matches() ? token : null;
    }
}
