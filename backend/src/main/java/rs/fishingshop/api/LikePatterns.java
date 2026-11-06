package rs.fishingshop.api;

import java.util.Locale;

public final class LikePatterns {

    private LikePatterns() {
    }

    public static String contains(String text) {
        String value = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        return "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
