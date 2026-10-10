package greycos.solver.core.impl.iteratedlocalsearch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchSemanticsTest.EmptyMoves;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchSemanticsTest.LandscapeScoreCalculator;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchSemanticsTest.NextMoves;
import greycos.solver.core.impl.move.SolutionAssignmentMove;
import greycos.solver.core.impl.move.SolutionAssignments;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Injects the same pending complete-assignment move used by island migration at exact boundaries.
 */
@Timeout(30)
class IteratedLocalSearchAdoptionTest {

  @ParameterizedTest
  @CsvSource({"NONE,1", "1,1", "2,1", "4,1", "NONE,2", "1,2", "2,2", "4,2"})
  void adoptionSupersedesInitialOrPerturbedEpisodeAndSurvivesLaterRejection(
      String threads, int arrivalEpisode) {
    var run = run(threads, arrivalEpisode, false, false);
    assertThat(run.result.getScore()).isEqualTo(SimpleScore.of(10));
    assertThat(code(run.result)).isEqualTo("2:10");
    assertThat(run.phaseEndCode).isEqualTo("2:10");
    assertThat(run.scope.getMigrantRestarts()).isEqualTo(1);
    assertThat(run.scope.getCompletedIterations()).isEqualTo(1);
    assertThat(run.scope.getAcceptedIterations()).isZero();
    assertThat(run.scope.getRejectedIterations()).isEqualTo(1);
    assertThat(run.scope.getFailedPerturbations()).isZero();
    assertThat(run.scope.getEpisodes()).isEqualTo(arrivalEpisode + 2);
    assertThat(run.committedValues).hasSize(arrivalEpisode);
    assertThat(run.migrantEpisodeStarts).containsExactly("2:10");
    assertThat(run.scope.getLastCompletedStepScope().getStepIndex()).isEqualTo(arrivalEpisode - 1);
  }

  @ParameterizedTest
  @CsvSource({"NONE,1", "2,1", "NONE,2", "2,2"})
  void adoptionPublishesAuthoritativeBestBeforeImmediateBestScoreTermination(
      String threads, int arrivalEpisode) {
    var run = run(threads, arrivalEpisode, true, false);
    assertThat(code(run.result)).isEqualTo("2:10");
    assertThat(run.result.getScore()).isEqualTo(SimpleScore.of(10));
    assertThat(run.scope.getBestScore().raw()).isEqualTo(SimpleScore.of(10));
    assertThat(code(run.scope.getSolverScope().getBestSolution())).isEqualTo("2:10");
    assertThat(run.scope.getCompletedIterations()).isZero();
    assertThat(run.committedValues).hasSize(arrivalEpisode - 1);
    assertThat(run.scope.getLastCompletedStepScope().getStepIndex()).isEqualTo(arrivalEpisode - 2);
  }

  @ParameterizedTest
  @CsvSource({"NONE,1", "2,1", "NONE,2", "2,2"})
  void cancellationDuringZeroMoveMigrantEpisodeRetainsPublishedAssignments(
      String threads, int arrivalEpisode) {
    var run = run(threads, arrivalEpisode, false, true);
    assertThat(code(run.result)).isEqualTo("2:10");
    assertThat(run.result.getScore()).isEqualTo(SimpleScore.of(10));
    assertThat(run.scope.getMigrantRestarts()).isEqualTo(1);
    assertThat(run.scope.getCompletedIterations()).isZero();
    assertThat(run.committedValues).hasSize(arrivalEpisode - 1);
    assertThat(run.migrantEpisodeStarts).containsExactly("2:10");
  }

  private static Run run(
      String threads, int arrivalEpisode, boolean bestScoreLimit, boolean cancelMigrantEpisode) {
    var phase =
        new IteratedLocalSearchPhaseConfig()
            .withLocalSearch(
                new LocalSearchPhaseConfig()
                    .withMoveSelectorConfig(
                        new MoveIteratorFactoryConfig()
                            .withMoveIteratorFactoryClass(EmptyMoves.class))
                    .withAcceptorConfig(new LocalSearchAcceptorConfig().withLateAcceptanceSize(4)))
            .withPerturbationMoveSelectorConfig(
                new MoveIteratorFactoryConfig().withMoveIteratorFactoryClass(NextMoves.class))
            .withPerturbationStrengths(1, 2)
            .withPerturbationAttemptLimit(4)
            .withEpisodeCandidateAttemptLimit(4)
            .withIterationCountLimit(1);
    if (bestScoreLimit)
      phase.withTerminationConfig(new TerminationConfig().withBestScoreLimit("10"));
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(LandscapeScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
            .withRandomSeed(0L)
            .withMoveThreadCount(threads)
            .withPhases(phase);
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var injected = new AtomicBoolean();
    var migrantSnapshot = new AtomicReference<TestdataSolution>();
    var run = new Run();
    solver
        .getPhaseList()
        .getFirst()
        .addPhaseLifecycleListener(
            new PhaseLifecycleListenerAdapter<>() {
              @Override
              public void stepStarted(AbstractStepScope<TestdataSolution> step) {
                var ils = (IteratedLocalSearchStepScope<TestdataSolution>) step;
                if (ils.getOrigin() != IteratedLocalSearchStepScope.Origin.LOCAL_SEARCH) return;
                var scope = ils.getPhaseScope();
                if (scope.getEpisodes() == arrivalEpisode && injected.compareAndSet(false, true)) {
                  var director = scope.getScoreDirector();
                  var migrant = director.cloneWorkingSolution();
                  migrant.getEntityList().getFirst().setValue(migrant.getValueList().get(2));
                  migrant.setScore(SimpleScore.of(10));
                  migrantSnapshot.set(migrant);
                  var move =
                      new SolutionAssignmentMove<>(
                          SolutionAssignments.captureComplete(
                                  director.getSolutionDescriptor(), migrant)
                              .rebase(director));
                  scope
                      .getSolverScope()
                      .setPendingMoveIfBetter(
                          move, InnerScore.fullyAssigned(SimpleScore.of(10)), true);
                } else if (scope.getEpisodes() == arrivalEpisode + 1 && injected.get()) {
                  // Episode setup and its first real decision each have an abortable start
                  // boundary.
                  assertThat(code(step.getWorkingSolution())).isEqualTo("2:10");
                  if (run.migrantEpisodeStarts.isEmpty()) {
                    run.migrantEpisodeStarts.add(code(step.getWorkingSolution()));
                  }
                  assertThat(step.getPhaseScope().getBestScore().raw())
                      .isEqualTo(SimpleScore.of(10));
                  assertThat(ils.getStartingScore().raw()).isEqualTo(SimpleScore.of(10));
                  assertThat(scope.getStrength()).isEqualTo(1);
                  if (cancelMigrantEpisode) solver.terminateEarly();
                }
              }

              @Override
              public void stepEnded(AbstractStepScope<TestdataSolution> step) {
                run.committedValues.add(code(step.getWorkingSolution()));
              }

              @Override
              public void phaseEnded(AbstractPhaseScope<TestdataSolution> phaseScope) {
                run.scope = (IteratedLocalSearchPhaseScope<TestdataSolution>) phaseScope;
                run.phaseEndCode = code(phaseScope.getWorkingSolution());
              }
            });
    var problem = new TestdataSolution("adoption");
    problem.setValueList(
        List.of(new TestdataValue("0:0"), new TestdataValue("1:-1"), new TestdataValue("2:10")));
    problem.setEntityList(List.of(new TestdataEntity("entity", problem.getValueList().getFirst())));
    run.result = solver.solve(problem);
    assertThat(injected).isTrue();
    assertThat(new LandscapeScoreCalculator().calculateScore(run.result))
        .isEqualTo(run.result.getScore());
    assertThat(code(migrantSnapshot.get())).isEqualTo("2:10");
    assertThat(solver.getSolverScope().hasPendingMove()).isFalse();
    assertThat(solver.isSolving()).isFalse();
    return run;
  }

  private static String code(TestdataSolution solution) {
    return solution.getEntityList().getFirst().getValue().getCode();
  }

  private static final class Run {
    final List<String> committedValues = new ArrayList<>();
    final List<String> migrantEpisodeStarts = new ArrayList<>();
    TestdataSolution result;
    IteratedLocalSearchPhaseScope<TestdataSolution> scope;
    String phaseEndCode;
  }
}
