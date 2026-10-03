package greycos.solver.core.impl.heuristic.move;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveListFactoryConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.move.factory.MoveIteratorFactory;
import greycos.solver.core.impl.heuristic.selector.move.factory.MoveListFactory;
import greycos.solver.core.impl.heuristic.selector.move.generic.SelectorBasedChangeMove;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import greycos.solver.core.impl.solver.AbstractSolver;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.builtin.Moves;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.shadow.TestdataShadowedConstraintProviderClass;
import greycos.solver.core.testcotwin.shadow.TestdataShadowedEntity;
import greycos.solver.core.testcotwin.shadow.TestdataShadowedSolution;

import org.junit.jupiter.api.Test;

class MoveExecutionIntegrationTest {
  @Test
  void phaseCachedPreviewSwapMustSwapCurrentValuesAtEveryStep() {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(ZeroScore.class)
            .withMoveThreadCount("NONE")
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withLocalSearchType(LocalSearchType.HILL_CLIMBING)
                    .withMoveSelectorConfig(
                        new MoveListFactoryConfig()
                            .withMoveListFactoryClass(CachedSwapFactory.class)
                            .withCacheType(SelectionCacheType.PHASE)
                            .withSelectionOrder(SelectionOrder.ORIGINAL))
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(2)));
    var solver =
        (AbstractSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var states = new ArrayList<List<String>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
            states.add(
                scope.getWorkingSolution().getEntityList().stream()
                    .map(e -> e.getValue().getCode())
                    .toList());
          }
        });
    var solution = TestdataSolution.generateSolution(2, 2);
    var original = solution.getEntityList().stream().map(e -> e.getValue().getCode()).toList();
    solver.solve(solution);
    assertThat(states).hasSize(2);
    assertThat(states.get(1)).isEqualTo(original);
  }

  public static class ZeroScore implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  public static class CachedSwapFactory implements MoveListFactory<TestdataSolution> {
    @Override
    public List<? extends Move<TestdataSolution>> createMoveList(TestdataSolution solution) {
      var variable =
          TestdataSolution.buildSolutionDescriptor()
              .getMetaModel()
              .genuineEntity(TestdataEntity.class)
              .basicVariable("value", TestdataValue.class);
      return List.of(
          Moves.swap(variable, solution.getEntityList().get(0), solution.getEntityList().get(1)));
    }
  }

  @Test
  void compositeCustomFactoryMustFlushBeforeDependentChildInActualSolver() {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataShadowedSolution.class)
            .withEntityClasses(TestdataShadowedEntity.class)
            .withConstraintProviderClass(TestdataShadowedConstraintProviderClass.class)
            .withMoveThreadCount("NONE")
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withLocalSearchType(LocalSearchType.HILL_CLIMBING)
                    .withMoveSelectorConfig(
                        new MoveIteratorFactoryConfig()
                            .withMoveIteratorFactoryClass(CompositeFactory.class)
                            .withSelectionOrder(SelectionOrder.ORIGINAL))
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(1)));
    var solver =
        (AbstractSolver<TestdataShadowedSolution>)
            SolverFactory.<TestdataShadowedSolution>create(config).buildSolver();
    var states = new ArrayList<List<String>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataShadowedSolution> scope) {
            states.add(
                scope.getWorkingSolution().getEntityList().stream()
                    .map(e -> e.getValue().getCode())
                    .toList());
          }
        });
    var solution = TestdataShadowedSolution.generateSolution(2, 2);
    solution.getEntityList().get(1).setValue(solution.getValueList().getFirst());
    solver.solve(solution);
    assertThat(states).containsExactly(List.of("Generated Value 1", "Generated Value 1"));
  }

  public static class CompositeFactory
      implements MoveIteratorFactory<TestdataShadowedSolution, Move<TestdataShadowedSolution>> {
    @Override
    public long getSize(ScoreDirector<TestdataShadowedSolution> scoreDirector) {
      return 1;
    }

    @Override
    public Iterator<Move<TestdataShadowedSolution>> createRandomMoveIterator(
        ScoreDirector<TestdataShadowedSolution> scoreDirector, RandomGenerator random) {
      return createOriginalMoveIterator(scoreDirector);
    }

    @Override
    public Iterator<Move<TestdataShadowedSolution>> createOriginalMoveIterator(
        ScoreDirector<TestdataShadowedSolution> scoreDirector) {
      var solution = scoreDirector.getWorkingSolution();
      var descriptor =
          ((VariableDescriptorAwareScoreDirector<TestdataShadowedSolution>) scoreDirector)
              .getSolutionDescriptor();
      var variable =
          descriptor
              .findEntityDescriptorOrFail(TestdataShadowedEntity.class)
              .getGenuineVariableDescriptor("value");
      var meta =
          descriptor
              .getMetaModel()
              .genuineEntity(TestdataShadowedEntity.class)
              .basicVariable("value", TestdataValue.class);
      var first = solution.getEntityList().get(0);
      var second = solution.getEntityList().get(1);
      Move<TestdataShadowedSolution> dependent =
          view -> {
            var target =
                solution.getValueList().stream()
                    .filter(v -> first.getFirstShadow().equals(v.getCode() + "/firstShadow"))
                    .findFirst()
                    .orElseThrow();
            view.changeVariable(meta, second, target);
          };
      return List.of(
              SelectorBasedCompositeMove.buildMove(
                  new SelectorBasedChangeMove<>(variable, first, solution.getValueList().get(1)),
                  dependent))
          .iterator();
    }
  }
}
