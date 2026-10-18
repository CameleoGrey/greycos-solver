package greycos.solver.core.api.solver;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import greycos.solver.core.api.function.PentaFunction;
import greycos.solver.core.api.function.QuadFunction;
import greycos.solver.core.api.function.TriFunction;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.analysis.ConstraintAnalysis;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.score.calculator.ConstraintMatchRegistration;
import greycos.solver.core.api.score.calculator.ConstraintMatchRegistry;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintMetadata;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.score.stream.DefaultConstraintJustification;
import greycos.solver.core.api.score.stream.bi.BiConstraintBuilder;
import greycos.solver.core.api.score.stream.quad.QuadConstraintBuilder;
import greycos.solver.core.api.score.stream.test.ShadowVariableAwareSingleConstraintAssertion;
import greycos.solver.core.api.score.stream.test.SingleConstraintAssertion;
import greycos.solver.core.api.score.stream.tri.TriConstraintBuilder;
import greycos.solver.core.api.score.stream.uni.UniConstraintBuilder;
import greycos.solver.core.preview.api.cotwin.solution.diff.PlanningSolutionDiff;

/**
 * Uses only the public API from before indictments. The binary compatibility runner compiles this
 * source against the baseline, then executes that unchanged bytecode against the candidate.
 */
public final class LegacyIndictmentApiFixture {

  private LegacyIndictmentApiFixture() {}

  public static void main(String[] args) {
    if (args.length == 1 && args[0].equals("inventory")) {
      printPublicMethodInventory();
    } else {
      verifyLegacyCalls();
      System.out.println("Legacy indictment API calls passed.");
    }
  }

  private static void printPublicMethodInventory() {
    for (var api :
        List.of(
            ScoreAnalysis.class,
            SolutionManager.class,
            ConstraintMatchRegistry.class,
            ConstraintMatchRegistration.class,
            UniConstraintBuilder.class,
            BiConstraintBuilder.class,
            TriConstraintBuilder.class,
            QuadConstraintBuilder.class,
            SingleConstraintAssertion.class,
            ShadowVariableAwareSingleConstraintAssertion.class)) {
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
  }

  public static void verifyLegacyCalls() {
    check(ScoreAnalysisFetchPolicy.values().length == 3, "The existing fetch enum changed.");
    for (var policy : ScoreAnalysisFetchPolicy.values()) {
      check(legacyExhaustiveSwitch(policy) > 0, "The legacy exhaustive switch failed.");
    }
    var solution = new Object();
    var manager = new LegacySolutionManager();
    check(manager.update(solution).equals(SimpleScore.ONE), "Legacy update failed.");
    check(manager.analyze(solution) == manager.analysis, "Legacy analyze failed.");
    check(
        manager.analyze(solution, ScoreAnalysisFetchPolicy.FETCH_SHALLOW) == manager.analysis,
        "Legacy two-argument analyze failed.");
    // These uncast null calls must remain source-compatible and unambiguous.
    check(manager.analyze(solution, null) == manager.analysis, "Legacy null policy failed.");
    check(
        manager.analyze(solution, null, null) == manager.analysis, "Legacy null policies failed.");
    check(
        manager.recommendAssignment(solution, solution, Function.identity()).isEmpty(),
        "Legacy recommendation failed.");
    check(
        manager.recommendAssignment(solution, solution, Function.identity(), null).isEmpty(),
        "Legacy null recommendation policy failed.");
    check(manager.analysis.constraintMap().isEmpty(), "Legacy analysis map failed.");
    check(manager.analysis.isSolutionInitialized(), "Legacy initialization flag failed.");
    check(
        manager.analysis.diff(manager.analysis).score().equals(SimpleScore.ZERO),
        "Legacy analysis diff failed.");

    var registry = new LegacyRegistry();
    var ref = ConstraintRef.of("legacy");
    ConstraintJustification justification =
        DefaultConstraintJustification.of(SimpleScore.ONE, solution);
    check(
        registry.registerConstraintMatch(ref, SimpleScore.ONE, justification).justification()
            == justification,
        "The explicit justification overload changed.");
    var collection = List.of(solution);
    var registration =
        registry.registerConstraintMatch(ref, SimpleScore.ONE, justification, collection);
    var facts = ((DefaultConstraintJustification) registration.justification()).getFacts();
    check(
        facts.size() == 2 && facts.get(0) == justification && facts.get(1) == collection,
        "The legacy justification-plus-collection varargs call changed meaning.");
    var singleCollection = registry.registerConstraintMatch(ref, SimpleScore.ONE, collection);
    check(
        ((DefaultConstraintJustification) singleCollection.justification()).getFacts().getFirst()
            == collection,
        "A collection must remain one legacy varargs fact.");
    registration.cancel();
    check(registry.totalScore().equals(SimpleScore.ONE), "Legacy registry dispatch failed.");

    var builder = new LegacyBuilder();
    UniConstraintBuilder<Object, SimpleScore> uni = builder;
    BiConstraintBuilder<Object, Object, SimpleScore> bi = builder;
    TriConstraintBuilder<Object, Object, Object, SimpleScore> tri = builder;
    QuadConstraintBuilder<Object, Object, Object, Object, SimpleScore> quad = builder;
    check(uni.justifyWith((a, score) -> justification) == builder, "Legacy uni builder failed.");
    check(bi.justifyWith((a, b, score) -> justification) == builder, "Legacy bi builder failed.");
    check(
        tri.justifyWith((a, b, c, score) -> justification) == builder,
        "Legacy tri builder failed.");
    check(
        quad.justifyWith((a, b, c, d, score) -> justification) == builder,
        "Legacy quad builder failed.");
    uni.asConstraint("legacy");
    check(builder.metadata.id().equals("legacy"), "Legacy constraint builder dispatch failed.");
  }

  public static int legacyExhaustiveSwitch(ScoreAnalysisFetchPolicy policy) {
    return switch (policy) {
      case FETCH_ALL -> 1;
      case FETCH_SHALLOW -> 2;
      case FETCH_MATCH_COUNT -> 3;
    };
  }

  static void check(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  public record LegacyAnalysis(SimpleScore score) implements ScoreAnalysis<SimpleScore> {
    @Override
    public SequencedMap<ConstraintRef, ConstraintAnalysis<SimpleScore>> constraintMap() {
      return Collections.unmodifiableSequencedMap(new LinkedHashMap<>());
    }

    @Override
    public boolean isSolutionInitialized() {
      return true;
    }

    @Override
    public ConstraintAnalysis<SimpleScore> getConstraintAnalysis(ConstraintRef constraintRef) {
      return constraintMap().get(constraintRef);
    }

    @Override
    public ConstraintAnalysis<SimpleScore> getConstraintAnalysis(String constraintId) {
      return getConstraintAnalysis(ConstraintRef.of(constraintId));
    }

    @Override
    public ScoreAnalysis<SimpleScore> diff(ScoreAnalysis<SimpleScore> other) {
      return new LegacyAnalysis(score.subtract(other.score()));
    }

    @Override
    public Collection<ConstraintAnalysis<SimpleScore>> constraintAnalyses() {
      return constraintMap().values();
    }

    @Override
    public String summarize() {
      return summarize(3);
    }

    @Override
    public String summarize(int topLimit) {
      return score.toString();
    }
  }

  public static final class LegacySolutionManager implements SolutionManager<Object, SimpleScore> {
    public final LegacyAnalysis analysis = new LegacyAnalysis(SimpleScore.ONE);
    public ScoreAnalysisFetchPolicy lastFetchPolicy;
    public SolutionUpdatePolicy lastUpdatePolicy;
    public Object lastSolution;
    public int analysisCalls;
    public int recommendationCalls;

    @Override
    public SimpleScore update(Object solution, SolutionUpdatePolicy updatePolicy) {
      return analysis.score();
    }

    @Override
    public ScoreAnalysis<SimpleScore> analyze(
        Object solution, ScoreAnalysisFetchPolicy fetchPolicy, SolutionUpdatePolicy updatePolicy) {
      lastSolution = solution;
      lastFetchPolicy = fetchPolicy;
      lastUpdatePolicy = updatePolicy;
      analysisCalls++;
      return analysis;
    }

    @Override
    public PlanningSolutionDiff<Object> diff(Object oldSolution, Object newSolution) {
      throw new UnsupportedOperationException(
          "The legacy fixture does not compute solution diffs.");
    }

    @Override
    public <Entity_, Proposition_>
        List<RecommendedAssignment<Proposition_, SimpleScore>> recommendAssignment(
            Object solution,
            Entity_ entity,
            Function<Entity_, Proposition_> propositionFunction,
            ScoreAnalysisFetchPolicy fetchPolicy) {
      lastSolution = solution;
      lastFetchPolicy = fetchPolicy;
      recommendationCalls++;
      return List.of();
    }
  }

  public static final class LegacyRegistry implements ConstraintMatchRegistry<SimpleScore> {
    private SimpleScore score = SimpleScore.ZERO;

    @Override
    public ConstraintMatchRegistration<SimpleScore> registerConstraintMatch(
        ConstraintRef ref, SimpleScore matchScore, ConstraintJustification justification) {
      score = matchScore;
      return new LegacyRegistration(ref, matchScore, justification);
    }

    @Override
    public SimpleScore totalScore() {
      return score;
    }
  }

  public record LegacyRegistration(
      ConstraintRef constraintRef, SimpleScore score, ConstraintJustification justification)
      implements ConstraintMatchRegistration<SimpleScore> {
    @Override
    public void cancel() {}
  }

  public static final class LegacyBuilder
      implements UniConstraintBuilder<Object, SimpleScore>,
          BiConstraintBuilder<Object, Object, SimpleScore>,
          TriConstraintBuilder<Object, Object, Object, SimpleScore>,
          QuadConstraintBuilder<Object, Object, Object, Object, SimpleScore> {
    private ConstraintMetadata metadata;

    @Override
    public Constraint asConstraint(ConstraintMetadata constraintMetadata) {
      metadata = constraintMetadata;
      return null;
    }

    @Override
    public <J extends ConstraintJustification> LegacyBuilder justifyWith(
        BiFunction<Object, SimpleScore, J> mapping) {
      return this;
    }

    @Override
    public <J extends ConstraintJustification> LegacyBuilder justifyWith(
        TriFunction<Object, Object, SimpleScore, J> mapping) {
      return this;
    }

    @Override
    public <J extends ConstraintJustification> LegacyBuilder justifyWith(
        QuadFunction<Object, Object, Object, SimpleScore, J> mapping) {
      return this;
    }

    @Override
    public <J extends ConstraintJustification> LegacyBuilder justifyWith(
        PentaFunction<Object, Object, Object, Object, SimpleScore, J> mapping) {
      return this;
    }
  }
}
