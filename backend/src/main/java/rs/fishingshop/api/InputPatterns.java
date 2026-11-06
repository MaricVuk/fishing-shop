package rs.fishingshop.api;

public final class InputPatterns {

    public static final String SINGLE_LINE_TEXT = "[^\\p{Cntrl}\\p{Cf}]*";
    public static final String MULTI_LINE_TEXT = "(?:[^\\p{Cntrl}\\p{Cf}]|[\\r\\n])*";
    public static final String PHONE = "^$|[0-9+(][0-9+()/ .-]{4,48}[0-9)]";

    private InputPatterns() {
    }
}
