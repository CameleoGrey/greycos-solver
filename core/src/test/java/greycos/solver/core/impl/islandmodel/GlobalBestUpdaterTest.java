package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.TestdataListVarEasyScoreCalculator;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class GlobalBestUpdaterTest {

  @Test
  void detachedBestIsPublishedWithoutAnotherClone() {
    var working = TestdataSolution.generateSolution(2, 2);
    var detachedBest = TestdataSolution.generateSolution(2, 2);
    @SuppressWarnings("unchecked")
    InnerScoreDirector<TestdataSolution, SimpleScore> director = mock(InnerScoreDirector.class);
    when(director.getWorkingSolution()).thenReturn(working);
    var scope = new SolverScope<TestdataSolution>();
    scope.setScoreDirector(director);
    scope.setBestSolution(detachedBest);
    scope.setInitializedBestScore(SimpleScore.ZERO);
    var global = new SharedGlobalState<TestdataSolution>();

    new GlobalBestUpdater<>(global, 0)
        .stepEnded(new LocalSearchStepScope<>(new LocalSearchPhaseScope<>(scope, 0)));

    assertThat(global.getBestSolution()).isSameAs(detachedBest);
    verify(director, never()).cloneSolution(detachedBest);
  }

  @Test
  void retainedConstructionSnapshotsKeepBasicAssignmentsAndScores() throws Exception {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class);
    var snapshots =
        collectConstructionSnapshots(
            config,
            TestdataSolution.generateUninitializedSolution(3, 8),
            snapshot -> {
              var solution = snapshot.getSolution();
              assertThat(snapshot.getInnerScore().unassignedCount())
                  .isEqualTo(
                      (int)
                          solution.getEntityList().stream()
                              .filter(entity -> entity.getValue() == null)
                              .count());
              assertThat(snapshot.getScore())
                  .isEqualTo(new TestdataEasyScoreCalculator().calculateScore(solution));
            });
    assertThat(snapshots).hasSizeGreaterThan(2);
    assertThat(snapshots.getFirst().getInnerScore().unassignedCount()).isPositive();
    assertThat(snapshots.getLast().getInnerScore().isFullyAssigned()).isTrue();
  }

  @Test
  void retainedConstructionSnapshotsKeepListAssignmentsAndShadows() throws Exception {
    var config =
        PlannerTestUtils.buildSolverConfig(
                TestdataListSolution.class, TestdataListEntity.class, TestdataListValue.class)
            .withEasyScoreCalculatorClass(TestdataListVarEasyScoreCalculator.class);
    var snapshots =
        collectConstructionSnapshots(
            config,
            TestdataListSolution.generateUninitializedSolution(8, 3),
            snapshot -> {
              var solution = snapshot.getSolution();
              var assignedCount =
                  solution.getEntityList().stream()
                      .mapToInt(entity -> entity.getValueList().size())
                      .sum();
              assertThat(snapshot.getInnerScore().unassignedCount())
                  .isEqualTo(solution.getValueList().size() - assignedCount);
              assertThat(snapshot.getScore())
                  .isEqualTo(new TestdataListVarEasyScoreCalculator().calculateScore(solution));
              for (var entity : solution.getEntityList()) {
                for (int index = 0; index < entity.getValueList().size(); index++) {
                  var value = entity.getValueList().get(index);
                  assertThat(value.getEntity()).isSameAs(entity);
                  assertThat(value.getIndex()).isEqualTo(index);
                }
              }
            });
    assertThat(snapshots).hasSizeGreaterThan(2);
    assertThat(snapshots.getLast().getInnerScore().isFullyAssigned()).isTrue();
  }

  private static <Solution_>
      List<SharedGlobalState.BestSolutionSnapshot<Solution_>> collectConstructionSnapshots(
          SolverConfig config,
          Solution_ problem,
          Consumer<SharedGlobalState.BestSolutionSnapshot<Solution_>> verifySnapshot)
          throws Exception {
    config
        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
        .withPhases(
            new IslandModelPhaseConfig()
                .withIslandCount(1)
                .withPhaseConfigList(List.of(new ConstructionHeuristicPhaseConfig())));
    var solver = (DefaultSolver<Solution_>) SolverFactory.<Solution_>create(config).buildSolver();
    var snapshots = new CopyOnWriteArrayList<SharedGlobalState.BestSolutionSnapshot<Solution_>>();
    ((DefaultIslandModelPhase<Solution_>) solver.getPhaseList().getFirst())
        .getGlobalState()
        .addObserver(snapshots::add);
    var done = new AtomicBoolean();
    var readerStarted = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var reader =
          executor.submit(
              () -> {
                readerStarted.countDown();
                do {
                  snapshots.forEach(verifySnapshot);
                  Thread.yield();
                } while (!done.get());
              });
      assertThat(readerStarted.await(5, TimeUnit.SECONDS)).isTrue();
      try {
        solver.solve(problem);
      } finally {
        done.set(true);
      }
      reader.get(5, TimeUnit.SECONDS);
    }
    snapshots.forEach(verifySnapshot);
    return snapshots;
  }
}
