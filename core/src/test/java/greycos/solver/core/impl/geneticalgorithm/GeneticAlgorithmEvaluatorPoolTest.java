package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.impl.solver.thread.DefaultSolverThreadFactory;
import greycos.solver.core.impl.solver.thread.ThreadUtils;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.ResourceLock;

@Timeout(20)
class GeneticAlgorithmEvaluatorPoolTest {

  @Test
  void ownsRetainedSessionsAndTransfersEveryPhysicalCalculationExactlyOnce() throws Exception {
    try (var fixture = fixture(2, UnaryOperator.identity())) {
      var parentGenome = fixture.workspace.genome();
      long originalCount = fixture.director.getCalculationCount();
      fixture.pool.start();
      assertThat(fixture.pool.getDiagnostics().sessionCount()).isEqualTo(2);
      assertThat(fixture.pool.getCalculationCount()).isEqualTo(2);
      assertThat(fixture.director.getCalculationCount()).isEqualTo(originalCount + 2);
      for (int round = 0; round < 10; round++) {
        var first = fixture.pool.submit(round * 2L, fixture.genome(1));
        var second = fixture.pool.submit(round * 2L + 1, fixture.genome(2));
        assertThatThrownBy(() -> fixture.pool.submit(999, fixture.genome(0)))
            .hasMessageContaining("unconsumed jobs");
        assertThat(await(fixture.pool, first).score())
            .isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(-4)));
        assertThat(await(fixture.pool, second).score())
            .isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(-6)));
        // Re-reading a ticket must neither consume nor credit its work again.
        assertThat(fixture.pool.poll(first, 0).workId()).isEqualTo(round * 2L);
      }
      fixture.pool.close();
      var diagnostics = fixture.pool.getDiagnostics();
      assertThat(diagnostics.sessionCount()).isEqualTo(2);
      assertThat(diagnostics.initializedWorkerCount()).isEqualTo(2);
      assertThat(diagnostics.closedWorkerCount()).isEqualTo(2);
      assertThat(diagnostics.submittedCount()).isEqualTo(20);
      assertThat(diagnostics.evaluatedCount()).isEqualTo(20);
      assertThat(diagnostics.consumedCount()).isEqualTo(20);
      assertThat(diagnostics.calculationCount()).isEqualTo(22);
      assertThat(diagnostics.transferredCalculationCount()).isEqualTo(22);
      assertThat(fixture.pool.transferCalculationCount()).isZero();
      assertThat(fixture.director.getCalculationCount()).isEqualTo(originalCount + 22);
      assertThat(fixture.workspace.genome()).isEqualTo(parentGenome);
      assertThat(fixture.director.getWorkingSolution().getEntityList())
          .extracting(entity -> entity.getValue().getCode())
          .containsExactly("Generated Value 0", "Generated Value 1");
      assertThat(fixture.scope.getMoveEvaluationCount()).isZero();
      fixture.scope.getWorkerRegistry().assertNoActiveWorkers();
    }
  }

  @Test
  void invalidPreflightDoesNotScoreAndDoesNotPoisonTheNextCandidate() throws Exception {
    try (var fixture = fixture(1, UnaryOperator.identity())) {
      fixture.pool.start();
      var invalid = fixture.pool.submit(10, new GeneticAlgorithmGenome(new Object[] {null, null}));
      var rejected = await(fixture.pool, invalid);
      assertThat(rejected.valid()).isFalse();
      assertThat(rejected.score()).isNull();
      assertThat(fixture.pool.getCalculationCount()).isEqualTo(1);
      var valid = fixture.pool.submit(11, fixture.genome(2));
      assertThat(await(fixture.pool, valid).score())
          .isEqualTo(InnerScore.fullyAssigned(SimpleScore.of(-6)));
      assertThat(fixture.pool.getCalculationCount()).isEqualTo(2);
    }
  }

  @Test
  void failureBypassesBlockedEarlierWorkAndClosesEveryDirector() throws Exception {
    var firstEntered = new CountDownLatch(1);
    var failure = new IllegalArgumentException("second evaluation failed");
    var fixture =
        fixture(
            2,
            child -> {
              var wrapped = spy(child);
              var initial = new AtomicBoolean(true);
              doAnswer(
                      invocation -> {
                        if (initial.getAndSet(false)) return invocation.callRealMethod();
                        if (wrapped
                            .getWorkingSolution()
                            .getEntityList()
                            .getFirst()
                            .getValue()
                            .getCode()
                            .endsWith("1")) {
                          firstEntered.countDown();
                          new CountDownLatch(1).await();
                          throw new AssertionError("The blocked worker must be interrupted.");
                        }
                        throw failure;
                      })
                  .when(wrapped)
                  .calculateScore();
              return wrapped;
            });
    try {
      fixture.pool.start();
      var first = fixture.pool.submit(0, fixture.genome(1));
      assertThat(firstEntered.await(5, TimeUnit.SECONDS)).isTrue();
      fixture.pool.submit(1, fixture.genome(2));
      assertThatThrownBy(() -> fixture.pool.poll(first, 5000))
          .isInstanceOf(IllegalStateException.class)
          .hasCause(failure);
      assertThatThrownBy(fixture.pool::abort).hasCause(failure);
      assertThat(fixture.pool.getDiagnostics().closedWorkerCount()).isEqualTo(2);
      assertThat(fixture.pool.getDiagnostics().consumedCount()).isZero();
      assertThat(fixture.pool.getCalculationCount()).isEqualTo(2);
      fixture.scope.getWorkerRegistry().assertNoActiveWorkers();
    } finally {
      fixture.closeAfterFailure();
    }
  }

  @Test
  void startupScoreMismatchTransfersInitializationAndClosesThePrivateDirector() {
    var fixture =
        fixture(
            1,
            child -> {
              var wrapped = spy(child);
              doAnswer(
                      invocation -> {
                        invocation.callRealMethod();
                        return InnerScore.fullyAssigned(SimpleScore.of(-100));
                      })
                  .when(wrapped)
                  .calculateScore();
              return wrapped;
            });
    long initialCount = fixture.director.getCalculationCount();
    try {
      assertThatThrownBy(fixture.pool::start)
          .hasRootCauseMessage(
              "Evaluator initial score (-100) differs from coordinator score (-3).");
      assertThat(fixture.pool.getCalculationCount()).isEqualTo(1);
      assertThat(fixture.director.getCalculationCount()).isEqualTo(initialCount + 1);
      assertThat(fixture.pool.getDiagnostics().closedWorkerCount()).isEqualTo(1);
      fixture.scope.getWorkerRegistry().assertNoActiveWorkers();
    } finally {
      fixture.closeAfterFailure();
    }
  }

  @Test
  void cleanupFailureCannotLoseScoredWorkOrReplaceAnEarlierFailure() {
    var evaluationFailure = new IllegalArgumentException("score failed");
    var cleanupFailure = new IllegalStateException("close failed");
    var fixture =
        fixture(
            1,
            child -> {
              var wrapped = spy(child);
              doAnswer(
                      invocation -> {
                        invocation.callRealMethod();
                        throw evaluationFailure;
                      })
                  .when(wrapped)
                  .calculateScore();
              doAnswer(
                      invocation -> {
                        invocation.callRealMethod();
                        throw cleanupFailure;
                      })
                  .when(wrapped)
                  .close();
              return wrapped;
            });
    try {
      assertThatThrownBy(fixture.pool::start)
          .hasCause(evaluationFailure)
          .satisfies(
              error ->
                  assertThat(error.getSuppressed())
                      .anySatisfy(suppressed -> assertThat(suppressed).hasCause(cleanupFailure)));
      assertThat(fixture.pool.getCalculationCount()).isEqualTo(1);
      assertThat(fixture.pool.getDiagnostics().transferredCalculationCount()).isEqualTo(1);
      assertThat(fixture.pool.getDiagnostics().closedWorkerCount()).isEqualTo(1);
    } finally {
      fixture.closeAfterFailure();
    }
  }

  @Test
  void abortCountsAnUnpublishedCalculationAndClosesOnlyOnWorkerThread() throws Exception {
    var calculated = new CountDownLatch(1);
    var coordinator = Thread.currentThread();
    var wrongCloseThread = new AtomicBoolean();
    var fixture =
        fixture(
            1,
            child -> {
              var wrapped = spy(child);
              var initial = new AtomicBoolean(true);
              doAnswer(
                      invocation -> {
                        var score = invocation.callRealMethod();
                        if (!initial.getAndSet(false)) {
                          calculated.countDown();
                          new CountDownLatch(1).await();
                        }
                        return score;
                      })
                  .when(wrapped)
                  .calculateScore();
              doAnswer(
                      invocation -> {
                        wrongCloseThread.set(Thread.currentThread() == coordinator);
                        return invocation.callRealMethod();
                      })
                  .when(wrapped)
                  .close();
              return wrapped;
            });
    try {
      fixture.pool.start();
      fixture.pool.submit(0, fixture.genome(2));
      assertThat(calculated.await(5, TimeUnit.SECONDS)).isTrue();
      fixture.pool.abort();
      assertThat(fixture.pool.getCalculationCount()).isEqualTo(2);
      assertThat(fixture.pool.getDiagnostics().transferredCalculationCount()).isEqualTo(2);
      assertThat(fixture.pool.getDiagnostics().consumedCount()).isZero();
      assertThat(fixture.pool.getDiagnostics().closedWorkerCount()).isEqualTo(1);
      assertThat(wrongCloseThread).isFalse();
    } finally {
      fixture.closeAfterFailure();
    }
  }

  @Test
  void cancellationDuringStartupJoinsWorkersBeforeReturning() {
    var entered = new AtomicBoolean();
    try (var fixture =
        fixture(
            1,
            child -> {
              var wrapped = spy(child);
              doAnswer(
                      invocation -> {
                        invocation.callRealMethod();
                        entered.set(true);
                        new CountDownLatch(1).await();
                        throw new AssertionError("Startup should be interrupted.");
                      })
                  .when(wrapped)
                  .calculateScore();
              return wrapped;
            })) {
      assertThat(fixture.pool.start(entered::get)).isFalse();
      assertThat(fixture.pool.getDiagnostics().closedWorkerCount()).isEqualTo(1);
      assertThat(fixture.pool.getCalculationCount()).isEqualTo(1);
      fixture.scope.getWorkerRegistry().assertNoActiveWorkers();
    }
  }

  @Test
  void rejectedWorkerStartupStopsAlreadyStartedWorkers() {
    var threadCount = new AtomicInteger();
    var threads = new DefaultSolverThreadFactory("EvaluatorTest");
    try (var fixture = fixture(2, UnaryOperator.identity())) {
      var rejected = new IllegalArgumentException("thread creation failed");
      var pool =
          new GeneticAlgorithmEvaluatorPool<>(
              fixture.director,
              fixture.workspace,
              fixture.scope,
              2,
              task -> {
                if (threadCount.incrementAndGet() == 2) throw rejected;
                return threads.newThread(task);
              });
      assertThatThrownBy(pool::start).isSameAs(rejected);
      fixture.scope.getWorkerRegistry().assertNoActiveWorkers();
      assertThat(pool.getDiagnostics().transferredCalculationCount())
          .isEqualTo(pool.getCalculationCount());
    }
  }

  @Test
  @ResourceLock("ThreadUtils.shutdownTimeout")
  void timedOutWorkerRemainsRegisteredUntilItActuallyStops() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var closeCalled = new CountDownLatch(1);
    int previousTimeout = ThreadUtils.getDefaultShutdownTimeout();
    var fixture =
        fixture(
            1,
            child -> {
              var wrapped = spy(child);
              var initial = new AtomicBoolean(true);
              doAnswer(
                      invocation -> {
                        var result = invocation.callRealMethod();
                        if (!initial.getAndSet(false)) {
                          entered.countDown();
                          boolean interrupted = false;
                          while (true) {
                            try {
                              release.await();
                              break;
                            } catch (InterruptedException ignored) {
                              interrupted =
                                  true; // Deliberately model noncooperative user scoring code.
                            }
                          }
                          if (interrupted) Thread.currentThread().interrupt();
                        }
                        return result;
                      })
                  .when(wrapped)
                  .calculateScore();
              doAnswer(
                      invocation -> {
                        try {
                          return invocation.callRealMethod();
                        } finally {
                          closeCalled.countDown();
                        }
                      })
                  .when(wrapped)
                  .close();
              return wrapped;
            });
    try {
      fixture.pool.start();
      fixture.pool.submit(0, fixture.genome(2));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      ThreadUtils.setDefaultShutdownTimeout(1);
      assertThatThrownBy(fixture.pool::abort).hasMessageContaining("did not terminate");
      assertThat(closeCalled.getCount()).isEqualTo(1);
      assertThatThrownBy(() -> fixture.scope.getWorkerRegistry().assertNoActiveWorkers())
          .hasMessageContaining("previous solve have not stopped");
      release.countDown();
      assertThat(closeCalled.await(5, TimeUnit.SECONDS)).isTrue();
      // A later join still reports the initiating timeout and reconciles the final native work.
      assertThatThrownBy(fixture.pool::abort).hasMessageContaining("did not terminate");
      assertThat(fixture.pool.getDiagnostics().closedWorkerCount()).isEqualTo(1);
      assertThat(fixture.pool.getCalculationCount()).isEqualTo(2);
      assertThat(fixture.pool.getDiagnostics().transferredCalculationCount()).isEqualTo(2);
      fixture.scope.getWorkerRegistry().assertNoActiveWorkers();
      try (var next =
          new GeneticAlgorithmEvaluatorPool<>(
              fixture.director, fixture.workspace, fixture.scope, 1)) {
        next.start();
      }
      fixture.scope.getWorkerRegistry().assertNoActiveWorkers();
    } finally {
      release.countDown();
      ThreadUtils.setDefaultShutdownTimeout(previousTimeout);
      fixture.closeAfterFailure();
    }
  }

  private static GeneticAlgorithmEvaluatorPool.Result<SimpleScore> await(
      GeneticAlgorithmEvaluatorPool<TestdataSolution, SimpleScore> pool,
      GeneticAlgorithmEvaluatorPool.Ticket<SimpleScore> ticket)
      throws InterruptedException {
    var result = pool.poll(ticket, 5000);
    assertThat(result).isNotNull();
    return result;
  }

  private static Fixture fixture(
      int workers, UnaryOperator<InnerScoreDirector<TestdataSolution, SimpleScore>> decorate) {
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataSolution, SimpleScore>(
            TestdataSolution.buildSolutionDescriptor(),
            constraints ->
                new Constraint[] {
                  constraints
                      .forEach(TestdataEntity.class)
                      .penalize(
                          SimpleScore.ONE,
                          entity ->
                              Long.parseLong(
                                      entity
                                          .getValue()
                                          .getCode()
                                          .substring("Generated Value ".length()))
                                  + 1)
                      .asConstraint("value")
                },
            EnvironmentMode.NO_ASSERT);
    var director = spy(factory.createScoreDirectorBuilder().withLookUpEnabled(true).build());
    director.setWorkingSolution(TestdataSolution.generateSolution(3, 2));
    var workspace = new GeneticAlgorithmWorkspace<>(director, director.calculateScore());
    var scope = new SolverScope<TestdataSolution>();
    scope.setScoreDirector(director);
    var children = new CopyOnWriteArrayList<InnerScoreDirector<TestdataSolution, SimpleScore>>();
    doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              var child =
                  (InnerScoreDirector<TestdataSolution, SimpleScore>) invocation.callRealMethod();
              var decorated = decorate.apply(child);
              children.add(decorated);
              return decorated;
            })
        .when(director)
        .createChildThreadScoreDirector(ChildThreadType.MOVE_THREAD);
    return new Fixture(
        director,
        workspace,
        scope,
        new GeneticAlgorithmEvaluatorPool<>(director, workspace, scope, workers),
        children);
  }

  private record Fixture(
      InnerScoreDirector<TestdataSolution, SimpleScore> director,
      GeneticAlgorithmWorkspace<TestdataSolution, SimpleScore> workspace,
      SolverScope<TestdataSolution> scope,
      GeneticAlgorithmEvaluatorPool<TestdataSolution, SimpleScore> pool,
      List<InnerScoreDirector<TestdataSolution, SimpleScore>> children)
      implements AutoCloseable {
    GeneticAlgorithmGenome genome(int valueIndex) {
      var value = director.getWorkingSolution().getValueList().get(valueIndex);
      return new GeneticAlgorithmGenome(new Object[] {value, value});
    }

    @Override
    public void close() {
      try {
        pool.close();
      } finally {
        director.close();
      }
    }

    void closeAfterFailure() {
      try {
        pool.abort();
      } catch (RuntimeException expected) {
        // Every failure test asserts the initiating exception before this last-resort cleanup.
      } finally {
        director.close();
      }
    }
  }
}
