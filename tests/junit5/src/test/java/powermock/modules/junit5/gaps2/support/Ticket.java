package powermock.modules.junit5.gaps2.support;

import java.io.Serializable;

/** Created by Jupiter's implicit String-to-object conversion (public constructor taking one String). */
public class Ticket implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String code;

    public Ticket(String code) {
        this.code = code;
    }

    public final String code() {
        return code;
    }

    public String describe() {
        return code + "@" + Ids.next();
    }
}
