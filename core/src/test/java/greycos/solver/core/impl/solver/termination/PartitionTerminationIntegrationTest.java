package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.MockClock;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class PartitionTerminationIntegrationTest {

  @Test
  void phaseSpentLimitStartsAfterThePrecedingPhase() {
    var clock = new MockClock(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    var executedChildren = new AtomicInteger();
    var precedingPhase =
        new CustomPhaseConfig()
            .withCustomPhaseCommands(context -> clock.tick(Duration.ofMillis(400)));
    var partition =
        partition(
            new CustomPhaseConfig()
                .withCustomPhaseCommands(context -> executedChildren.incrementAndGet()));
    partition.setTerminationConfig(new TerminationConfig().withMillisecondsSpentLimit(200L));
    var config = PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class);
    config.setClock(clock);
    config.withPhases(precedingPhase, partition);
    var solver = SolverFactory.<TestdataSolution>create(config).buildSolver();
    assertThat(solver.solve(TestdataSolution.generateSolution(3, 2))).isNotNull();
    assertThat(executedChildren).hasValue(2);
  }

  @Test
  void mergedParentScoreTerminatesAllChildrenAfterCompleteInitialization() throws Exception {
    var partition =
        partition(
            new ConstructionHeuristicPhaseConfig(),
            new CustomPhaseConfig()
                .withCustomPhaseCommands(
                    context -> {
                      // Each child can reach score 1, while the complete parent's target is 2.
                      while (!context.isPhaseTerminated()) {
                        Thread.onSpinWait();
                      }
                    }));
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEasyScoreCalculatorClass(AssignedEntityCountCalculator.class)
            .withTerminationConfig(new TerminationConfig().withBestScoreLimit("2"))
            .withPhases(partition);
    var solver = SolverFactory.<TestdataSolution>create(config).buildSolver();
    var executor = Executors.newSingleThreadExecutor();
    try {
      var future =
          executor.submit(() -> solver.solve(TestdataSolution.generateUninitializedSolution(3, 2)));
      var result = future.get(5, TimeUnit.SECONDS);
      assertThat(result.getEntityList())
          .allSatisfy(entity -> assertThat(entity.getValue()).isNotNull());
      assertThat(result.getScore()).isEqualTo(SimpleScore.of(2));
      assertThat(new AssignedEntityCountCalculator().calculateScore(result))
          .isEqualTo(result.getScore());
    } finally {
      solver.terminateEarly();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
    }
  }

  private static PartitionedSearchPhaseConfig partition(PhaseConfig<?>... phases) {
    var config = new PartitionedSearchPhaseConfig();
    config.setSolutionPartitionerClass(OneEntityPerPartition.class);
    config.setRunnablePartThreadLimit(PartitionedSearchPhaseConfig.ACTIVE_THREAD_COUNT_UNLIMITED);
    config.setPhaseConfigList(List.of(phases));
    return config;
  }

  public static final class OneEntityPerPartition implements SolutionPartitioner<TestdataSolution> {
    @Override
    public List<TestdataSolution> splitWorkingSolution(
        ScoreDirector<TestdataSolution> scoreDirector, Integer runnablePartThreadLimit) {
      var source = scoreDirector.getWorkingSolution();
      return source.getEntityList().stream()
          .map(
              entity -> {
                var copy = new TestdataEntity(entity.getCode());
                copy.setValue(entity.getValue());
                var partition = new TestdataSolution();
                partition.setEntityList(new ArrayList<>(List.of(copy)));
                partition.setValueList(new ArrayList<>(source.getValueList()));
                return partition;
              })
          .toList();
    }
  }

  public static final class AssignedEntityCountCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.of(
          solution.getEntityList().stream().filter(entity -> entity.getValue() != null).count());
    }
  }
}
