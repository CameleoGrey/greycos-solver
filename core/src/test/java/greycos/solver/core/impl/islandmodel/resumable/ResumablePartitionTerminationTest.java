package greycos.solver.core.impl.islandmodel.resumable;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.islandmodel.ResumableSearchConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.solver.termination.TerminationCompositionStyle;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.islandmodel.resumable.ResumableSearchTestSupport.RecordingForager;
import greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Isolated("Observes the test forager's instance registry.")
@Timeout(60)
class ResumablePartitionTerminationTest {

  @ParameterizedTest
  @CsvSource({
    "1,1,1,step",
    "1,7,1,step",
    "2,7,2,step",
    "1,1,1,and",
    "2,7,2,and",
    "1,1,1,or",
    "2,7,2,or"
  })
  void partitionPhaseLimitsMatchNativeAcrossSequentialPhasesAndNestedIslands(
      int workers, int quantum, int partitions, String expression) {
    var expected = new ArrayList<PhaseWork>();
    for (int i = 0; i < partitions; i++) {
      expected.add(new PhaseWork(0, 2));
      expected.add(new PhaseWork(1, 5));
      expected.add(new PhaseWork(0, 1));
    }
    for (boolean resumable : new boolean[] {false, true}) {
      RecordingForager.INSTANCES.clear();
      var workload = new BasicWorkload();
      var config = ResumableSearchRuntimeTest.config(workload, "la", 37, 2);
      var first = (LocalSearchPhaseConfig) config.getPhaseConfigList().getFirst();
      first.getForagerConfig().setForagerClass(RecordingForager.class);
      var second =
          first.copyConfig().withTerminationConfig(new TerminationConfig().withStepCountLimit(10));
      var inner =
          first.copyConfig().withTerminationConfig(new TerminationConfig().withStepCountLimit(1));
      var island =
          new IslandModelPhaseConfig()
              .withIslandCount(1)
              .withMoveThreadCount("NONE")
              .withPhaseConfigList(List.of(inner));
      if (resumable) {
        island.setResumableSearchConfig(
            new ResumableSearchConfig().withWorkerCount(workers).withStepQuantum(quantum));
      }
      var partition =
          new PartitionedSearchPhaseConfig()
              .withSolutionPartitionerClass(
                  partitions == 1 ? OnePartition.class : TwoPartitions.class)
              .withRunnablePartThreadLimit("1")
              .withTerminationConfig(partitionTermination(expression))
              .withPhaseConfigs(first, second, island);
      config.withPhases(partition);
      var solver = SolverFactory.<BasicSolution>create(config).buildSolver();
      var result = solver.solve(workload.createProblem(24));
      assertThat(result.getScore()).isEqualTo(workload.recompute(result));
      var actual =
          RecordingForager.INSTANCES.stream()
              .filter(forager -> forager.phasesStarted > 0)
              .map(
                  forager -> {
                    var phaseEvent =
                        forager.events.stream()
                            .filter(event -> event.startsWith("phaseStarted:"))
                            .findFirst()
                            .orElseThrow();
                    assertThat(forager.phasesStarted).isEqualTo(1);
                    assertThat(forager.phasesEnded).isEqualTo(1);
                    return new PhaseWork(
                        Integer.parseInt(phaseEvent.substring("phaseStarted:".length())),
                        forager.stepsEnded);
                  })
              .toList();
      assertThat(actual)
          .as(
              "resumable=%s, expression=%s, workers=%s, quantum=%s",
              resumable, expression, workers, quantum)
          .containsExactlyInAnyOrderElementsOf(expected);
    }
  }

  private static TerminationConfig partitionTermination(String expression) {
    var steps = new TerminationConfig().withStepCountLimit(5);
    return switch (expression) {
      case "step" -> steps;
      case "and" ->
          new TerminationConfig()
              .withTerminationCompositionStyle(TerminationCompositionStyle.AND)
              .withTerminationConfigList(
                  List.of(steps, new TerminationConfig().withMillisecondsSpentLimit(0L)));
      case "or" ->
          new TerminationConfig()
              .withTerminationCompositionStyle(TerminationCompositionStyle.OR)
              .withTerminationConfigList(
                  List.of(steps, new TerminationConfig().withBestScoreLimit("999999")));
      default -> throw new IllegalArgumentException(expression);
    };
  }

  private record PhaseWork(int phaseIndex, int completedSteps) {}

  public static final class OnePartition implements SolutionPartitioner<BasicSolution> {
    @Override
    public List<BasicSolution> splitWorkingSolution(
        ScoreDirector<BasicSolution> director, Integer limit) {
      return List.of(((InnerScoreDirector<BasicSolution, ?>) director).cloneWorkingSolution());
    }
  }

  public static final class TwoPartitions implements SolutionPartitioner<BasicSolution> {
    @Override
    public List<BasicSolution> splitWorkingSolution(
        ScoreDirector<BasicSolution> director, Integer limit) {
      var inner = (InnerScoreDirector<BasicSolution, ?>) director;
      var first = inner.cloneWorkingSolution();
      var second = inner.cloneWorkingSolution();
      int middle = first.getJobs().size() / 2;
      first.setJobs(new ArrayList<>(first.getJobs().subList(0, middle)));
      second.setJobs(new ArrayList<>(second.getJobs().subList(middle, second.getJobs().size())));
      first.setScore(null);
      second.setScore(null);
      return List.of(first, second);
    }
  }
}
