package rs.fishingshop.jobs;

public class PermanentJobFailure extends RuntimeException {

    public PermanentJobFailure(String message, Throwable cause) {
        super(message, cause);
    }

    public PermanentJobFailure(String message) {
        super(message);
    }
}
