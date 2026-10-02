package powermock.modules.junit5.gaps2.c8easymock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.powermock.api.easymock.annotation.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps2.support.FinalService;
import powermock.modules.junit5.gaps2.support.Ids;

import static org.easymock.EasyMock.expect;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.mockStatic;
import static org.powermock.api.easymock.PowerMock.replayAll;
import static org.powermock.api.easymock.PowerMock.verifyAll;

/**
 * C8: PowerMock's automatic reset between tests. Every test records, replays and verifies the annotated
 * {@code @Mock} field and a static mock: each must get a fresh mock in record state (a mock replayed by another
 * test would reject {@code expect}), and replayAll/verifyAll must only cover this test's mocks. The tests are
 * identical in structure, so the outcome does not depend on their order. 3 tests; the one without the annotated
 * field is the control.
 */
@ExtendWith(PowerMockExtension.class)
@PrepareForTest({FinalService.class, Ids.class})
class C8ResetBetweenTestsTest {

    @Mock
    private FinalService service;

    @Test
    void firstRecordReplayVerifyOnAnnotatedMock() {
        recordReplayVerify("first");
    }

    @Test
    void secondRecordReplayVerifyOnAnnotatedMock() {
        recordReplayVerify("second");
    }

    @Test
    void controlProgrammaticMocksAreResetToo() {
        assertEquals("real-id", Ids.next(), "static mock of another test leaked");
        FinalService created = createMock(FinalService.class);
        mockStatic(Ids.class);
        expect(Ids.next()).andReturn("control");
        expect(created.compute(1)).andReturn(1);
        replayAll();
        assertEquals("control", Ids.next());
        assertEquals(1, created.compute(1));
        verifyAll();
    }

    private void recordReplayVerify(String value) {
        assertNotNull(service, "@Mock field must be injected");
        assertEquals("real-id", Ids.next(), "static mock of another test leaked");
        mockStatic(Ids.class);
        expect(service.name()).andReturn(value);
        expect(Ids.next()).andReturn(value + "-id");
        replayAll();
        assertEquals(value, service.name());
        assertEquals(value + "-id", Ids.next());
        verifyAll();
    }
}
