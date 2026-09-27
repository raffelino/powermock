package powermock.modules.junit5.acceptance;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import samples.singleton.StaticService;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/** T9: @RepeatedTest, @ParameterizedTest and @TestFactory (incl. its dynamic tests) run under PowerMock. */
@ExtendWith(PowerMockExtension.class)
@PrepareForTest(StaticService.class)
class T9TestTemplatesAndFactoriesTest {

    @RepeatedTest(2)
    void repeatedTestCanMockStatics() {
        assertMockedStatic("world");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "b"})
    void parameterizedTestCanMockStatics(String name) {
        assertMockedStatic(name);
    }

    @TestFactory
    Stream<DynamicTest> dynamicTestsCanMockStatics() {
        return Stream.of("x", "y").map(name -> dynamicTest(name, () -> assertMockedStatic(name)));
    }

    private static void assertMockedStatic(String name) {
        mockStatic(StaticService.class);
        when(StaticService.say(name)).thenReturn("mocked " + name);
        assertEquals("mocked " + name, StaticService.say(name));
    }
}
