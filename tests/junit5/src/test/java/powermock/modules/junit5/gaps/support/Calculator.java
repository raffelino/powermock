package powermock.modules.junit5.gaps.support;

public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }

    public int twice(int a) {
        return add(a, a);
    }

    public final int answer() {
        return 41;
    }
}
