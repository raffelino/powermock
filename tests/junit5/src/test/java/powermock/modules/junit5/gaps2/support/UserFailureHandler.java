package powermock.modules.junit5.gaps2.support;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;

/**
 * A typical user extension: swallows exactly the {@link UserFailure}s whose message starts with
 * {@link Validator#EXPECTED_PREFIX} ({@code instanceof} check, as extensions do), rethrows everything else.
 * <p>
 * Its AfterTestExecutionCallback requires, for test methods whose name starts with {@code handlerSwallows},
 * that the handler swallowed exactly one such UserFailure and that Jupiter therefore records no execution
 * exception ({@code context.getExecutionException()} empty) - so a test that never throws cannot pass either.
 */
public class UserFailureHandler implements TestExecutionExceptionHandler, AfterTestExecutionCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(UserFailureHandler.class);

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        if (throwable instanceof UserFailure && throwable.getMessage().startsWith(Validator.EXPECTED_PREFIX)) {
            context.getStore(NAMESPACE).put("swallowed", throwable);
            return;
        }
        throw throwable;
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (!context.getRequiredTestMethod().getName().startsWith("handlerSwallows")) {
            return;
        }
        Object swallowed = context.getStore(NAMESPACE).get("swallowed");
        if (!(swallowed instanceof UserFailure)) {
            throw new AssertionError("UserFailureHandler expected to swallow a UserFailure, but saw "
                + context.getExecutionException().map(t -> t.getClass().getName() + " (loader "
                + t.getClass().getClassLoader() + ")").orElse("nothing"));
        }
        if (context.getExecutionException().isPresent()) {
            throw new AssertionError("swallowed exception still reported: " + context.getExecutionException().get());
        }
    }
}
