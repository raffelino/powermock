package powermock.modules.junit5.gaps2.c7exceptions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit5.PowerMockExtension;
import powermock.modules.junit5.gaps2.support.Ids;
import powermock.modules.junit5.gaps2.support.RecordingWatcher;
import powermock.modules.junit5.gaps2.support.UserFailure;
import powermock.modules.junit5.gaps2.support.Validator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;
import static org.junit.platform.testkit.engine.EventConditions.event;
import static org.junit.platform.testkit.engine.EventConditions.finishedSuccessfully;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.EventConditions.test;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.instanceOf;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.message;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * C7: how Jupiter and other extensions see a FAILING PowerMock test. Each test here runs one method of the
 * scenario class {@link Scenario} (PowerMockExtension + {@link RecordingWatcher}) with JUnit's EngineTestKit and
 * requires that the failure is reported with the user exception type ({@code instanceof UserFailure}) and its
 * message - in the engine's events, in the TestWatcher and in the AfterTestExecutionCallback
 * ({@code context.getExecutionException()}). 3 tests; the successful scenario is the control.
 * <p>
 * The scenario's deliberately failing tests are parameterized tests whose source yields one invocation only while
 * the enclosing test runs them through EngineTestKit, and none (allowZeroInvocations) when the build discovers the
 * static nested class itself - so they never show up as tests of the build.
 */
class C7ReportedExceptionTypeTest {

    private static volatile boolean runningInEngineTestKit;

    @BeforeEach
    void clearRecords() {
        RecordingWatcher.RECORDED.clear();
    }

    @Test
    void watcherAndCallbackSeeUserExceptionTypeFromPreparedClass() {
        Events events = run("throwsFromPreparedClass");
        events.assertThatEvents().haveExactly(1, event(test("throwsFromPreparedClass"),
            finishedWithFailure(instanceOf(UserFailure.class), message(Validator.EXPECTED_PREFIX + "empty input"))));
        assertEquals(Arrays.asList(
            "afterTestExecution:throwsFromPreparedClass:true:" + Validator.EXPECTED_PREFIX + "empty input",
            "testFailed:throwsFromPreparedClass:true:" + Validator.EXPECTED_PREFIX + "empty input"),
            new ArrayList<String>(RecordingWatcher.RECORDED));
    }

    @Test
    void watcherAndCallbackSeeUserExceptionTypeFromSeparateThreadTimeout() {
        Events events = run("throwsFromStubbedStaticInSeparateThread");
        events.assertThatEvents().haveExactly(1, event(test("throwsFromStubbedStaticInSeparateThread"),
            finishedWithFailure(instanceOf(UserFailure.class), message("stubbed in separate thread"))));
        assertEquals(Arrays.asList(
            "afterTestExecution:throwsFromStubbedStaticInSeparateThread:true:stubbed in separate thread",
            "testFailed:throwsFromStubbedStaticInSeparateThread:true:stubbed in separate thread"),
            new ArrayList<String>(RecordingWatcher.RECORDED));
    }

    @Test
    void controlSuccessfulScenarioIsReportedSuccessful() {
        Events events = run("passesWithStaticMock");
        events.assertThatEvents().haveExactly(1, event(test("passesWithStaticMock"), finishedSuccessfully()));
        List<String> expected = Arrays.asList(
            "afterTestExecution:passesWithStaticMock:false:-", "testSuccessful:passesWithStaticMock:false:-");
        assertEquals(expected, new ArrayList<String>(RecordingWatcher.RECORDED));
    }

    private static Events run(String method) {
        runningInEngineTestKit = true;
        try {
            return EngineTestKit.engine("junit-jupiter")
                .selectors(selectMethod(Scenario.class, method, String.class.getName()))
                .execute()
                .testEvents();
        } finally {
            runningInEngineTestKit = false;
        }
    }

    @ExtendWith({PowerMockExtension.class, RecordingWatcher.class})
    @PrepareForTest({Validator.class, Ids.class})
    static class Scenario {

        /** One invocation when run by the enclosing test through EngineTestKit, none otherwise. */
        static Stream<String> onlyInEngineTestKit() {
            return runningInEngineTestKit ? Stream.of("run") : Stream.<String>empty();
        }

        @ParameterizedTest(allowZeroInvocations = true)
        @MethodSource("onlyInEngineTestKit")
        void throwsFromPreparedClass(String run) {
            Validator.check("");
        }

        @ParameterizedTest(allowZeroInvocations = true)
        @MethodSource("onlyInEngineTestKit")
        @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
        void throwsFromStubbedStaticInSeparateThread(String run) throws Exception {
            mockStatic(Validator.class);
            when(Validator.parse("1")).thenThrow(new UserFailure("stubbed in separate thread"));
            Validator.parse("1");
        }

        @ParameterizedTest(allowZeroInvocations = true)
        @MethodSource("onlyInEngineTestKit")
        void passesWithStaticMock(String run) {
            mockStatic(Ids.class);
            when(Ids.next()).thenReturn("scenario");
            assertEquals("scenario", Ids.next());
        }
    }
}
