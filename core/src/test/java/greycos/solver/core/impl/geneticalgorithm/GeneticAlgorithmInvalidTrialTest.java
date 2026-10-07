package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationOperatorConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmWorkspaceTest.CycleEntity;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmWorkspaceTest.CycleSolution;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GeneticAlgorithmInvalidTrialTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rangeInvalidAttemptsAdvanceSoleGlobalAndPhaseMoveLimits(boolean phaseLocal) {
    var solver = build(RangeSolution.class, RangeEntity.class, RangeConstraints.class, phaseLocal);
    var counts = observe(solver);
    var input = new RangeSolution();
    var first = new RangeEntity();
    first.range = List.of(0L, 1L);
    first.value = 0L;
    var second = new RangeEntity();
    second.range = List.of(10L, 11L);
    second.value = 10L;
    input.entities = new ArrayList<>(List.of(first, second));

    var result = solver.solve(input);

    assertThat(counts.get()).isEqualTo(1L); // Initial phase score only; no range-invalid scoring.
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(7);
    assertThat(result.entities).extracting(entity -> entity.value).containsExactly(0L, 10L);
    assertThat(result.score).isEqualTo(SimpleScore.of(10));
  }

  @Test
  void structuralInvalidAttemptsRestoreBeforeCompletionAndKeepSession() {
    var solver = build(CycleSolution.class, CycleEntity.class, CycleConstraints.class, true);
    var counts = observe(solver);
    var input = new CycleSolution();
    var root = new CycleEntity();
    var child = new CycleEntity();
    child.previous = root;
    input.entities = new ArrayList<>(List.of(root, child));

    var result = solver.solve(input);

    assertThat(counts.get()).isEqualTo(8L); // Initial score plus seven restored-workspace scores.
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(7);
    assertThat(result.entities.getFirst().previous).isNull();
    assertThat(result.entities.getLast().previous).isSameAs(result.entities.getFirst());
    assertThat(result.entities).extracting(entity -> entity.depth).containsExactly(0, 1);
    assertThat(result.score).isEqualTo(SimpleScore.of(-1));
  }

  private static <Solution_> DefaultSolver<Solution_> build(
      Class<Solution_> solutionClass,
      Class<?> entityClass,
      Class<? extends ConstraintProvider> constraints,
      boolean phaseLocal) {
    var termination = new TerminationConfig().withMoveCountLimit(7L);
    var phase =
        new GeneticAlgorithmPhaseConfig()
            .withPopulationSize(1)
            .withMutationOperators(
                new GeneticAlgorithmMutationOperatorConfig()
                    .withType(GeneticAlgorithmMutationType.SWAP)
                    .withProbability(1.0));
    var config =
        new SolverConfig()
            .withSolutionClass(solutionClass)
            .withEntityClasses(entityClass)
            .withConstraintProviderClass(constraints)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withRandomSeed(37L)
            .withPhases(phase);
    if (phaseLocal) phase.setTerminationConfig(termination);
    else config.setTerminationConfig(termination);
    return (DefaultSolver<Solution_>) SolverFactory.<Solution_>create(config).buildSolver();
  }

  private static <Solution_> AtomicReference<Long> observe(DefaultSolver<Solution_> solver) {
    var calculations = new AtomicReference<Long>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          private Object session;

          @Override
          public void phaseStarted(AbstractPhaseScope<Solution_> scope) {
            session =
                ((BavetConstraintStreamScoreDirector<?, ?>) scope.getScoreDirector()).getSession();
          }

          @Override
          public void stepEnded(AbstractStepScope<Solution_> scope) {
            var step = (GeneticAlgorithmStepScope<Solution_>) scope;
            assertThat(step.getOutcome()).isEqualTo(GeneticAlgorithmOutcome.INVALID);
            assertThat(step.isAdmitted()).isFalse();
            assertThat(step.getCandidateScore()).isNull();
            assertThat(step.getScore().isFullyAssigned()).isTrue();
            assertThat(step.getScore().isStructurallyFlawed()).isFalse();
            assertThat(
                    ((BavetConstraintStreamScoreDirector<?, ?>) step.getScoreDirector())
                        .getSession())
                .isSameAs(session);
            assertThat(solver.getSolverScope().getMoveEvaluationCount())
                .isEqualTo(step.getStepIndex() + 1);
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<Solution_> scope) {
            calculations.set(scope.getPhaseScoreCalculationCount());
            assertThat(scope.getNextStepIndex()).isEqualTo(7);
          }
        });
    return calculations;
  }

  @PlanningSolution
  public static class RangeSolution {
    @PlanningEntityCollectionProperty public List<RangeEntity> entities;
    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class RangeEntity {
    @ValueRangeProvider(id = "range")
    public List<Long> range;

    @PlanningVariable(valueRangeProviderRefs = "range")
    public Long value;
  }

  public static class RangeConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(RangeEntity.class)
            .reward(SimpleScore.ONE, entity -> entity.value)
            .asConstraint("value")
      };
    }
  }

  public static class CycleConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEachIncludingUnassigned(CycleEntity.class)
            .penalize(SimpleScore.ONE, entity -> entity.depth)
            .asConstraint("depth")
      };
    }
  }
}
