package greycos.solver.core.impl.alns;

import static greycos.solver.core.impl.alns.AlnsThreadingTestSupport.assertThreadStopped;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.api.solver.alns.AlnsTerminationException;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Workload;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(60)
class AlnsRepairAttemptExecutorTest {

  private static final AlnsRepairOperatorConfig REPAIR =
      new AlnsRepairOperatorConfig().withType(AlnsRepairOperatorType.RANDOMIZED_GREEDY).withTopK(3);
  private static final long[] SEEDS = {31, 7, 99, 4, 81};

  static Stream<Arguments> shapesAndAssertions() {
    return Stream.of("basic", "list", "mixed")
        .flatMap(
            shape ->
                Stream.of(EnvironmentMode.NO_ASSERT, EnvironmentMode.FULL_ASSERT)
                    .map(mode -> Arguments.of(shape, mode)));
  }

  @ParameterizedTest
  @MethodSource("shapesAndAssertions")
  void attemptsMatchSerialSeedsScoresAndQueryCountsAcrossCommitAndRollback(
      String shape, EnvironmentMode mode) {
    var workload = AlnsMoveThreadingWorkload.named(shape);
    var serial = trace(workload, null, mode);
    assertThat(trace(workload, 1, mode)).isEqualTo(serial);
    assertThat(trace(workload, 3, mode)).isEqualTo(serial);
  }

  private static <S> List<String> trace(
      Workload<S> workload, Integer threads, EnvironmentMode mode) {
    try (var fixture = new Fixture<>(workload, threads, mode)) {
      var trace = new ArrayList<String>();
      for (int trial = 0; trial < 3; trial++) {
        String before = workload.state(fixture.solution);
        var targets = fixture.destroy();
        long initialQueries = fixture.director.getCalculationCount();
        var candidate = fixture.executor.evaluate(fixture.context, targets, REPAIR, SEEDS);
        assertThat(candidate).isNotNull();
        long queries = fixture.director.getCalculationCount() - initialQueries;
        fixture.context.applyRepairJournal(candidate.journal(), candidate.score());
        assertThat(workload.recompute(fixture.solution)).isEqualTo(candidate.score().raw());
        trace.add(
            candidate.attemptIndex()
                + ":"
                + candidate.score()
                + ":"
                + queries
                + ":"
                + fixture.context.probeCount()
                + ":"
                + workload.state(fixture.solution));
        if (trial == 1) {
          fixture.context.rollback();
          assertThat(workload.state(fixture.solution)).isEqualTo(before);
        } else {
          fixture.context.commit();
        }
      }
      fixture.executor.close();
      int copies = threads == null ? 1 : threads;
      assertThat(fixture.children).hasSize(copies);
      verify(fixture.director, times(copies))
          .createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
      var diagnostics = fixture.executor.getDiagnostics();
      assertThat(diagnostics.started()).isEqualTo(15);
      assertThat(diagnostics.completed()).isEqualTo(15);
      assertThat(diagnostics.incomplete()).isZero();
      assertThat(diagnostics.discarded()).isZero();
      long physical =
          fixture.children.stream().mapToLong(InnerScoreDirector::getCalculationCount).sum();
      assertThat(fixture.executor.getAdditionalCalculationCount())
          .isEqualTo(physical - diagnostics.creditedQueries());
      fixture.threads.forEach(thread -> assertThreadStopped(thread));
      return trace;
    }
  }

  @ParameterizedTest
  @ValueSource(longs = {1, 2, 31, 32, 33, 65, 109, 110})
  void orderedLogicalCutoffIsExactAndWorkersRemainUsableForTheNextTrial(long limit) {
    var serial = cutoffTrace(null, limit);
    assertThat(cutoffTrace(1, limit)).isEqualTo(serial);
    assertThat(cutoffTrace(3, limit)).isEqualTo(serial);
  }

  private static List<String> cutoffTrace(Integer threads, long limit) {
    try (var fixture = new Fixture<>(new BasicWorkload(), threads, EnvironmentMode.NO_ASSERT)) {
      String original = fixture.workload.state(fixture.solution);
      var targets = fixture.destroy();
      long initial = fixture.director.getCalculationCount();
      fixture.calculationLimit.set(initial + limit);
      assertThatThrownBy(() -> fixture.executor.evaluate(fixture.context, targets, REPAIR, SEEDS))
          .isInstanceOf(AlnsTerminationException.class);
      assertThat(fixture.director.getCalculationCount() - initial).isEqualTo(limit);
      long creditedProbes = fixture.context.probeCount();
      fixture.calculationLimit.set(Long.MAX_VALUE);
      fixture.context.rollback();
      assertThat(fixture.workload.state(fixture.solution)).isEqualTo(original);
      var followingTargets = fixture.destroy();
      var candidate = fixture.executor.evaluate(fixture.context, followingTargets, REPAIR, SEEDS);
      assertThat(candidate).isNotNull();
      fixture.context.applyRepairJournal(candidate.journal(), candidate.score());
      assertThat(fixture.workload.recompute(fixture.solution)).isEqualTo(candidate.score().raw());
      var result =
          List.of(
              Long.toString(creditedProbes),
              candidate.attemptIndex() + ":" + candidate.score(),
              fixture.workload.state(fixture.solution));
      fixture.context.rollback();
      return result;
    }
  }

  @Test
  void rejectsUnsupportedRepairsBeforeStartingWorkers() {
    try (var fixture = new Fixture<>(new BasicWorkload(), 2, EnvironmentMode.NO_ASSERT)) {
      var targets = fixture.destroy();
      assertThatThrownBy(
              () ->
                  fixture.executor.evaluate(
                      fixture.context,
                      targets,
                      new AlnsRepairOperatorConfig().withType(AlnsRepairOperatorType.GREEDY),
                      SEEDS))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(
              () ->
                  fixture.executor.evaluate(
                      fixture.context,
                      targets,
                      new AlnsRepairOperatorConfig()
                          .withType(AlnsRepairOperatorType.RANDOMIZED_GREEDY)
                          .withTopK(0),
                      SEEDS))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(fixture.children).isEmpty();
    }
  }

  @Test
  void serialAttemptModeStartsNoThreads() {
    try (var fixture = new Fixture<>(new BasicWorkload(), null, EnvironmentMode.NO_ASSERT)) {
      var candidate = fixture.executor.evaluate(fixture.context, fixture.destroy(), REPAIR, SEEDS);
      assertThat(candidate).isNotNull();
      assertThat(fixture.threads).isEmpty();
      assertThat(fixture.children).hasSize(1);
    }
  }

  @Test
  void budgetExpiryDuringInitialCloningPreservesWorkersForTheFollowingTrial() {
    try (var fixture = new Fixture<>(new BasicWorkload(), 2, EnvironmentMode.NO_ASSERT)) {
      var targets = fixture.destroy();
      fixture.cancelWhenChildCreated.set(true);
      assertThatThrownBy(() -> fixture.executor.evaluate(fixture.context, targets, REPAIR, SEEDS))
          .isInstanceOf(AlnsTerminationException.class);
      assertThat(fixture.children).hasSize(2);
      fixture.cancelWhenChildCreated.set(false);
      fixture.forcedTermination.set(false);
      fixture.context.rollback();
      var candidate = fixture.executor.evaluate(fixture.context, fixture.destroy(), REPAIR, SEEDS);
      assertThat(candidate).isNotNull();
      assertThat(fixture.children).hasSize(2);
      fixture.context.applyRepairJournal(candidate.journal(), candidate.score());
      assertThat(fixture.workload.recompute(fixture.solution)).isEqualTo(candidate.score().raw());
    }
  }

  @Test
  void topKOneRemainsASupportedRandomizedRepair() {
    try (var fixture = new Fixture<>(new BasicWorkload(), 2, EnvironmentMode.NO_ASSERT)) {
      var candidate =
          fixture.executor.evaluate(
              fixture.context, fixture.destroy(), REPAIR.copyConfig().withTopK(1), SEEDS);
      assertThat(candidate).isNotNull();
    }
  }

  @Test
  void fullAssertChecksEveryWorkerCandidateAndRestoredBaseline() {
    try (var fixture = new Fixture<>(new BasicWorkload(), 2, EnvironmentMode.FULL_ASSERT)) {
      assertThat(fixture.executor.evaluate(fixture.context, fixture.destroy(), REPAIR, SEEDS))
          .isNotNull();
      for (var child : fixture.children) {
        verify(child, atLeastOnce()).assertPredictedScoreFromScratch(any(), any());
        verify(child, atLeastOnce()).assertExpectedWorkingScore(any(), any());
      }
    }
  }

  @Test
  void workerScoreFailureAbortsPeersAndClosesEveryDirector() {
    var fixture = new Fixture<>(new BasicWorkload(), 2, EnvironmentMode.NO_ASSERT);
    var failure = new IllegalStateException("repair-attempt score failure");
    try {
      var targets = fixture.destroy();
      fixture.workerScoreFailure.set(failure);
      assertThatThrownBy(() -> fixture.executor.evaluate(fixture.context, targets, REPAIR, SEEDS))
          .isInstanceOf(IllegalStateException.class)
          .hasCause(failure);
      fixture.context.rollback();
      for (var child : fixture.children) verify(child).close();
      fixture.threads.forEach(thread -> assertThreadStopped(thread));
    } finally {
      fixture.executor.abort();
      fixture.context.close();
      fixture.director.close();
    }
  }

  @Test
  void aggregateAllowanceBoundsSpeculativeWorkerQueries() {
    try (var fixture = new Fixture<>(new BasicWorkload(), 3, EnvironmentMode.NO_ASSERT)) {
      var targets = fixture.destroy();
      long initial = fixture.director.getCalculationCount();
      fixture.calculationLimit.set(initial + 3);
      fixture.context.configureRepairQueryAllowance(
          () -> fixture.calculationLimit.get() - fixture.director.getCalculationCount());
      assertThatThrownBy(() -> fixture.executor.evaluate(fixture.context, targets, REPAIR, SEEDS))
          .isInstanceOf(AlnsTerminationException.class);
      assertThat(fixture.director.getCalculationCount() - initial).isEqualTo(3);
      fixture.calculationLimit.set(Long.MAX_VALUE);
      fixture.context.rollback();
      fixture.executor.close();
      long physical =
          fixture.children.stream().mapToLong(InnerScoreDirector::getCalculationCount).sum();
      // Each active attempt gets at most three queries; initialization and its transaction add two.
      assertThat(physical).isLessThanOrEqualTo(3 * (3 + 2));
      var diagnostics = fixture.executor.getDiagnostics();
      assertThat(diagnostics.started())
          .isEqualTo(diagnostics.completed() + diagnostics.incomplete() + diagnostics.discarded());
    }
  }

  private static final class Fixture<S> implements AutoCloseable {
    final Workload<S> workload;
    final S solution;
    final InnerScoreDirector<S, SimpleScore> director;
    final DefaultAlnsContext<S, SimpleScore> context;
    final AlnsRepairAttemptExecutor<S, SimpleScore> executor;
    final List<InnerScoreDirector<S, SimpleScore>> children = new CopyOnWriteArrayList<>();
    final List<Thread> threads = new CopyOnWriteArrayList<>();
    final AtomicLong calculationLimit = new AtomicLong(Long.MAX_VALUE);
    final AtomicBoolean forcedTermination = new AtomicBoolean();
    final AtomicBoolean cancelWhenChildCreated = new AtomicBoolean();
    final AtomicReference<RuntimeException> workerScoreFailure = new AtomicReference<>();

    @SuppressWarnings("unchecked")
    Fixture(Workload<S> workload, Integer threads, EnvironmentMode mode) {
      this.workload = workload;
      solution = workload.createProblem(40);
      var config = workload.solverConfig("NONE", 1L, 1, new TerminationConfig(), mode);
      var descriptor =
          SolutionDescriptor.buildSolutionDescriptor(
              (Class<S>) config.getSolutionClass(), config.getEntityClassList());
      var scoreConfig = config.getScoreDirectorFactoryConfig();
      var provider =
          ConfigUtils.newInstance(
              scoreConfig, "constraintProviderClass", scoreConfig.getConstraintProviderClass());
      var factory =
          new BavetConstraintStreamScoreDirectorFactory<S, SimpleScore>(
              descriptor, provider, mode, false);
      var realDirector = factory.createScoreDirectorBuilder().build();
      director = mock(InnerScoreDirector.class, delegatesTo(realDirector));
      director.setWorkingSolution(solution);
      director.calculateScore();
      doAnswer(
              invocation -> {
                var realChild =
                    realDirector.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
                InnerScoreDirector<S, SimpleScore> child =
                    mock(InnerScoreDirector.class, delegatesTo(realChild));
                var childQueries = new AtomicInteger();
                doAnswer(
                        query -> {
                          if (childQueries.incrementAndGet() > 2
                              && workerScoreFailure.get() != null) {
                            throw workerScoreFailure.get();
                          }
                          return realChild.calculateScore();
                        })
                    .when(child)
                    .calculateScore();
                children.add(child);
                if (cancelWhenChildCreated.get()) forcedTermination.set(true);
                return child;
              })
          .when(director)
          .createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
      context =
          new DefaultAlnsContext<>(
              director,
              new Random(5),
              () ->
                  forcedTermination.get()
                      || director.getCalculationCount() >= calculationLimit.get());
      context.enableReplayRecording();
      executor =
          new AlnsRepairAttemptExecutor<>(
              director,
              threads,
              runnable -> {
                var thread =
                    new Thread(runnable, "alns-repair-attempt-test-" + this.threads.size());
                this.threads.add(thread);
                return thread;
              },
              mode,
              SEEDS.length);
    }

    List<AlnsTarget<S>> destroy() {
      context.beginTrial();
      var targets = context.targets().subList(0, 5);
      context.setPendingTargets(targets);
      context.destroy(targets);
      return targets;
    }

    @Override
    public void close() {
      calculationLimit.set(Long.MAX_VALUE);
      forcedTermination.set(false);
      try {
        executor.close();
      } finally {
        try {
          context.close();
        } finally {
          director.close();
        }
      }
      threads.forEach(thread -> assertThreadStopped(thread));
    }
  }
}
