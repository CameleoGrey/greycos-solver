package greycos.solver.core.impl.alns;

import static greycos.solver.core.impl.alns.AlnsThreadingTestSupport.assertThreadStopped;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import greycos.solver.core.api.cotwin.solution.cloner.SolutionCloner;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.alns.AlnsAssignment;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.api.solver.alns.AlnsTerminationException;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsDestroyOperatorType;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Workload;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(120)
class AlnsThreadedContextTest {

  static Stream<Arguments> shapesAndAssertions() {
    return Stream.of("basic", "list", "mixed")
        .flatMap(
            shape ->
                Stream.of(EnvironmentMode.NO_ASSERT, EnvironmentMode.FULL_ASSERT)
                    .map(mode -> Arguments.of(shape, mode)));
  }

  @ParameterizedTest
  @MethodSource("shapesAndAssertions")
  void publishedNestedScratchAndWorstRemovalMatchSequentialStateScoresAndProbes(
      String shape, EnvironmentMode mode) {
    var workload = AlnsMoveThreadingWorkload.named(shape);
    assertThat(scratchTrace(workload, 2, mode)).isEqualTo(scratchTrace(workload, null, mode));
  }

  private static <S> List<String> scratchTrace(
      Workload<S> workload, Integer threads, EnvironmentMode mode) {
    try (var fixture = new Fixture<>(workload, threads, mode)) {
      var context = fixture.context;
      var trace = new ArrayList<String>();
      var destroy =
          BuiltinAlnsOperators.<S, SimpleScore>destroy(
              new AlnsDestroyOperatorConfig().withType(AlnsDestroyOperatorType.WORST_REMOVAL));
      var repair =
          BuiltinAlnsOperators.<S, SimpleScore>repair(
              new AlnsRepairOperatorConfig().withType(AlnsRepairOperatorType.REGRET_3));
      for (int trial = 0; trial < 2; trial++) {
        context.beginTrial();
        String before = workload.state(fixture.solution);
        var beforeScore = context.score();
        var pool = context.targets();
        var first = pool.get(0);
        var second = pool.get(1);
        var remaining = pool.subList(2, Math.min(8, pool.size()));

        // Publish a changed outer scratch state, then another nested state, then probe each
        // restored baseline before finally returning to the incumbent.
        context.evaluate(
            outer -> {
              outer.destroy(first);
              var enclosing = context.score();
              trace.add(context.evaluateRemovals(remaining).toString());
              context.evaluate(
                  inner -> {
                    inner.destroy(second);
                    trace.add(context.evaluateRemovals(remaining).toString());
                  });
              assertThat(context.score()).isEqualTo(enclosing);
              assertThat(context.currentAssignment(first).isUnassigned()).isTrue();
              assertThat(context.currentAssignment(second).isUnassigned()).isFalse();
              trace.add(context.evaluateRemovals(remaining).toString());
            });
        assertThat(context.score()).isEqualTo(beforeScore);
        assertThat(workload.state(fixture.solution)).isEqualTo(before);
        assertThat(context.pendingTargets()).isEmpty();
        assertThat(context.isChanged()).isFalse();

        var selected = destroy.select(context, 2);
        trace.add(selected.stream().map(pool::indexOf).toList().toString());
        assertThat(context.score()).isEqualTo(beforeScore);
        assertThat(workload.state(fixture.solution)).isEqualTo(before);
        assertThat(context.isChanged()).isFalse();
        // This batch forces replay of WORST_REMOVAL's scratch rollback before real destruction.
        trace.add(context.evaluateRemovals(remaining).toString());
        context.setPendingTargets(selected);
        context.destroy(selected);
        assertThat(repair.repair(context, selected)).isTrue();
        var candidate = context.score();
        assertThat(candidate.isComplete()).isTrue();
        assertThat(workload.recompute(fixture.solution)).isEqualTo(candidate.score());
        trace.add(candidate + ":" + workload.state(fixture.solution) + ":" + context.probeCount());
        if (trial == 0) {
          context.commit();
        } else {
          context.rollback();
          assertThat(context.score()).isEqualTo(beforeScore);
          assertThat(workload.state(fixture.solution)).isEqualTo(before);
        }
      }
      // Reuse the same workers once more after trial rejection, including its published undo.
      context.beginTrial();
      trace.add(context.evaluateRemovals(context.targets().subList(0, 6)).toString());
      context.rollback();
      return trace;
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void guardedSequentialProbeRecoversAnIncompleteNotificationAndDiscardsStartedWorkers(
      int threads) {
    try (var fixture =
        new Fixture<>(
            new BasicWorkload(), threads == 0 ? null : threads, EnvironmentMode.NO_ASSERT)) {
      var context = fixture.context;
      String original = fixture.workload.state(fixture.solution);
      var originalScore = context.score();
      context.beginTrial();
      var targets = context.targets();
      context.evaluateRemovals(targets.subList(0, 6));
      context.assign(differentAssignment(context, targets.get(0)));
      var target = targets.get(1);
      var assignment = differentAssignment(context, target);
      var descriptor =
          fixture.director.getSolutionDescriptor().getBasicVariableDescriptorList().getFirst();
      var failure = new IllegalStateException("Failure after a primitive wrote its genuine value.");
      doThrow(failure)
          .doAnswer(delegatesTo(fixture.realDirector))
          .when(fixture.director)
          .afterVariableChanged(descriptor, target.entity());

      // A singleton batch uses the guarded fast coordinator path even with move workers enabled.
      assertThatThrownBy(() -> context.evaluateAssignments(List.of(assignment))).isSameAs(failure);
      assertThat(context.isActive()).isFalse();
      assertThat(context.score()).isEqualTo(originalScore);
      assertThat(fixture.workload.state(fixture.solution)).isEqualTo(original);
      assertThat(fixture.workload.recompute(fixture.solution)).isEqualTo(originalScore.score());
      context.close();
      assertThat(fixture.threads).allSatisfy(AlnsThreadingTestSupport::assertThreadStopped);
    }
  }

  @Test
  void scoreFailureAfterABalancedFastProbeRestoresItsEnclosingStateAndReusableRecorder() {
    try (var fixture = new Fixture<>(new BasicWorkload(), null, EnvironmentMode.NO_ASSERT)) {
      var context = fixture.context;
      context.beginTrial();
      var target = context.targets().getFirst();
      context.setPendingTargets(List.of(target));
      var assignment = differentAssignment(context, target);
      String original = fixture.workload.state(fixture.solution);
      var originalScore = context.score();
      var failure = new IllegalStateException("Score calculation failed after a balanced probe.");
      doThrow(failure)
          .doAnswer(delegatesTo(fixture.realDirector))
          .when(fixture.director)
          .calculateScore();

      assertThatThrownBy(() -> context.evaluateAssignments(List.of(assignment))).isSameAs(failure);
      assertThat(context.isActive()).isTrue();
      assertThat(context.pendingTargets()).containsExactly(target);
      assertThat(fixture.workload.state(fixture.solution)).isEqualTo(original);
      assertThat(fixture.workload.score(fixture.solution)).isEqualTo(originalScore.score());
      var result = context.evaluateAssignments(List.of(assignment)).getFirst();
      assertThat(result.isComplete()).isTrue();
      assertThat(context.score()).isEqualTo(originalScore);
      assertThat(fixture.workload.state(fixture.solution)).isEqualTo(original);
      context.rollback();
    }
  }

  @Test
  void physicalWorkerCalculationsAreAddedOnlyBeyondTransferredCreditsAndClonesStayConstant() {
    try (var fixture = new Fixture<>(new BasicWorkload(), 2, EnvironmentMode.NO_ASSERT)) {
      var context = fixture.context;
      context.beginTrial();
      assertThat(fixture.children).isEmpty();
      var targets = context.targets().subList(0, 6);
      long parentBefore = fixture.director.getCalculationCount();
      for (int batch = 0; batch < 5; batch++) {
        context.evaluateRemovals(targets);
      }
      assertThat(fixture.director.getCalculationCount() - parentBefore).isEqualTo(30);
      assertThat(context.probeCount()).isEqualTo(30);
      context.rollback();
      context.close();

      long physical =
          fixture.children.stream().mapToLong(InnerScoreDirector::getCalculationCount).sum();
      assertThat(fixture.children).hasSize(2);
      verify(fixture.director, times(2))
          .createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
      assertThat(fixture.cloneCount).hasValue(2);
      assertThat(context.additionalCalculationCount()).isEqualTo(physical - 30);
      assertThat(context.moveEvaluationDiagnostics().consumed()).isEqualTo(30);
      var scope = new SolverScope<BasicSolution>();
      scope.setScoreDirector(fixture.director);
      scope.addChildThreadsScoreCalculationCount(context.additionalCalculationCount());
      assertThat(scope.getScoreCalculationCount()).isEqualTo(parentBefore + physical);
      assertThat(fixture.threads).allSatisfy(AlnsThreadingTestSupport::assertThreadStopped);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @Timeout(10)
  void threadFactoryFailureAfterOneWorkerStartsAllowsRollbackAndClosesThatWorker(
      boolean returnsNull) {
    try (var fixture = new Fixture<>(new BasicWorkload(), null, EnvironmentMode.NO_ASSERT)) {
      var failure = new IllegalStateException("Thread factory rejected the second ALNS worker.");
      var factoryCalls = new AtomicInteger();
      var context = fixture.context;
      context.configureMoveThreads(
          2,
          2,
          runnable -> {
            if (factoryCalls.incrementAndGet() == 2) {
              if (returnsNull) return null;
              throw failure;
            }
            var thread = new Thread(runnable, "alns-context-startup-failure-worker");
            fixture.threads.add(thread);
            return thread;
          },
          0,
          EnvironmentMode.NO_ASSERT);
      String original = fixture.workload.state(fixture.solution);
      var originalScore = context.score();
      context.beginTrial();
      var target = context.targets().getFirst();
      context.setPendingTargets(List.of(target));
      context.destroy(target);

      var assertion =
          assertThatThrownBy(() -> context.evaluateAssignments(context.assignments(target)));
      if (returnsNull) {
        assertion.isInstanceOf(IllegalStateException.class).hasMessageContaining("returned null");
      } else {
        assertion.isSameAs(failure);
      }
      // This rollback publishes an inverse delta; it must not await a never-started worker.
      context.rollback();
      assertThat(context.isActive()).isFalse();
      assertThat(context.score()).isEqualTo(originalScore);
      assertThat(fixture.workload.state(fixture.solution)).isEqualTo(original);
      context.close();
      assertThat(factoryCalls).hasValue(2);
      assertThat(fixture.threads)
          .hasSize(1)
          .allSatisfy(
              thread -> {
                assertThreadStopped(thread);
                assertThat(thread.getState()).isEqualTo(Thread.State.TERMINATED);
              });
    }
  }

  @Test
  @Timeout(10)
  void coordinatorInterruptedDuringInitialCloningCancelsAndRollsBackCleanly() throws Exception {
    var cloneStarted = new CountDownLatch(1);
    var coordinator = Thread.currentThread();
    var interrupter =
        new Thread(
            () -> {
              try {
                if (cloneStarted.await(5, TimeUnit.SECONDS)) coordinator.interrupt();
              } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
              }
            },
            "alns-context-startup-interrupter");
    try (var fixture = new Fixture<>(new BasicWorkload(), 2, EnvironmentMode.NO_ASSERT)) {
      doAnswer(
              invocation -> {
                cloneStarted.countDown();
                new CountDownLatch(1).await(); // Aborting startup interrupts this worker.
                throw new AssertionError("Interrupted cloning must not return a child director.");
              })
          .when(fixture.director)
          .createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
      var context = fixture.context;
      String original = fixture.workload.state(fixture.solution);
      var originalScore = context.score();
      context.beginTrial();
      var target = context.targets().getFirst();
      context.setPendingTargets(List.of(target));
      context.destroy(target);
      var assignments = context.assignments(target);
      interrupter.start();
      try {
        assertThatThrownBy(() -> context.evaluateAssignments(assignments))
            .isInstanceOf(AlnsTerminationException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        context.rollback();
        assertThat(context.score()).isEqualTo(originalScore);
        assertThat(fixture.workload.state(fixture.solution)).isEqualTo(original);
        assertThat(fixture.threads).allSatisfy(AlnsThreadingTestSupport::assertThreadStopped);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } finally {
        Thread.interrupted();
        interrupter.interrupt();
        interrupter.join(5000);
      }
    }
    assertThreadStopped(interrupter);
  }

  private static <S> AlnsAssignment<S> differentAssignment(
      DefaultAlnsContext<S, SimpleScore> context, AlnsTarget<S> target) {
    var current = context.currentAssignment(target);
    return context.assignments(target).stream()
        .filter(assignment -> !assignment.equals(current))
        .findFirst()
        .orElseThrow();
  }

  private static final class Fixture<S> implements AutoCloseable {
    private final Workload<S> workload;
    private final S solution;
    private final InnerScoreDirector<S, SimpleScore> director;
    private final InnerScoreDirector<S, SimpleScore> realDirector;
    private final DefaultAlnsContext<S, SimpleScore> context;
    private final List<InnerScoreDirector<S, SimpleScore>> children = new CopyOnWriteArrayList<>();
    private final List<Thread> threads = new CopyOnWriteArrayList<>();
    private final AtomicInteger cloneCount = new AtomicInteger();

    @SuppressWarnings("unchecked")
    private Fixture(Workload<S> workload, Integer workerCount, EnvironmentMode mode) {
      this.workload = workload;
      solution = workload.createProblem(12);
      var config = workload.solverConfig("NONE", 1L, 1, new TerminationConfig(), mode);
      var realDescriptor =
          SolutionDescriptor.buildSolutionDescriptor(
              (Class<S>) config.getSolutionClass(), config.getEntityClassList());
      SolutionDescriptor<S> descriptor =
          mock(SolutionDescriptor.class, delegatesTo(realDescriptor));
      SolutionCloner<S> countingCloner =
          original -> {
            cloneCount.incrementAndGet();
            return realDescriptor.getSolutionCloner().cloneSolution(original);
          };
      doReturn(countingCloner).when(descriptor).getSolutionCloner();
      var scoreConfig = config.getScoreDirectorFactoryConfig();
      var provider =
          ConfigUtils.newInstance(
              scoreConfig, "constraintProviderClass", scoreConfig.getConstraintProviderClass());
      var factory =
          new BavetConstraintStreamScoreDirectorFactory<S, SimpleScore>(
              descriptor, provider, mode, false);
      realDirector = factory.createScoreDirectorBuilder().build();
      director = mock(InnerScoreDirector.class, delegatesTo(realDirector));
      director.setWorkingSolution(solution);
      director.calculateScore();
      doAnswer(
              invocation -> {
                var child =
                    realDirector.createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
                children.add(child);
                return child;
              })
          .when(director)
          .createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
      context = new DefaultAlnsContext<>(director, new Random(31L), () -> false);
      context.configureMoveThreads(
          workerCount,
          2,
          runnable -> {
            var thread = new Thread(runnable, "alns-context-worker-" + threads.size());
            threads.add(thread);
            return thread;
          },
          0,
          mode);
    }

    @Override
    public void close() {
      try {
        context.close();
      } finally {
        director.close();
      }
    }
  }
}
