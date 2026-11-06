package rs.fishingshop.auth;

public final class PasswordRules {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 64;
    // BCrypt only reads the first 72 bytes, so a longer password would be silently cut.
    public static final int MAX_BYTES = 72;

    private PasswordRules() {
    }
}
