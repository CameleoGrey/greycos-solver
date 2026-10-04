package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicForagerConfig;
import greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicPickEarlyType;
import greycos.solver.core.config.constructionheuristic.placer.PooledEntityPlacerConfig;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.constructionheuristic.DefaultConstructionHeuristicPhase;
import greycos.solver.core.impl.constructionheuristic.placer.PooledEntityPlacer;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionSorter;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ListAssignMove;
import greycos.solver.core.impl.localsearch.DefaultLocalSearchPhase;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/** End-to-end construction edge cases with independent assignment and score checks. */
@Execution(ExecutionMode.SAME_THREAD)
@Timeout(20)
class ConstructionEdgeCaseTest {
  private static final ThreadLocal<List<String>> TRACE = new ThreadLocal<>();

  @AfterEach
  void clearTrace() {
    TRACE.remove();
  }

  @Test
  void emptyValuesWithTwoOwnersCompleteDefaultPhasesNaturally() {
    // No configured phases or termination: use the actual default CH -> LS pipeline.
    var factory = SolverFactory.<TestdataListSolution>create(listConfig());
    var solver = (DefaultSolver<TestdataListSolution>) factory.buildSolver();
    assertThat(solver.getPhaseList()).hasSize(2);
    assertThat(solver.getPhaseList().get(0)).isInstanceOf(DefaultConstructionHeuristicPhase.class);
    assertThat(solver.getPhaseList().get(1)).isInstanceOf(DefaultLocalSearchPhase.class);
    var phaseStarts = new ArrayList<Integer>();
    var completedSteps = new ArrayList<Integer>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataListSolution> scope) {
            phaseStarts.add(scope.getPhaseIndex());
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataListSolution> scope) {
            completedSteps.add(scope.getNextStepIndex());
          }
        });
    var solved = solver.solve(TestdataListSolution.generateUninitializedSolution(0, 2));
    // Local search fast-exits before phaseStarted when the problem has exactly one solution.
    assertThat(phaseStarts).containsExactly(0);
    assertThat(completedSteps).containsExactly(0);
    assertThat(solver.isTerminateEarly()).isFalse();
    assertThat(solver.getSolverScope().getProblemSizeStatistics().approximateProblemSizeLog())
        .isZero();
    var construction = (DefaultConstructionHeuristicPhase<?>) solver.getPhaseList().getFirst();
    assertThat(construction.getTerminationStatus().early()).isFalse();
    assertThat(construction.getTerminationStatus().stepCount()).isZero();
    assertThat(solved.getEntityList())
        .hasSize(2)
        .allSatisfy(e -> assertThat(e.getValueList()).isEmpty());
    assertThat(solved.getValueList()).isEmpty();
    assertThat(solved.getScore()).isEqualTo(SimpleScore.of(2));
    assertThat(SolutionManager.create(factory).update(solved)).isEqualTo(SimpleScore.of(2));
  }

  @Test
  void pooledStepSortingOccursBeforeEachStepAndRefreshesOrder() {
    TRACE.set(new ArrayList<>());
    var selector =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(
                new EntitySelectorConfig(TestdataEntity.class)
                    .withSelectionOrder(SelectionOrder.SORTED)
                    .withCacheType(SelectionCacheType.STEP)
                    .withSorterClass(AssignmentDependentSorter.class));
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(AssignedCountCalculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount("NONE")
            .withPhases(
                new ConstructionHeuristicPhaseConfig()
                    .withEntityPlacerConfig(
                        new PooledEntityPlacerConfig().withMoveSelectorConfig(selector))
                    .withForagerConfig(
                        new ConstructionHeuristicForagerConfig()
                            .withPickEarlyType(
                                ConstructionHeuristicPickEarlyType.FIRST_NON_DETERIORATING_SCORE)));
    var factory = SolverFactory.<TestdataSolution>create(config);
    var solver = (DefaultSolver<TestdataSolution>) factory.buildSolver();
    var construction =
        (DefaultConstructionHeuristicPhase<TestdataSolution>) solver.getPhaseList().getFirst();
    assertThat(construction.getEntityPlacer()).isInstanceOf(PooledEntityPlacer.class);
    var chosen = new ArrayList<String>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepStarted(AbstractStepScope<TestdataSolution> scope) {
            TRACE.get().add("start:" + scope.getStepIndex());
          }

          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
            var move =
                (ChangeMove<?>)
                    ((ConstructionHeuristicStepScope<TestdataSolution>) scope).getStep();
            chosen.add(((TestdataEntity) move.getEntity()).getCode());
          }
        });
    var solved = solver.solve(TestdataSolution.generateUninitializedSolution(1, 3));
    assertThat(TRACE.get())
        .containsSubsequence(
            "sort:0:[Generated Entity 0, Generated Entity 1, Generated Entity 2]", "start:0",
            "sort:1:[Generated Entity 2, Generated Entity 1, Generated Entity 0]", "start:1",
            "sort:2:[Generated Entity 0, Generated Entity 1, Generated Entity 2]", "start:2");
    assertThat(chosen)
        .containsExactly("Generated Entity 0", "Generated Entity 2", "Generated Entity 1");
    assertThat(solved.getEntityList())
        .allSatisfy(e -> assertThat(e.getValue()).isSameAs(solved.getValueList().getFirst()));
    assertThat(solved.getScore()).isEqualTo(SimpleScore.of(3));
    assertThat(SolutionManager.create(factory).update(solved)).isEqualTo(SimpleScore.of(3));
    assertThat(construction.getTerminationStatus().early()).isFalse();
  }

  @Test
  void topLevelListChangeFilterRejectsOwnerAndNonTailDestinations() {
    TRACE.set(new ArrayList<>());
    var topLevel =
        new ListChangeMoveSelectorConfig().withFilterClass(OnlySecondOwnerAppendFilter.class);
    var config =
        listConfig()
            .withPhases(
                new ConstructionHeuristicPhaseConfig()
                    .withMoveSelectorConfigList(List.of(topLevel)));
    var factory = SolverFactory.<TestdataListSolution>create(config);
    var solver = (DefaultSolver<TestdataListSolution>) factory.buildSolver();
    var solved = solver.solve(TestdataListSolution.generateUninitializedSolution(3, 2));
    assertThat(TRACE.get())
        .anyMatch(s -> s.startsWith("reject-owner:"))
        .anyMatch(s -> s.startsWith("reject-index:"))
        .anyMatch(s -> s.startsWith("accept:"));
    assertThat(TRACE.get().stream().filter(s -> s.startsWith("reject-owner:")).count())
        .isEqualTo(3);
    assertThat(TRACE.get().stream().filter(s -> s.startsWith("reject-index:")).count())
        .isEqualTo(3);
    assertThat(TRACE.get().stream().filter(s -> s.startsWith("accept:")).count()).isEqualTo(3);
    assertThat(solved.getEntityList().getFirst().getValueList()).isEmpty();
    assertThat(solved.getEntityList().get(1).getValueList())
        .containsExactlyElementsOf(solved.getValueList());
    for (int i = 0; i < solved.getValueList().size(); i++) {
      assertThat(solved.getValueList().get(i).getEntity()).isSameAs(solved.getEntityList().get(1));
      assertThat(solved.getValueList().get(i).getIndex()).isEqualTo(i);
    }
    // Independent formula for this score: one point per owner plus one per singleton.
    var replay =
        2 + solved.getEntityList().stream().filter(e -> e.getValueList().size() == 1).count();
    assertThat(solved.getScore())
        .isEqualTo(SimpleScore.of((int) replay))
        .isEqualTo(SimpleScore.of(2));
    assertThat(SolutionManager.create(factory).update(solved)).isEqualTo(SimpleScore.of(2));
    // Factory must copy the supplied top-level selector when inserting the source mimic.
    assertThat(topLevel.getValueSelectorConfig()).isNull();
  }

  private static SolverConfig listConfig() {
    return new SolverConfig()
        .withSolutionClass(TestdataListSolution.class)
        .withEntityClasses(TestdataListEntity.class, TestdataListValue.class)
        .withConstraintProviderClass(OwnerListConstraintProvider.class)
        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
        .withMoveThreadCount("NONE");
  }

  public static final class AssignmentDependentSorter
      implements SelectionSorter<TestdataSolution, TestdataEntity> {
    @Override
    public void sort(TestdataSolution solution, List<TestdataEntity> entities) {
      long assigned = solution.getEntityList().stream().filter(e -> e.getValue() != null).count();
      Comparator<TestdataEntity> comparator = Comparator.comparing(TestdataEntity::getCode);
      entities.sort(assigned % 2 == 0 ? comparator : comparator.reversed());
      TRACE
          .get()
          .add("sort:" + assigned + ":" + entities.stream().map(TestdataEntity::getCode).toList());
    }

    @Override
    public SortedSet<TestdataEntity> sort(TestdataSolution solution, Set<TestdataEntity> entities) {
      throw new UnsupportedOperationException("List sorting is expected.");
    }
  }

  public static final class OwnerListConstraintProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory.forEach(TestdataListEntity.class).reward(SimpleScore.ONE).asConstraint("owners"),
        factory
            .forEach(TestdataListEntity.class)
            .filter(e -> e.getValueList().size() == 1)
            .reward(SimpleScore.ONE)
            .asConstraint("singletons")
      };
    }
  }

  public static final class AssignedCountCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      int score = 0;
      for (var entity : solution.getEntityList()) {
        if (entity.getValue() != null) score++;
      }
      return SimpleScore.of(score);
    }
  }

  public static final class OnlySecondOwnerAppendFilter
      implements SelectionFilter<TestdataListSolution, Move<TestdataListSolution>> {
    @Override
    public boolean accept(
        ScoreDirector<TestdataListSolution> director, Move<TestdataListSolution> move) {
      var assign = (ListAssignMove<TestdataListSolution>) move;
      var destination = (TestdataListEntity) assign.getDestinationEntity();
      String result =
          destination.getCode().endsWith(" 0")
              ? "reject-owner"
              : assign.getDestinationIndex() != destination.getValueList().size()
                  ? "reject-index"
                  : "accept";
      TRACE.get().add(result + ":" + assign);
      return result.equals("accept");
    }
  }
}
