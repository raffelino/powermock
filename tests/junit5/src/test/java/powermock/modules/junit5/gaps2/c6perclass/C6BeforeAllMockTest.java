package powermock.modules.junit5.gaps2.c6perclass;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps2.support.FinalService;
import powermock.modules.junit5.gaps2.support.Ids;
import powermock.modules.junit5.gaps2.support.Order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * C6: @TestInstance(PER_CLASS). A non-static @BeforeAll creates and stubs a mock of a FINAL prepared class and a
 * spy of a prepared class with a stubbed FINAL method, kept in instance fields and used by every test; a non-static @AfterAll verifies both
 * were used by all three tests. Static mocking inside a test does not disturb the shared mock. Order-independent: every test does the
 * same with the shared mock.
 */
@ExtendWith(PowerMockExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@PrepareForTest({FinalService.class, Ids.class, Order.class})
class C6BeforeAllMockTest {

    private FinalService shared;
    private Order sharedSpy;

    @BeforeAll
    void createSharedMockOfFinalClass() {
        shared = mock(FinalService.class);
        when(shared.name()).thenReturn("shared-mock");
        when(shared.compute(2)).thenReturn(42);
        sharedSpy = spy(new Order("shared-order", 5));
        doReturn(9).when(sharedSpy).net();
    }

    @AfterAll
    void sharedMockWasUsedByAllTests() {
        verify(shared, times(3)).name();
        verify(sharedSpy, times(3)).id();
    }

    @Test
    void firstUseOfSharedMock() {
        assertNotNull(shared);
        assertEquals("shared-mock", shared.name());
        assertEquals(42, shared.compute(2));
        assertEquals(9, sharedSpy.net(), "final method stubbed on the @BeforeAll spy");
        assertEquals("shared-order", sharedSpy.id());
    }

    @Test
    void secondUseOfSharedMock() {
        assertNotNull(shared);
        assertEquals("shared-mock", shared.name());
        assertEquals(0, shared.compute(3));
        assertEquals(9, sharedSpy.net(), "final method stubbed on the @BeforeAll spy");
        assertEquals("shared-order", sharedSpy.id());
    }

    @Test
    void sharedMockAlongsideStaticMocking() {
        mockStatic(Ids.class);
        when(Ids.next()).thenReturn("static");
        assertEquals("static", Ids.next());
        assertEquals(9, sharedSpy.net(), "final method stubbed on the @BeforeAll spy");
        assertEquals("shared-order", sharedSpy.id());
        assertEquals("shared-mock", shared.name());
    }
}
