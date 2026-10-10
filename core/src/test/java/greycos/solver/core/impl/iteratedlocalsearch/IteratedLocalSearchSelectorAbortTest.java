package greycos.solver.core.impl.iteratedlocalsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveListFactoryConfig;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.heuristic.selector.move.factory.MoveListFactory;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class IteratedLocalSearchSelectorAbortTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void emptyAndRejectedStepCachesCompleteStrengthCycleWithoutPublishingStepsAndCanBeReused(
      boolean rejectProposals) {
    var phaseConfig =
        new IteratedLocalSearchPhaseConfig()
            .withLocalSearch(new LocalSearchPhaseConfig().withMoveSelectorConfig(emptyMoves()))
            .withPerturbationMoveSelectorConfig(
                new MoveListFactoryConfig()
                    .withMoveListFactoryClass(
                        rejectProposals ? SameValueMoves.class : EmptyMoves.class)
                    .withSelectionOrder(SelectionOrder.ORIGINAL))
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
            .withPhases(phaseConfig);
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var phase =
        (DefaultIteratedLocalSearchPhase<TestdataSolution>) solver.getPhaseList().getFirst();
    var stepEnds = new AtomicInteger();
    phase.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> step) {
            stepEnds.incrementAndGet();
          }
        });
    for (int solve = 0; solve < 2; solve++) {
      var result = solver.solve(problem());
      assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
      assertThat(result.getEntityList().getFirst().getValue().getCode()).isEqualTo("v");
      var diagnostics = phase.getDiagnostics();
      assertThat(diagnostics.completionReason()).isEqualTo("NO_PROGRESS");
      assertThat(diagnostics.completedIterations()).isEqualTo(2);
      assertThat(diagnostics.failedPerturbations()).isEqualTo(2);
      assertThat(diagnostics.perturbationAttempts()).isEqualTo(rejectProposals ? 2 : 0);
      assertThat(diagnostics.primitiveSteps()).isZero();
      assertThat(stepEnds).hasValue(0);
    }
  }

  private static MoveListFactoryConfig emptyMoves() {
    return new MoveListFactoryConfig()
        .withMoveListFactoryClass(EmptyMoves.class)
        .withSelectionOrder(SelectionOrder.ORIGINAL);
  }

  private static TestdataSolution problem() {
    var value = new TestdataValue("v");
    var problem = new TestdataSolution("cache abort");
    problem.setValueList(List.of(value));
    problem.setEntityList(List.of(new TestdataEntity("e", value)));
    return problem;
  }

  public static final class ConstantScore
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  public static final class EmptyMoves implements MoveListFactory<TestdataSolution> {
    @Override
    public List<? extends Move<TestdataSolution>> createMoveList(TestdataSolution solution) {
      return List.of();
    }
  }

  public static final class SameValueMoves implements MoveListFactory<TestdataSolution> {
    @Override
    public List<? extends Move<TestdataSolution>> createMoveList(TestdataSolution solution) {
      var entity = solution.getEntityList().getFirst();
      return List.of(
          new ChangeMove<>(
              TestdataEntity.buildVariableDescriptorForValue(), entity, entity.getValue()));
    }
  }
}
