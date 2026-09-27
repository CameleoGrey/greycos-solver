package greycos.solver.core.impl.solver.termination;

import static greycos.solver.core.impl.solver.termination.DiminishedReturnsTermination.NANOS_PER_MILLISECOND;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.DiminishedReturnsTerminationConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.islandmodel.DefaultIslandModelPhase;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IslandDiminishedReturnsTerminationTest {

  @ParameterizedTest
  @MethodSource("slidingWindows")
  @SuppressWarnings("unchecked")
  void solverLevelWindowSurvivesIslandChildCreation(
      DiminishedReturnsTerminationConfig diminishedReturnsConfig, long expectedWindowMillis) {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
            .withPhases(new IslandModelPhaseConfig().withIslandCount(1))
            .withTerminationConfig(
                new TerminationConfig().withDiminishedReturnsConfig(diminishedReturnsConfig));
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    try {
      var island = (DefaultIslandModelPhase<TestdataSolution>) solver.getPhaseList().getFirst();
      // The phase bridge delegates to the solver termination and strips itself when copied,
      // following the same PART_THREAD copy path used when an island agent is created.
      var child =
          ChildThreadSupportingTermination
              .<TestdataSolution, SolverScope<TestdataSolution>>assertChildThreadSupport(
                  island.getPhaseTermination())
              .createChildThreadTermination(new SolverScope<>(), ChildThreadType.PART_THREAD);
      var childLeaves =
          ((UniversalTermination<TestdataSolution>) child)
              .getPhaseTerminationList().stream()
                  .filter(DiminishedReturnsTermination.class::isInstance)
                  .toList();
      assertThat(childLeaves).hasSize(1);
      var diminished =
          (DiminishedReturnsTermination<TestdataSolution, SimpleScore>) childLeaves.getFirst();
      long windowNanos = expectedWindowMillis * NANOS_PER_MILLISECOND;
      assertThat(diminished.getSlidingWindowNanos()).isEqualTo(windowNanos);
      var score = InnerScore.fullyAssigned(SimpleScore.ZERO);
      assertThat(diminished.isTerminated(windowNanos, score)).isFalse();
      diminished.start(0, score);
      if (windowNanos > 0) {
        assertThat(diminished.isTerminated(windowNanos - 1, score)).isFalse();
      }
      assertThat(diminished.isTerminated(windowNanos, score)).isTrue();
    } finally {
      solver.getSolverScope().getScoreDirector().close();
    }
  }

  private static Stream<Arguments> slidingWindows() {
    return Stream.of(
        Arguments.of(
            new DiminishedReturnsTerminationConfig().withSlidingWindowMilliseconds(0L), 0L),
        Arguments.of(
            new DiminishedReturnsTerminationConfig().withSlidingWindowMilliseconds(1L), 1L),
        Arguments.of(
            new DiminishedReturnsTerminationConfig().withSlidingWindowMilliseconds(100L), 100L),
        Arguments.of(new DiminishedReturnsTerminationConfig(), 30_000L));
  }
}
