package powermock.modules.junit5.gaps2.support;

/** User exception class (unchecked). */
public class UserFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public UserFailure(String message) {
        super(message);
    }
}
