package powermock.modules.junit5.gaps2.support;

/** Final class with (implicitly) final methods: mockable only when prepared. */
public final class FinalService {
    public String name() {
        return "real-service";
    }

    public int compute(int x) {
        return x;
    }
}
