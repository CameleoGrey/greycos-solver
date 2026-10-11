package greycos.solver.math.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.math.config.SolveOptions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MathSolverTest {

  @Test
  void zeroBudgetHonorsInitialAndFixedValuesAndPreservesSnapshots() {
    var builder = MathModel.builder();
    var x = builder.intVar("x", -3, 5);
    var y = builder.binaryVar("y");
    builder.initialValue(x, -2);
    builder.fix(y, 1);
    builder.addConstraint("capacity", x.plus(y).le(0));
    builder.minimize(x.times(2).minus(y));
    var model = builder.build();
    var result = MathSolver.solve(model, SolveOptions.moveEvaluationLimit(0));
    assertThat(result.values().keySet()).containsExactly(x, y);
    assertThat(result.value(x)).isEqualTo(-2);
    assertThat(result.value(y)).isEqualTo(1);
    assertThat(result.objectiveValue()).isEqualTo(-5);
    assertThat(result.status()).isEqualTo(MathResult.Status.FEASIBLE);
    assertThat(result.termination()).isEqualTo(MathResult.Termination.ZERO_LIMIT);
    assertThat(result.constraintReports())
        .containsExactly(new ConstraintReport("capacity", Relation.LE, -1, 0, -1, 0));
    assertThatThrownBy(() -> result.values().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> result.constraintReports().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> result.value(MathModel.builder().binaryVar("y")));
    MathSolver.solve(model, SolveOptions.moveEvaluationLimit(30).withSeed(1));
    assertThat(result.value(x)).isEqualTo(-2);
    assertThat(result.objectiveValue()).isEqualTo(-5);
  }

  @Test
  void anyZeroLimitBypassesSearchAndConstantContradictionsRemainVisible() {
    var builder = MathModel.builder();
    var x = builder.binaryVar("x");
    builder.initialValue(x, 0);
    builder.maximize(x);
    builder.addConstraint("impossible", LinearExpression.constant(0).le(-1));
    var model = builder.build();
    for (var options :
        List.of(
            SolveOptions.timeLimit(Duration.ZERO).withMoveEvaluationLimit(100),
            SolveOptions.moveEvaluationLimit(0).withTimeLimit(Duration.ofSeconds(1)))) {
      var result = MathSolver.solve(model, options);
      assertThat(result.termination()).isEqualTo(MathResult.Termination.ZERO_LIMIT);
      assertThat(result.value(x)).isZero();
      assertThat(result.status()).isEqualTo(MathResult.Status.NO_FEASIBLE_SOLUTION_FOUND);
      assertThat(result.constraintReports().getFirst().violation()).isEqualTo(1);
    }
  }

  @Test
  @Timeout(10)
  void timeBoundedInfeasibleRunReportsOnlyObservedTermination() {
    var builder = MathModel.builder();
    var x = builder.binaryVar("x");
    builder.addConstraint("contradiction", LinearExpression.constant(0).ge(1));
    builder.maximize(x);
    var result = MathSolver.solve(builder.build(), SolveOptions.timeLimit(Duration.ofMillis(10)));
    assertThat(result.status()).isEqualTo(MathResult.Status.NO_FEASIBLE_SOLUTION_FOUND);
    assertThat(result.termination()).isEqualTo(MathResult.Termination.NORMAL_RETURN);
    assertThat(result.constraintReports().getFirst().violation()).isEqualTo(1);
    assertThat(result.elapsed()).isGreaterThanOrEqualTo(Duration.ZERO);
    assertThat(result.options().timeLimit()).contains(Duration.ofMillis(10));
  }

  @Test
  void emptyFixedAndSingletonModelsNeedNoSearchIncludingExtremeValues() {
    var empty = MathSolver.solve(MathModel.builder().build(), SolveOptions.moveEvaluationLimit(1));
    assertThat(empty.values()).isEmpty();
    assertThat(empty.objectiveValue()).isZero();
    assertThat(empty.status()).isEqualTo(MathResult.Status.FEASIBLE);
    assertThat(empty.termination()).isEqualTo(MathResult.Termination.NO_MUTABLE_DECISIONS);
    var builder = MathModel.builder();
    var min = builder.intVar("min", Long.MIN_VALUE, Long.MIN_VALUE);
    var max = builder.intVar("max", Long.MAX_VALUE, Long.MAX_VALUE);
    var fixed = builder.intVar("fixed", -10, 10);
    builder.fix(fixed, 4);
    builder.maximize(min.plus(max));
    var result = MathSolver.solve(builder.build(), SolveOptions.moveEvaluationLimit(100));
    assertThat(result.termination()).isEqualTo(MathResult.Termination.NO_MUTABLE_DECISIONS);
    assertThat(result.value(min)).isEqualTo(Long.MIN_VALUE);
    assertThat(result.value(max)).isEqualTo(Long.MAX_VALUE);
    assertThat(result.value(fixed)).isEqualTo(4);
    assertThat(result.objectiveValue()).isEqualTo(-1);
  }

  @Test
  void signedHardMinimumSurvivesBackendScoringAndReplay() {
    var builder = MathModel.builder();
    builder.addConstraint("large", LinearExpression.constant(Long.MAX_VALUE).le(0));
    builder.addConstraint("one", LinearExpression.constant(1).le(0));
    builder.maximize(LinearExpression.constant(Long.MIN_VALUE));
    var result = MathSolver.solve(builder.build(), SolveOptions.moveEvaluationLimit(1));
    assertThat(result.status()).isEqualTo(MathResult.Status.NO_FEASIBLE_SOLUTION_FOUND);
    assertThat(result.constraintReports())
        .extracting(ConstraintReport::violation)
        .containsExactly(Long.MAX_VALUE, 1L);
    assertThat(result.objectiveValue()).isEqualTo(Long.MIN_VALUE);
  }

  @Test
  void initialValuesCanChangeWhileFixedAndSingletonValuesRemainPinned() {
    var builder = MathModel.builder();
    var initial = builder.binaryVar("initial");
    var fixed = builder.binaryVar("fixed");
    var singleton = builder.intVar("singleton", Long.MAX_VALUE, Long.MAX_VALUE);
    builder.initialValue(initial, 0);
    builder.fix(fixed, 1);
    builder.maximize(initial);
    var result =
        MathSolver.solve(
            builder.build(),
            SolveOptions.moveEvaluationLimit(1).withEnvironmentMode(EnvironmentMode.FULL_ASSERT));
    assertThat(result.value(initial)).isEqualTo(1);
    assertThat(result.value(fixed)).isEqualTo(1);
    assertThat(result.value(singleton)).isEqualTo(Long.MAX_VALUE);
    assertThat(result.objectiveValue()).isEqualTo(1);
  }

  @Test
  @Timeout(30)
  void realFullAssertSearchIsSeededAcrossRepeatedAndConcurrentSolves() throws Exception {
    var builder = MathModel.builder();
    var x = builder.intVar("x", -5, 5);
    var y = builder.intVar("y", 0, 9);
    var fixed = builder.binaryVar("fixed");
    builder.fix(fixed, 1);
    builder.addConstraint("capacity", x.times(2).plus(y).le(10));
    builder.maximize(x.plus(y.times(3)).plus(fixed));
    var model = builder.build();
    var options =
        SolveOptions.moveEvaluationLimit(150)
            .withSeed(93)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT);
    var first = MathSolver.solve(model, options);
    var second = MathSolver.solve(model, options);
    assertThat(first.values()).isEqualTo(second.values());
    assertThat(first.constraintReports()).isEqualTo(second.constraintReports());
    assertThat(first.objectiveValue()).isEqualTo(second.objectiveValue());
    assertThat(first.value(fixed)).isEqualTo(1L);
    assertThat(first.termination()).isEqualTo(MathResult.Termination.NORMAL_RETURN);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a = executor.submit(() -> MathSolver.solve(model, options));
      var b = executor.submit(() -> MathSolver.solve(model, options));
      assertThat(a.get(10, TimeUnit.SECONDS).values()).isEqualTo(first.values());
      assertThat(b.get(10, TimeUnit.SECONDS).values()).isEqualTo(first.values());
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = AcceptorType.class,
      names = {"DIVERSIFIED_LATE_ACCEPTANCE", "UNDO_MOVE_TABU"},
      mode = EnumSource.Mode.EXCLUDE)
  @Timeout(15)
  void stableExpertAcceptorsUseOrdinaryScalarSearch(AcceptorType type) {
    var builder = MathModel.builder();
    var x = builder.intVar("x", 0, 3);
    var y = builder.intVar("y", 0, 4);
    builder.maximize(x.plus(y));
    var acceptor = new LocalSearchAcceptorConfig().withAcceptorTypeList(List.of(type));
    if (type == AcceptorType.SIMULATED_ANNEALING) {
      acceptor.withSimulatedAnnealingStartingTemperature("1hard/10soft");
    } else if (type == AcceptorType.VALUE_TABU) {
      acceptor.withValueTabuSize(1);
    } else if (type == AcceptorType.MOVE_TABU) {
      acceptor.withMoveTabuSize(1);
    }
    var result =
        MathSolver.solve(
            builder.build(),
            SolveOptions.moveEvaluationLimit(25)
                .withAcceptorConfig(acceptor)
                .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(2)));
    assertThat(result.status()).isEqualTo(MathResult.Status.FEASIBLE);
    assertThat(result.value(x)).isBetween(0L, 3L);
    assertThat(result.value(y)).isBetween(0L, 4L);
    assertThat(result.objectiveValue()).isEqualTo(result.value(x) + result.value(y));
  }

  @Test
  void invalidExpertSettingsFailEvenWhenNoSearchWouldRun() {
    var model = MathModel.builder().build();
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                MathSolver.solve(
                    model,
                    SolveOptions.moveEvaluationLimit(0)
                        .withAcceptorConfig(new LocalSearchAcceptorConfig())));
  }

  @Test
  void directEvaluationAndInterruptedEntryNeverIgnoreInvalidAcceptorParameters() {
    var mutableBuilder = MathModel.builder();
    mutableBuilder.binaryVar("x");
    var mutableModel = mutableBuilder.build();
    var fixedModel = MathModel.builder().build();
    var invalidGreatDeluge = new LocalSearchAcceptorConfig();
    invalidGreatDeluge.setGreatDelugeWaterLevelIncrementRatio(Double.NaN);
    var conflictingGreatDeluge = new LocalSearchAcceptorConfig();
    conflictingGreatDeluge.setGreatDelugeWaterLevelIncrementRatio(0.1);
    conflictingGreatDeluge.setGreatDelugeWaterLevelIncrementScore("0hard/1soft");
    var infiniteGreatDeluge = new LocalSearchAcceptorConfig();
    infiniteGreatDeluge.setGreatDelugeWaterLevelIncrementRatio(Double.POSITIVE_INFINITY);
    var invalidConfigs =
        List.of(
            new LocalSearchAcceptorConfig().withLateAcceptanceSize(0),
            new LocalSearchAcceptorConfig().withStepCountingHillClimbingSize(0),
            new LocalSearchAcceptorConfig()
                .withSimulatedAnnealingStartingTemperature("-1hard/1soft"),
            new LocalSearchAcceptorConfig()
                .withSimulatedAnnealingStartingTemperature("1hard/-1soft"),
            new LocalSearchAcceptorConfig().withEntityTabuRatio(Double.NaN),
            new LocalSearchAcceptorConfig().withEntityTabuRatio(Double.POSITIVE_INFINITY),
            new LocalSearchAcceptorConfig().withFadingEntityTabuRatio(Double.NaN),
            new LocalSearchAcceptorConfig().withFadingEntityTabuRatio(Double.NEGATIVE_INFINITY),
            invalidGreatDeluge,
            infiniteGreatDeluge,
            conflictingGreatDeluge);
    for (var invalid : invalidConfigs) {
      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  MathSolver.solve(
                      mutableModel,
                      SolveOptions.moveEvaluationLimit(0).withAcceptorConfig(invalid)));
      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  MathSolver.solve(
                      fixedModel, SolveOptions.moveEvaluationLimit(1).withAcceptorConfig(invalid)));
      try {
        Thread.currentThread().interrupt();
        assertThatIllegalArgumentException()
            .isThrownBy(
                () ->
                    MathSolver.solve(
                        mutableModel,
                        SolveOptions.moveEvaluationLimit(1).withAcceptorConfig(invalid)));
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } finally {
        Thread.interrupted();
      }
    }
  }

  @Test
  void validCompositeWithZeroAnnealingTemperatureStillSolves() {
    var builder = MathModel.builder();
    var x = builder.binaryVar("x");
    builder.initialValue(x, 0);
    builder.maximize(x);
    var acceptor =
        new LocalSearchAcceptorConfig()
            .withAcceptorTypeList(
                List.of(AcceptorType.LATE_ACCEPTANCE, AcceptorType.SIMULATED_ANNEALING))
            .withLateAcceptanceSize(1)
            .withSimulatedAnnealingStartingTemperature("0hard/0soft");
    var result =
        MathSolver.solve(
            builder.build(),
            SolveOptions.moveEvaluationLimit(1)
                .withAcceptorConfig(acceptor)
                .withEnvironmentMode(EnvironmentMode.FULL_ASSERT));
    assertThat(result.value(x)).isEqualTo(1);
    assertThat(result.status()).isEqualTo(MathResult.Status.FEASIBLE);
  }

  @Test
  void interruptedEntryReturnsCheckedInitialAssignmentAndPreservesInterrupt() {
    var builder = MathModel.builder();
    var x = builder.binaryVar("x");
    builder.initialValue(x, 0);
    builder.maximize(x);
    var model = builder.build();
    try {
      Thread.currentThread().interrupt();
      var result = MathSolver.solve(model, SolveOptions.moveEvaluationLimit(100));
      assertThat(result.value(x)).isZero();
      assertThat(result.termination()).isEqualTo(MathResult.Termination.EARLY_TERMINATION_OBSERVED);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
    assertThat(MathSolver.solve(model, SolveOptions.moveEvaluationLimit(0)).termination())
        .isEqualTo(MathResult.Termination.ZERO_LIMIT);
  }

  @Test
  @Timeout(20)
  void interruptionDuringRealSearchReturnsUsableSnapshotAndStopsThread() throws Exception {
    var builder = MathModel.builder();
    for (int i = 0; i < 8; i++) {
      builder.binaryVar("x" + i);
    }
    var model = builder.build();
    var result = new AtomicReference<MathResult>();
    var failure = new AtomicReference<Throwable>();
    var preserved = new AtomicBoolean();
    var worker =
        new Thread(
            () -> {
              try {
                result.set(
                    MathSolver.solve(model, SolveOptions.moveEvaluationLimit(Long.MAX_VALUE)));
                preserved.set(Thread.currentThread().isInterrupted());
              } catch (Throwable throwable) {
                failure.set(throwable);
              }
            },
            "math-interruption-test");
    worker.start();
    boolean reachedSearch = false;
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    try {
      while (worker.isAlive() && System.nanoTime() < deadline) {
        reachedSearch =
            Arrays.stream(worker.getStackTrace())
                .anyMatch(
                    frame ->
                        frame.getClassName().startsWith("greycos.solver.core.impl.localsearch."));
        if (reachedSearch) {
          break;
        }
        Thread.sleep(5);
      }
    } finally {
      worker.interrupt();
      worker.join(5_000);
    }
    assertThat(reachedSearch).isTrue();
    assertThat(worker.isAlive()).isFalse();
    assertThat(failure.get()).isNull();
    assertThat(preserved.get()).isTrue();
    assertThat(result.get().termination())
        .isEqualTo(MathResult.Termination.EARLY_TERMINATION_OBSERVED);
    assertThat(result.get().values()).hasSize(8);
    assertThat(result.get().status()).isEqualTo(MathResult.Status.FEASIBLE);
  }
}
