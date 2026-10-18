package greycos.solver.core.api.solver;

import static greycos.solver.core.api.solver.LegacyIndictmentApiFixture.check;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Function;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.ConstraintMatchRegistry;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.score.stream.DefaultConstraintJustification;
import greycos.solver.core.api.score.stream.test.SingleConstraintAssertion;

/** Candidate-side assertions, also run without JUnit against the baseline-compiled fixture. */
public final class IndictmentApiCompatibilityProbe {

  private IndictmentApiCompatibilityProbe() {}

  public static void main(String[] args) {
    LegacyIndictmentApiFixture.verifyLegacyCalls();
    verifyAnalysisDefaults();
    verifyManagerDefaults();
    verifyRegistryDefaults();
    verifyBuilderDefaults();
    verifyAssertionDefaults();
    System.out.println(
        "Indictment API compatibility passed on Java " + Runtime.version().feature() + ".");
  }

  public static void verifyAnalysisDefaults() {
    var analysis = new LegacyIndictmentApiFixture.LegacyAnalysis(SimpleScore.ONE);
    check(analysis.indictmentMap() == null, "Legacy terminal attribution must be unavailable.");
    check(
        analysis.originalIndictmentMap() == null,
        "Legacy original attribution must be unavailable.");
    expect(IllegalStateException.class, () -> analysis.getIndictmentAnalysis(new Object()));
    expect(IllegalStateException.class, () -> analysis.getOriginalIndictmentAnalysis(new Object()));
  }

  public static void verifyManagerDefaults() {
    var manager = new LegacyIndictmentApiFixture.LegacySolutionManager();
    var solution = new Object();
    check(
        manager.analyze(
                solution,
                ScoreAnalysisFetchPolicy.FETCH_SHALLOW,
                SolutionUpdatePolicy.NO_UPDATE,
                IndictmentFetchPolicy.NONE)
            == manager.analysis,
        "NONE must delegate to the old analyze method.");
    check(
        manager.lastSolution == solution
            && manager.lastFetchPolicy == ScoreAnalysisFetchPolicy.FETCH_SHALLOW
            && manager.lastUpdatePolicy == SolutionUpdatePolicy.NO_UPDATE,
        "NONE must preserve analyze arguments.");
    check(
        manager.analyzeWithIndictments(solution, IndictmentFetchPolicy.NONE) == manager.analysis,
        "The named convenience method must support NONE on old implementations.");
    check(
        manager.lastFetchPolicy == ScoreAnalysisFetchPolicy.FETCH_ALL
            && manager.lastUpdatePolicy == SolutionUpdatePolicy.UPDATE_ALL,
        "The named convenience method must use its documented defaults.");
    check(
        manager
            .recommendAssignment(
                solution,
                solution,
                Function.identity(),
                ScoreAnalysisFetchPolicy.FETCH_MATCH_COUNT,
                IndictmentFetchPolicy.NONE)
            .isEmpty(),
        "NONE must delegate to the old recommendAssignment method.");
    check(
        manager.lastSolution == solution
            && manager.lastFetchPolicy == ScoreAnalysisFetchPolicy.FETCH_MATCH_COUNT,
        "NONE must preserve recommendation arguments.");
    for (var policy :
        List.of(IndictmentFetchPolicy.TERMINAL, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL)) {
      expect(
          UnsupportedOperationException.class,
          () ->
              manager.analyze(
                  solution,
                  ScoreAnalysisFetchPolicy.FETCH_ALL,
                  SolutionUpdatePolicy.UPDATE_ALL,
                  policy));
      expect(
          UnsupportedOperationException.class,
          () -> manager.analyzeWithIndictments(solution, policy));
      expect(
          UnsupportedOperationException.class,
          () ->
              manager.recommendAssignment(
                  solution,
                  solution,
                  Function.identity(),
                  ScoreAnalysisFetchPolicy.FETCH_ALL,
                  policy));
    }
    check(
        manager.analysisCalls == 2 && manager.recommendationCalls == 1,
        "Unsupported requests must not silently call legacy implementations.");
  }

  public static void verifyRegistryDefaults() {
    ConstraintMatchRegistry<SimpleScore> registry = new LegacyIndictmentApiFixture.LegacyRegistry();
    check(
        registry.indictmentFetchPolicy() == IndictmentFetchPolicy.NONE,
        "Legacy registries must request no attribution.");
    var ref = ConstraintRef.of("legacy");
    var object = new Object();
    var justification = DefaultConstraintJustification.of(SimpleScore.ONE, object);
    var terminal =
        registry.registerConstraintMatchWithIndictments(
            ref, SimpleScore.ONE, justification, List.of(object));
    var original =
        registry.registerConstraintMatchWithIndictments(
            ref, SimpleScore.ONE, justification, List.of(object), List.of(object));
    for (var registration : List.of(terminal, original)) {
      check(
          registration.justification() == justification,
          "Registry fallback changed justification.");
      check(registration.score().equals(SimpleScore.ONE), "Registry fallback changed the score.");
      check(
          registration.indictedObjects() == null,
          "Legacy registrations cannot invent terminal data.");
      check(
          registration.originalIndictedObjects() == null,
          "Legacy registrations cannot invent original data.");
    }
  }

  public static void verifyBuilderDefaults() {
    var builder = new LegacyIndictmentApiFixture.LegacyBuilder();
    expect(UnsupportedOperationException.class, () -> builder.indictWith(a -> List.of(a)));
    expect(UnsupportedOperationException.class, () -> builder.indictWith((a, b) -> List.of(a, b)));
    expect(
        UnsupportedOperationException.class,
        () -> builder.indictWith((a, b, c) -> List.of(a, b, c)));
    expect(
        UnsupportedOperationException.class,
        () -> builder.indictWith((a, b, c, d) -> List.of(a, b, c, d)));
  }

  public static void verifyAssertionDefaults() {
    // The baseline inventory verifies abstract-method compatibility for this large interface.
    // This proxy tests the real default implementations, never intercepting them with fake results.
    var assertion =
        (SingleConstraintAssertion)
            Proxy.newProxyInstance(
                SingleConstraintAssertion.class.getClassLoader(),
                new Class<?>[] {SingleConstraintAssertion.class},
                (proxy, method, args) -> {
                  if (method.isDefault()) {
                    return InvocationHandler.invokeDefault(proxy, method, args);
                  }
                  throw new AssertionError(
                      "An indictment assertion called an abstract legacy method: " + method);
                });
    for (var method : SingleConstraintAssertion.class.getMethods()) {
      if (method.getName().startsWith("originallyIndicts")
          || method.getName().startsWith("indicts")) {
        check(method.isDefault(), "New assertion methods must be defaults: " + method);
      }
    }
    var object = new Object();
    expect(UnsupportedOperationException.class, () -> assertion.indictsWith(object));
    expect(
        UnsupportedOperationException.class, () -> assertion.indictsWithMessage("message", object));
    expect(UnsupportedOperationException.class, () -> assertion.indictsWithExactly(object));
    expect(
        UnsupportedOperationException.class,
        () -> assertion.indictsExactlyWithMessage("message", object));
    expect(UnsupportedOperationException.class, () -> assertion.originallyIndictsWith(object));
    expect(
        UnsupportedOperationException.class,
        () -> assertion.originallyIndictsWithMessage("message", object));
    expect(
        UnsupportedOperationException.class, () -> assertion.originallyIndictsWithExactly(object));
    expect(
        UnsupportedOperationException.class,
        () -> assertion.originallyIndictsExactlyWithMessage("message", object));
  }

  private static void expect(Class<? extends RuntimeException> exceptionType, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException exception) {
      if (exception.getClass() == exceptionType) {
        return;
      }
      throw new AssertionError(
          "Expected " + exceptionType.getName() + ", got " + exception, exception);
    }
    throw new AssertionError("Expected " + exceptionType.getName() + ".");
  }
}
