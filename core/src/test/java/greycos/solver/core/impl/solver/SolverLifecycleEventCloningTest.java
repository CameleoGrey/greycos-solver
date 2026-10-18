package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.event.FirstInitializedSolutionEvent;
import greycos.solver.core.api.solver.event.SolverJobStartedEvent;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.testcotwin.shadow.inverserelation.TestdataInverseRelationConstraintProvider;
import greycos.solver.core.testcotwin.shadow.inverserelation.TestdataInverseRelationEntity;
import greycos.solver.core.testcotwin.shadow.inverserelation.TestdataInverseRelationSolution;
import greycos.solver.core.testcotwin.shadow.inverserelation.TestdataInverseRelationValue;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(20)
class SolverLifecycleEventCloningTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void lifecycleConsumersNeverSeeWorkingEntitiesOrShadowCollections(boolean startedEvent)
      throws Exception {
    var config =
        PlannerTestUtils.buildSolverConfig(
                TestdataInverseRelationSolution.class,
                TestdataInverseRelationEntity.class,
                TestdataInverseRelationValue.class)
            .withEasyScoreCalculatorClass(null)
            .withConstraintProviderClass(TestdataInverseRelationConstraintProvider.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT);
    var solver =
        (DefaultSolver<TestdataInverseRelationSolution>)
            SolverFactory.<TestdataInverseRelationSolution>create(config).buildSolver();
    var consumed = new CountDownLatch(1);
    var eventSolution = new AtomicReference<TestdataInverseRelationSolution>();
    var eventEntity = new AtomicReference<TestdataInverseRelationEntity>();
    var failure = new AtomicReference<Throwable>();
    Consumer<TestdataInverseRelationSolution> mutate =
        solution -> {
          eventSolution.set(solution);
          eventEntity.set(solution.getEntityList().getFirst());
          solution.getEntityList().getFirst().setValue(null);
          solution.getValueList().forEach(value -> value.getEntities().clear());
          solution.getEntityList().clear();
          consumed.countDown();
        };
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<TestdataInverseRelationSolution> scope) {
            if (scope instanceof LocalSearchPhaseScope<?>) {
              try {
                assertThat(consumed.await(5, TimeUnit.SECONDS)).isTrue();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
              }
              var working = scope.getWorkingSolution();
              assertThat(working).isNotSameAs(eventSolution.get());
              assertThat(working.getEntityList()).hasSize(2);
              assertThat(working.getEntityList().getFirst()).isNotSameAs(eventEntity.get());
              assertThat(
                      working.getValueList().stream()
                          .mapToInt(value -> value.getEntities().size())
                          .sum())
                  .isEqualTo(2);
            }
          }
        });
    var problem = new TestdataInverseRelationSolution("problem");
    problem.setEntityList(
        new ArrayList<>(
            List.of(
                new TestdataInverseRelationEntity("e1"), new TestdataInverseRelationEntity("e2"))));
    problem.setValueList(
        new ArrayList<>(
            List.of(
                new TestdataInverseRelationValue("v1"), new TestdataInverseRelationValue("v2"))));
    Consumer<FirstInitializedSolutionEvent<TestdataInverseRelationSolution>> firstConsumer =
        startedEvent ? null : event -> mutate.accept(event.solution());
    Consumer<SolverJobStartedEvent<TestdataInverseRelationSolution>> startConsumer =
        startedEvent ? event -> mutate.accept(event.solution()) : null;
    DefaultSolverManager<TestdataInverseRelationSolution> manager =
        mock(DefaultSolverManager.class);
    var job =
        new DefaultSolverJob<>(
            manager,
            solver,
            1L,
            id -> problem,
            null,
            null,
            firstConsumer,
            startConsumer,
            (id, throwable) -> failure.set(throwable));
    var task = new FutureTask<>(job);
    job.setFinalBestSolutionFuture(task);
    task.run();
    var result = task.get(5, TimeUnit.SECONDS);
    assertThat(failure.get()).isNull();
    assertThat(result.solution().getEntityList()).hasSize(2);
    assertThat(problem.getEntityList()).hasSize(2);
  }
}
