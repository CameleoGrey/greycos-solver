package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.event.BestSolutionChangedEvent;
import greycos.solver.core.api.solver.phase.PhaseCommand;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.preview.api.move.builtin.Moves;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class IslandEventDeliveryTest {

  @Test
  void nestedConstructionOnlyDeliversInitializedEventsOnTheCallingThread() {
    var solver = solver(2, new ConstructionHeuristicPhaseConfig());
    var callingThread = Thread.currentThread();
    var callbackThreads = new CopyOnWriteArrayList<Thread>();
    var events = new CopyOnWriteArrayList<BestSolutionChangedEvent<TestdataSolution>>();
    solver.addEventListener(
        event -> {
          callbackThreads.add(Thread.currentThread());
          events.add(event);
        });

    var result = solver.solve(TestdataSolution.generateUninitializedSolution(3, 8));

    assertThat(events)
        .hasSize(1)
        .allSatisfy(
            event -> {
              assertThat(event.isNewBestSolutionInitialized()).isTrue();
              assertThat(event.getNewBestSolution().getEntityList())
                  .allSatisfy(entity -> assertThat(entity.getValue()).isNotNull());
              assertThat(
                      new TestdataEasyScoreCalculator().calculateScore(event.getNewBestSolution()))
                  .isEqualTo(event.getNewBestScore());
              assertThat(event.getProducerId().phaseIndex()).hasValue(0);
            });
    assertThat(callbackThreads).containsExactly(callingThread);
    assertThat(result.getScore()).isEqualTo(events.getFirst().getNewBestScore());
    assertThat(solver.getSolverScope().<SimpleScore>getStartingInitializedScore())
        .isEqualTo(events.getFirst().getNewBestScore());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3})
  void preservesEveryStrictImprovementAndDeliversTheFinalEventBeforeReturning(int islandCount) {
    var solver = solver(islandCount, improvingPhase());
    var callingThread = Thread.currentThread();
    var callbackThreads = new CopyOnWriteArrayList<Thread>();
    var events = new CopyOnWriteArrayList<BestSolutionChangedEvent<TestdataSolution>>();
    solver.addEventListener(
        event -> {
          callbackThreads.add(Thread.currentThread());
          events.add(event);
        });

    var result = solver.solve(conflictingSolution());

    assertThat(events)
        .extracting(BestSolutionChangedEvent::getNewBestScore)
        .containsExactly(
            SimpleScore.of(-20),
            SimpleScore.of(-12),
            SimpleScore.of(-6),
            SimpleScore.of(-2),
            SimpleScore.ZERO);
    assertThat(callbackThreads).allMatch(thread -> thread == callingThread);
    assertThat(events)
        .allSatisfy(
            event -> {
              assertThat(event.isNewBestSolutionInitialized()).isTrue();
              assertThat(
                      new TestdataEasyScoreCalculator().calculateScore(event.getNewBestSolution()))
                  .isEqualTo(event.getNewBestScore());
            });
    assertThat(events).extracting(BestSolutionChangedEvent::getTimeMillisSpent).isSorted();
    assertThat(result.getScore()).isEqualTo(events.getLast().getNewBestScore());
    assertThat(solver.getSolverScope().<SimpleScore>getStartingInitializedScore())
        .isEqualTo(SimpleScore.of(-20));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void listenerFailuresPropagateAndStopFurtherCallbacks(boolean error) {
    Throwable failure =
        error
            ? new AssertionError("Listener failed")
            : new IllegalStateException("Listener failed");
    var solver = solver(2, improvingPhase());
    var callbackThreads = new CopyOnWriteArrayList<Thread>();
    var scores = new CopyOnWriteArrayList<Object>();
    solver.addEventListener(
        event -> {
          callbackThreads.add(Thread.currentThread());
          scores.add(event.getNewBestScore());
          if (event.getProducerId().phaseIndex().isPresent()) {
            if (failure instanceof Error listenerError) {
              throw listenerError;
            }
            throw (RuntimeException) failure;
          }
        });

    assertThat(catchThrowable(() -> solver.solve(conflictingSolution()))).isSameAs(failure);

    assertThat(scores).containsExactly(SimpleScore.of(-20), SimpleScore.of(-12));
    assertThat(callbackThreads).containsOnly(Thread.currentThread());
    assertThat(solver.isSolving()).isFalse();
  }

  private static DefaultSolver<TestdataSolution> solver(int islandCount, PhaseConfig<?> phase) {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(islandCount)
                    .withPhaseConfigList(List.of(phase)));
    return (DefaultSolver<TestdataSolution>)
        SolverFactory.<TestdataSolution>create(config).buildSolver();
  }

  private static CustomPhaseConfig improvingPhase() {
    var commands = new ArrayList<PhaseCommand<TestdataSolution>>();
    for (int i = 1; i < 5; i++) {
      var index = i;
      commands.add(
          context -> {
            var solution = context.getWorkingSolution();
            var variable =
                context
                    .getSolutionMetaModel()
                    .genuineEntity(TestdataEntity.class)
                    .basicVariable("value", TestdataValue.class);
            context.executeAndCalculateScore(
                Moves.change(
                    variable,
                    solution.getEntityList().get(index),
                    solution.getValueList().get(index)));
          });
    }
    return new CustomPhaseConfig().withCustomPhaseCommandList(commands);
  }

  private static TestdataSolution conflictingSolution() {
    var solution = TestdataSolution.generateSolution(5, 5);
    solution.getEntityList().forEach(entity -> entity.setValue(solution.getValueList().getFirst()));
    return solution;
  }
}
