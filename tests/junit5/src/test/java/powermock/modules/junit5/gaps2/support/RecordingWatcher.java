package powermock.modules.junit5.gaps2.support;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Records what a TestWatcher and an AfterTestExecutionCallback see of a test's execution exception:
 * {@code "<callback>:<method>:<instanceof UserFailure>:<message>"}.
 */
public class RecordingWatcher implements TestWatcher, AfterTestExecutionCallback {

    public static final List<String> RECORDED = Collections.synchronizedList(new ArrayList<String>());

    @Override
    public void afterTestExecution(ExtensionContext context) {
        Throwable t = context.getExecutionException().orElse(null);
        RECORDED.add(entry("afterTestExecution", context, t));
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        RECORDED.add(entry("testFailed", context, cause));
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        RECORDED.add(entry("testSuccessful", context, null));
    }

    private static String entry(String callback, ExtensionContext context, Throwable t) {
        return callback + ":" + context.getRequiredTestMethod().getName() + ":"
            + (t instanceof UserFailure) + ":" + (t == null ? "-" : t.getMessage());
    }
}
