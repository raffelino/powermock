package powermock.modules.junit5.gaps.support;

/** Static factory: mockable only with PowerMockito.mockStatic when prepared. */
public class IdGenerator {
    public static String next() {
        return "real-id";
    }
}
