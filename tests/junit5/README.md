# tests/junit5 — acceptance spec for `PowerMockExtension` (JUnit Jupiter 5.14.4)

All classes: `@ExtendWith(PowerMockExtension.class)`, package `powermock.modules.junit5.acceptance`, class-level annotations only.
Red column = result on the empty skeleton extension (same on JDK 17+opens and JDK 8: 18 tests, 4 pass, 14 fail).

| # | Class | Sample class(es) | Key assertion | Red on skeleton |
|---|-------|------------------|---------------|-----------------|
| T1 | T1StaticMockingTest | singleton.StaticService | `say`/`sayFinal` return the stub; `verifyStatic` | ClassNotPreparedException (2/2) |
| T2 | T2FinalClassMockingTest | finalmocking.FinalDemo | `mock(FinalDemo)` stubs `say` | Mockito "cannot mock final class" (1/1) |
| T3 | T3FinalMethodMockingTest | simplemix.SimpleMix, privateandfinal.PrivateFinal | final `calculate()` of a mock returns 42; private final `sayIt` of a spy stubbed | real final body runs: NPE / InvalidUseOfMatchers (2/2) |
| T4 | T4ConstructorInterceptionTest | expectnew.ExpectNewDemo, newmocking.MyClass | `new MyClass()` inside ExpectNewDemo returns the `whenNew` mock / throws; `verifyNew` | `new` not intercepted (no exception thrown; whenNew state leaks between tests) (2/2) |
| T5 | T5WhiteboxOnPreparedClassTest | simplemix.SimpleMix(+Collaborator, final), staticinitializer.StaticInitializerExample | final-class mock injected with `setInternalState`, read back with `getInternalState`; static final field set on a class whose static init is suppressed (`@SuppressStaticInitializationFor`) | "cannot mock final class"; ExceptionInInitializerError (2/2) |
| T6 | T6ChunkATest / T6ChunkBTest | StaticService (A) / staticandinstance.StaticAndInstanceDemo (B) | own prepared class mockable; the class prepared only in the other chunk returns its real value | ClassNotPreparedException (1/2 each; "real" tests pass) |
| T7 | T7NoPrepareForTestTest | StaticService, MyClass, Service | real code + plain Mockito mocks work | green by design (empty extension changes nothing) |
| T8 | T8LifecycleTest | StaticService | `mockStatic` in @BeforeEach seen by test, `verifyStatic` in @AfterEach; static field written in @BeforeAll visible to the test | ClassNotPreparedException in @BeforeEach (3/3) |

Implementers: @BeforeAll/@BeforeEach/@AfterEach must run against the MockClassLoader copy of the test class
(T8 reads a static field written by @BeforeAll). PowerMock state (MockRepository) must be cleared after each test
(T4). Run: `./gradlew :tests:junit5:test` (JDK 9+ needs the lab opens init script).

## Gaps (Lab 13)

Acceptance tests for four capabilities the extension does not have yet. Package `powermock.modules.junit5.gaps`
(sub-packages `c1annotations`, `c2nested`, `c3injection`, `c4extensions`; own sample classes, resolver and extension
in `gaps.support`). Manifest for the oracle: `tests/junit5/GAPS.tsv` (15 classes, 51 tests; @Nested tests count for
their top-level class). Only @Test methods. Test dependency added: `org.mockito:mockito-junit-jupiter:${mockitoVersion}`.

Measured on the current extension with `./gradlew :tests:junit5:clean :tests:junit5:test` (identical on JDK 17 and
JDK 8): 69 tests, 25 pass, 44 fail. The 9 old classes: 18/18 green. Gap classes: 51 tests, 7 pass (controls), 44 fail;
every gap class is red on both JDKs. "Controls" are tests that already pass today (at most one third per class).

| Class | Cap. | What it demands | Red today (measured, JDK 17 = JDK 8) |
|-------|------|-----------------|---------------------------------------|
| C1MockAnnotationTest | C1 | `@Mock` of a plain class and of a FINAL `@PrepareForTest` class initialised, stubbable, verifiable, fresh per test (no invocations/stubbings from another test); `@Mock` + `mockStatic` in one test | 4/5 fail: fields null (AssertionFailedError "must be initialised", NPE). Control (programmatic `mock()` of final class) passes |
| C1SpyAndCaptorTest | C1 | two initialised `@Spy` fields (as PowerMockRunner requires) replaced by PowerMock spies (`isMock()`, real calls, partial stubbing, final method of prepared class stubbed), `@Captor` capturing args to a `@Mock` | 3/3 fail: spy not a mock / spy not a mock / captor null |
| C1InjectMocksTest | C1 | `@InjectMocks UserService` gets the `@Mock` collaborators (one final); combined with `mockStatic` and with `whenNew`/`verifyNew` | 3/3 fail: `@InjectMocks` field null, NPE |
| C1AnnotatedMocksInLifecycleTest | C1 | `@Mock` fields exist before `@BeforeEach`; stubbing in `@BeforeEach` seen by the test; `verify` in `@AfterEach`; final method of prepared-class mock; + `mockStatic` | 3/3 fail in `@BeforeEach`: "@Mock must be initialised before @BeforeEach" |
| C2NestedInheritsPrepareForTest | C2 | `@Nested` class inherits the enclosing `@PrepareForTest`: static mocking and final-class mocking inside the nested test | 2/3 fail: `NoSuchMethodException Inner.<init>()` (shadow needs no-arg ctor). Control (outer test) passes |
| C2NestedLifecycleTest | C2 | outer `@BeforeEach` (incl. `mockStatic`) and inner `@BeforeEach` run on the instances the nested test sees; nested test writes an outer field that the outer `@AfterEach` asserts, outer `@AfterEach` `verifyStatic` | 2/3 fail: `NoSuchMethodException Inner.<init>()`. Control passes |
| C2DoubleNestingTest | C2 | two nesting levels: all three `@BeforeEach` levels in order, outer final-class mock usable in the innermost test, static mocking of outer-prepared class | 3/3 fail: `NoSuchMethodException Level1.<init>()` |
| C2NestedOwnPrepareForTest | C2 | nested class adds its own `@PrepareForTest`; inside it both the outer and the nested prepared classes are mockable; `whenNew` for a class prepared only by the nested class | 2/3 fail: `NoSuchMethodException WithMorePreparedClasses.<init>()`. Control passes |
| C3ConstructorInjectionTest | C3 | only constructor `(TestInfo, Account)`; TestInfo is Jupiter's (test class, display name of class or method); `Account` from the custom `AccountResolver` uses the mocked static of a prepared class; coexists with final mocking | 3/3 fail: `NoSuchMethodException C3ConstructorInjectionTest.<init>()` |
| C3MethodParameterInjectionTest | C3 | `Account` from custom resolver as test and `@BeforeEach` parameter behaves as an object of a prepared class (mocked static inside, `spy` + stub of final method); TestInfo/TestReporter params are the current ones | 4/4 fail: `IllegalArgumentException: argument type mismatch` (app-loader Account passed to MockClassLoader method; `@BeforeEach` takes Account, so the TestInfo control fails too) |
| C3TempDirTest | C3 | `@TempDir` field injected, empty, usable (also with `mockStatic`); an `AfterEachCallback` reads the field from `getRequiredTestInstance()` and must find the file the test wrote (= the directory Jupiter manages and deletes) | 3/4 fail: field null. Control (`@TempDir` parameter) passes |
| C4PowerMockThenMockitoExtensionTest | C4 | `@ExtendWith({PowerMockExtension, MockitoExtension})`: `@Mock` field is a usable mock (stub/verify) and works with `mockStatic`/`verifyStatic` in one test | 2/3 fail: `@Mock` null / NPE. Control (static mocking alone with MockitoExtension present) passes |
| C4MockitoThenPowerMockExtensionTest | C4 | same, registration order reversed | 2/3 fail: `@Mock` null / NPE. Control passes |
| C4PowerMockThenCustomExtensionTest | C4 | `InstanceStateExtension` (TestInstancePostProcessor sets a field; BeforeEachCallback reads it and writes another via `getRequiredTestInstance()`; AfterEachCallback requires what the test wrote) registered after PowerMockExtension; `@BeforeEach` method also sees the callback's write; + `mockStatic` | 4/4 fail: fields null in the test; AfterEachCallback "expected writtenByTest=... but found null" |
| C4CustomThenPowerMockExtensionTest | C4 | same, custom extension registered first | 4/4 fail: same reasons |

Notes for implementers:
- Fairness: nothing depends on the shadow design. `AccountResolver` and `InstanceStateExtension` are class-loader
  neutral (match types/fields by name, instantiate the parameter's declared type), so both "shadow + sync/transfer"
  and "Jupiter's own instance lives in the MockClassLoader" (TestInstanceFactory) designs can satisfy them.
- C1 mirrors `tests/mockito/junit4/.../annotationbased/*` (PowerMockRunner uses `AnnotationEnabler` + Mockito's annotation engine).
- C4 with MockitoExtension: MockitoExtension runs Mockito from the application class loader; the test code runs
  Mockito from the MockClassLoader. The tests only demand that the `@Mock` field seen by the test is a working mock for
  the Mockito the test code uses (e.g. PowerMock (re)initialises the annotated fields in the MockClassLoader).
- Jupiter-side assumptions (constructor TestInfo, `@TempDir` field visible to an AfterEachCallback before deletion,
  the custom extension's semantics, MockitoExtension + `@Mock`) were checked by running a copy of these classes with
  PowerMockExtension replaced by a no-op extension (JDK 17): all non-PowerMock parts passed; the copy was deleted.

## Gaps, round 2 (Lab 13)

Acceptance tests for four further capabilities. Package `powermock.modules.junit5.gaps2` (sub-packages
`c5templates`, `c6perclass`, `c7exceptions`, `c8easymock`; own sample classes and extensions in `gaps2.support`).
Manifest: 12 lines appended to `tests/junit5/GAPS.tsv`; the number is the count of test INVOCATIONS a correct
implementation produces (each parameterized invocation, repetition and dynamic test counts as one; @Nested tests count
for their top-level class). Test dependencies added: `org.junit.platform:junit-platform-testkit:1.14.4` (C7),
`powermock-api-easymock` and `org.easymock:easymock:${easymockVersion}` (C8).

None of these is satisfied by a solution of C1-C4 (fresh Mockito annotations in the MockClassLoader, one
MockClassLoader per top-level class incl. @Nested, Jupiter-resolved parameters transferred into the MockClassLoader,
two-way instance field sync):
- C5 needs the TestFactory/dynamic-test invocations (not intercepted at all today) to run in the MockClassLoader, and
  arguments that Jupiter creates itself (Class literals, enum constants, implicit String conversion, @MethodSource
  objects) mapped onto the MockClassLoader's classes.
- C6 needs PowerMock state created in a non-static @BeforeAll (spies with stubbed final methods, verification counts
  of a shared mock) to survive the per-test reset, and PER_CLASS nested instances with their own @BeforeAll.
- C7 needs exceptions leaving the MockClassLoader to be seen by Jupiter and other extensions with the application
  class loader's type (instanceof), also from a SEPARATE_THREAD @Timeout, and @RegisterExtension objects shared
  between Jupiter and the test code.
- C8 needs PowerMock's EasyMock annotation support (`@Mock`, `@MockNice`, `@MockStrict` of
  `org.powermock.api.easymock.annotation`), which Mockito's annotation engine does not handle.

Measured with `./gradlew :tests:junit5:clean :tests:junit5:test` (JDK 17 and JDK 8, identical per class): 121 test
cases in the XML (old 18/18 green; round 1 unchanged, 7 of 51 pass; round 2 see below). "Controls" already pass
today (at most one third of a class's invocations).

| Class | Cap. | What it demands | Red today (measured, JDK 17 = JDK 8) |
|-------|------|-----------------|---------------------------------------|
| C5ValueAndCsvSourceTest | C5 | @ValueSource Class literal is the prepared class (`mockStatic(type)`); @ValueSource String implicitly converted to a user class, @CsvSource enum column and converted user object: static mocking inside their methods, final-method stub on a spy, enum constant identical; @TestFactory with one dynamic test per `Tier` constant: `mockStatic(Ids)` in the executable is used by `tier.tagged()` | 8/10 fail: `IllegalArgumentException: argument type mismatch` (app-loader objects passed to MockClassLoader method), Class literal not the test's class, dynamic tests `ClassNotPreparedException`. Controls: 2 int invocations |
| C5MethodSourceAndRepeatedTest | C5 | @MethodSource objects of a prepared user class use a mocked static (`TaxRates`) and allow final-method stubbing on a spy; @TestFactory with one dynamic test per `orders()` element: `mockStatic(TaxRates)` in the executable is used by the sourced `Order`; @RepeatedTest(2) with RepetitionInfo gets fresh PowerMock state | 6/8 fail: argument type mismatch, dynamic tests `ClassNotPreparedException`. Controls: the 2 repetitions |
| C5TestFactoryTest | C5 | DynamicTests from @TestFactory: `mockStatic`, `whenNew` of a prepared class, mock of a final class inside executables; a static stub set up in the factory is effective in the executable | 5/6 fail: `ClassNotPreparedException` (executables and factory run outside the MockClassLoader), whenNew ineffective, "Cannot mock final class"; the factory with the stub fails as a whole (1 XML case instead of 1 dynamic test). Control: plain @Test |
| C6BeforeAllMockTest | C6 | PER_CLASS: non-static @BeforeAll creates a mock of a FINAL class and a spy of a prepared class with a stubbed final method; all 3 tests use them; non-static @AfterAll verifies the shared mock/spy were used 3 times | 2/3 fail (whichever tests run after the first): spy stub lost after the first test's reset (`expected 9 but was 5`); @AfterAll `TooFewActualInvocations` (extra `executionError` case) |
| C6InstanceStateTest | C6 | PER_CLASS: instance state from @BeforeAll carries over (@AfterAll sees all 3 tests), spy with stubbed final method from @BeforeAll works in every test, static stub from @BeforeEach fresh per test | 2/3 fail: spy stub lost after first test (`expected 7 but was 100`) |
| C6NestedPerClassTest | C6 | @Nested PER_CLASS inner class with its own non-static @BeforeAll; outer and inner @BeforeAll each create a final-class mock and a spy of `Order` with stubbed final `net()` (9); the outer test uses the outer spy, every inner test uses both spies and both mocks; each @BeforeAll ran once | nested class fails as a whole: `NoSuchMethodException Inner.<init>()` (2 XML cases instead of 4). Control: outer test (passes today only because it runs first; a per-test reset loses the spy stubs) |
| C7ExceptionHandlerTest | C7 | `UserFailureHandler` (TestExecutionExceptionHandler, `instanceof UserFailure`) swallows user exceptions thrown by a prepared class, by a stubbed static, and under @Timeout SEPARATE_THREAD; its AfterTestExecutionCallback requires the swallow and an empty `getExecutionException()` | 3/4 fail: the UserFailure (MockClassLoader type) is not `instanceof` the handler's UserFailure, rethrown. Control: assertThrows inside the test |
| C7ReportedExceptionTypeTest | C7 | runs a scenario class (PowerMockExtension + `RecordingWatcher`) through EngineTestKit: failure event, TestWatcher.testFailed and AfterTestExecutionCallback's `getExecutionException()` carry a `UserFailure` (instanceof) with its message; also with @Timeout SEPARATE_THREAD | 2/3 fail: event condition `instanceOf(UserFailure)` not met. Control: successful scenario reported successful |
| C7RegisterExtensionAndTimeoutTest | C7 | @RegisterExtension instance field is the object Jupiter calls: the test reads what its beforeEach prepared and records what its afterEach checks, also under default @Timeout with final-class mocking; static stub from @BeforeEach works in a SEPARATE_THREAD @Timeout body | 2/3 fail: the test sees a different extension object (`preparedFor()` null). Control: SEPARATE_THREAD |
| C8AnnotationMockTest | C8 | EasyMock-API `@Mock` (default: unexpected call fails) and `@MockNice` of a final class injected, expect/replayAll/verify | 2/3 fail: fields null. Control: `PowerMock.createMock` of the final class |
| C8StaticAndNewWithAnnotationsTest | C8 | `mockStatic` + `expect` + `expectNew` returning an annotated `@Mock`, replayAll/verifyAll; `@MockStrict` of a final class checks call order | 2/3 fail: fields null. Control: static + expectNew with programmatic mock |
| C8ResetBetweenTestsTest | C8 | automatic reset: every test records, replays, verifies the annotated `@Mock` and a static mock (a mock replayed by another test would reject `expect`), no static stub leaks | 2/3 fail: field null. Control: programmatic mocks |

Notes for implementers:
- The scenario class of C7ReportedExceptionTypeTest (`$Scenario`) consists of parameterized tests with
  `allowZeroInvocations = true` whose source yields one invocation only while the enclosing test drives it through
  EngineTestKit, so the build itself reports no test for it. Its failures are deliberate.
- C8 puts `powermock-api-easymock` next to `powermock-api-mockito2`. Both contain a class
  `org.powermock.api.extension.listener.AnnotationEnabler`; use `EasyMockAnnotationSupport` rather than the colliding
  name.
- C5 dynamic tests: Jupiter runs no afterEach between the dynamic tests of one factory; no test requires a reset there.
- C6 order independence: every test of a class does the same with the shared objects; which test runs first does not
  matter for a correct implementation (today, whichever runs first passes).
