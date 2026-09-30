package greycos.solver.core.impl.heuristic.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.heuristic.selector.move.generic.SelectorBasedChangeMove;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationPipeline.CandidateMetadataCollector;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationPipeline.EvaluationContext;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationPipeline.EvaluationMetadata;
import greycos.solver.core.impl.move.builtin.ChangeMove;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;
import greycos.solver.core.preview.api.cotwin.metamodel.PlanningVariableMetaModel;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.SolutionView;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class MoveEvaluationPipelineMetadataTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final InnerScore<SimpleScore> SECOND_SCORE =
      InnerScore.fullyAssigned(SimpleScore.of(-1));

  @Test
  void metadataObservesTheAppliedCandidateAndTheWorkerThenUndoesIt() throws Exception {
    try (var fixture = new Fixture(1, 1)) {
      fixture.start();
      var context = new TestContext(17);
      var candidate = fixture.changeTo(1);
      fixture.pipeline.submit(0, candidate, context);

      var result = fixture.pipeline.take();
      assertThat(result.move()).isSameAs(candidate);
      assertThat(result.context()).isSameAs(context);
      assertThat(result.metadata()).isEqualTo(new TestMetadata("second", 17));
      assertThat(result.score()).isEqualTo(SECOND_SCORE);
      assertThat(fixture.pipeline.awaitEvaluationQuiescence()).isTrue();
      fixture.assertWorkerValues("first");
      assertThat(fixture.solution.getEntityList().getFirst().getValue().getCode())
          .isEqualTo("first");
    }
  }

  @Test
  void resultContextsAndMetadataRemainStableAfterRingSlotsAreReused() throws Exception {
    try (var fixture = new Fixture(2, 2)) {
      fixture.start();
      var results = new ArrayList<MoveEvaluationPipeline.Result<TestdataSolution>>();
      for (int index = 0; index < 12; index += 2) {
        fixture.pipeline.submit(index, fixture.changeTo(1), new TestContext(index));
        fixture.pipeline.submit(index + 1, fixture.changeTo(2), new TestContext(index + 1));
        results.add(fixture.pipeline.take());
        results.add(fixture.pipeline.take());
      }

      for (int index = 0; index < results.size(); index++) {
        var result = results.get(index);
        assertThat(result.stepIndex()).isZero();
        assertThat(result.moveIndex()).isEqualTo(index);
        assertThat(result.context()).isEqualTo(new TestContext(index));
        assertThat(result.metadata())
            .isEqualTo(new TestMetadata(index % 2 == 0 ? "second" : "third", index));
      }
      assertThat(fixture.pipeline.awaitEvaluationQuiescence()).isTrue();
      fixture.assertWorkerValues("first");
    }
  }

  @Test
  void createsOnePrivateCollectorPerWorkerAndClosesEachExactlyOnce() {
    try (var fixture = new Fixture(3, 1)) {
      fixture.start();
      assertThat(fixture.createdCollectors).hasValue(3);
      assertThat(fixture.workerSolutions).hasSize(3).doesNotHaveDuplicates();
      assertThat(fixture.workerSolutions).doesNotContain(fixture.solution);
      fixture.pipeline.close();
      fixture.pipeline.abort();
      assertThat(fixture.closedCollectors).hasValue(3);
      fixture.assertWorkerValues("first");
    }
  }

  @Test
  void collectorFailureUndoesTheCandidateAndClosesEveryCollector() {
    try (var fixture = new Fixture(2, 1)) {
      var failure = new IllegalArgumentException("feature collection failed");
      fixture.actions.put(
          7,
          () -> {
            throw failure;
          });
      fixture.start();
      fixture.pipeline.submit(0, fixture.changeTo(1), new TestContext(7));

      assertThatThrownBy(fixture.pipeline::take)
          .isInstanceOf(IllegalStateException.class)
          .hasCause(failure);
      fixture.pipeline.abort();
      assertThat(fixture.closedCollectors).hasValue(2);
      fixture.assertWorkerValues("first");
    }
  }

  @Test
  void collectorCloseFailureKeepsTheEvaluationFailureAndStillClosesTheDirector() {
    try (var fixture = new Fixture(1, 1)) {
      var evaluationFailure = new IllegalArgumentException("feature collection failed");
      var closeFailure = new IllegalStateException("feature cleanup failed");
      var child = new ArrayList<InnerScoreDirector<TestdataSolution, ?>>();
      fixture.pipeline.setMetadataCollectorFactory(
          director -> {
            child.add(director);
            return new CandidateMetadataCollector<>() {
              @Override
              public EvaluationMetadata collect(
                  SolutionView<TestdataSolution> view,
                  Move<TestdataSolution> move,
                  EvaluationContext context) {
                throw evaluationFailure;
              }

              @Override
              public void close() {
                throw closeFailure;
              }
            };
          });
      fixture.start();
      fixture.pipeline.submit(0, fixture.changeTo(1), new TestContext(1));
      var reportedFailure = catchThrowable(fixture.pipeline::take);
      assertThat(reportedFailure)
          .isInstanceOf(IllegalStateException.class)
          .hasCause(evaluationFailure);
      fixture.pipeline.abort();

      assertThat(reportedFailure.getSuppressed()).contains(closeFailure);
      assertThat(child).hasSize(1);
      assertThat(child.getFirst().getWorkingSolution()).isNull();
    }
  }

  @Test
  void quiescenceWaitsForCancelledPriorStepWorkAfterAllCurrentResultsWereConsumed()
      throws Exception {
    var releaseObsolete = new CountDownLatch(1);
    try (var fixture = new Fixture(2, 2)) {
      try {
        leaveCancelledPriorStepEvaluationRunning(fixture, releaseObsolete);
        try (var releasing =
            releaseWhenParked(Thread.currentThread(), releaseObsolete::countDown)) {
          assertThat(fixture.pipeline.awaitEvaluationQuiescence()).isTrue();
          releasing.get();
        }
        fixture.assertWorkerValues("second");
      } finally {
        releaseObsolete.countDown();
      }
    }
  }

  @Test
  void terminationDuringQuiescenceDoesNotGrantPermissionToChangeEvaluationState() throws Exception {
    var releaseObsolete = new CountDownLatch(1);
    try (var fixture = new Fixture(2, 2)) {
      try {
        leaveCancelledPriorStepEvaluationRunning(fixture, releaseObsolete);
        var terminate = new AtomicBoolean();
        fixture.pipeline.setTerminationCheck(terminate::get);
        try (var terminating =
            releaseWhenParked(Thread.currentThread(), () -> terminate.set(true))) {
          assertThat(fixture.pipeline.awaitEvaluationQuiescence()).isFalse();
          terminating.get();
        }
        assertThat(releaseObsolete.getCount()).isEqualTo(1);
        terminate.set(false);
        releaseObsolete.countDown();
        assertThat(fixture.pipeline.awaitEvaluationQuiescence()).isTrue();
        fixture.assertWorkerValues("second");
      } finally {
        releaseObsolete.countDown();
      }
    }
  }

  @Test
  void quiescenceRejectsUnconsumedResultsAndAcceptsTheSameStepAfterConsumption() throws Exception {
    try (var fixture = new Fixture(1, 1)) {
      fixture.start();
      fixture.pipeline.submit(0, fixture.changeTo(1), new TestContext(0));
      assertThatThrownBy(fixture.pipeline::awaitEvaluationQuiescence)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("consumed");
      assertThat(fixture.pipeline.take().metadata()).isEqualTo(new TestMetadata("second", 0));
      assertThat(fixture.pipeline.awaitEvaluationQuiescence()).isTrue();
    }
  }

  @Test
  void quiescenceRequiresAStartedOpenPipeline() {
    try (var fixture = new Fixture(1, 1)) {
      assertThatThrownBy(fixture.pipeline::awaitEvaluationQuiescence)
          .isInstanceOf(IllegalStateException.class);
      fixture.start();
      fixture.pipeline.cancelStep();
      assertThatThrownBy(fixture.pipeline::awaitEvaluationQuiescence)
          .isInstanceOf(IllegalStateException.class);
      fixture.pipeline.close();
      assertThatThrownBy(fixture.pipeline::awaitEvaluationQuiescence)
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void collectorFactoryCannotChangeAfterStartup() {
    try (var fixture = new Fixture(1, 1)) {
      fixture.start();
      assertThatThrownBy(
              () ->
                  fixture.pipeline.setMetadataCollectorFactory(
                      director -> (view, move, context) -> null))
          .isInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void nonDoableCandidatePreservesContextWithoutCollectingMetadata() throws Exception {
    try (var fixture = new Fixture(1, 1)) {
      fixture.start();
      var context = new TestContext(5);
      var candidate =
          new SelectorBasedChangeMove<>(
              fixture
                  .parent
                  .getSolutionDescriptor()
                  .findEntityDescriptorOrFail(TestdataEntity.class)
                  .getGenuineVariableDescriptor("value"),
              fixture.solution.getEntityList().getFirst(),
              fixture.solution.getValueList().getFirst());
      fixture.pipeline.submit(0, candidate, context);
      var result = fixture.pipeline.take();
      assertThat(result.context()).isSameAs(context);
      assertThat(result.isMoveDoable()).isFalse();
      assertThat(result.score()).isNull();
      assertThat(result.metadata()).isNull();
      assertThat(fixture.collections).hasValue(0);
    }
  }

  private static void leaveCancelledPriorStepEvaluationRunning(
      Fixture fixture, CountDownLatch releaseObsolete) throws Exception {
    var obsoleteStarted = new CountDownLatch(1);
    fixture.actions.put(
        1,
        () -> {
          obsoleteStarted.countDown();
          awaitLatch(releaseObsolete);
        });
    fixture.start();
    fixture.pipeline.submit(0, fixture.changeTo(1), new TestContext(0));
    fixture.pipeline.submit(1, fixture.changeTo(2), new TestContext(1));
    fixture.pipeline.flush();
    awaitLatch(obsoleteStarted);
    assertThat(fixture.pipeline.take().moveIndex()).isZero();
    fixture.pipeline.cancelStep();
    fixture.pipeline.applyStep(1, fixture.changeTo(1), SECOND_SCORE);
    fixture.pipeline.startNextStep(1);
    fixture.pipeline.submit(0, fixture.changeTo(2), new TestContext(2));
    var result = fixture.pipeline.take();
    assertThat(result.stepIndex()).isEqualTo(1);
    assertThat(result.metadata()).isEqualTo(new TestMetadata("third", 2));
    assertThat(releaseObsolete.getCount()).isEqualTo(1);
  }

  private static void awaitLatch(CountDownLatch latch) {
    try {
      assertThat(latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while waiting for the test evaluation.", interrupted);
    }
  }

  private static ParkedAction releaseWhenParked(Thread coordinator, Runnable action) {
    var operationCompleted = new AtomicBoolean();
    Callable<Void> task =
        () -> {
          await()
              .atMost(TIMEOUT)
              .until(
                  () ->
                      operationCompleted.get()
                          || coordinator.getState() == Thread.State.WAITING
                          || coordinator.getState() == Thread.State.TIMED_WAITING);
          assertThat(operationCompleted)
              .as("The quiescence barrier must wait for the obsolete evaluation.")
              .isFalse();
          action.run();
          return null;
        };
    var future = new FutureTask<>(task);
    var thread = new Thread(future, "metadata-test-controller");
    thread.start();
    return new ParkedAction(thread, future, operationCompleted);
  }

  private record ParkedAction(
      Thread thread, FutureTask<Void> future, AtomicBoolean operationCompleted)
      implements AutoCloseable {

    private void get() throws Exception {
      operationCompleted.set(true);
      future.get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() throws InterruptedException {
      if (thread.isAlive()) {
        thread.interrupt();
      }
      thread.join(TIMEOUT.toMillis());
      assertThat(thread.isAlive()).isFalse();
    }
  }

  private record TestContext(int generation) implements EvaluationContext {}

  private record TestMetadata(String valueCode, int generation) implements EvaluationMetadata {}

  private static final class Fixture implements AutoCloseable {

    private final TestdataSolution solution = new TestdataSolution("metadata");
    private final InnerScoreDirector<TestdataSolution, SimpleScore> parent;
    private final PlanningVariableMetaModel<TestdataSolution, TestdataEntity, TestdataValue>
        variable;
    private final ExecutorService executor;
    private final MoveEvaluationPipeline<TestdataSolution> pipeline;
    private final List<TestdataSolution> workerSolutions = new CopyOnWriteArrayList<>();
    private final AtomicInteger createdCollectors = new AtomicInteger();
    private final AtomicInteger closedCollectors = new AtomicInteger();
    private final AtomicInteger collections = new AtomicInteger();
    private final ConcurrentHashMap<Integer, Runnable> actions = new ConcurrentHashMap<>();

    private Fixture(int workers, int capacity) {
      var descriptor = TestdataSolution.buildSolutionDescriptor();
      variable =
          descriptor
              .getMetaModel()
              .genuineEntity(TestdataEntity.class)
              .basicVariable("value", TestdataValue.class);
      solution.setValueList(
          List.of(
              new TestdataValue("first"), new TestdataValue("second"), new TestdataValue("third")));
      solution.setEntityList(
          List.of(new TestdataEntity("entity", solution.getValueList().getFirst())));
      var factory =
          new EasyScoreDirectorFactory<TestdataSolution, SimpleScore>(
              descriptor,
              workingSolution ->
                  SimpleScore.of(
                      -workingSolution
                          .getValueList()
                          .indexOf(workingSolution.getEntityList().getFirst().getValue())),
              EnvironmentMode.PHASE_ASSERT);
      parent = factory.buildScoreDirector();
      parent.setWorkingSolution(solution);
      parent.calculateScore();
      executor = Executors.newFixedThreadPool(workers);
      pipeline =
          new MoveEvaluationPipeline<>(
              executor, workers, capacity, 0, true, false, false, false, false, false);
      pipeline.setMetadataCollectorFactory(
          director -> {
            createdCollectors.incrementAndGet();
            var workerSolution = director.getWorkingSolution();
            workerSolutions.add(workerSolution);
            return new CandidateMetadataCollector<>() {
              @Override
              public EvaluationMetadata collect(
                  SolutionView<TestdataSolution> view,
                  Move<TestdataSolution> move,
                  EvaluationContext context) {
                collections.incrementAndGet();
                int generation = ((TestContext) context).generation();
                var action = actions.get(generation);
                if (action != null) {
                  action.run();
                }
                var value = view.getValue(variable, workerSolution.getEntityList().getFirst());
                return new TestMetadata(value.getCode(), generation);
              }

              @Override
              public void close() {
                closedCollectors.incrementAndGet();
              }
            };
          });
    }

    private Move<TestdataSolution> changeTo(int valueIndex) {
      return new ChangeMove<>(
          variable, solution.getEntityList().getFirst(), solution.getValueList().get(valueIndex));
    }

    private void start() {
      pipeline.start(parent);
      pipeline.startNextStep(0);
    }

    private void assertWorkerValues(String valueCode) {
      assertThat(workerSolutions)
          .allSatisfy(
              workingSolution ->
                  assertThat(workingSolution.getEntityList().getFirst().getValue().getCode())
                      .isEqualTo(valueCode));
    }

    @Override
    public void close() {
      try {
        pipeline.abort();
      } finally {
        executor.shutdownNow();
        try {
          assertThat(executor.awaitTermination(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(interrupted);
        } finally {
          parent.close();
        }
      }
    }
  }
}
