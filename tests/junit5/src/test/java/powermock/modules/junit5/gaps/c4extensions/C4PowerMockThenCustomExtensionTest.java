package powermock.modules.junit5.gaps.c4extensions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps.support.IdGenerator;
import powermock.modules.junit5.gaps.support.InstanceStateExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * C4: a custom instance-state extension registered after PowerMockExtension.
 * The test sees what the extension did to the test instance (TestInstancePostProcessor, BeforeEachCallback
 * via getRequiredTestInstance()), and the extension's AfterEachCallback sees what the test wrote.
 */
@ExtendWith({PowerMockExtension.class, InstanceStateExtension.class})
@PrepareForTest(IdGenerator.class)
class C4PowerMockThenCustomExtensionTest {

    private String postProcessed;
    private String seenByBeforeEach;
    private String writtenByTest;
    private String seenByBeforeEachMethod;

    @BeforeEach
    void beforeEachMethod() {
        seenByBeforeEachMethod = seenByBeforeEach;
    }

    @Test
    void testSeesFieldSetByTestInstancePostProcessor() {
        assertEquals(InstanceStateExtension.POST_PROCESSED, postProcessed);
    }

    @Test
    void testAndBeforeEachMethodSeeWhatBeforeEachCallbackWrote() {
        assertEquals("before-each saw post-processed", seenByBeforeEach);
        assertEquals("before-each saw post-processed", seenByBeforeEachMethod);
    }

    @Test
    void extensionSeesWhatTestWrote() {
        writtenByTest = "extensionSeesWhatTestWrote"; // verified by InstanceStateExtension.afterEach
    }

    @Test
    void extensionSeesWhatTestWroteAlongsideStaticMocking() {
        mockStatic(IdGenerator.class);
        when(IdGenerator.next()).thenReturn("static");
        assertEquals("static", IdGenerator.next());
        assertEquals(InstanceStateExtension.POST_PROCESSED, postProcessed);
        writtenByTest = "extensionSeesWhatTestWroteAlongsideStaticMocking";
    }
}
