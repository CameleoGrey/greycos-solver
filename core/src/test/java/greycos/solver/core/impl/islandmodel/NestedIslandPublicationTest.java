package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.event.BestSolutionChangedEvent;
import greycos.solver.core.api.solver.phase.PhaseCommand;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.score.director.InnerScore;
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
class NestedIslandPublicationTest {

  @ParameterizedTest
  @ValueSource(ints = {2, 3})
  void constructionResultAndInitializedEventSurviveEveryLevel(int depth) {
    var solver = solver(nest(depth, new ConstructionHeuristicPhaseConfig()));
    var caller = Thread.currentThread();
    var events = new ArrayList<BestSolutionChangedEvent<TestdataSolution>>();
    solver.addEventListener(
        event -> {
          assertThat(Thread.currentThread()).isSameAs(caller);
          assertThat(event.isNewBestSolutionInitialized()).isTrue();
          events.add(event);
        });

    var result = solver.solve(TestdataSolution.generateUninitializedSolution(3, 8));

    assertThat(result.getEntityList())
        .allSatisfy(entity -> assertThat(entity.getValue()).isNotNull());
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().getNewBestScore()).isEqualTo(result.getScore());
    assertThat(solver.getSolverScope().<SimpleScore>getStartingInitializedScore())
        .isEqualTo(result.getScore());
    assertReplay(events);
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 3})
  void deliversEveryImprovementBeforeReturnAndResetsPublicationOnReuse(int depth) {
    var solver = solver(nest(depth, improvingPhase()));
    var caller = Thread.currentThread();
    var events = new ArrayList<BestSolutionChangedEvent<TestdataSolution>>();
    solver.addEventListener(
        event -> {
          assertThat(Thread.currentThread()).isSameAs(caller);
          events.add(event);
        });

    for (int run = 0; run < 2; run++) {
      events.clear();
      var result = solver.solve(conflictingSolution());
      assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
      assertThat(events)
          .extracting(BestSolutionChangedEvent::getNewBestScore)
          .containsExactly(
              SimpleScore.of(-20),
              SimpleScore.of(-12),
              SimpleScore.of(-6),
              SimpleScore.of(-2),
              SimpleScore.ZERO);
      assertThat(events).extracting(BestSolutionChangedEvent::getTimeMillisSpent).isSorted();
      assertReplay(events);
    }
  }

  @Test
  void nextPhaseUsesTheNestedBestAndKeepsItAfterWorseningWork() {
    var nextPhaseCalls = new AtomicInteger();
    PhaseCommand<TestdataSolution> worsen =
        context -> {
          var working = context.getWorkingSolution();
          assertThat(working.getScore()).isEqualTo(SimpleScore.ZERO);
          assertThat(new TestdataEasyScoreCalculator().calculateScore(working))
              .isEqualTo(SimpleScore.ZERO);
          nextPhaseCalls.incrementAndGet();
          var variable =
              context
                  .getSolutionMetaModel()
                  .genuineEntity(TestdataEntity.class)
                  .basicVariable("value", TestdataValue.class);
          context.executeAndCalculateScore(
              Moves.change(
                  variable, working.getEntityList().getLast(), working.getValueList().getFirst()));
        };
    var solver =
        solver(
            new IslandModelPhaseConfig()
                .withIslandCount(1)
                .withPhaseConfigList(
                    List.of(
                        nest(1, improvingPhase()),
                        new CustomPhaseConfig().withCustomPhaseCommands(worsen))));

    var result = solver.solve(conflictingSolution());

    assertThat(nextPhaseCalls).hasValue(1);
    assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
    assertThat(new TestdataEasyScoreCalculator().calculateScore(result))
        .isEqualTo(result.getScore());
  }

  @Test
  void rootListenerFailureStopsNestedDelivery() {
    var solver = solver(nest(3, improvingPhase()));
    var failure = new IllegalStateException("Nested root listener failed.");
    var scores = new ArrayList<Object>();
    solver.addEventListener(
        event -> {
          scores.add(event.getNewBestScore());
          if (event.getProducerId().phaseIndex().isPresent()) {
            throw failure;
          }
        });

    assertThat(catchThrowable(() -> solver.solve(conflictingSolution()))).isSameAs(failure);
    assertThat(scores).containsExactly(SimpleScore.of(-20), SimpleScore.of(-12));
    assertThat(solver.isSolving()).isFalse();
  }

  @Test
  void ancestorProgressPrecedesLocalDeliveryAndResetDropsTheAncestorLink() {
    var order = new ArrayList<String>();
    var ancestor = new SharedGlobalState<String>();
    ancestor.reset(Clock.systemUTC(), snapshot -> order.add("ancestor progress"));
    ancestor.setPublicationObserver(snapshot -> order.add("ancestor delivery"));
    var nested = new SharedGlobalState<String>();
    nested.reset(Clock.systemUTC(), snapshot -> order.add("nested progress"), ancestor);
    nested.setPublicationObserver(
        snapshot -> {
          assertThat(ancestor.getBestScore()).isEqualTo(snapshot.getScore());
          assertThat(nested.getBestSnapshot()).isSameAs(snapshot);
          order.add("nested delivery");
        });

    nested.tryUpdate("first", InnerScore.fullyAssigned(SimpleScore.ZERO));

    assertThat(order)
        .containsExactly(
            "nested progress", "ancestor progress", "ancestor delivery", "nested delivery");
    nested.setPublicationObserver(null);
    nested.reset();
    nested.tryUpdate("second", InnerScore.fullyAssigned(SimpleScore.ONE));
    assertThat(ancestor.getBestScore()).isEqualTo(SimpleScore.ZERO);
  }

  private static PhaseConfig<?> nest(int depth, PhaseConfig<?> phase) {
    for (int i = 0; i < depth; i++) {
      phase = new IslandModelPhaseConfig().withIslandCount(1).withPhaseConfigList(List.of(phase));
    }
    return phase;
  }

  private static DefaultSolver<TestdataSolution> solver(PhaseConfig<?> phase) {
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
            .withPhases(phase);
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

  private static void assertReplay(List<BestSolutionChangedEvent<TestdataSolution>> events) {
    assertThat(events)
        .allSatisfy(
            event ->
                assertThat(
                        new TestdataEasyScoreCalculator()
                            .calculateScore(event.getNewBestSolution()))
                    .isEqualTo(event.getNewBestScore()));
  }
}
