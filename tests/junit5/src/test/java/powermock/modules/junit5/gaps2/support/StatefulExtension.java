package powermock.modules.junit5.gaps2.support;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.ArrayList;
import java.util.List;

/**
 * An extension meant for {@code @RegisterExtension} instance fields: Jupiter calls it, and the test talks to the
 * very same object through the field (reads what beforeEach prepared, records what afterEach then checks).
 */
public class StatefulExtension implements BeforeEachCallback, AfterEachCallback {

    private String preparedFor;
    private final List<String> recorded = new ArrayList<String>();

    @Override
    public void beforeEach(ExtensionContext context) {
        preparedFor = "prepared for " + context.getRequiredTestMethod().getName();
        recorded.clear();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        String method = context.getRequiredTestMethod().getName();
        if (method.startsWith("extensionSees") && !recorded.contains(method)) {
            throw new AssertionError("StatefulExtension expected the test to record " + method
                + " through the @RegisterExtension field, but recorded " + recorded);
        }
    }

    public String preparedFor() {
        return preparedFor;
    }

    public void record(String entry) {
        recorded.add(entry);
    }
}
