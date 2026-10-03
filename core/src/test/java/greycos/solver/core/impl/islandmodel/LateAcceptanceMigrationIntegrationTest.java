package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.localsearch.DefaultLocalSearchPhase;
import greycos.solver.core.impl.localsearch.decider.LocalSearchDecider;
import greycos.solver.core.impl.localsearch.decider.acceptor.AcceptorMigrationState;
import greycos.solver.core.impl.localsearch.decider.acceptor.LateAcceptanceHistory;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class LateAcceptanceMigrationIntegrationTest {

  @ParameterizedTest
  @CsvSource({"NONE,false", "2,false", "NONE,true", "2,true"})
  void ringAndGlobalAdoptionCarryAndRepublishTheDonorHistory(String workers, boolean global) {
    var state =
        new LateAcceptanceHistory(
            List.of(score(0), score(-6), score(-2)), 1, SimpleScore.class, 1, 0);
    var receiver =
        new ScriptedSolver(
            workers,
            false,
            5,
            List.of(
                local(1, 0, 0, 0, 0),
                new Injection(new int[] {1, 2, 3, 4, 0}, null, false),
                local(1, 1, 3, 4, 0)));
    var scope = receiver.solver.getSolverScope();
    var phases = receiver.solver.getPhaseList();
    scope.setSolver(
        new IslandSolver<>(
            EnvironmentMode.FULL_ASSERT,
            receiver.solver.getScoreDirectorFactory(),
            receiver.solver.getBestSolutionRecaller(),
            new BasicPlumbingTermination<>(false),
            phases));
    scope.startingNow();
    var random = DefaultRandomSource.seeded(0).splitForChildThread();
    scope.setWorkingRandom(random);
    var shared = new SharedGlobalState<TestdataSolution>();
    var sender = new BoundedChannel<AgentUpdate<TestdataSolution>>(1);
    var inbox = new BoundedChannel<AgentUpdate<TestdataSolution>>(1);
    var target = TestdataSolution.generateSolution(5, 5);
    for (var i = 0; i < 5; i++) {
      target.getEntityList().get(i).setValue(target.getValueList().get((i + 1) % 5));
    }
    if (global) {
      shared.tryUpdate(target, score(0), state);
    } else {
      var alive = new BitSet();
      alive.set(0);
      inbox.replace(new AgentUpdate<>(1, target, score(0), alive, state));
    }
    var config =
        IslandModelConfig.builder()
            .withIslandCount(1)
            .withMigrationFrequency(1)
            .withCompareGlobalEnabled(global)
            .withReceiveGlobalUpdateFrequency(1)
            .build();
    var latch = new CountDownLatch(1);
    new IslandAgent<>(0, phases, problem(), shared, sender, inbox, config, random, scope, latch)
        .run();

    assertThat(receiver.histories.get(1)).isEqualTo(state);
    assertThat(lateHistory(receiver.histories.get(2)).scores())
        .containsExactly(score(0), score(-2), score(-2));
    assertThat(sender.tryReceive().getAcceptorState()).isEqualTo(state);
    assertThat(shared.getBestSnapshot().getAcceptorState()).isEqualTo(state);
    assertThat(scope.getBestAcceptorMigrationState()).isEqualTo(state);
    assertThat(scope.getBestScore()).isEqualTo(score(0));
    assertThat(new TestdataEasyScoreCalculator().calculateScore(scope.getBestSolution()))
        .isEqualTo(SimpleScore.ZERO);
    assertThat(scope.getMoveEvaluationCount()).isEqualTo(3);
    assertThat(latch.getCount()).isZero();
    scope.getWorkerRegistry().assertNoActiveWorkers();
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void nestedPublicationPreservesHistoryThroughFinalCloningAndSolveReuse(String workers) {
    var local =
        new LocalSearchPhaseConfig()
            .withAcceptorConfig(new LocalSearchAcceptorConfig().withLateAcceptanceSize(3))
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(12));
    var nested =
        new IslandModelPhaseConfig()
            .withIslandCount(1)
            .withMoveThreadCount(workers)
            .withPhaseConfigList(List.of(local));
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(1)
                    .withPhaseConfigList(List.of(nested)));
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var snapshots =
        new CopyOnWriteArrayList<SharedGlobalState.BestSolutionSnapshot<TestdataSolution>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> scope) {
            ((DefaultIslandModelPhase<TestdataSolution>) solver.getPhaseList().getFirst())
                .getGlobalState()
                .addObserver(snapshots::add);
          }
        });
    for (var run = 0; run < 2; run++) {
      snapshots.clear();
      var result = solver.solve(problem());
      assertThat(snapshots.size()).isGreaterThan(1);
      assertThat(snapshots.getFirst().getAcceptorState())
          .isSameAs(AcceptorMigrationState.Empty.INSTANCE);
      for (var snapshot : snapshots.subList(1, snapshots.size())) {
        var history = lateHistory(snapshot.getAcceptorState());
        assertThat(
                history
                    .scores()
                    .get(
                        (history.nextIndex() + history.scores().size() - 1)
                            % history.scores().size()))
            .isEqualTo(snapshot.getInnerScore());
        assertThat(new TestdataEasyScoreCalculator().calculateScore(snapshot.getSolution()))
            .isEqualTo(snapshot.getScore());
      }
      assertThat(solver.getSolverScope().getBestAcceptorMigrationState())
          .isEqualTo(snapshots.getLast().getAcceptorState());
      assertThat(result.getScore()).isEqualTo(snapshots.getLast().getScore());
      solver.getSolverScope().getWorkerRegistry().assertNoActiveWorkers();
    }
  }

  @ParameterizedTest
  @CsvSource({"NONE,false", "2,false", "NONE,true", "2,true"})
  void adoptsHistoryCapturedAtBestAndKeepsWorkersAndReuseConsistent(
      String workers, boolean composite) {
    var donorSteps =
        List.of(
            local(1, 0, 0, 0, 0),
            local(1, 2, 0, 0, 0),
            local(1, 2, 3, 0, 0),
            local(1, 2, 3, 4, 0),
            local(1, 1, 3, 4, 0),
            local(1, 1, 1, 4, 0));
    var donor = new ScriptedSolver(workers, composite, 3, donorSteps);
    donor.solve();
    var bestHistory = donor.bestStates.get(3);
    assertThat(lateHistory(bestHistory).scores()).containsExactly(score(0), score(-6), score(-2));
    assertThat(lateHistory(bestHistory).nextIndex()).isEqualTo(1);
    assertThat(donor.bestStates.get(4)).isSameAs(bestHistory);
    assertThat(donor.bestStates.get(5)).isSameAs(bestHistory);
    assertThat(lateHistory(donor.histories.get(5)).scores())
        .containsExactly(score(0), score(-2), score(-6));

    var receiver =
        new ScriptedSolver(
            workers,
            composite,
            5,
            List.of(
                local(1, 0, 0, 0, 0),
                new Injection(new int[] {1, 2, 3, 4, 0}, bestHistory),
                local(1, 1, 3, 4, 0)));
    for (var run = 0; run < 2; run++) {
      receiver.solve();
      assertThat(lateHistory(receiver.histories.getFirst()).scores()).hasSize(5);
      assertThat(receiver.histories.get(1)).isEqualTo(bestHistory);
      assertThat(lateHistory(receiver.histories.get(2)).scores())
          .containsExactly(score(0), score(-2), score(-2));
      assertThat(lateHistory(receiver.histories.get(2)).nextIndex()).isEqualTo(2);
      assertThat(receiver.bestStates.get(2)).isEqualTo(bestHistory);
      assertThat(receiver.solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(3);
      receiver.solver.getSolverScope().getWorkerRegistry().assertNoActiveWorkers();
    }
    assertThat(lateHistory(bestHistory).scores()).containsExactly(score(0), score(-6), score(-2));
  }

  private static Injection local(int... assignments) {
    return new Injection(assignments, null);
  }

  private record Injection(int[] assignments, AcceptorMigrationState state, boolean injected) {
    Injection(int[] assignments, AcceptorMigrationState state) {
      this(assignments, state, true);
    }
  }

  private static TestdataSolution problem() {
    var problem = TestdataSolution.generateSolution(5, 5);
    problem.getEntityList().forEach(entity -> entity.setValue(problem.getValueList().getFirst()));
    return problem;
  }

  private static InnerScore<SimpleScore> score(long score) {
    return InnerScore.fullyAssigned(SimpleScore.of(score));
  }

  private static LateAcceptanceHistory lateHistory(AcceptorMigrationState state) {
    if (state instanceof LateAcceptanceHistory history) return history;
    return lateHistory(((AcceptorMigrationState.Composite) state).children().getLast());
  }

  private static final class ScriptedSolver {
    final DefaultSolver<TestdataSolution> solver;
    final List<AcceptorMigrationState> histories = new ArrayList<>();
    final List<AcceptorMigrationState> bestStates = new ArrayList<>();

    ScriptedSolver(String workers, boolean composite, int historySize, List<Injection> injections) {
      var acceptorConfig = new LocalSearchAcceptorConfig().withLateAcceptanceSize(historySize);
      if (composite) {
        acceptorConfig.withAcceptorTypeList(
            List.of(AcceptorType.HILL_CLIMBING, AcceptorType.LATE_ACCEPTANCE));
      }
      var config =
          PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
              .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
              .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
              .withMoveThreadCount(workers)
              .withPhases(
                  new LocalSearchPhaseConfig()
                      .withAcceptorConfig(acceptorConfig)
                      .withTerminationConfig(
                          new TerminationConfig().withStepCountLimit(injections.size())));
      solver =
          (DefaultSolver<TestdataSolution>)
              SolverFactory.<TestdataSolution>create(config).buildSolver();
      solver.getSolverScope().setAcceptorMigrationEnabled(true);
      var phase = (DefaultLocalSearchPhase<TestdataSolution>) solver.getPhaseList().getFirst();
      var decider = (LocalSearchDecider<TestdataSolution>) phase.getDecider();
      phase.addPhaseLifecycleListener(
          new PhaseLifecycleListenerAdapter<>() {
            @Override
            public void phaseStarted(AbstractPhaseScope<TestdataSolution> phaseScope) {
              assertThat(phaseScope.getSolverScope().getBestAcceptorMigrationState())
                  .isSameAs(AcceptorMigrationState.Empty.INSTANCE);
            }

            @Override
            public void stepStarted(AbstractStepScope<TestdataSolution> scope) {
              var injection = injections.get(scope.getStepIndex());
              if (!injection.injected) return;
              var target = scope.getScoreDirector().cloneWorkingSolution();
              for (var i = 0; i < injection.assignments.length; i++) {
                target
                    .getEntityList()
                    .get(i)
                    .setValue(target.getValueList().get(injection.assignments[i]));
              }
              var move = SolutionSyncMove.createMove(scope.getScoreDirector(), target);
              var solverScope = scope.getPhaseScope().getSolverScope();
              if (injection.state == null) {
                solverScope.setPendingMove(move);
              } else {
                solverScope.setPendingMoveIfBetter(
                    move,
                    InnerScore.fullyAssigned(
                        new TestdataEasyScoreCalculator().calculateScore(target)),
                    true,
                    injection.state);
              }
            }

            @Override
            public void stepEnded(AbstractStepScope<TestdataSolution> scope) {
              var local = (LocalSearchStepScope<TestdataSolution>) scope;
              histories.add(decider.getAcceptor().snapshotMigrationState(local.getPhaseScope()));
              bestStates.add(
                  scope.getPhaseScope().getSolverScope().getBestAcceptorMigrationState());
              assertThat(scope.getScore().raw())
                  .isEqualTo(
                      new TestdataEasyScoreCalculator().calculateScore(scope.getWorkingSolution()));
              var assignments = injections.get(scope.getStepIndex()).assignments;
              for (var i = 0; i < assignments.length; i++) {
                assertThat(scope.getWorkingSolution().getEntityList().get(i).getValue())
                    .isSameAs(scope.getWorkingSolution().getValueList().get(assignments[i]));
              }
            }
          });
    }

    void solve() {
      histories.clear();
      bestStates.clear();
      var result = solver.solve(problem());
      assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
      assertThat(new TestdataEasyScoreCalculator().calculateScore(result))
          .isEqualTo(result.getScore());
    }
  }
}
