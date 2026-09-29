package powermock.modules.junit5.gaps2.support;

import java.io.Serializable;

/** User class whose objects are created outside the test method (argument sources) and must behave as prepared. */
public class Order implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final int net;

    public Order(String id, int net) {
        this.id = id;
        this.net = net;
    }

    public String id() {
        return id;
    }

    public final int net() {
        return net;
    }

    /** Uses the static {@link TaxRates#percent()}. */
    public int gross() {
        return net() + net() * TaxRates.percent() / 100;
    }

    @Override
    public String toString() {
        return id;
    }
}
