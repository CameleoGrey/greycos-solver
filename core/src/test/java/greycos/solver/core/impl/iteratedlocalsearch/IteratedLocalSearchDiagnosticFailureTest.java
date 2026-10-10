package greycos.solver.core.impl.iteratedlocalsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Iterator;
import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchSemanticsTest.LandscapeScoreCalculator;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchSemanticsTest.NextMoves;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class IteratedLocalSearchDiagnosticFailureTest {

  @Test
  void failedEpisodeRetainsConsumedAttemptsEvenWhenHistoryCleanupAlsoFails() {
    var phaseConfig =
        new IteratedLocalSearchPhaseConfig()
            .withLocalSearch(
                new LocalSearchPhaseConfig()
                    .withMoveSelectorConfig(
                        new MoveIteratorFactoryConfig()
                            .withMoveIteratorFactoryClass(FailingMoves.class)
                            .withSelectionOrder(SelectionOrder.RANDOM))
                    .withAcceptorConfig(new LocalSearchAcceptorConfig().withLateAcceptanceSize(4))
                    .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(8)))
            .withPerturbationStrengths(1)
            .withPerturbationAttemptLimit(8)
            .withEpisodeCandidateAttemptLimit(8)
            .withIterationCountLimit(1);
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(LandscapeScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withMoveThreadCount("NONE")
            .withPhases(phaseConfig);
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var values = List.of(new TestdataValue("0:0"), new TestdataValue("1:1"));
    var problem = new TestdataSolution("failure accounting");
    problem.setValueList(values);
    problem.setEntityList(List.of(new TestdataEntity("e", values.getFirst())));

    assertThatThrownBy(() -> solver.solve(problem))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("selection failed after two consumed attempts")
        .satisfies(
            failure ->
                assertThat(failure.getSuppressed())
                    .anySatisfy(
                        cleanup -> assertThat(cleanup).hasMessage("history cleanup failed")));

    var diagnostics =
        ((DefaultIteratedLocalSearchPhase<TestdataSolution>) solver.getPhaseList().getFirst())
            .getDiagnostics();
    assertThat(diagnostics.episodes()).isEqualTo(1);
    assertThat(diagnostics.episodeAttempts()).isEqualTo(2);
    assertThat(diagnostics.primitiveSteps()).isZero();
    assertThat(diagnostics.completionReason()).isEqualTo("FAILURE");
  }

  public static final class FailingMoves extends NextMoves {
    @Override
    public Iterator<Move<TestdataSolution>> createRandomMoveIterator(
        ScoreDirector<TestdataSolution> director, RandomGenerator random) {
      var delegate = super.createRandomMoveIterator(director, random);
      return new Iterator<>() {
        private int generated;

        @Override
        public boolean hasNext() {
          if (generated == 2)
            throw new IllegalStateException("selection failed after two consumed attempts");
          return true;
        }

        @Override
        public Move<TestdataSolution> next() {
          generated++;
          return delegate.next();
        }
      };
    }

    @Override
    public void phaseEnded(ScoreDirector<TestdataSolution> director) {
      throw new IllegalStateException("history cleanup failed");
    }
  }
}
