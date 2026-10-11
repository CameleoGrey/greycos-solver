package greycos.solver.math.impl.score;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.math.api.MathModel;
import greycos.solver.math.impl.cotwin.MathDecision;
import greycos.solver.math.impl.cotwin.MathSolution;
import greycos.solver.math.impl.cotwin.MathSolutionCloner;
import greycos.solver.math.impl.model.MathCompiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MathConstraintProviderTest {

  @ParameterizedTest
  @EnumSource(ConstraintMatchPolicy.class)
  void enumeratedScoresAndIncrementalUndoAgreeWithIndependentArithmetic(
      ConstraintMatchPolicy policy) {
    var builder = MathModel.builder();
    var x = builder.intVar("x", -2, 2);
    var y = builder.intVar("y", -1, 2);
    builder.addConstraint("le", x.times(3).minus(y.times(2)).plus(1).le(2));
    builder.addConstraint("ge", x.plus(y).ge(-1));
    builder.addConstraint("eq", x.minus(y).eq(1));
    builder.addConstraint("contradiction", x.minus(x).le(-2));
    builder.minimize(x.times(-4).plus(y.times(3)).minus(7));
    var solution = MathSolution.initialize(MathCompiler.compile(builder.build()), 0);
    try (var director = director(new MathConstraintProvider(), policy)) {
      director.setWorkingSolution(solution);
      var session = director.getSession();
      for (long xv = -2; xv <= 2; xv++) {
        for (long yv = -1; yv <= 2; yv++) {
          change(director, solution.getDecisions().get(0), xv);
          change(director, solution.getDecisions().get(1), yv);
          assertThat(score(director)).isEqualTo(expected(xv, yv));
          assertThat(director.getSession()).isSameAs(session);
          try (var fresh = director(new MathConstraintProvider(), policy)) {
            fresh.setWorkingSolution(new MathSolutionCloner().cloneSolution(solution));
            assertThat(score(fresh)).isEqualTo(score(director));
          }
          var original = solution.getDecisions().get(0).getValue();
          change(director, solution.getDecisions().get(0), -xv);
          assertThat(score(director)).isEqualTo(expected(-xv, yv));
          change(director, solution.getDecisions().get(0), original);
          assertThat(score(director)).isEqualTo(expected(xv, yv));
        }
      }
    }
  }

  @Test
  void emptyRowsAndJustificationsKeepTheirMathematicalIdentity() {
    var builder = MathModel.builder();
    var x = builder.intVar("x", 0, 2);
    builder.initialValue(x, 1);
    builder.addConstraint("cancelled contradiction", x.minus(x).le(-1));
    builder.addConstraint("same violation, different row", x.minus(x).ge(1));
    builder.addConstraint("empty equality", x.times(0).plus(2).eq(0));
    builder.maximize(x.times(0).minus(5));
    var solution = MathSolution.initialize(MathCompiler.compile(builder.build()), 0);
    assertThat(solution.getTerms()).isEmpty();
    try (var director = director(new MathConstraintProvider(), ConstraintMatchPolicy.ENABLED)) {
      director.setWorkingSolution(solution);
      assertThat(score(director)).isEqualTo(HardSoftScore.of(-4, -5));
      var before = justifications(director);
      assertThat(before).hasSize(4);
      assertThat(before).extracting(MathRowJustification::rowId).doesNotHaveDuplicates();
      assertThat(before)
          .anySatisfy(
              justification -> {
                assertThat(justification.name()).isEqualTo("cancelled contradiction");
                assertThat(justification.activity()).isZero();
                assertThat(justification.bound()).isEqualTo(-1);
                assertThat(justification.residual()).isEqualTo(1);
                assertThat(justification.violation()).isEqualTo(1);
              });
      change(director, solution.getDecisions().getFirst(), 2L);
      assertThat(score(director)).isEqualTo(HardSoftScore.of(-4, -5));
      assertThat(justifications(director)).containsExactlyInAnyOrderElementsOf(before);
    }
  }

  @Test
  void immutableJustificationSnapshotsSurviveLaterChanges() {
    var builder = MathModel.builder();
    var x = builder.intVar("x", 0, 5);
    builder.initialValue(x, 1);
    builder.addConstraint("capacity", x.le(0));
    builder.maximize(x);
    var solution = MathSolution.initialize(MathCompiler.compile(builder.build()), 0);
    try (var director = director(new MathConstraintProvider(), ConstraintMatchPolicy.ENABLED)) {
      director.setWorkingSolution(solution);
      score(director);
      var original =
          justifications(director).stream()
              .filter(justification -> justification.name().equals("capacity"))
              .findFirst()
              .orElseThrow();
      change(director, solution.getDecisions().getFirst(), 5L);
      assertThat(score(director)).isEqualTo(HardSoftScore.of(-5, 5));
      assertThat(original.activity()).isEqualTo(1);
      assertThat(original.violation()).isEqualTo(1);
    }
  }

  @Test
  void scalarAndBatchedChangesOnlyEvaluateIncidentTermsAndRows() {
    var builder = MathModel.builder();
    var x = builder.intVar("x", 0, 10);
    var y = builder.intVar("y", 0, 10);
    var unrelated = builder.intVar("unrelated", 0, 10);
    var unused = builder.intVar("unused", 0, 10);
    builder.addConstraint("x only", x.le(4));
    builder.addConstraint("shared", x.plus(y).eq(7));
    builder.addConstraint("unrelated", unrelated.ge(3));
    builder.maximize(x.plus(y));
    var solution = MathSolution.initialize(MathCompiler.compile(builder.build()), 0);
    var calls = new EvaluationCalls();
    try (var director = director(calls.provider(), ConstraintMatchPolicy.DISABLED)) {
      director.setWorkingSolution(solution);
      score(director);
      calls.clear();
      change(director, solution.getDecisions().get(x.id()), 8L);
      score(director);
      assertThat(calls.terms).containsOnlyKeys("x only", "shared", "$objective");
      assertThat(calls.terms.values()).containsOnly(1);
      assertThat(calls.rows).containsOnlyKeys("x only", "shared", "$objective");
      assertThat(calls.rows.values()).containsOnly(1);
      calls.clear();
      change(director, solution.getDecisions().get(x.id()), 2L);
      change(director, solution.getDecisions().get(y.id()), 5L);
      score(director);
      assertThat(calls.terms)
          .containsEntry("x only", 1)
          .containsEntry("shared", 2)
          .containsEntry("$objective", 2)
          .hasSize(3);
      assertThat(calls.rows).containsOnlyKeys("x only", "shared", "$objective");
      assertThat(calls.rows.values()).containsOnly(1);
      calls.clear();
      change(director, solution.getDecisions().get(unused.id()), 4L);
      score(director);
      assertThat(calls.terms).isEmpty();
      assertThat(calls.rows).isEmpty();
    }
  }

  @Test
  void independentSessionsAndClonesNeverShareAssignmentsOrCollectorState() throws Exception {
    var builder = MathModel.builder();
    var x = builder.intVar("x", -100, 100);
    builder.maximize(x.times(3).minus(7));
    builder.addConstraint("nonnegative", x.ge(0));
    var compiled = MathCompiler.compile(builder.build());
    var solution = MathSolution.initialize(compiled, 0);
    var factory = factory(new MathConstraintProvider());
    try (var first = factory.createScoreDirectorBuilder().build();
        var second = factory.createScoreDirectorBuilder().build()) {
      first.setWorkingSolution(solution);
      var clone = first.cloneSolution(solution);
      second.setWorkingSolution(clone);
      assertThat(first.getSession()).isNotSameAs(second.getSession());
      change(first, solution.getDecisions().getFirst(), 7L);
      change(second, clone.getDecisions().getFirst(), -3L);
      assertThat(score(first)).isEqualTo(HardSoftScore.of(0, 14));
      assertThat(score(second)).isEqualTo(HardSoftScore.of(-3, -16));
      assertThat(solution.getDecisions().getFirst().getValue()).isEqualTo(7L);
      var retainedScore = clone.getScore();
      change(first, solution.getDecisions().getFirst(), 8L);
      score(first);
      assertThat(clone.getScore()).isEqualTo(retainedScore);
      var previousSession = first.getSession();
      first.setWorkingSolution(MathSolution.initialize(compiled, 1));
      assertThat(first.getSession()).isNotSameAs(previousSession);
      assertThat(score(second)).isEqualTo(HardSoftScore.of(-3, -16));
    }
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> replayTrace(factory, compiled, 1));
      var second = executor.submit(() -> replayTrace(factory, compiled, 2));
      first.get();
      second.get();
    }
  }

  @ParameterizedTest
  @EnumSource(ConstraintMatchPolicy.class)
  void exactEndpointScoresAndWideObjectiveTransitionsRemainValid(ConstraintMatchPolicy policy) {
    var builder = MathModel.builder();
    var x = builder.binaryVar("x");
    var y = builder.binaryVar("y");
    builder.initialValue(x, 0);
    builder.initialValue(y, 1);
    builder.addConstraint("large constant penalty", x.times(0).le(-Long.MAX_VALUE));
    builder.addConstraint("one more penalty", y.times(0).le(-1));
    builder.maximize(x.times(Long.MAX_VALUE).minus(y.times(Long.MAX_VALUE)).minus(1));
    var solution = MathSolution.initialize(MathCompiler.compile(builder.build()), 0);
    try (var director = director(new MathConstraintProvider(), policy)) {
      director.setWorkingSolution(solution);
      assertThat(score(director)).isEqualTo(HardSoftScore.of(Long.MIN_VALUE, Long.MIN_VALUE));
      change(director, solution.getDecisions().get(0), 1L);
      change(director, solution.getDecisions().get(1), 0L);
      assertThat(score(director)).isEqualTo(HardSoftScore.of(Long.MIN_VALUE, Long.MAX_VALUE - 1));
      change(director, solution.getDecisions().get(0), 0L);
      change(director, solution.getDecisions().get(1), 1L);
      assertThat(score(director)).isEqualTo(HardSoftScore.of(Long.MIN_VALUE, Long.MIN_VALUE));
    }
  }

  @Test
  void rowConstantIsAppliedToTheCompletedAggregate() {
    var builder = MathModel.builder();
    var positive = builder.intVar("positive", Long.MAX_VALUE, Long.MAX_VALUE);
    var negative = builder.intVar("negative", Long.MIN_VALUE, Long.MIN_VALUE);
    var expression = positive.plus(negative).plus(Long.MAX_VALUE);
    builder.addConstraint("balanced", expression.eq(Long.MAX_VALUE - 1));
    builder.maximize(expression);
    var solution = MathSolution.initialize(MathCompiler.compile(builder.build()), 0);
    try (var director = director(new MathConstraintProvider(), ConstraintMatchPolicy.ENABLED)) {
      // The first contribution and constant would overflow if evaluated before grouping completes.
      director.setWorkingSolution(solution);
      assertThat(score(director)).isEqualTo(HardSoftScore.of(0, Long.MAX_VALUE - 1));
    }
  }

  private static Void replayTrace(
      BavetConstraintStreamScoreDirectorFactory<MathSolution, HardSoftScore> factory,
      greycos.solver.math.impl.model.CompiledModel compiled,
      long seed) {
    var solution = MathSolution.initialize(compiled, seed);
    var random = new Random(seed);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var session = director.getSession();
      for (int i = 0; i < 500; i++) {
        long value = random.nextInt(201) - 100;
        change(director, solution.getDecisions().getFirst(), value);
        assertThat(score(director)).isEqualTo(HardSoftScore.of(Math.min(0, value), 3 * value - 7));
        assertThat(director.getSession()).isSameAs(session);
      }
    }
    return null;
  }

  private static HardSoftScore expected(long x, long y) {
    var bx = BigInteger.valueOf(x);
    var by = BigInteger.valueOf(y);
    var le =
        bx.multiply(BigInteger.valueOf(3))
            .subtract(by.multiply(BigInteger.TWO))
            .subtract(BigInteger.ONE)
            .max(BigInteger.ZERO);
    var ge = BigInteger.valueOf(-1).subtract(bx.add(by)).max(BigInteger.ZERO);
    var eq = bx.subtract(by).subtract(BigInteger.ONE).abs();
    var hard = le.add(ge).add(eq).add(BigInteger.TWO).negate();
    var soft =
        bx.multiply(BigInteger.valueOf(-4))
            .add(by.multiply(BigInteger.valueOf(3)))
            .subtract(BigInteger.valueOf(7))
            .negate();
    return HardSoftScore.of(hard.longValueExact(), soft.longValueExact());
  }

  private static BavetConstraintStreamScoreDirectorFactory<MathSolution, HardSoftScore> factory(
      MathConstraintProvider provider) {
    return new BavetConstraintStreamScoreDirectorFactory<>(
        SolutionDescriptor.buildSolutionDescriptor(MathSolution.class, MathDecision.class),
        provider,
        EnvironmentMode.PHASE_ASSERT);
  }

  private static BavetConstraintStreamScoreDirector<MathSolution, HardSoftScore> director(
      MathConstraintProvider provider, ConstraintMatchPolicy policy) {
    return factory(provider).createScoreDirectorBuilder().withConstraintMatchPolicy(policy).build();
  }

  private static void change(
      BavetConstraintStreamScoreDirector<MathSolution, HardSoftScore> director,
      MathDecision decision,
      long value) {
    director.beforeVariableChanged(decision, "value");
    decision.setValue(value);
    director.afterVariableChanged(decision, "value");
  }

  private static HardSoftScore score(
      BavetConstraintStreamScoreDirector<MathSolution, HardSoftScore> director) {
    director.updateShadowVariables();
    return director.calculateScore().raw();
  }

  private static List<MathRowJustification> justifications(
      BavetConstraintStreamScoreDirector<MathSolution, HardSoftScore> director) {
    return director.getConstraintMatchTotalMap().values().stream()
        .flatMap(total -> total.getConstraintMatchSet().stream())
        .map(match -> (MathRowJustification) match.getJustification())
        .toList();
  }

  private static final class EvaluationCalls {
    private final Map<String, Integer> terms = new HashMap<>();
    private final Map<String, Integer> rows = new HashMap<>();

    MathConstraintProvider provider() {
      return new MathConstraintProvider(
          (term, decision) -> {
            terms.merge(term.row().name(), 1, Integer::sum);
            return Math.multiplyExact(term.coefficient(), decision.getValue());
          },
          (row, sum) -> {
            rows.merge(row.name(), 1, Integer::sum);
            return row.activity(sum);
          });
    }

    void clear() {
      terms.clear();
      rows.clear();
    }
  }
}
