package powermock.modules.junit5.gaps2.c6perclass;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps2.support.Ids;
import powermock.modules.junit5.gaps2.support.Order;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.spy;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * C6: @TestInstance(PER_CLASS). Instance state carries over from test to test (one instance: every test appends
 * to a list created in @BeforeAll, @AfterAll sees all three); a spy of a prepared class with a stubbed FINAL
 * method, created in the non-static @BeforeAll, keeps working in every test; static mocking set up in @BeforeEach
 * is fresh per test (each test sees its own display name, never another test's stub).
 */
@ExtendWith(PowerMockExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@PrepareForTest({Ids.class, Order.class})
class C6InstanceStateTest {

    private List<String> visited;
    private Order spiedOrder;

    @BeforeAll
    void createStateAndSpy() {
        visited = new ArrayList<String>();
        spiedOrder = spy(new Order("shared", 100));
        doReturn(7).when(spiedOrder).net();
    }

    @BeforeEach
    void freshStaticStubPerTest(TestInfo info) {
        assertEquals("real-id", Ids.next(), "static stub of an earlier test leaked into @BeforeEach");
        mockStatic(Ids.class);
        when(Ids.next()).thenReturn(info.getDisplayName());
    }

    @AfterAll
    void stateCarriedOverAcrossAllTests() {
        List<String> sorted = new ArrayList<String>(visited);
        Collections.sort(sorted);
        assertEquals(Arrays.asList("a()", "b()", "c()"), sorted);
    }

    @Test
    void a() {
        check("a()");
    }

    @Test
    void b() {
        check("b()");
    }

    @Test
    void c() {
        check("c()");
    }

    private void check(String displayName) {
        assertFalse(visited.contains(displayName));
        visited.add(displayName);
        assertEquals(displayName, Ids.next());
        assertEquals(7, spiedOrder.net(), "final method stubbed on the @BeforeAll spy");
        assertEquals("shared", spiedOrder.id());
    }
}
