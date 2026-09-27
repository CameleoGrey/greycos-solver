package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.phase.PhaseCommand;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationCompositionStyle;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class IslandNestedWorkQuotaIntegrationTest {

  @Test
  void parentWorkAndTwoNestedChildrenExhaustOneQuotaAcrossRepeatedPublicSolves() {
    var preparation = new AtomicInteger();
    var childSteps = new AtomicInteger();
    var innerMarker = new AtomicInteger();
    var outerMarker = new AtomicInteger();
    var bothChildrenEntered = new CyclicBarrier(2);
    PhaseCommand<TestdataSolution> prepare = context -> preparation.incrementAndGet();
    PhaseCommand<TestdataSolution> child =
        context -> {
          childSteps.incrementAndGet();
          try {
            // Both children start their single permitted step while two outer steps remain.
            // Their own step limit then prevents any scheduling-dependent overshoot.
            bothChildrenEntered.await(10, TimeUnit.SECONDS);
          } catch (Exception failure) {
            throw new IllegalStateException(
                "Both nested islands must reach their first step.", failure);
          }
        };
    var nested =
        new IslandModelPhaseConfig()
            .withIslandCount(2)
            .withCompareGlobalEnabled(false)
            .withMigrationFrequency(Integer.MAX_VALUE)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(1))
            .withPhaseConfigList(
                List.of(
                    new CustomPhaseConfig()
                        .withCustomPhaseCommands(child, context -> innerMarker.incrementAndGet())));
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withMoveThreadCount("NONE")
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(1)
                    .withCompareGlobalEnabled(false)
                    .withMigrationFrequency(Integer.MAX_VALUE)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(4))
                    .withPhaseConfigList(
                        List.of(
                            new CustomPhaseConfig().withCustomPhaseCommands(prepare, prepare),
                            nested,
                            new CustomPhaseConfig()
                                .withCustomPhaseCommands(
                                    (PhaseCommand<TestdataSolution>)
                                        context -> outerMarker.incrementAndGet()))));
    var solver = SolverFactory.<TestdataSolution>create(config).buildSolver();
    for (int run = 0; run < 2; run++) {
      preparation.set(0);
      childSteps.set(0);
      innerMarker.set(0);
      outerMarker.set(0);
      var problem = TestdataSolution.generateSolution(2, 2);
      problem.getEntityList().forEach(entity -> entity.setValue(problem.getValueList().getFirst()));
      var result = solver.solve(problem);
      assertThat(result.getScore()).isNotNull();
      assertThat(preparation).hasValue(2);
      assertThat(childSteps).hasValue(2);
      assertThat(innerMarker).hasValue(0);
      assertThat(outerMarker).hasValue(0);
    }
  }

  @Test
  void partitionChildrenContributeWorkWithoutReplacingFullProblemSearchHistory() {
    var partitionCommands = new AtomicInteger();
    var laterMarker = new AtomicInteger();
    var partition =
        new PartitionedSearchPhaseConfig()
            .withSolutionPartitionerClass(TwoEntitiesPerPartition.class)
            .withRunnablePartThreadLimit(PartitionedSearchPhaseConfig.ACTIVE_THREAD_COUNT_UNLIMITED)
            .withPhaseConfigList(
                List.of(
                    new CustomPhaseConfig()
                        .withCustomPhaseCommands(context -> partitionCommands.incrementAndGet())));
    var outerLimit =
        new TerminationConfig()
            .withTerminationCompositionStyle(TerminationCompositionStyle.AND)
            .withTerminationConfigList(
                List.of(
                    new TerminationConfig().withStepCountLimit(8),
                    new TerminationConfig().withUnimprovedStepCountLimit(3)));
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(NegativeEntityCountCalculator.class)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withMoveThreadCount("NONE")
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(1)
                    .withCompareGlobalEnabled(false)
                    .withMigrationFrequency(Integer.MAX_VALUE)
                    .withTerminationConfig(outerLimit)
                    .withPhaseConfigList(
                        List.of(
                            new LocalSearchPhaseConfig()
                                .withTerminationConfig(
                                    new TerminationConfig().withStepCountLimit(2)),
                            partition,
                            new LocalSearchPhaseConfig()
                                .withTerminationConfig(
                                    new TerminationConfig().withStepCountLimit(2)),
                            new CustomPhaseConfig()
                                .withCustomPhaseCommands(
                                    context -> laterMarker.incrementAndGet()))));
    var problem = TestdataSolution.generateSolution(2, 10);
    problem.getEntityList().forEach(entity -> entity.setValue(problem.getValueList().getFirst()));
    var result = SolverFactory.<TestdataSolution>create(config).buildSolver().solve(problem);
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(-100));
    assertThat(partitionCommands).hasValue(5);
    // Two initial search steps + five partition custom steps + one final search step consume
    // eight work steps and three unimproved search steps. Partition scores of -20 must not reset
    // the history of the complete problem with score -100.
    assertThat(laterMarker).hasValue(0);
  }

  public static final class TwoEntitiesPerPartition
      implements SolutionPartitioner<TestdataSolution> {
    @Override
    public List<TestdataSolution> splitWorkingSolution(
        ScoreDirector<TestdataSolution> scoreDirector, Integer runnablePartThreadLimit) {
      var source = scoreDirector.getWorkingSolution();
      var partitions = new ArrayList<TestdataSolution>();
      for (int offset = 0; offset < source.getEntityList().size(); offset += 2) {
        var partition = new TestdataSolution();
        var entities = new ArrayList<TestdataEntity>();
        for (var original : source.getEntityList().subList(offset, offset + 2)) {
          entities.add(new TestdataEntity(original.getCode(), original.getValue()));
        }
        partition.setEntityList(entities);
        partition.setValueList(new ArrayList<>(source.getValueList()));
        partitions.add(partition);
      }
      return partitions;
    }
  }

  public static final class NegativeEntityCountCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.of(-10L * solution.getEntityList().size());
    }
  }
}
