package powermock.modules.junit5.gaps.support;

/** User class supplied by {@link AccountResolver}. */
public class Account {
    private final String owner;

    public Account(String owner) {
        this.owner = owner;
    }

    public String owner() {
        return owner;
    }

    public final String label() {
        return "account:" + owner;
    }

    public String idLabel() {
        return owner + "#" + IdGenerator.next();
    }
}
