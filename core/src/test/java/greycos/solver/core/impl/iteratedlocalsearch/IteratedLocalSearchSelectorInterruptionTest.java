package greycos.solver.core.impl.iteratedlocalsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveListFactoryConfig;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.heuristic.selector.AbstractSelector;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchSelectorAbortTest.ConstantScore;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchSelectorAbortTest.EmptyMoves;
import greycos.solver.core.impl.move.SolutionAssignmentMove;
import greycos.solver.core.impl.move.SolutionAssignments;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListener;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.event.PhaseLifecycleSupport;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class IteratedLocalSearchSelectorInterruptionTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void cancellationAndQueuedAdoptionAbortConstructedStepCacheBeforeNextUse(boolean cancel)
      throws ReflectiveOperationException {
    var solver = solver();
    var phase =
        (DefaultIteratedLocalSearchPhase<TestdataSolution>) solver.getPhaseList().getFirst();
    var selector = selector(phase);
    var injected = new AtomicBoolean();
    var aborts = new AtomicInteger();
    addSelectorListener(
        selector,
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataSolution> step) {
            // Appended after the cache bridge: successful getSize proves that construction
            // finished.
            assertThat(selector.getSize()).isZero();
            if (!injected.compareAndSet(false, true)) return;
            if (cancel) solver.terminateEarly();
            else {
              var director = step.getScoreDirector();
              // This equal incumbent will be rejected on adoption; queuing it still interrupts
              // selection.
              var assignments =
                  SolutionAssignments.captureComplete(
                      director.getSolutionDescriptor(), director.getWorkingSolution());
              step.getPhaseScope()
                  .getSolverScope()
                  .setPendingMove(new SolutionAssignmentMove<>(assignments));
            }
          }

          @Override
          public void stepAborted(AbstractStepScope<TestdataSolution> step) {
            aborts.incrementAndGet();
            assertThatThrownBy(selector::getSize).isInstanceOf(NullPointerException.class);
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> step) {
            throw new AssertionError(
                "An interrupted or empty selector step must not publish stepEnded.");
          }
        });
    assertThat(solver.solve(problem()).getScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(injected).isTrue();
    assertThat(aborts).hasValue(cancel ? 1 : 3);
    assertThat(solver.getSolverScope().hasPendingMove()).isFalse();
    assertThat(phase.getDiagnostics().primitiveSteps()).isZero();
    assertThat(solver.solve(problem()).getScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(phase.getDiagnostics().completionReason()).isEqualTo("NO_PROGRESS");
    assertThat(aborts).hasValue(cancel ? 3 : 5);
  }

  @Test
  void selectorStartupExceptionPreservesPrimaryAndAbortFailureAndAllowsSolverReuse()
      throws ReflectiveOperationException {
    var solver = solver();
    var phase =
        (DefaultIteratedLocalSearchPhase<TestdataSolution>) solver.getPhaseList().getFirst();
    var selector = selector(phase);
    var startupFailure = new IllegalStateException("selector startup");
    var abortFailure = new IllegalStateException("selector abort");
    var failStartup = new AtomicBoolean(true);
    var failAbort = new AtomicBoolean(true);
    var aborts = new AtomicInteger();
    addSelectorListener(
        selector,
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataSolution> step) {
            assertThat(selector.getSize()).isZero();
            if (failStartup.getAndSet(false)) throw startupFailure;
          }

          @Override
          public void stepAborted(AbstractStepScope<TestdataSolution> step) {
            aborts.incrementAndGet();
            assertThatThrownBy(selector::getSize).isInstanceOf(NullPointerException.class);
            if (failAbort.getAndSet(false)) throw abortFailure;
          }
        });
    assertThatThrownBy(() -> solver.solve(problem()))
        .isSameAs(startupFailure)
        .satisfies(failure -> assertThat(failure.getSuppressed()).contains(abortFailure));
    assertThat(aborts).hasValue(1);
    assertThat(solver.solve(problem()).getScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(phase.getDiagnostics().completionReason()).isEqualTo("NO_PROGRESS");
    assertThat(aborts).hasValue(3);
  }

  @SuppressWarnings("unchecked")
  private static MoveSelector<TestdataSolution> selector(
      DefaultIteratedLocalSearchPhase<TestdataSolution> phase) throws ReflectiveOperationException {
    var field = DefaultIteratedLocalSearchPhase.class.getDeclaredField("perturbation");
    field.setAccessible(true);
    return (MoveSelector<TestdataSolution>) field.get(phase);
  }

  @SuppressWarnings("unchecked")
  private static void addSelectorListener(
      MoveSelector<TestdataSolution> selector, PhaseLifecycleListener<TestdataSolution> listener)
      throws ReflectiveOperationException {
    var field = AbstractSelector.class.getDeclaredField("phaseLifecycleSupport");
    field.setAccessible(true);
    ((PhaseLifecycleSupport<TestdataSolution>) field.get(selector)).addEventListener(listener);
  }

  private static DefaultSolver<TestdataSolution> solver() {
    var phase =
        new IteratedLocalSearchPhaseConfig()
            .withLocalSearch(new LocalSearchPhaseConfig().withMoveSelectorConfig(emptyMoves()))
            .withPerturbationMoveSelectorConfig(emptyMoves())
            .withPerturbationStrengths(1, 2)
            .withPerturbationAttemptLimit(8)
            .withEpisodeCandidateAttemptLimit(8)
            .withIterationCountLimit(10);
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(ConstantScore.class)
            .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
            .withMoveThreadCount("NONE")
            .withPhases(phase);
    return (DefaultSolver<TestdataSolution>)
        SolverFactory.<TestdataSolution>create(config).buildSolver();
  }

  private static MoveListFactoryConfig emptyMoves() {
    return new MoveListFactoryConfig()
        .withMoveListFactoryClass(EmptyMoves.class)
        .withSelectionOrder(SelectionOrder.ORIGINAL);
  }

  private static TestdataSolution problem() {
    var value = new TestdataValue("v");
    var problem = new TestdataSolution("selector interruption");
    problem.setValueList(List.of(value));
    problem.setEntityList(List.of(new TestdataEntity("e", value)));
    return problem;
  }
}
