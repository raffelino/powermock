package powermock.modules.junit5.gaps2.c8easymock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.powermock.api.easymock.annotation.Mock;
import org.powermock.api.easymock.annotation.MockStrict;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps2.support.FinalService;
import powermock.modules.junit5.gaps2.support.Ids;
import powermock.modules.junit5.gaps2.support.Widget;
import powermock.modules.junit5.gaps2.support.WidgetFactory;

import static org.easymock.EasyMock.expect;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.powermock.api.easymock.PowerMock.createMock;
import static org.powermock.api.easymock.PowerMock.expectNew;
import static org.powermock.api.easymock.PowerMock.mockStatic;
import static org.powermock.api.easymock.PowerMock.replayAll;
import static org.powermock.api.easymock.PowerMock.verifyAll;

/**
 * C8: EasyMock API static mocking (mockStatic + expect + replayAll/verifyAll) and expectNew, together with
 * annotated mocks: an annotated {@code @Mock} returned by expectNew; an annotated {@code @MockStrict} of a final
 * class that checks call order. 3 tests; static mocking with a programmatic mock is the control.
 */
@ExtendWith(PowerMockExtension.class)
@PrepareForTest({Ids.class, WidgetFactory.class, FinalService.class})
class C8StaticAndNewWithAnnotationsTest {

    @Mock
    private Widget widget;

    @MockStrict
    private FinalService strictService;

    @Test
    void expectNewReturnsAnnotatedMockAlongsideStaticMock() throws Exception {
        assertNotNull(widget, "@Mock field must be injected");
        mockStatic(Ids.class);
        expect(Ids.next()).andReturn("static-id");
        expectNew(Widget.class, "w").andReturn(widget);
        expect(widget.name()).andReturn("annotated-widget");
        replayAll();
        assertEquals("static-id", Ids.next());
        Widget created = new WidgetFactory().create("w");
        assertSame(widget, created);
        assertEquals("annotated-widget", created.name());
        verifyAll();
    }

    @Test
    void annotatedStrictMockOfFinalClassChecksOrder() {
        assertNotNull(strictService, "@MockStrict field must be injected");
        expect(strictService.name()).andReturn("first");
        expect(strictService.compute(1)).andReturn(2);
        replayAll();
        assertThrows(AssertionError.class, () -> strictService.compute(1), "strict mock: wrong order");
    }

    @Test
    void controlStaticMockingWithProgrammaticMock() throws Exception {
        mockStatic(Ids.class);
        expect(Ids.next()).andReturn("static-only");
        Widget created = createMock(Widget.class);
        expectNew(Widget.class, "x").andReturn(created);
        replayAll();
        assertEquals("static-only", Ids.next());
        assertSame(created, new WidgetFactory().create("x"));
        verifyAll();
    }
}
