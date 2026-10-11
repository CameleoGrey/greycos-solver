package greycos.solver.core.impl.islandmodel.resumable;

import static greycos.solver.core.impl.islandmodel.resumable.ResumableSearchTestSupport.sessions;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.solution.cloner.SolutionCloner;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import greycos.solver.core.api.solver.Solver;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.islandmodel.ResumableSearchCooperationPolicy;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.localsearch.decider.forager.AbstractLocalSearchForager;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForagerFactory;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.event.SolveEventProducerId;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated("Uses latch-controlled startup callbacks and records private scorer ownership.")
@Timeout(30)
class ResumableStartupCancellationTest {

  private static final ConcurrentLinkedQueue<Thread> OWNERS = new ConcurrentLinkedQueue<>();
  private static final ConcurrentLinkedQueue<TrackingCalculator> CALCULATORS =
      new ConcurrentLinkedQueue<>();
  private static final AtomicInteger PARTITIONS = new AtomicInteger();
  private static final AtomicInteger CLONES_AFTER_GATE = new AtomicInteger();
  private static volatile Gate gate;
  private static volatile boolean cloningArmed;
  private static volatile boolean improveFirstPartition;

  @BeforeEach
  void reset() {
    OWNERS.clear();
    CALCULATORS.clear();
    PARTITIONS.set(0);
    CLONES_AFTER_GATE.set(0);
    LifecycleForager.INSTANCES.clear();
    gate = new Gate(Stage.NONE);
    cloningArmed = false;
    improveFirstPartition = false;
  }

  @Test
  void cancellationDuringFirstInitializerAdmitsNoMoreSessionsAndSolverCanBeReused()
      throws Exception {
    gate = new Gate(Stage.INITIALIZATION);
    var search = search();
    var solver = solver(config(sessions(search, 8, 2, 1, ResumableSearchCooperationPolicy.NONE)));

    var result = runUntilGate(solver, current -> assertThat(current.terminateEarly()).isTrue());

    assertBaseline(result);
    assertThat(childCalculators()).hasSize(1);
    assertStopped();
    assertThat(LifecycleForager.INSTANCES)
        .allSatisfy(
            forager -> {
              assertThat(forager.phasesStarted).isZero();
              assertThat(forager.phasesEnded).isZero();
              assertThat(forager.events).containsSubsequence("solvingStarted", "solvingEnded");
            });

    gate = new Gate(Stage.NONE);
    var reused = solver.solve(problem());
    assertThat(reused.score).isEqualTo(recompute(reused));
    assertStopped();
  }

  @Test
  void cancellationDuringInputCloningStopsMaterializationBeforeAnySessionStarts() throws Exception {
    gate = new Gate(Stage.CLONE);
    var solver =
        solver(config(sessions(search(), 32, 2, 1, ResumableSearchCooperationPolicy.NONE)));
    solver.addEventListener(
        event -> {
          if (event.getProducerId() == SolveEventProducerId.SOLVING_STARTED) cloningArmed = true;
        });

    var result = runUntilGate(solver, current -> current.terminateEarly());

    assertBaseline(result);
    assertThat(childCalculators()).isEmpty();
    // A started container may clone the retained archive for final verification. The remaining
    // logical inputs must not be materialized after the cancelled first input clone.
    assertThat(CLONES_AFTER_GATE.get()).isLessThanOrEqualTo(2);
    assertThat(LifecycleForager.INSTANCES).isEmpty();
    assertStopped();
  }

  @Test
  void cancellationDuringPartitionCallbackStartsNoChildrenAndReturnsTheBaseline() throws Exception {
    gate = new Gate(Stage.PARTITION);
    improveFirstPartition = true;
    var solver =
        solver(
            config(partition(sessions(search(), 4, 2, 1, ResumableSearchCooperationPolicy.NONE))));

    var result = runUntilGate(solver, current -> current.terminateEarly());

    assertBaseline(result);
    assertThat(PARTITIONS).hasValue(1);
    assertThat(childCalculators()).isEmpty();
    assertStopped();
  }

  @Test
  void cancelledPartitionInitializationCannotPublishItsUncommittedBetterInput() throws Exception {
    gate = new Gate(Stage.INITIALIZATION);
    improveFirstPartition = true;
    var solver =
        solver(
            config(partition(sessions(search(), 2, 2, 1, ResumableSearchCooperationPolicy.NONE))));
    var phaseEvents = new AtomicInteger();
    solver.addEventListener(
        event -> {
          if (event.getProducerId().phaseIndex().isPresent()) phaseEvents.incrementAndGet();
        });

    var result = runUntilGate(solver, current -> current.terminateEarly());

    assertBaseline(result);
    assertThat(childCalculators()).hasSize(1);
    assertThat(childCalculators().getFirst().solution.entities.getFirst().value.id()).isEqualTo(1);
    assertThat(phaseEvents).hasValue(0);
    assertStopped();
  }

  @Test
  void nestedStartupCancellationClosesStartedLifecycleWithoutStartingFollowingPhases()
      throws Exception {
    gate = new Gate(Stage.PARTITION);
    var innerPartition = partition(search());
    var outer = sessions(search(), 2, 1, 1, ResumableSearchCooperationPolicy.NONE);
    outer.setPhaseConfigList(List.of(innerPartition, search()));
    var solver = solver(config(outer));
    var started = new AtomicInteger();
    var ended = new AtomicInteger();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<StartupSolution> scope) {
            started.incrementAndGet();
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<StartupSolution> scope) {
            ended.incrementAndGet();
          }
        });

    var result = runUntilGate(solver, current -> current.terminateEarly());

    assertBaseline(result);
    assertThat(PARTITIONS).hasValue(1);
    assertThat(childCalculators()).hasSize(2);
    assertThat(started).hasValue(1);
    assertThat(ended).hasValue(1);
    assertThat(LifecycleForager.INSTANCES)
        .isNotEmpty()
        .allSatisfy(
            forager -> {
              assertThat(forager.phasesStarted).isZero();
              assertThat(forager.phasesEnded).isZero();
              assertThat(forager.events.stream().filter("solvingEnded"::equals)).hasSize(1);
            });
    assertStopped();
  }

  @Test
  void problemChangeDuringInitializationWaitsForOldReadersWithoutStartingRemainingSessions()
      throws Exception {
    gate = new Gate(Stage.INITIALIZATION);
    var solver = solver(config(sessions(search(), 8, 2, 1, ResumableSearchCooperationPolicy.NONE)));
    var changes = new AtomicInteger();
    var revisions = new ArrayList<Long>();
    solver.addEventListener(
        event -> {
          if (event.getProducerId() instanceof ResumableSearchEventProducerId id)
            revisions.add(id.modelRevision());
        });

    var result =
        runUntilGate(
            solver,
            current ->
                current.addProblemChange(
                    (working, director) -> {
                      assertThat(childCalculators()).hasSize(1);
                      assertStopped();
                      changes.incrementAndGet();
                      director.changeVariable(
                          working.entities.getFirst(),
                          "value",
                          entity -> entity.value = working.values.getLast());
                    }));

    assertThat(changes).hasValue(1);
    assertThat(result.score).isEqualTo(recompute(result));
    assertThat(revisions).isNotEmpty().allMatch(revision -> revision == 1L);
    assertStopped();
  }

  private static LocalSearchPhaseConfig search() {
    return new LocalSearchPhaseConfig()
        .withMoveSelectorConfig(
            new ChangeMoveSelectorConfig().withSelectionOrder(SelectionOrder.ORIGINAL))
        .withAcceptorConfig(
            new LocalSearchAcceptorConfig()
                .withAcceptorTypeList(List.of(AcceptorType.HILL_CLIMBING)))
        .withForagerConfig(
            new LocalSearchForagerConfig()
                .withForagerClass(LifecycleForager.class)
                .withAcceptedCountLimit(1))
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(2));
  }

  private static PartitionedSearchPhaseConfig partition(PhaseConfig<?> child) {
    return new PartitionedSearchPhaseConfig()
        .withSolutionPartitionerClass(BlockingPartitioner.class)
        .withRunnablePartThreadLimit("1")
        .withPhaseConfigList(List.of(child));
  }

  private static SolverConfig config(PhaseConfig<?> phase) {
    return new SolverConfig()
        .withSolutionClass(StartupSolution.class)
        .withEntityClasses(StartupEntity.class)
        .withScoreDirectorFactory(
            new ScoreDirectorFactoryConfig()
                .withIncrementalScoreCalculatorClass(TrackingCalculator.class))
        .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
        .withRandomSeed(37L)
        .withThreadFactoryClass(TrackingThreadFactory.class)
        .withMoveThreadCount("NONE")
        .withPhases(phase);
  }

  private static DefaultSolver<StartupSolution> solver(SolverConfig config) {
    return (DefaultSolver<StartupSolution>)
        SolverFactory.<StartupSolution>create(config).buildSolver();
  }

  private static StartupSolution runUntilGate(
      Solver<StartupSolution> solver, Consumer<Solver<StartupSolution>> request) throws Exception {
    var result = new AtomicReference<StartupSolution>();
    var failure = new AtomicReference<Throwable>();
    var coordinator =
        new Thread(
            () -> {
              try {
                result.set(solver.solve(problem()));
              } catch (Throwable throwable) {
                failure.set(throwable);
              }
            },
            "startup-test-coordinator");
    coordinator.start();
    try {
      assertThat(gate.entered.await(10, TimeUnit.SECONDS)).isTrue();
      request.accept(solver);
      gate.release.countDown();
      coordinator.join(10_000);
      assertThat(coordinator.isAlive()).isFalse();
      assertThat(failure.get()).isNull();
      return result.get();
    } finally {
      gate.release.countDown();
      coordinator.interrupt();
      coordinator.join(10_000);
    }
  }

  private static List<TrackingCalculator> childCalculators() {
    return CALCULATORS.stream().filter(calculator -> calculator.ownerCreated).toList();
  }

  private static void assertStopped() {
    assertThat(OWNERS).allSatisfy(thread -> assertThat(thread.isAlive()).isFalse());
  }

  private static void assertBaseline(StartupSolution result) {
    assertThat(result).isNotNull();
    assertThat(result.score).isEqualTo(SimpleScore.ZERO).isEqualTo(recompute(result));
    assertThat(result.entities).allSatisfy(entity -> assertThat(entity.value.id()).isZero());
  }

  private static StartupSolution problem() {
    var solution = new StartupSolution();
    solution.values = List.of(new StartupValue(0), new StartupValue(1));
    solution.entities =
        List.of(
            new StartupEntity(0, solution.values.getFirst()),
            new StartupEntity(1, solution.values.getFirst()));
    solution.score = SimpleScore.ZERO;
    return solution;
  }

  private static SimpleScore recompute(StartupSolution solution) {
    return SimpleScore.of(solution.entities.stream().mapToLong(entity -> entity.value.id()).sum());
  }

  private enum Stage {
    NONE,
    CLONE,
    PARTITION,
    INITIALIZATION
  }

  private static final class Gate {
    private final Stage stage;
    private final AtomicBoolean claimed = new AtomicBoolean();
    private final CountDownLatch entered = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    private Gate(Stage stage) {
      this.stage = stage;
    }

    private void enter(Stage candidate) {
      if (stage != candidate || !claimed.compareAndSet(false, true)) return;
      entered.countDown();
      try {
        if (!release.await(10, TimeUnit.SECONDS))
          throw new IllegalStateException("Startup test gate was not released.");
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted startup test callback.", exception);
      }
    }
  }

  public record StartupValue(@PlanningId int id) {}

  @PlanningEntity
  public static class StartupEntity {
    @PlanningId private int id;

    @PlanningVariable(valueRangeProviderRefs = "values")
    private StartupValue value;

    public StartupEntity() {}

    public StartupEntity(int id, StartupValue value) {
      this.id = id;
      this.value = value;
    }

    public int getId() {
      return id;
    }

    public StartupValue getValue() {
      return value;
    }

    public void setValue(StartupValue value) {
      this.value = value;
    }
  }

  @PlanningSolution(solutionCloner = TrackingCloner.class)
  public static class StartupSolution {
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "values")
    private List<StartupValue> values;

    @PlanningEntityCollectionProperty private List<StartupEntity> entities;
    @PlanningScore private SimpleScore score;

    public StartupSolution() {}

    public List<StartupValue> getValues() {
      return values;
    }

    public void setValues(List<StartupValue> values) {
      this.values = values;
    }

    public List<StartupEntity> getEntities() {
      return entities;
    }

    public void setEntities(List<StartupEntity> entities) {
      this.entities = entities;
    }

    public SimpleScore getScore() {
      return score;
    }

    public void setScore(SimpleScore score) {
      this.score = score;
    }
  }

  public static final class TrackingCloner implements SolutionCloner<StartupSolution> {
    @Override
    public StartupSolution cloneSolution(StartupSolution original) {
      if (gate.stage == Stage.CLONE && cloningArmed) {
        if (gate.claimed.get()) CLONES_AFTER_GATE.incrementAndGet();
        gate.enter(Stage.CLONE);
      }
      var clone = new StartupSolution();
      clone.values = original.values;
      clone.entities =
          original.entities.stream()
              .map(entity -> new StartupEntity(entity.id, entity.value))
              .toList();
      clone.score = original.score;
      return clone;
    }
  }

  public static final class TrackingCalculator
      implements IncrementalScoreCalculator<StartupSolution, SimpleScore> {
    private final boolean ownerCreated = OWNERS.contains(Thread.currentThread());
    private StartupSolution solution;

    public TrackingCalculator() {
      CALCULATORS.add(this);
    }

    @Override
    public void resetWorkingSolution(StartupSolution workingSolution) {
      if (ownerCreated) gate.enter(Stage.INITIALIZATION);
      solution = workingSolution;
    }

    @Override
    public void beforeVariableChanged(Object entity, String variableName) {}

    @Override
    public void afterVariableChanged(Object entity, String variableName) {}

    @Override
    public SimpleScore calculateScore() {
      return recompute(solution);
    }
  }

  public static final class BlockingPartitioner implements SolutionPartitioner<StartupSolution> {
    @Override
    public List<StartupSolution> splitWorkingSolution(
        ScoreDirector<StartupSolution> director, Integer limit) {
      PARTITIONS.incrementAndGet();
      gate.enter(Stage.PARTITION);
      var inner = (InnerScoreDirector<StartupSolution, ?>) director;
      var first = inner.cloneWorkingSolution();
      var second = inner.cloneWorkingSolution();
      first.entities = List.of(first.entities.getFirst());
      second.entities = List.of(second.entities.getLast());
      if (improveFirstPartition) first.entities.getFirst().value = first.values.getLast();
      first.score = null;
      second.score = null;
      return List.of(first, second);
    }
  }

  public static final class LifecycleForager extends AbstractLocalSearchForager<StartupSolution> {
    private static final ConcurrentLinkedQueue<LifecycleForager> INSTANCES =
        new ConcurrentLinkedQueue<>();
    private final LocalSearchForager<StartupSolution> delegate;
    private final List<String> events = new ArrayList<>();
    private int phasesStarted;
    private int phasesEnded;

    public LifecycleForager(LocalSearchForagerConfig config) {
      var delegateConfig = config.copyConfig();
      delegateConfig.setForagerClass(null);
      delegate = LocalSearchForagerFactory.<StartupSolution>create(delegateConfig).buildForager();
      INSTANCES.add(this);
    }

    @Override
    public void solvingStarted(SolverScope<StartupSolution> scope) {
      events.add("solvingStarted");
      delegate.solvingStarted(scope);
    }

    @Override
    public void solvingEnded(SolverScope<StartupSolution> scope) {
      events.add("solvingEnded");
      delegate.solvingEnded(scope);
    }

    @Override
    public void phaseStarted(LocalSearchPhaseScope<StartupSolution> scope) {
      phasesStarted++;
      delegate.phaseStarted(scope);
    }

    @Override
    public void phaseEnded(LocalSearchPhaseScope<StartupSolution> scope) {
      phasesEnded++;
      delegate.phaseEnded(scope);
    }

    @Override
    public void stepStarted(LocalSearchStepScope<StartupSolution> scope) {
      delegate.stepStarted(scope);
    }

    @Override
    public void stepEnded(LocalSearchStepScope<StartupSolution> scope) {
      delegate.stepEnded(scope);
    }

    @Override
    public boolean supportsNeverEndingMoveSelector() {
      return delegate.supportsNeverEndingMoveSelector();
    }

    @Override
    public void addMove(LocalSearchMoveScope<StartupSolution> scope) {
      delegate.addMove(scope);
    }

    @Override
    public boolean isQuitEarly() {
      return delegate.isQuitEarly();
    }

    @Override
    public LocalSearchMoveScope<StartupSolution> pickMove(
        LocalSearchStepScope<StartupSolution> scope) {
      return delegate.pickMove(scope);
    }
  }

  public static final class TrackingThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(Runnable runnable) {
      var thread = new Thread(runnable, "startup-test-owner-" + OWNERS.size());
      OWNERS.add(thread);
      return thread;
    }
  }
}
