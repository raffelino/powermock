package powermock.modules.junit5.gaps2.c6perclass;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps2.support.FinalService;
import powermock.modules.junit5.gaps2.support.Ids;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * C6: @Nested PER_CLASS inner class with its own non-static @BeforeAll inside a PER_CLASS outer class. Both
 * @BeforeAll methods create final-class mocks kept in fields; the inner tests use both (and static mocking).
 * 4 tests (1 outer + 3 nested).
 */
@ExtendWith(PowerMockExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@PrepareForTest({FinalService.class, Ids.class})
class C6NestedPerClassTest {

    FinalService outerService;
    int outerBeforeAllRuns;

    @BeforeAll
    void outerSetUp() {
        outerBeforeAllRuns++;
        outerService = mock(FinalService.class);
        when(outerService.name()).thenReturn("outer-mock");
    }

    @Test
    void outerTestUsesOuterMock() {
        assertEquals("outer-mock", outerService.name());
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Inner {

        FinalService innerService;
        int innerBeforeAllRuns;

        @BeforeAll
        void innerSetUp() {
            innerBeforeAllRuns++;
            innerService = mock(FinalService.class);
            when(innerService.name()).thenReturn("inner-mock");
        }

        @Test
        void innerTestUsesBothMocks() {
            assertNotNull(innerService, "inner non-static @BeforeAll must have run on this instance");
            assertEquals("inner-mock", innerService.name());
            assertEquals("outer-mock", outerService.name());
        }

        @Test
        void innerBeforeAllRanOnceOnTheInnerInstance() {
            assertEquals(1, innerBeforeAllRuns);
            assertEquals(1, outerBeforeAllRuns);
            assertEquals("inner-mock", innerService.name());
        }

        @Test
        void innerMocksAlongsideStaticMocking() {
            mockStatic(Ids.class);
            when(Ids.next()).thenReturn("nested-static");
            assertEquals("nested-static", Ids.next());
            assertEquals("inner-mock", innerService.name());
        }
    }
}
