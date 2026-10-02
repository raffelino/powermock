package powermock.modules.junit5.gaps.support;

/** Final class with a final method: mockable only when prepared by PowerMock. */
public final class Greeter {
    public String greet(String name) {
        return "Hello " + name;
    }
}
