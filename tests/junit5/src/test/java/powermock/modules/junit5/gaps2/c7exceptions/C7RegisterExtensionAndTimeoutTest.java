package powermock.modules.junit5.gaps2.c7exceptions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps2.support.FinalService;
import powermock.modules.junit5.gaps2.support.Ids;
import powermock.modules.junit5.gaps2.support.StatefulExtension;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * C7: a @RegisterExtension instance field is the extension object Jupiter calls: the test reads what its
 * beforeEach prepared and records through it what its afterEach then requires - also under @Timeout (default
 * thread mode) with PowerMock mocking. @Timeout SEPARATE_THREAD: static mocks set up in @BeforeEach on the
 * Jupiter thread work in the test body on the other thread (control). 3 tests.
 */
@ExtendWith(PowerMockExtension.class)
@PrepareForTest({Ids.class, FinalService.class})
class C7RegisterExtensionAndTimeoutTest {

    @RegisterExtension
    final StatefulExtension state = new StatefulExtension();

    private Thread beforeEachThread;

    @BeforeEach
    void stubStatic() {
        beforeEachThread = Thread.currentThread();
        mockStatic(Ids.class);
        when(Ids.next()).thenReturn("from-before-each");
    }

    @Test
    void extensionSeesWhatTestRecordedThroughTheField() {
        assertEquals("prepared for extensionSeesWhatTestRecordedThroughTheField", state.preparedFor());
        state.record("extensionSeesWhatTestRecordedThroughTheField");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void extensionSeesRecordUnderDefaultTimeoutWithMocking() {
        FinalService service = mock(FinalService.class);
        when(service.name()).thenReturn("final-mock");
        assertEquals("final-mock", service.name());
        assertEquals("from-before-each", Ids.next());
        assertEquals("prepared for extensionSeesRecordUnderDefaultTimeoutWithMocking", state.preparedFor());
        state.record("extensionSeesRecordUnderDefaultTimeoutWithMocking");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void controlStaticMockFromBeforeEachWorksInSeparateThread() {
        assertNotSame(beforeEachThread, Thread.currentThread());
        assertEquals("from-before-each", Ids.next());
        FinalService service = mock(FinalService.class);
        when(service.compute(1)).thenReturn(11);
        assertEquals(11, service.compute(1));
    }
}
