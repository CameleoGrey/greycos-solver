package greycos.solver.core.impl.heuristic.selector.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveListFactoryConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.selector.move.factory.MoveListFactory;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirectorFactoryFactory;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class SelectionCacheLifecycleBridgeTest {

  @ParameterizedTest
  @EnumSource(
      value = SelectionCacheType.class,
      names = {"PHASE", "SOLVER"})
  void stepStartRebuildsForDirectorIdentityAndEntityRevision(SelectionCacheType cacheType) {
    try (var initialDirector = buildDirector(EnvironmentMode.PHASE_ASSERT);
        var replacementDirector = buildDirector(EnvironmentMode.FULL_ASSERT)) {
      assertThat(replacementDirector.getWorkingEntityListRevision())
          .isEqualTo(initialDirector.getWorkingEntityListRevision());
      var solverScope = new SolverScope<TestdataSolution>(Clock.systemUTC());
      solverScope.setScoreDirector(initialDirector);
      var phaseScope = new LocalSearchPhaseScope<>(solverScope, 0);
      var stepScope = new LocalSearchStepScope<>(phaseScope);
      var listener = new EntityCache();
      var bridge = new SelectionCacheLifecycleBridge<>(cacheType, listener);
      bridge.solvingStarted(solverScope);
      bridge.phaseStarted(phaseScope);
      bridge.stepStarted(stepScope);
      assertThat(listener.constructCount).isEqualTo(1);
      assertThat(listener.cachedEntity)
          .isSameAs(initialDirector.getWorkingSolution().getEntityList().getFirst());

      solverScope.setScoreDirector(replacementDirector);
      bridge.stepStarted(stepScope);
      assertThat(listener.constructCount).isEqualTo(2);
      assertThat(listener.disposeCount).isEqualTo(1);
      assertThat(listener.cachedEntity)
          .isSameAs(replacementDirector.getWorkingSolution().getEntityList().getFirst());

      replacementDirector.setWorkingSolution(replacementDirector.cloneWorkingSolution());
      bridge.stepStarted(stepScope);
      assertThat(listener.constructCount).isEqualTo(3);
      assertThat(listener.disposeCount).isEqualTo(2);
      assertThat(listener.cachedEntity)
          .isSameAs(replacementDirector.getWorkingSolution().getEntityList().getFirst());

      bridge.stepStarted(stepScope);
      assertThat(listener.constructCount).isEqualTo(3);
      assertThat(listener.disposeCount).isEqualTo(2);
      bridge.phaseEnded(phaseScope);
      bridge.solvingEnded(solverScope);
      assertThat(listener.cachedEntity).isNull();
      assertThat(listener.disposeCount).isEqualTo(3);
    }
  }

  @Test
  void solverCacheRefreshesAtPhaseEntryAndReusesAnUnchangedDirector() {
    try (var initialDirector = buildDirector(EnvironmentMode.PHASE_ASSERT);
        var fullAssertDirector = buildDirector(EnvironmentMode.FULL_ASSERT);
        var restoredModeDirector = buildDirector(EnvironmentMode.PHASE_ASSERT)) {
      var solverScope = new SolverScope<TestdataSolution>(Clock.systemUTC());
      solverScope.setScoreDirector(initialDirector);
      var listener = new EntityCache();
      var bridge = new SelectionCacheLifecycleBridge<>(SelectionCacheType.SOLVER, listener);
      bridge.solvingStarted(solverScope);

      var directors = List.of(initialDirector, fullAssertDirector, restoredModeDirector);
      for (var phaseIndex = 0; phaseIndex < directors.size(); phaseIndex++) {
        var director = directors.get(phaseIndex);
        assertThat(director.getWorkingEntityListRevision())
            .isEqualTo(initialDirector.getWorkingEntityListRevision());
        solverScope.setScoreDirector(director);
        var phaseScope = new LocalSearchPhaseScope<>(solverScope, phaseIndex);
        bridge.phaseStarted(phaseScope);
        // A parent selector may use this cache immediately during its own phaseStarted().
        assertThat(listener.cachedEntity)
            .isSameAs(director.getWorkingSolution().getEntityList().getFirst());
        assertThat(listener.constructCount).isEqualTo(phaseIndex + 1);
        bridge.stepStarted(new LocalSearchStepScope<>(phaseScope));
        bridge.stepStarted(new LocalSearchStepScope<>(phaseScope));
        assertThat(listener.constructCount).isEqualTo(phaseIndex + 1);
        bridge.phaseEnded(phaseScope);

        var unchangedPhase = new LocalSearchPhaseScope<>(solverScope, phaseIndex + 1);
        bridge.phaseStarted(unchangedPhase);
        bridge.stepStarted(new LocalSearchStepScope<>(unchangedPhase));
        bridge.phaseEnded(unchangedPhase);
        assertThat(listener.constructCount).isEqualTo(phaseIndex + 1);
      }
      bridge.solvingEnded(solverScope);
      assertThat(listener.cachedEntity).isNull();
      assertThat(listener.disposeCount).isEqualTo(3);
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = SelectionCacheType.class,
      names = {"STEP", "PHASE", "SOLVER"})
  void disposedCachesCanBeUsedByAnotherSolve(SelectionCacheType cacheType) {
    try (var director = buildDirector(EnvironmentMode.PHASE_ASSERT)) {
      var solverScope = new SolverScope<TestdataSolution>(Clock.systemUTC());
      solverScope.setScoreDirector(director);
      var listener = new EntityCache();
      var bridge = new SelectionCacheLifecycleBridge<>(cacheType, listener);
      for (var solveIndex = 0; solveIndex < 2; solveIndex++) {
        bridge.solvingStarted(solverScope);
        var phaseScope = new LocalSearchPhaseScope<>(solverScope, 0);
        bridge.phaseStarted(phaseScope);
        var stepScope = new LocalSearchStepScope<>(phaseScope);
        bridge.stepStarted(stepScope);
        assertThat(listener.cachedEntity)
            .isSameAs(director.getWorkingSolution().getEntityList().getFirst());
        assertThat(listener.constructCount).isEqualTo(solveIndex + 1);
        bridge.stepEnded(stepScope);
        bridge.phaseEnded(phaseScope);
        bridge.solvingEnded(solverScope);
        assertThat(listener.cachedEntity).isNull();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @Timeout(10)
  void solverCachedMovesFollowWorkingClonesAcrossPhaseModes(boolean partitioned) {
    List<PhaseConfig> phases =
        List.of(
            cachedSearch(EnvironmentMode.PHASE_ASSERT),
            cachedSearch(EnvironmentMode.FULL_ASSERT),
            cachedSearch(EnvironmentMode.PHASE_ASSERT),
            cachedSearch(EnvironmentMode.FULL_ASSERT));
    if (partitioned) {
      phases =
          List.of(
              new PartitionedSearchPhaseConfig()
                  .withSolutionPartitionerClass(OnePartition.class)
                  .withPhaseConfigList(phases));
    }
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class);
    config.setPhaseConfigList(phases);
    var solver = SolverFactory.<TestdataSolution>create(config).buildSolver();
    for (var solveIndex = 0; solveIndex < 2; solveIndex++) {
      var result = solver.solve(TestdataSolution.generateSolution(3, 3));
      assertThat(result.getEntityList())
          .allSatisfy(entity -> assertThat(entity.getValue()).isNotNull());
      assertThat(result.getScore())
          .isEqualTo(new TestdataEasyScoreCalculator().calculateScore(result));
    }
  }

  private static LocalSearchPhaseConfig cachedSearch(EnvironmentMode environmentMode) {
    return new LocalSearchPhaseConfig()
        .withEnvironmentMode(environmentMode)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(1))
        .withMoveSelectorConfig(
            new MoveListFactoryConfig()
                .withMoveListFactoryClass(AllChanges.class)
                .withCacheType(SelectionCacheType.SOLVER)
                .withSelectionOrder(SelectionOrder.ORIGINAL));
  }

  private static InnerScoreDirector<TestdataSolution, SimpleScore> buildDirector(
      EnvironmentMode environmentMode) {
    var factory =
        new ScoreDirectorFactoryFactory<TestdataSolution, SimpleScore>(
                new ScoreDirectorFactoryConfig()
                    .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class))
            .buildScoreDirectorFactory(environmentMode, TestdataSolution.buildSolutionDescriptor());
    var director = factory.createScoreDirectorBuilder(environmentMode).build();
    director.setWorkingSolution(TestdataSolution.generateSolution(3, 3));
    return director;
  }

  private static class EntityCache implements SelectionCacheLifecycleListener<TestdataSolution> {
    private TestdataEntity cachedEntity;
    private int constructCount;
    private int disposeCount;

    @Override
    public void constructCache(SolverScope<TestdataSolution> solverScope) {
      cachedEntity = solverScope.getWorkingSolution().getEntityList().getFirst();
      constructCount++;
    }

    @Override
    public void disposeCache(SolverScope<TestdataSolution> solverScope) {
      cachedEntity = null;
      disposeCount++;
    }
  }

  public static class OnePartition implements SolutionPartitioner<TestdataSolution> {
    @Override
    public List<TestdataSolution> splitWorkingSolution(
        ScoreDirector<TestdataSolution> scoreDirector, Integer runnablePartThreadLimit) {
      return List.of(
          ((InnerScoreDirector<TestdataSolution, ?>) scoreDirector).cloneWorkingSolution());
    }
  }

  public static class AllChanges implements MoveListFactory<TestdataSolution> {
    @Override
    public List<? extends Move<TestdataSolution>> createMoveList(TestdataSolution solution) {
      var variableDescriptor = TestdataEntity.buildVariableDescriptorForValue();
      var moves = new ArrayList<Move<TestdataSolution>>();
      for (var entity : solution.getEntityList()) {
        for (var value : solution.getValueList()) {
          moves.add(new ChangeMove<>(variableDescriptor, entity, value));
        }
      }
      return moves;
    }
  }
}
