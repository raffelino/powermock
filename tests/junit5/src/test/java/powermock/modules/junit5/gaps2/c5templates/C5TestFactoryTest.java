package powermock.modules.junit5.gaps2.c5templates;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps2.support.FinalService;
import powermock.modules.junit5.gaps2.support.Ids;
import powermock.modules.junit5.gaps2.support.Widget;
import powermock.modules.junit5.gaps2.support.WidgetFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

/**
 * C5: @TestFactory. Jupiter calls the factory and later executes the returned DynamicTests; PowerMock mocking
 * (mockStatic, whenNew, final-class mocks) set up in the executables or in the factory must be effective when
 * the dynamic test runs. Jupiter runs no afterEach between the dynamic tests of one factory, so no test here
 * requires PowerMock state to be reset between them. 6 invocations (5 dynamic tests + 1 control @Test).
 */
@ExtendWith(PowerMockExtension.class)
@PrepareForTest({Ids.class, WidgetFactory.class, FinalService.class})
class C5TestFactoryTest {

    @TestFactory
    Stream<DynamicTest> staticMockingInsideExecutables() {
        return Stream.of("a", "b").map(s -> dynamicTest("static " + s, () -> {
            mockStatic(Ids.class);
            when(Ids.next()).thenReturn("dyn-" + s);
            assertEquals("dyn-" + s, Ids.next());
        }));
    }

    @TestFactory
    List<DynamicTest> whenNewAndFinalMockInsideExecutables() {
        return Arrays.asList(
            dynamicTest("whenNew", () -> {
                Widget widget = mock(Widget.class);
                when(widget.name()).thenReturn("mocked-widget");
                whenNew(Widget.class).withArguments("w").thenReturn(widget);
                assertSame(widget, new WidgetFactory().create("w"));
                assertEquals("mocked-widget", new WidgetFactory().create("w").name());
            }),
            dynamicTest("final class", () -> {
                FinalService service = mock(FinalService.class);
                when(service.name()).thenReturn("mocked-final");
                assertEquals("mocked-final", service.name());
            }));
    }

    @TestFactory
    Stream<DynamicTest> stubsFromTheFactoryAreEffectiveInExecutables() throws Exception {
        mockStatic(Ids.class);
        when(Ids.next()).thenReturn("from-factory");
        return Stream.of(dynamicTest("factory stub", () -> assertEquals("from-factory", Ids.next())));
    }

    @Test
    void controlStaticMockingInPlainTest() {
        mockStatic(Ids.class);
        when(Ids.next()).thenReturn("plain");
        assertEquals("plain", Ids.next());
    }
}
