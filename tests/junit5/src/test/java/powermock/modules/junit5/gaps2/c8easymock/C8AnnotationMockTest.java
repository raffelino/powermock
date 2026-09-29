package powermock.modules.junit5.gaps2.c8easymock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.powermock.api.easymock.annotation.Mock;
import org.powermock.api.easymock.annotation.MockNice;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps2.support.FinalService;

import static org.easymock.EasyMock.expect;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.replay;
import static org.powermock.api.easymock.PowerMock.replayAll;
import static org.powermock.api.easymock.PowerMock.verify;
import static org.powermock.api.easymock.PowerMock.verifyAll;

/**
 * C8: EasyMock API annotations (as PowerMockRunner's EasyMock annotation listener injects them, cf.
 * tests/easymock/junit4 samples.junit4.annotationbased): {@code @Mock} (default mock: unexpected calls fail)
 * and {@code @MockNice} (nice mock: defaults) of a FINAL prepared class, with expect/replayAll/verifyAll.
 * 3 tests; programmatic {@code createMock} of the final class is the control.
 */
@ExtendWith(PowerMockExtension.class)
@PrepareForTest(FinalService.class)
class C8AnnotationMockTest {

    @Mock
    private FinalService service;

    @MockNice
    private FinalService niceService;

    @Test
    void annotatedDefaultMockOfFinalClassRecordsReplaysVerifies() {
        assertNotNull(service, "@Mock field must be injected");
        expect(service.name()).andReturn("annotated");
        replayAll();
        assertEquals("annotated", service.name());
        assertThrows(AssertionError.class, () -> service.compute(1), "unexpected call on a default mock");
        assertNotSame(service, niceService);
    }

    @Test
    void annotatedNiceMockReturnsDefaultsForUnexpectedCalls() {
        assertNotNull(niceService, "@MockNice field must be injected");
        expect(niceService.compute(2)).andReturn(4);
        replay(niceService);
        assertEquals(4, niceService.compute(2));
        assertNull(niceService.name());
        assertEquals(0, niceService.compute(3));
        verify(niceService);
    }

    @Test
    void controlProgrammaticMockOfFinalClass() {
        FinalService created = createMock(FinalService.class);
        expect(created.name()).andReturn("created");
        replayAll();
        assertEquals("created", created.name());
        verifyAll();
    }
}
