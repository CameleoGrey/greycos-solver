package greycos.solver.core.api.solver;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.analysis.ConstraintAnalysis;
import greycos.solver.core.api.score.analysis.IndictmentAnalysis;
import greycos.solver.core.api.score.analysis.IndictmentMatchAnalysis;
import greycos.solver.core.api.score.analysis.MatchAnalysis;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.preview.api.cotwin.solution.diff.PlanningSolutionDiff;

/** Public-API implementations that compile against the frozen pre-raw-analysis baseline. */
public final class LegacyRawAnalysisApiFixture {

  private LegacyRawAnalysisApiFixture() {}

  public static void main(String[] args) {
    if (args.length == 1 && args[0].equals("inventory")) {
      for (var api :
          List.of(
              ScoreAnalysis.class,
              ConstraintAnalysis.class,
              MatchAnalysis.class,
              IndictmentAnalysis.class,
              IndictmentMatchAnalysis.class,
              SolutionManager.class)) {
        Arrays.stream(api.getMethods())
            .map(
                method ->
                    api.getName()
                        + "|"
                        + (Modifier.isAbstract(method.getModifiers()) ? "abstract" : "concrete")
                        + "|"
                        + (Modifier.isStatic(method.getModifiers()) ? "static" : "instance")
                        + "|"
                        + method.getName()
                        + "("
                        + Arrays.stream(method.getParameterTypes())
                            .map(Class::getName)
                            .collect(Collectors.joining(","))
                        + "):"
                        + method.getReturnType().getName())
            .sorted()
            .forEach(System.out::println);
      }
    } else {
      verifyLegacyCalls();
      System.out.println("Pre-raw-analysis public API calls passed.");
    }
  }

  public static void verifyLegacyCalls() {
    check(ScoreAnalysisFetchPolicy.values().length == 3, "Existing score fetch enum changed.");
    check(IndictmentFetchPolicy.values().length == 3, "Existing indictment fetch enum changed.");
    for (var policy : ScoreAnalysisFetchPolicy.values()) {
      check(scorePolicy(policy) > 0, "Old exhaustive score-policy switch failed.");
    }
    for (var policy : IndictmentFetchPolicy.values()) {
      check(indictmentPolicy(policy) > 0, "Old exhaustive indictment-policy switch failed.");
    }
    var manager = new LegacyManager();
    var solution = new Object();
    check(
        manager.analyze(solution, null) == manager.analysis, "Old null analyze overload changed.");
    check(
        manager.analyze(solution, null, null) == manager.analysis,
        "Old three-argument analyze changed.");
    check(
        manager.analyze(solution, null, null, null) == manager.analysis,
        "Old four-argument analyze must remain unambiguous.");
    check(
        manager.recommendAssignment(solution, solution, Function.identity(), null).isEmpty(),
        "Old recommendation overload changed.");
    check(
        manager.recommendAssignment(solution, solution, Function.identity(), null, null).isEmpty(),
        "Old five-argument recommendation must remain unambiguous.");
    var analysis = manager.analysis;
    check(analysis.constraintAnalyses().size() == 1, "Old constraint enumeration changed.");
    check(
        analysis.getConstraintAnalysis("legacy-raw").matchCount() == 1,
        "Old grouped match count changed.");
    check(
        analysis.getIndictmentAnalysis("object").matchCount() == 2,
        "Old indictment occurrence count changed.");
    check(
        analysis.getIndictmentAnalysis("object").matches().getFirst().matchCount() == 2,
        "Old indictment group count changed.");
  }

  private static int scorePolicy(ScoreAnalysisFetchPolicy policy) {
    return switch (policy) {
      case FETCH_ALL -> 1;
      case FETCH_SHALLOW -> 2;
      case FETCH_MATCH_COUNT -> 3;
    };
  }

  private static int indictmentPolicy(IndictmentFetchPolicy policy) {
    return switch (policy) {
      case NONE -> 1;
      case TERMINAL -> 2;
      case TERMINAL_AND_ORIGINAL -> 3;
    };
  }

  static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  public record LegacyJustification(String reason) implements ConstraintJustification {}

  public record LegacyMatch(
      ConstraintRef constraintRef,
      SimpleScore score,
      ConstraintJustification justification,
      long matchCount)
      implements IndictmentMatchAnalysis<SimpleScore> {
    @Override
    public int compareTo(MatchAnalysis<SimpleScore> other) {
      return score.compareTo(other.score());
    }
  }

  public record LegacyConstraint(
      ConstraintRef constraintRef,
      SimpleScore weight,
      SimpleScore score,
      List<MatchAnalysis<SimpleScore>> matches,
      int matchCount)
      implements ConstraintAnalysis<SimpleScore> {
    @Override
    public String summarize() {
      return summarize(3);
    }

    @Override
    public String summarize(int limit) {
      return constraintRef.id();
    }
  }

  public record LegacyIndictment(
      Object indictedObject,
      SimpleScore score,
      List<IndictmentMatchAnalysis<SimpleScore>> matches,
      long matchCount)
      implements IndictmentAnalysis<SimpleScore> {}

  public record LegacyAnalysis(
      SimpleScore score,
      SequencedMap<ConstraintRef, ConstraintAnalysis<SimpleScore>> constraintMap,
      SequencedMap<Object, IndictmentAnalysis<SimpleScore>> indictmentMap)
      implements ScoreAnalysis<SimpleScore> {
    public static LegacyAnalysis create() {
      var ref = ConstraintRef.of("legacy-raw");
      var score = new SimpleScore(-2);
      var match = new LegacyMatch(ref, score, new LegacyJustification("reason"), 2);
      SequencedMap<ConstraintRef, ConstraintAnalysis<SimpleScore>> constraints =
          new LinkedHashMap<>();
      constraints.put(ref, new LegacyConstraint(ref, SimpleScore.ONE, score, List.of(match), 1));
      SequencedMap<Object, IndictmentAnalysis<SimpleScore>> indictments = new LinkedHashMap<>();
      indictments.put("object", new LegacyIndictment("object", score, List.of(match), 2));
      return new LegacyAnalysis(score, constraints, indictments);
    }

    @Override
    public boolean isSolutionInitialized() {
      return true;
    }

    @Override
    public ConstraintAnalysis<SimpleScore> getConstraintAnalysis(ConstraintRef ref) {
      return constraintMap.get(ref);
    }

    @Override
    public ConstraintAnalysis<SimpleScore> getConstraintAnalysis(String id) {
      return getConstraintAnalysis(ConstraintRef.of(id));
    }

    @Override
    public ScoreAnalysis<SimpleScore> diff(ScoreAnalysis<SimpleScore> other) {
      throw new UnsupportedOperationException(
          "The legacy custom fixture has no diff implementation.");
    }

    @Override
    public Collection<ConstraintAnalysis<SimpleScore>> constraintAnalyses() {
      return constraintMap.values();
    }

    @Override
    public String summarize() {
      return summarize(3);
    }

    @Override
    public String summarize(int limit) {
      return score.toString();
    }
  }

  public static final class LegacyManager implements SolutionManager<Object, SimpleScore> {
    public final LegacyAnalysis analysis = LegacyAnalysis.create();
    public Object lastSolution;
    public ScoreAnalysisFetchPolicy lastFetchPolicy;
    public SolutionUpdatePolicy lastUpdatePolicy;
    public IndictmentFetchPolicy lastIndictmentPolicy;
    public int analysisCalls;
    public int recommendationCalls;

    @Override
    public SimpleScore update(Object solution, SolutionUpdatePolicy policy) {
      return analysis.score();
    }

    @Override
    public ScoreAnalysis<SimpleScore> analyze(
        Object solution, ScoreAnalysisFetchPolicy fetch, SolutionUpdatePolicy update) {
      return analyze(solution, fetch, update, IndictmentFetchPolicy.NONE);
    }

    @Override
    public ScoreAnalysis<SimpleScore> analyze(
        Object solution,
        ScoreAnalysisFetchPolicy fetch,
        SolutionUpdatePolicy update,
        IndictmentFetchPolicy indictments) {
      lastSolution = solution;
      lastFetchPolicy = fetch;
      lastUpdatePolicy = update;
      lastIndictmentPolicy = indictments;
      analysisCalls++;
      return analysis;
    }

    @Override
    public PlanningSolutionDiff<Object> diff(Object oldSolution, Object newSolution) {
      throw new UnsupportedOperationException(
          "The legacy fixture has no solution diff implementation.");
    }

    @Override
    public <Entity_, Proposition_>
        List<RecommendedAssignment<Proposition_, SimpleScore>> recommendAssignment(
            Object solution,
            Entity_ entity,
            Function<Entity_, Proposition_> function,
            ScoreAnalysisFetchPolicy fetch) {
      return recommendAssignment(solution, entity, function, fetch, IndictmentFetchPolicy.NONE);
    }

    @Override
    public <Entity_, Proposition_>
        List<RecommendedAssignment<Proposition_, SimpleScore>> recommendAssignment(
            Object solution,
            Entity_ entity,
            Function<Entity_, Proposition_> function,
            ScoreAnalysisFetchPolicy fetch,
            IndictmentFetchPolicy indictments) {
      lastSolution = solution;
      lastFetchPolicy = fetch;
      lastIndictmentPolicy = indictments;
      recommendationCalls++;
      return List.of();
    }
  }
}
