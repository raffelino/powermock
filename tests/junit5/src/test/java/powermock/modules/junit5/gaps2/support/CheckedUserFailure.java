package powermock.modules.junit5.gaps2.support;

/** User exception class (checked). */
public class CheckedUserFailure extends Exception {
    private static final long serialVersionUID = 1L;

    public CheckedUserFailure(String message) {
        super(message);
    }
}
