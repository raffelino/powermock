package powermock.modules.junit5.gaps.support;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;

import java.lang.reflect.Field;

/**
 * A third-party style extension that works on the test instance, as extensions commonly do.
 * Fields are accessed by name via reflection (no shared interface), so it is class-loader neutral.
 * <ul>
 * <li>postProcessTestInstance: sets {@code postProcessed = "post-processed"}</li>
 * <li>beforeEach: reads {@code postProcessed} of {@code getRequiredTestInstance()} and writes
 * {@code seenByBeforeEach = "before-each saw " + postProcessed}</li>
 * <li>afterEach: for test methods whose name starts with {@code extensionSees}, requires that the
 * test wrote {@code writtenByTest = <test method name>} on the test instance; fails the test otherwise.</li>
 * </ul>
 */
public class InstanceStateExtension implements TestInstancePostProcessor, BeforeEachCallback, AfterEachCallback {

    public static final String POST_PROCESSED = "post-processed";

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
        set(testInstance, "postProcessed", POST_PROCESSED);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        Object instance = context.getRequiredTestInstance();
        set(instance, "seenByBeforeEach", "before-each saw " + get(instance, "postProcessed"));
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        String method = context.getRequiredTestMethod().getName();
        if (method.startsWith("extensionSees")) {
            Object written = get(context.getRequiredTestInstance(), "writtenByTest");
            if (!method.equals(written)) {
                throw new AssertionError("AfterEachCallback expected writtenByTest=" + method
                    + " on the test instance, but found " + written);
            }
        }
    }

    private static Field field(Object instance, String name) throws NoSuchFieldException {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void set(Object instance, String name, Object value) throws Exception {
        field(instance, name).set(instance, value);
    }

    private static Object get(Object instance, String name) throws Exception {
        return field(instance, name).get(instance);
    }
}
