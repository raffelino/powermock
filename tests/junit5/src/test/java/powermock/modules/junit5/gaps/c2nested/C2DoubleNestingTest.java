package powermock.modules.junit5.gaps.c2nested;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps.support.Greeter;
import powermock.modules.junit5.gaps.support.IdGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/** C2: two levels of @Nested; outer @PrepareForTest and all @BeforeEach levels apply to the innermost test. */
@ExtendWith(PowerMockExtension.class)
@PrepareForTest({IdGenerator.class, Greeter.class})
class C2DoubleNestingTest {

    private StringBuilder trace;
    private Greeter greeter;

    @BeforeEach
    void level0() {
        trace = new StringBuilder("0");
        greeter = mock(Greeter.class);
        when(greeter.greet("deep")).thenReturn("final-mocked");
    }

    @Nested
    class Level1 {

        @BeforeEach
        void level1() {
            trace.append("1");
        }

        @Test
        void levelOneSeesOuterState() {
            assertEquals("01", trace.toString());
            assertEquals("final-mocked", greeter.greet("deep"));
        }

        @Nested
        class Level2 {

            @BeforeEach
            void level2() {
                trace.append("2");
            }

            @Test
            void innermostSeesAllBeforeEachAndOuterMocks() {
                assertEquals("012", trace.toString());
                assertEquals("final-mocked", greeter.greet("deep"));
            }

            @Test
            void innermostCanMockStaticsOfOuterPrepareForTest() {
                mockStatic(IdGenerator.class);
                when(IdGenerator.next()).thenReturn("deep-static");
                assertEquals("deep-static", IdGenerator.next());
                assertEquals("012", trace.toString());
            }
        }
    }
}
