package powermock.modules.junit5.gaps.c2nested;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps.support.IdGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * C2: the enclosing class's @BeforeEach/@AfterEach run for nested tests against the same outer instance
 * the nested test sees; the nested test reads and writes the outer instance's fields; a static mock set
 * up in the outer @BeforeEach is in effect in the nested test and verified in the outer @AfterEach.
 */
@ExtendWith(PowerMockExtension.class)
@PrepareForTest(IdGenerator.class)
class C2NestedLifecycleTest {

    private List<String> events;
    private String writtenByTest;

    @BeforeEach
    void outerBeforeEach() {
        events = new ArrayList<>();
        events.add("outer-before");
        writtenByTest = null;
        mockStatic(IdGenerator.class);
        when(IdGenerator.next()).thenReturn("from-outer-before-each");
    }

    @AfterEach
    void outerAfterEach() {
        assertEquals("written", writtenByTest, "outer @AfterEach must see what the test wrote into the outer instance");
        verifyStatic(IdGenerator.class);
        IdGenerator.next();
    }

    @Test
    void controlOuterTest() {
        assertEquals(Arrays.asList("outer-before"), events);
        assertEquals("from-outer-before-each", IdGenerator.next());
        writtenByTest = "written";
    }

    @Nested
    class Inner {

        private String innerState;

        @BeforeEach
        void innerBeforeEach() {
            events.add("inner-before");
            innerState = "inner-" + events.size();
        }

        @Test
        void nestedTestSeesOuterAndInnerBeforeEachOnSameInstances() {
            assertEquals(Arrays.asList("outer-before", "inner-before"), events);
            assertEquals("inner-2", innerState);
            writtenByTest = "written";
            assertEquals("from-outer-before-each", IdGenerator.next());
        }

        @Test
        void nestedTestWritesOuterFieldSeenByOuterAfterEach() {
            C2NestedLifecycleTest.this.writtenByTest = "written";
            assertEquals("from-outer-before-each", IdGenerator.next());
        }
    }
}
