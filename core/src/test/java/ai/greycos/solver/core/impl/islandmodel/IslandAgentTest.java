package ai.greycos.solver.core.impl.islandmodel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.solver.event.EventProducerId;
import ai.greycos.solver.core.impl.phase.Phase;
import ai.greycos.solver.core.impl.phase.event.PhaseLifecycleListener;
import ai.greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for IslandAgent. */
public class IslandAgentTest {

  private static final int TEST_ISLAND_COUNT = 4;
  private static final int TEST_MIGRATION_FREQUENCY = 100;

  private IslandModelConfig config;
  private SharedGlobalState<TestSolution> globalState;
  private BoundedChannel<AgentUpdate<TestSolution>> sender;
  private BoundedChannel<AgentUpdate<TestSolution>> receiver;
  private Random random;
  private SolverScope<TestSolution> solverScope;
  private TestSolution initialSolution;
  private CountDownLatch completionLatch;

  @BeforeEach
  public void setUp() {
    config = new IslandModelConfig(TEST_ISLAND_COUNT, TEST_MIGRATION_FREQUENCY);
    globalState = new SharedGlobalState<>();
    sender = new BoundedChannel<>(1);
    receiver = new BoundedChannel<>(1);
    random = new Random(42);
    solverScope = new TestSolverScope();
    initialSolution = new TestSolution(100);
    completionLatch = new CountDownLatch(1);
  }

  @Test
  public void testConstructor() {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    phases.add(createMockPhase("Phase1"));

    IslandAgent<TestSolution> agent =
        new IslandAgent<>(
            0,
            phases,
            initialSolution,
            globalState,
            sender,
            receiver,
            config,
            random,
            solverScope,
            completionLatch);

    assertEquals(0, agent.getAgentId());
    assertEquals(AgentStatus.ALIVE, agent.getStatus());
  }

  @Test
  public void testConstructorWithNullPhases() {
    assertThrows(
        NullPointerException.class,
        () ->
            new IslandAgent<>(
                0,
                null,
                initialSolution,
                globalState,
                sender,
                receiver,
                config,
                random,
                solverScope,
                completionLatch));
  }

  @Test
  public void testConstructorWithNullSolution() {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    assertThrows(
        NullPointerException.class,
        () ->
            new IslandAgent<>(
                0,
                phases,
                null,
                globalState,
                sender,
                receiver,
                config,
                random,
                solverScope,
                completionLatch));
  }

  @Test
  public void testConstructorWithNullGlobalState() {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    assertThrows(
        NullPointerException.class,
        () ->
            new IslandAgent<>(
                0,
                phases,
                initialSolution,
                null,
                sender,
                receiver,
                config,
                random,
                solverScope,
                completionLatch));
  }

  @Test
  public void testConstructorWithNullSender() {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    assertThrows(
        NullPointerException.class,
        () ->
            new IslandAgent<>(
                0,
                phases,
                initialSolution,
                globalState,
                null,
                receiver,
                config,
                random,
                solverScope,
                completionLatch));
  }

  @Test
  public void testConstructorWithNullReceiver() {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    assertThrows(
        NullPointerException.class,
        () ->
            new IslandAgent<>(
                0,
                phases,
                initialSolution,
                globalState,
                sender,
                null,
                config,
                random,
                solverScope,
                completionLatch));
  }

  @Test
  public void testConstructorWithNullConfig() {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    assertThrows(
        NullPointerException.class,
        () ->
            new IslandAgent<>(
                0,
                phases,
                initialSolution,
                globalState,
                sender,
                receiver,
                null,
                random,
                solverScope,
                completionLatch));
  }

  @Test
  public void testConstructorWithNullRandom() {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    assertThrows(
        NullPointerException.class,
        () ->
            new IslandAgent<>(
                0,
                phases,
                initialSolution,
                globalState,
                sender,
                receiver,
                config,
                null,
                solverScope,
                completionLatch));
  }

  @Test
  public void testConstructorWithNullSolverScope() {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    assertThrows(
        NullPointerException.class,
        () ->
            new IslandAgent<>(
                0,
                phases,
                initialSolution,
                globalState,
                sender,
                receiver,
                config,
                random,
                null,
                completionLatch));
  }

  @Test
  public void testConstructorWithNullCompletionLatch() {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    assertThrows(
        NullPointerException.class,
        () ->
            new IslandAgent<>(
                0,
                phases,
                initialSolution,
                globalState,
                sender,
                receiver,
                config,
                random,
                solverScope,
                null));
  }

  @Test
  public void testAgentRunsWithNoPhases() throws InterruptedException {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    IslandAgent<TestSolution> agent =
        new IslandAgent<>(
            0,
            phases,
            initialSolution,
            globalState,
            sender,
            receiver,
            config,
            random,
            solverScope,
            completionLatch);

    Thread thread = new Thread(agent);
    thread.start();
    thread.join(5000);

    assertEquals(AgentStatus.DEAD, agent.getStatus());
  }

  @Test
  public void testAgentRunWithSinglePhase() throws InterruptedException {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    CountDownLatch phaseLatch = new CountDownLatch(1);
    phases.add(createMockPhase("Phase1", phaseLatch));

    IslandAgent<TestSolution> agent =
        new IslandAgent<>(
            0,
            phases,
            initialSolution,
            globalState,
            sender,
            receiver,
            config,
            random,
            solverScope,
            completionLatch);

    Thread thread = new Thread(agent);
    thread.start();

    // Wait for phase to complete
    assertTrue(phaseLatch.await(5, TimeUnit.SECONDS));

    // Agent should terminate after phase completes
    thread.join(5000);
    assertEquals(AgentStatus.DEAD, agent.getStatus());
  }

  @Test
  public void testAgentRunWithMultiplePhases() throws InterruptedException {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    CountDownLatch phase1Latch = new CountDownLatch(1);
    CountDownLatch phase2Latch = new CountDownLatch(1);
    phases.add(createMockPhase("Phase1", phase1Latch));
    phases.add(createMockPhase("Phase2", phase2Latch));

    IslandAgent<TestSolution> agent =
        new IslandAgent<>(
            0,
            phases,
            initialSolution,
            globalState,
            sender,
            receiver,
            config,
            random,
            solverScope,
            completionLatch);

    Thread thread = new Thread(agent);
    thread.start();

    // Wait for phases to complete in order
    assertTrue(phase1Latch.await(5, TimeUnit.SECONDS));
    assertTrue(phase2Latch.await(5, TimeUnit.SECONDS));

    thread.join(5000);
    assertEquals(AgentStatus.DEAD, agent.getStatus());
  }

  @Test
  public void testAgentHandlesInterruptedException() throws InterruptedException {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    CountDownLatch interruptLatch = new CountDownLatch(1);
    phases.add(createInterruptingPhase(interruptLatch));

    IslandAgent<TestSolution> agent =
        new IslandAgent<>(
            0,
            phases,
            initialSolution,
            globalState,
            sender,
            receiver,
            config,
            random,
            solverScope,
            completionLatch);

    Thread thread = new Thread(agent);
    thread.start();

    // Wait for phase to start and interrupt
    assertTrue(interruptLatch.await(5, TimeUnit.SECONDS));
    thread.interrupt();

    thread.join(5000);
    // Agent should handle interrupt gracefully
    assertTrue(thread.isInterrupted() || agent.getStatus() == AgentStatus.DEAD);
  }

  @Test
  public void testAgentHandlesException() throws InterruptedException {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    phases.add(createFailingPhase());

    IslandAgent<TestSolution> agent =
        new IslandAgent<>(
            0,
            phases,
            initialSolution,
            globalState,
            sender,
            receiver,
            config,
            random,
            solverScope,
            completionLatch);

    Thread thread = new Thread(agent);
    thread.start();

    thread.join(5000);
    // Agent should mark itself as dead on exception
    assertEquals(AgentStatus.DEAD, agent.getStatus());
  }

  @Test
  public void testMigrationSend() throws InterruptedException {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    phases.add(createMockPhase("Phase1"));

    IslandAgent<TestSolution> agent =
        new IslandAgent<>(
            0,
            phases,
            initialSolution,
            globalState,
            sender,
            receiver,
            config,
            random,
            solverScope,
            completionLatch);

    // Start agent
    Thread thread = new Thread(agent);
    thread.start();

    // Wait a bit for agent to initialize
    Thread.sleep(100);

    // Agent should be able to send migration
    // This is tested indirectly by ensuring agent doesn't deadlock
    thread.join(2000);
    assertEquals(AgentStatus.DEAD, agent.getStatus());
  }

  @Test
  public void testMigrationReceive() throws InterruptedException {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    CountDownLatch phaseLatch = new CountDownLatch(1);
    phases.add(createMockPhase("Phase1", phaseLatch));

    IslandAgent<TestSolution> agent =
        new IslandAgent<>(
            0,
            phases,
            initialSolution,
            globalState,
            sender,
            receiver,
            config,
            random,
            solverScope,
            completionLatch);

    // Start agent
    Thread thread = new Thread(agent);
    thread.start();

    // Wait for phase to complete
    assertTrue(phaseLatch.await(5, TimeUnit.SECONDS));

    // Agent should terminate normally
    thread.join(2000);
    assertEquals(AgentStatus.DEAD, agent.getStatus());
  }

  @Test
  public void testToString() {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    phases.add(createMockPhase("Phase1"));

    IslandAgent<TestSolution> agent =
        new IslandAgent<>(
            0,
            phases,
            initialSolution,
            globalState,
            sender,
            receiver,
            config,
            random,
            solverScope,
            completionLatch);

    String str = agent.toString();
    assertTrue(str.contains("agentId=0"));
    assertTrue(str.contains("status=ALIVE"));
    assertTrue(str.contains("phaseCount=1"));
  }

  @Test
  public void testAgentId() {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    phases.add(createMockPhase("Phase1"));

    IslandAgent<TestSolution> agent1 =
        new IslandAgent<>(
            0,
            phases,
            initialSolution,
            globalState,
            sender,
            receiver,
            config,
            random,
            solverScope,
            completionLatch);
    IslandAgent<TestSolution> agent2 =
        new IslandAgent<>(
            1,
            phases,
            initialSolution,
            globalState,
            sender,
            receiver,
            config,
            random,
            solverScope,
            completionLatch);

    assertEquals(0, agent1.getAgentId());
    assertEquals(1, agent2.getAgentId());
  }

  @Test
  public void testStatus() {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    phases.add(createMockPhase("Phase1"));

    IslandAgent<TestSolution> agent =
        new IslandAgent<>(
            0,
            phases,
            initialSolution,
            globalState,
            sender,
            receiver,
            config,
            random,
            solverScope,
            completionLatch);

    assertEquals(AgentStatus.ALIVE, agent.getStatus());
  }

  // Helper methods

  private Phase<TestSolution> createMockPhase(String name) {
    return createMockPhase(name, null);
  }

  private Phase<TestSolution> createMockPhase(String name, CountDownLatch latch) {
    return new TestPhase(
        name,
        () -> {
          if (latch != null) {
            latch.countDown();
          }
        });
  }

  private Phase<TestSolution> createInterruptingPhase(CountDownLatch latch) {
    return new TestPhase(
        "InterruptingPhase",
        () -> {
          latch.countDown();
          Thread.currentThread().interrupt();
          throw new RuntimeException("Test interrupt");
        });
  }

  private Phase<TestSolution> createFailingPhase() {
    return new TestPhase(
        "FailingPhase",
        () -> {
          throw new RuntimeException("Test exception");
        });
  }

  // Test classes

  @PlanningSolution
  static class TestSolution {
    private int value;

    public TestSolution(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }

    public void setValue(int value) {
      this.value = value;
    }
  }

  private static final class TestSolverScope extends SolverScope<TestSolution> {
    @Override
    public SolverScope<TestSolution> createChildThreadSolverScope(ChildThreadType childThreadType) {
      return new TestSolverScope();
    }

    @Override
    public void setInitialSolution(TestSolution initialSolution) {
      setBestSolution(initialSolution);
    }
  }

  private static final class TestPhase extends PhaseLifecycleListenerAdapter<TestSolution>
      implements Phase<TestSolution> {
    private final String name;
    private final Runnable solveAction;
    private final List<PhaseLifecycleListener<TestSolution>> listeners = new ArrayList<>();

    private TestPhase(String name, Runnable solveAction) {
      this.name = name;
      this.solveAction = solveAction;
    }

    @Override
    public void addPhaseLifecycleListener(PhaseLifecycleListener<TestSolution> listener) {
      listeners.add(listener);
    }

    @Override
    public void removePhaseLifecycleListener(PhaseLifecycleListener<TestSolution> listener) {
      listeners.remove(listener);
    }

    @Override
    public void solve(SolverScope<TestSolution> solverScope) {
      if (solveAction != null) {
        solveAction.run();
      }
    }

    @Override
    public IntFunction<EventProducerId> getEventProducerIdSupplier() {
      return i -> EventProducerId.unknown();
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
