package powermock.modules.junit5.gaps.support;

/** Created with {@code new} inside {@link UserService#audit(String)}: interceptable with whenNew. */
public class AuditLog {
    public String record(String event) {
        return "real-audit:" + event;
    }
}
