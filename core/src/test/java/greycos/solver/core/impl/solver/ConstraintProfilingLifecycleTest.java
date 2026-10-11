package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.profile.ConstraintProfile;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.SolverManager;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.SolverManagerConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.bavet.common.DefaultConstraintProfiler;
import greycos.solver.core.impl.neighborhood.MoveSelectorBasedMoveRepository;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.WorkingSolutionMutationObserver;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.testcotwin.TestdataConstraintProvider;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class ConstraintProfilingLifecycleTest {

  static SolverConfig config(boolean profiling) {
    return new SolverConfig()
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
        .withMoveThreadCount("NONE")
        .withScoreDirectorFactory(
            new ScoreDirectorFactoryConfig()
                .withConstraintProviderClass(TestdataConstraintProvider.class)
                .withConstraintStreamProfilingEnabled(profiling))
        .withPhases(new ConstructionHeuristicPhaseConfig());
  }

  private static DefaultSolver<TestdataSolution> solver(SolverConfig config) {
    return (DefaultSolver<TestdataSolution>)
        SolverFactory.<TestdataSolution>create(config).buildSolver();
  }

  private static void assertCollectorDetached(DefaultSolver<?> solver) {
    assertThat(solver.getSolverScope().getConstraintProfiler()).isNull();
    assertThat(solver.getSolverScope().getScoreDirector().getConstraintProfiler()).isNull();
  }

  @Test
  void disabledSolveDoesNotAllocateCollectorOrPublishProfile() {
    var solver = spy(solver(config(false)));
    doThrow(new AssertionError("Disabled solve allocated a collector."))
        .when(solver)
        .createConstraintProfiler();
    solver.solve(TestdataSolution.generateSolution(2, 3));
    assertThat(solver.getConstraintProfile()).isEmpty();
    assertThat(solver.getConstraintProfileFreezeNanos()).isZero();
    assertThat(solver.getConstraintProfileReportNanos()).isZero();
    assertCollectorDetached(solver);
  }

  @Test
  void solveReuseAfterRepeatedRebuildsDetachesCollectorsAndRetainsPreviousSnapshot() {
    var solver = solver(config(true));
    var collectors = new ArrayList<DefaultConstraintProfiler>();
    var directors = new ArrayList<InnerScoreDirector<TestdataSolution, ?>>();
    int rebuildCount = 32;
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingStarted(SolverScope<TestdataSolution> scope) {
            assertThat(solver.getConstraintProfile()).isEmpty();
            collectors.add(scope.getConstraintProfiler());
            directors.add(scope.getScoreDirector());
          }

          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> phase) {
            var director = phase.getScoreDirector();
            for (int i = 0; i < rebuildCount; i++) {
              director.setWorkingSolution(director.cloneWorkingSolution());
            }
          }
        });
    assertThat(solver.getConstraintProfile()).isEmpty();
    solver.solve(TestdataSolution.generateSolution(2, 3));
    assertCollectorDetached(solver);
    var first = solver.getConstraintProfile().orElseThrow();
    assertThat(first.generations()).hasSize(rebuildCount + 2);
    long eventCount = first.combined().eventCount();
    var units = List.copyOf(first.units());
    solver.solve(TestdataSolution.generateSolution(2, 7));
    assertCollectorDetached(solver);
    var second = solver.getConstraintProfile().orElseThrow();
    assertThat(second).isNotSameAs(first);
    assertThat(first.combined().eventCount()).isEqualTo(eventCount);
    assertThat(first.units()).containsExactlyElementsOf(units);
    assertThat(collectors).hasSize(2).doesNotContainNull();
    assertThat(collectors.get(0)).isNotSameAs(collectors.get(1));
    assertThat(directors).allSatisfy(director -> assertThat(director.getConstraintProfiler()).isNull());
    assertThat(first.outcome()).isEqualTo(ConstraintProfile.Outcome.COMPLETED);
    assertThat(second.outcome()).isEqualTo(ConstraintProfile.Outcome.COMPLETED);
    assertThat(second.generations().getFirst().generationId())
        .isEqualTo(first.generations().getFirst().generationId());
  }

  @Test
  void environmentChangesAndProblemRestartKeepOneCollectorUntilOuterCleanup() {
    var solver =
        solver(
            config(true)
                .withPhases(
                    new ConstructionHeuristicPhaseConfig(),
                    new LocalSearchPhaseConfig()
                        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
                        .withTerminationConfig(new TerminationConfig().withStepCountLimit(1))));
    var collectors = new ArrayList<DefaultConstraintProfiler>();
    var starts = new AtomicInteger();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> phase) {
            collectors.add(phase.getScoreDirector().getConstraintProfiler());
            assertThat(solver.getConstraintProfile()).isEmpty();
          }

          @Override
          public void solvingStarted(SolverScope<TestdataSolution> scope) {
            if (starts.getAndIncrement() == 0) {
              solver.addProblemChange((solution, director) -> {});
            }
          }

          @Override
          public void solvingEnded(SolverScope<TestdataSolution> scope) {
            assertThat(solver.getConstraintProfile()).isEmpty();
          }
        });
    solver.solve(TestdataSolution.generateSolution(2, 3));
    assertThat(starts).hasValue(2);
    assertThat(collectors).hasSize(2).doesNotContainNull();
    assertThat(collectors.getFirst()).isSameAs(collectors.getLast());
    var profile = solver.getConstraintProfile().orElseThrow();
    assertThat(profile.generations().size()).isGreaterThanOrEqualTo(5);
    assertThat(profile.outcome()).isEqualTo(ConstraintProfile.Outcome.COMPLETED);
    assertCollectorDetached(solver);
  }

  @Test
  void replacementAndChildCloseDetachDirectorsWhileParentContinuesCollecting() {
    var solver =
        solver(
            config(true)
                .withPhases(
                    new ConstructionHeuristicPhaseConfig(),
                    new LocalSearchPhaseConfig()
                        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
                        .withTerminationConfig(new TerminationConfig().withStepCountLimit(1))));
    var phaseDirectors = new ArrayList<InnerScoreDirector<TestdataSolution, ?>>();
    var children = new ArrayList<InnerScoreDirector<TestdataSolution, ?>>();
    var collectors = new ArrayList<DefaultConstraintProfiler>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> phase) {
            var director = phase.getScoreDirector();
            var collector = phase.getSolverScope().getConstraintProfiler();
            assertThat(director.getConstraintProfiler()).isSameAs(collector).isNotNull();
            if (!phaseDirectors.isEmpty()) {
              var replaced = phaseDirectors.getLast();
              assertThat(director).isNotSameAs(replaced);
              assertThat(replaced.getConstraintProfiler()).isNull();
              assertThat(replaced.getWorkingSolution()).isNull();
            }
            phaseDirectors.add(director);
            collectors.add(collector);
            var child = director.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
            children.add(child);
            try (child) {
              assertThat(child.getConstraintProfiler()).isSameAs(collector);
              child.calculateScore();
            }
            assertThat(child.getConstraintProfiler()).isNull();
            assertThat(child.getWorkingSolution()).isNull();
            assertThat(director.getConstraintProfiler()).isSameAs(collector);
            assertThat(phase.getSolverScope().getConstraintProfiler()).isSameAs(collector);
            // Closing the child must not freeze or dispose the collector shared with the parent.
            director.setWorkingSolution(director.cloneWorkingSolution());
            director.calculateScore();
          }
        });
    solver.solve(TestdataSolution.generateSolution(2, 3));
    assertCollectorDetached(solver);
    assertThat(phaseDirectors).hasSize(2)
        .allSatisfy(director -> assertThat(director.getConstraintProfiler()).isNull());
    assertThat(children).hasSize(2)
        .allSatisfy(director -> assertThat(director.getConstraintProfiler()).isNull());
    assertThat(collectors.getFirst()).isSameAs(collectors.getLast());
    var profile = solver.getConstraintProfile().orElseThrow();
    assertThat(profile.outcome()).isEqualTo(ConstraintProfile.Outcome.COMPLETED);
    assertThat(profile.generations()).allMatch(generation -> generation.initializationCompleted());
    assertThat(profile.search().eventCount()).isPositive();
  }

  @Test
  void independentSolversAndSolutionAnalysisDoNotContributeToEachOthersProfiles() {
    var factory = SolverFactory.<TestdataSolution>create(config(true));
    var firstSolver = factory.buildSolver();
    var secondSolver = factory.buildSolver();
    var solution = firstSolver.solve(TestdataSolution.generateSolution(2, 3));
    var first = firstSolver.getConstraintProfile().orElseThrow();
    assertThat(secondSolver.getConstraintProfile()).isEmpty();
    SolutionManager.create(factory).update(solution);
    secondSolver.solve(TestdataSolution.generateSolution(2, 3));
    assertThat(secondSolver.getConstraintProfile().orElseThrow().combined().eventCount())
        .isEqualTo(first.combined().eventCount());
    assertThat(firstSolver.getConstraintProfile()).containsSame(first);
  }

  @Test
  void scratchAssertionsDoNotCreateGenerationsInTheRun() {
    var solver = solver(config(true));
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> phase) {
            var director = phase.getScoreDirector();
            director.calculateScore();
            director.assertScoreFromScratch(director.getWorkingSolution());
            director.assertScoreFromScratch(director.getWorkingSolution());
          }
        });
    solver.solve(TestdataSolution.generateSolution(2, 3));
    assertThat(solver.getConstraintProfile().orElseThrow().generations()).hasSize(2);
  }

  @Test
  void earlyTerminationPublishesAfterCleanup() {
    var solver = solver(config(true));
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingStarted(SolverScope<TestdataSolution> scope) {
            solver.terminateEarly();
          }
        });
    solver.solve(TestdataSolution.generateSolution(2, 3));
    assertThat(solver.getConstraintProfile().orElseThrow().outcome())
        .isEqualTo(ConstraintProfile.Outcome.TERMINATED_EARLY);
    assertThat(solver.getSolverScope().getScoreDirector().getWorkingSolution()).isNull();
    assertCollectorDetached(solver);
  }

  @Test
  void failedInitializationPublishesAndPreservesErrorIdentity() {
    var solver = solver(config(true).withConstraintProviderClass(FailingConstraintProvider.class));
    assertThatThrownBy(() -> solver.solve(TestdataSolution.generateSolution(2, 3)))
        .isSameAs(FailingConstraintProvider.FAILURE);
    assertThat(solver.getConstraintProfile().orElseThrow().outcome())
        .isEqualTo(ConstraintProfile.Outcome.FAILED);
    assertThat(solver.getSolverScope().getScoreDirector().getWorkingSolution()).isNull();
    assertCollectorDetached(solver);
  }

  public static final class FailingConstraintProvider implements ConstraintProvider {
    static final AssertionError FAILURE = new AssertionError("Scorer failed.");

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataEntity.class)
            .filter(
                entity -> {
                  throw FAILURE;
                })
            .penalize(SimpleScore.ONE)
            .asConstraint("Fail")
      };
    }
  }

  @Test
  void publicationPrecedesRenderingAndRendererFailureDoesNotFailSolve() {
    var profiler = spy(new DefaultConstraintProfiler());
    var solver = spy(solver(config(true)));
    doReturn(profiler).when(solver).createConstraintProfiler();
    doAnswer(
            invocation -> {
              assertThat(solver.getConstraintProfile()).containsSame(invocation.getArgument(0));
              assertThat(solver.getSolverScope().getScoreDirector().getConstraintProfiler()).isNull();
              assertThat(solver.getSolverScope().getConstraintProfiler()).isSameAs(profiler);
              throw new IllegalStateException("Renderer failed.");
            })
        .when(profiler)
        .report(any());
    assertThat(solver.solve(TestdataSolution.generateSolution(2, 3)).getScore())
        .isEqualTo(SimpleScore.of(-3));
    assertThat(solver.getConstraintProfile().orElseThrow().outcome())
        .isEqualTo(ConstraintProfile.Outcome.COMPLETED);
    verify(profiler, times(1)).freeze(ConstraintProfile.Outcome.COMPLETED);
    verify(profiler, times(1)).report(any());
    assertCollectorDetached(solver);
  }

  @Test
  void freezeFailureThrowsAfterOtherwiseSuccessfulSolve() {
    var profiler = spy(new DefaultConstraintProfiler());
    var solver = spy(solver(config(true)));
    var failure = new IllegalStateException("Snapshot failed.");
    doReturn(profiler).when(solver).createConstraintProfiler();
    doThrow(failure).when(profiler).freeze(any());
    assertThatThrownBy(() -> solver.solve(TestdataSolution.generateSolution(2, 3)))
        .isSameAs(failure);
    assertThat(solver.getConstraintProfile()).isEmpty();
    assertThat(solver.isSolving()).isFalse();
    assertCollectorDetached(solver);
  }

  @Test
  void cleanupAndFreezeFailuresAreSuppressedOntoOriginalFailure() {
    var profiler = spy(new DefaultConstraintProfiler());
    var solver = spy(solver(config(true)));
    var original = new AssertionError("Phase failed.");
    var cleanup = new AssertionError("Cleanup failed.");
    var snapshot = new AssertionError("Snapshot failed.");
    doReturn(profiler).when(solver).createConstraintProfiler();
    doThrow(snapshot).when(profiler).freeze(any());
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataSolution> phase) {
            throw original;
          }

          @Override
          public void solvingError(SolverScope<TestdataSolution> scope, Throwable failure) {
            throw cleanup;
          }
        });
    assertThatThrownBy(() -> solver.solve(TestdataSolution.generateSolution(2, 3)))
        .isSameAs(original);
    assertThat(original.getSuppressed()).containsExactly(cleanup, snapshot);
    verify(profiler, times(1)).freeze(ConstraintProfile.Outcome.FAILED);
    assertThat(solver.getConstraintProfile()).isEmpty();
    assertThat(solver.getSolverScope().getScoreDirector().getWorkingSolution()).isNull();
    assertCollectorDetached(solver);
  }

  @ParameterizedTest
  @ValueSource(strings = {"repository", "observer"})
  @SuppressWarnings("unchecked")
  void failingDirectorCleanupDetachesCollectorBeforeFailedProfileIsPublished(String cleanup) {
    var profiler = spy(new DefaultConstraintProfiler());
    var solver = spy(solver(config(true)));
    var failure = new AssertionError("Director cleanup failed.");
    doReturn(profiler).when(solver).createConstraintProfiler();
    doAnswer(
            invocation -> {
              assertThat(solver.getSolverScope().getScoreDirector().getConstraintProfiler()).isNull();
              assertThat(solver.getSolverScope().getConstraintProfiler()).isSameAs(profiler);
              return invocation.callRealMethod();
            })
        .when(profiler)
        .freeze(any());
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingEnded(SolverScope<TestdataSolution> scope) {
            var director = scope.getScoreDirector();
            if (cleanup.equals("repository")) {
              MoveSelectorBasedMoveRepository<TestdataSolution> repository =
                  mock(MoveSelectorBasedMoveRepository.class);
              doThrow(failure).when(repository).dispose();
              director.setMoveRepository(repository);
            } else {
              WorkingSolutionMutationObserver<TestdataSolution> observer =
                  mock(WorkingSolutionMutationObserver.class);
              doThrow(failure).when(observer).close();
              director.setWorkingSolutionMutationObserver(observer);
            }
          }
        });
    assertThatThrownBy(() -> solver.solve(TestdataSolution.generateSolution(2, 3)))
        .isSameAs(failure);
    assertCollectorDetached(solver);
    assertThat(solver.getConstraintProfile().orElseThrow().outcome())
        .isEqualTo(ConstraintProfile.Outcome.FAILED);
    verify(profiler, times(1)).freeze(ConstraintProfile.Outcome.FAILED);
  }

  @Test
  void concurrentManagerJobsPublishIndependentProfiles() throws Exception {
    var entered = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    var config =
        config(true)
            .withPhases(
                new CustomPhaseConfig()
                    .<TestdataSolution>withCustomPhaseCommands(
                        context -> {
                          entered.countDown();
                          await(release);
                        }));
    try (var manager =
        SolverManager.<TestdataSolution>create(
            config, new SolverManagerConfig().withParallelSolverCount("2"))) {
      var first = manager.solve("first", TestdataSolution.generateSolution(2, 3));
      var second = manager.solve("second", TestdataSolution.generateSolution(2, 7));
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(first.getConstraintProfile()).isEmpty();
        assertThat(second.getConstraintProfile()).isEmpty();
      } finally {
        release.countDown();
      }
      first.getFinalBestSolution();
      second.getFinalBestSolution();
      var firstProfile = first.getConstraintProfile().orElseThrow();
      var secondProfile = second.getConstraintProfile().orElseThrow();
      assertThat(firstProfile).isNotSameAs(secondProfile);
      assertThat(firstProfile.generations()).hasSize(2);
      assertThat(secondProfile.generations()).hasSize(2);
      assertThat(secondProfile.combined().eventCount())
          .isGreaterThan(firstProfile.combined().eventCount());
    }
  }

  @Test
  void canceledJobsExposeOnlyProfilesActuallyPublishedByAnAdmittedSolve() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var config =
        config(true)
            .withPhases(
                new CustomPhaseConfig()
                    .<TestdataSolution>withCustomPhaseCommands(
                        context -> {
                          entered.countDown();
                          boolean interrupted = false;
                          try {
                            while (true) {
                              try {
                                if (!release.await(5, TimeUnit.SECONDS))
                                  throw new AssertionError("Test release timed out.");
                                return;
                              } catch (InterruptedException exception) {
                                interrupted = true;
                              }
                            }
                          } finally {
                            if (interrupted) Thread.currentThread().interrupt();
                          }
                        }));
    try (var manager =
        SolverManager.<TestdataSolution>create(
            config, new SolverManagerConfig().withParallelSolverCount("1"))) {
      var activeInput = TestdataSolution.generateSolution(2, 3);
      var active = (DefaultSolverJob<TestdataSolution>) manager.solve("active", activeInput);
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var queuedInput = TestdataSolution.generateSolution(2, 5);
        var queued = manager.solve("queued", queuedInput);
        queued.terminateEarly();
        assertThat(queued.getFinalBestSolution()).isSameAs(queuedInput);
        assertThat(queued.getConstraintProfile()).isEmpty();
        active.terminateEarly(System.nanoTime());
        assertThat(active.getFinalBestSolution()).isSameAs(activeInput);
        assertThat(active.getConstraintProfile()).isEmpty();
      } finally {
        release.countDown();
      }
      org.awaitility.Awaitility.await()
          .atMost(5, TimeUnit.SECONDS)
          .untilAsserted(() -> assertThat(active.getConstraintProfile()).isPresent());
      assertThat(active.getConstraintProfile().orElseThrow().outcome())
          .isEqualTo(ConstraintProfile.Outcome.TERMINATED_EARLY);
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS))
        throw new AssertionError("Timed out waiting for test release.");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
