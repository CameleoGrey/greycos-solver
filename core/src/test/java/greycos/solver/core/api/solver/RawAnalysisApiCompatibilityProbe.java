package greycos.solver.core.api.solver;

import static greycos.solver.core.api.solver.LegacyRawAnalysisApiFixture.check;

import java.util.List;
import java.util.function.Function;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.score.stream.DefaultConstraintJustification;

/** Candidate-side calls executed against unchanged, baseline-compiled custom implementations. */
public final class RawAnalysisApiCompatibilityProbe {
  private RawAnalysisApiCompatibilityProbe() {}

  public static void main(String[] args) {
    LegacyRawAnalysisApiFixture.verifyLegacyCalls();
    verifyUnavailableRawDefaults();
    verifyLegacyMatchCountAvailability();
    verifyNewHelpersOnOldImplementations();
    verifyManagerDelegation();
    verifyUnsupportedRawRequests();
    LegacyRawAnalysisConstructorFixture.verifyFiveArgumentConstructor();
    System.out.println(
        "Raw-analysis API compatibility passed on Java " + Runtime.version().feature() + ".");
  }

  public static void verifyUnavailableRawDefaults() {
    var analysis = LegacyRawAnalysisApiFixture.LegacyAnalysis.create();
    check(analysis.rawAnalysis() == null, "Old custom analyses must not invent raw occurrences.");
    check(
        analysis.getConstraintAnalysis("legacy-raw").rawMatchCount() == null,
        "Old custom constraints cannot infer raw counts from grouped matches.");
    check(
        analysis.getIndictmentAnalysis("object").matchCount() == 2,
        "An old custom indictment must retain its original occurrence count.");
  }

  public static void verifyLegacyMatchCountAvailability() {
    var ref = ConstraintRef.of("legacy-count");
    for (int count : List.of(-2, -1, 0, 1)) {
      var constraint =
          new LegacyRawAnalysisApiFixture.LegacyConstraint(
              ref, SimpleScore.ONE, SimpleScore.ZERO, null, count);
      check(
          constraint.isMatchCountAvailable() == (count != -1),
          "Legacy count availability must preserve only the -1 unavailable sentinel.");
    }
    var fullDifference =
        new LegacyRawAnalysisApiFixture.LegacyConstraint(
            ref, SimpleScore.ONE, SimpleScore.ZERO, List.of(), -1);
    check(
        fullDifference.isMatchCountAvailable(),
        "A legacy full-match difference of -1 must remain available.");
  }

  public static void verifyNewHelpersOnOldImplementations() {
    var analysis = LegacyRawAnalysisApiFixture.LegacyAnalysis.create();
    expectUnavailable(analysis::justifications);
    expectUnavailable(() -> analysis.justifications(ConstraintJustification.class));
    var justification = new LegacyRawAnalysisApiFixture.LegacyJustification("same reason");
    var first =
        new LegacyRawAnalysisApiFixture.LegacyMatch(
            ConstraintRef.of("first"), SimpleScore.MINUS_ONE, justification, 1);
    var second =
        new LegacyRawAnalysisApiFixture.LegacyMatch(
            ConstraintRef.of("second"),
            SimpleScore.MINUS_ONE,
            new LegacyRawAnalysisApiFixture.LegacyJustification("same reason"),
            1);
    var indictment =
        new LegacyRawAnalysisApiFixture.LegacyIndictment(
            "object", new SimpleScore(-2), List.of(first, second), 2);
    check(
        indictment.justifications().size() == 1
            && indictment.justifications().getFirst() == justification,
        "New indictment helpers must preserve distinct equality and first encounter order.");
    check(
        indictment.justifications(ConstraintJustification.class).size() == 1
            && indictment.justifications(DefaultConstraintJustification.class).isEmpty(),
        "Typed justification filtering must work on old custom indictment implementations.");
    check(
        !indictment.summarize().isBlank()
            && !indictment.summarize(1).isBlank()
            && !analysis.summarize(1, 1).isBlank(),
        "New diagnostic defaults must work on old custom implementations.");
  }

  public static void verifyManagerDelegation() {
    var manager = new LegacyRawAnalysisApiFixture.LegacyManager();
    var solution = new Object();
    var options =
        new ScoreAnalysisOptions(
            ScoreAnalysisFetchPolicy.FETCH_ALL, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL, false);
    check(
        manager.analyzeWithOptions(solution, options, SolutionUpdatePolicy.NO_UPDATE)
            == manager.analysis,
        "Raw-disabled options must delegate to the existing manager method.");
    check(
        manager.lastSolution == solution
            && manager.lastFetchPolicy == ScoreAnalysisFetchPolicy.FETCH_ALL
            && manager.lastUpdatePolicy == SolutionUpdatePolicy.NO_UPDATE
            && manager.lastIndictmentPolicy == IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL,
        "Options delegation changed existing arguments.");
    check(
        manager.analyzeWithOptions(solution, options) == manager.analysis,
        "The convenience method must support old indictment-aware managers.");
    check(
        manager.lastUpdatePolicy == SolutionUpdatePolicy.UPDATE_ALL,
        "The convenience method must retain UPDATE_ALL.");
    check(
        manager
            .recommendAssignmentWithOptions(solution, solution, Function.identity(), options)
            .isEmpty(),
        "Raw-disabled recommendation options must delegate to the old method.");
    check(
        manager.lastIndictmentPolicy == IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL
            && manager.analysisCalls == 2
            && manager.recommendationCalls == 1,
        "Old manager delegation count or indictment policy changed.");
  }

  public static void verifyUnsupportedRawRequests() {
    var manager = new LegacyRawAnalysisApiFixture.LegacyManager();
    var solution = new Object();
    var options =
        new ScoreAnalysisOptions(
            ScoreAnalysisFetchPolicy.FETCH_ALL, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL, true);
    expectUnsupported(() -> manager.analyzeWithOptions(solution, options));
    expectUnsupported(
        () -> manager.analyzeWithOptions(solution, options, SolutionUpdatePolicy.NO_UPDATE));
    expectUnsupported(
        () ->
            manager.recommendAssignmentWithOptions(
                solution, solution, Function.identity(), options));
    check(
        manager.analysisCalls == 0 && manager.recommendationCalls == 0,
        "Unsupported raw requests must not silently invoke the legacy implementation.");
  }

  private static void expectUnsupported(Runnable action) {
    try {
      action.run();
    } catch (UnsupportedOperationException expected) {
      return;
    }
    throw new AssertionError("Old custom implementations must reject unsupported raw analysis.");
  }

  private static void expectUnavailable(Runnable action) {
    try {
      action.run();
    } catch (IllegalStateException expected) {
      return;
    }
    throw new AssertionError(
        "Old grouped-only analyses must report raw justifications unavailable.");
  }
}
