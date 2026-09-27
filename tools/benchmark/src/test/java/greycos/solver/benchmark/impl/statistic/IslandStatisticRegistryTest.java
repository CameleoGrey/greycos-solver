package greycos.solver.benchmark.impl.statistic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.FutureTask;

import greycos.solver.benchmark.impl.statistic.bestscore.BestScoreStatisticPoint;
import greycos.solver.benchmark.impl.statistic.bestscore.BestScoreSubSingleStatistic;
import greycos.solver.benchmark.impl.statistic.movecountperstep.MoveCountPerStepSubSingleStatistic;
import greycos.solver.benchmark.impl.statistic.moveevaluationspeed.MoveEvaluationSpeedSubSingleStatistic;
import greycos.solver.benchmark.impl.statistic.scorecalculationspeed.ScoreCalculationSpeedSubSingleStatistic;
import greycos.solver.benchmark.impl.statistic.stepscore.StepScoreStatisticPoint;
import greycos.solver.benchmark.impl.statistic.stepscore.StepScoreSubSingleStatistic;
import greycos.solver.benchmark.impl.statistic.subsingle.pickedmovetypebestscore.PickedMoveTypeBestScoreDiffSubSingleStatistic;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.event.SolverEventSupport;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSample;
import greycos.solver.core.impl.solver.monitoring.SolverTags;
import greycos.solver.core.impl.solver.monitoring.SolverWorkSnapshot;
import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;

class IslandStatisticRegistryTest {

  private static final Tags ROOT_TAGS = SolverTags.withProblemId("benchmark").asTags();

  @Test
  void recordsImmutableIslandStepsAndGlobalBestWithoutSyntheticSteps() {
    var registry = newRegistry();
    try {
      var step = new StepScoreSubSingleStatistic<TestdataSolution>(null);
      var best = new BestScoreSubSingleStatistic<TestdataSolution>(null);
      var moves = new MoveCountPerStepSubSingleStatistic<TestdataSolution>(null);
      var bestDiff = new PickedMoveTypeBestScoreDiffSubSingleStatistic<TestdataSolution>(null);
      for (var statistic : new SubSingleStatistic[] {step, best, moves, bestDiff}) {
        statistic.initPointList();
        statistic.open(registry, ROOT_TAGS);
      }
      var childTags = ROOT_TAGS.and("island.id", "0");
      var values = new HashMap<Meter.Id, Double>();
      values.put(
          meter(SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".accepted", childTags), 2.0);
      values.put(
          meter(SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".selected", childTags), 3.0);
      var diffTags = childTags.and("move.type", "ChangeMove");
      values.put(
          meter(SolverMetric.PICKED_MOVE_TYPE_BEST_SCORE_DIFF.getMeterId() + ".score", diffTags),
          4.0);
      values.put(
          meter(
              SolverMetric.PICKED_MOVE_TYPE_BEST_SCORE_DIFF.getMeterId() + ".unassigned.count",
              diffTags),
          0.0);
      var first =
          sample(SolverMetricSample.Kind.STEP, 100L, "phase-0/island-0", childTags, -4, values);
      values.replaceAll((id, value) -> 999.0);
      registry.accept(first);
      registry.accept(
          sample(
              SolverMetricSample.Kind.STEP,
              101L,
              "phase-0/island-1",
              ROOT_TAGS.and("island.id", "1"),
              -7,
              Map.of()));
      // A local improvement may still be worse than the global best.
      assertThat(best.getPointList()).isEmpty();
      registry.accept(sample(SolverMetricSample.Kind.BEST, 102L, "root", ROOT_TAGS, -4, Map.of()));
      registry.accept(sample(SolverMetricSample.Kind.BEST, 103L, "root", ROOT_TAGS, -1, Map.of()));

      assertThat(step.getPointList())
          .extracting(StepScoreStatisticPoint::getSource)
          .containsExactly("phase-0/island-0", "phase-0/island-1");
      assertThat(step.getPointList())
          .extracting(StepScoreStatisticPoint::getScore)
          .containsExactly(SimpleScore.of(-4), SimpleScore.of(-7));
      assertThat(moves.getPointList())
          .singleElement()
          .satisfies(
              point -> {
                assertThat(point.getAcceptedMoveCount()).isEqualTo(2L);
                assertThat(point.getSelectedMoveCount()).isEqualTo(3L);
                assertThat(point.getSource()).isEqualTo("phase-0/island-0");
              });
      assertThat(bestDiff.getPointList())
          .singleElement()
          .satisfies(
              point -> {
                assertThat(point.getBestScoreDiff()).isEqualTo(SimpleScore.of(4));
                assertThat(point.getSeriesLabel(point.getMoveType()))
                    .isEqualTo("ChangeMove [phase-0/island-0]");
              });
      assertThat(best.getPointList())
          .extracting(BestScoreStatisticPoint::getScore)
          .containsExactly(SimpleScore.of(-4), SimpleScore.of(-1));
      assertThat(best.getPointList().getLast().getTimeMillisSpent()).isEqualTo(103L);
    } finally {
      registry.close();
    }
  }

  @Test
  void finalSampleIncludesShortRunAggregateThroughput() {
    var registry = newRegistry();
    try {
      var scoreSpeed = new ScoreCalculationSpeedSubSingleStatistic<TestdataSolution>(null);
      var moveSpeed = new MoveEvaluationSpeedSubSingleStatistic<TestdataSolution>(null);
      scoreSpeed.initPointList();
      moveSpeed.initPointList();
      scoreSpeed.open(registry, ROOT_TAGS);
      moveSpeed.open(registry, ROOT_TAGS);
      registry.accept(
          sample(
                  SolverMetricSample.Kind.FINAL,
                  50L,
                  "phase-0/island-0",
                  ROOT_TAGS.and("island.id", "0"),
                  -1,
                  Map.of())
              .withWork(new SolverWorkSnapshot(2, 0, Map.of())));
      assertThat(scoreSpeed.getPointList()).isEmpty();
      registry.accept(
          sample(
                  SolverMetricSample.Kind.STEP,
                  100L,
                  "phase-0/island-0",
                  ROOT_TAGS.and("island.id", "0"),
                  -1,
                  Map.of())
              .withWork(new SolverWorkSnapshot(11, 8, Map.of())));
      registry.accept(
          sample(SolverMetricSample.Kind.FINAL, 200L, "root", ROOT_TAGS, -1, Map.of())
              .withWork(new SolverWorkSnapshot(20, 15, Map.of())));
      assertThat(scoreSpeed.getPointList())
          .singleElement()
          .satisfies(point -> assertThat(point.getValue()).isEqualTo(100));
      assertThat(moveSpeed.getPointList())
          .singleElement()
          .satisfies(point -> assertThat(point.getValue()).isEqualTo(75));
    } finally {
      registry.close();
    }
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void ordinaryChildWithInheritedRootLabelDoesNotPublishGlobalBest() throws Exception {
    var rootScope = new SolverScope<TestdataSolution>(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    var scoreDirector = mock(InnerScoreDirector.class);
    when(scoreDirector.createChildThreadScoreDirector(ChildThreadType.PART_THREAD))
        .thenReturn(mock(InnerScoreDirector.class));
    rootScope.setScoreDirector(scoreDirector);
    rootScope.setWorkingRandom(DefaultRandomSource.seeded(0L));
    rootScope.setMonitoringTags(ROOT_TAGS);
    rootScope.startingNow();
    rootScope.setBestScore(InnerScore.fullyAssigned(SimpleScore.of(-1)));
    rootScope.setBestSolutionTimeMillis(20L);
    var childScope = rootScope.createChildThreadSolverScope(ChildThreadType.PART_THREAD);
    childScope.setBestScore(InnerScore.fullyAssigned(SimpleScore.of(-99)));
    childScope.setBestSolutionTimeMillis(10L);
    assertThat(rootScope.isRootScope()).isTrue();
    assertThat(childScope.isRootScope()).isFalse();
    assertThat(childScope.getMetricSource()).isEqualTo("root");

    var registry = newRegistry();
    try {
      var best = new BestScoreSubSingleStatistic<TestdataSolution>(null);
      best.initPointList();
      best.open(registry, ROOT_TAGS);
      registry.attach(rootScope);
      assertThat(childScope.hasMetricSampleListeners()).isTrue();
      var events = new SolverEventSupport<TestdataSolution>(null);
      var solution = TestdataSolution.generateSolution(1, 1);
      var childPublication =
          new FutureTask<>(
              () ->
                  events.fireBestSolutionChanged(
                      childScope, EventProducerId.partitionedSearch(0), solution),
              null);
      Thread.ofPlatform().start(childPublication);
      childPublication.get();
      assertThat(best.getPointList()).isEmpty();

      events.fireBestSolutionChanged(rootScope, EventProducerId.partitionedSearch(0), solution);
      assertThat(best.getPointList())
          .singleElement()
          .satisfies(
              point -> {
                assertThat(point.getScore()).isEqualTo(SimpleScore.of(-1));
                assertThat(point.getTimeMillisSpent()).isEqualTo(20L);
              });
    } finally {
      registry.close();
    }
  }

  @Test
  void closingRegistryDetachesSampleSubscription() {
    var scope = new SolverScope<TestdataSolution>();
    var registry = newRegistry();
    registry.attach(scope);
    assertThat(scope.hasMetricSampleListeners()).isTrue();
    registry.close();
    assertThat(scope.hasMetricSampleListeners()).isFalse();
  }

  private static StatisticRegistry<TestdataSolution> newRegistry() {
    return new StatisticRegistry<>(TestdataSolution.buildSolutionDescriptor().getScoreDefinition());
  }

  private static Meter.Id meter(String name, Tags tags) {
    return new Meter.Id(name, tags, null, null, Meter.Type.GAUGE);
  }

  private static SolverMetricSample sample(
      SolverMetricSample.Kind kind,
      long time,
      String source,
      Tags tags,
      int score,
      Map<Meter.Id, Double> values) {
    return new SolverMetricSample(
        kind,
        time,
        source,
        tags,
        "ChangeMove",
        true,
        InnerScore.fullyAssigned(SimpleScore.of(score)),
        SolverWorkSnapshot.ZERO,
        values);
  }
}
