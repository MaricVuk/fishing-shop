package rs.fishingshop.orders;

final class InputPatterns {

    static final String SINGLE_LINE_TEXT = "[^\\p{Cntrl}]*";
    static final String MULTI_LINE_TEXT = "(?:[^\\p{Cntrl}]|[\\r\\n])*";
    static final String PHONE = "^$|[0-9+(][0-9+()/ .-]{4,48}[0-9)]";

    private InputPatterns() {
    }
}
