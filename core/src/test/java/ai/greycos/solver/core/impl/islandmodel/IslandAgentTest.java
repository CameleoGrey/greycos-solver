package ai.greycos.solver.core.impl.islandmodel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.phase.Phase;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.impl.solver.thread.ChildThreadType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Unit tests for IslandAgent. */
public class IslandAgentTest {

  private static final int TEST_ISLAND_COUNT = 4;
  private static final int TEST_MIGRATION_FREQUENCY = 100;
  private static final double TEST_MIGRATION_RATE = 0.1;

  private IslandModelConfig config;
  private SharedGlobalState<TestSolution> globalState;
  private BoundedChannel<AgentUpdate<TestSolution>> sender;
  private BoundedChannel<AgentUpdate<TestSolution>> receiver;
  private Random random;
  private SolverScope<TestSolution> solverScope;
  private TestSolution initialSolution;

  @BeforeEach
  public void setUp() {
    config =
        new IslandModelConfig(TEST_ISLAND_COUNT, TEST_MIGRATION_RATE, TEST_MIGRATION_FREQUENCY);
    globalState = new SharedGlobalState<>();
    sender = new BoundedChannel<>(1);
    receiver = new BoundedChannel<>(1);
    random = new Random(42);
    solverScope = createMockSolverScope();
    initialSolution = new TestSolution(100);
  }

  @Test
  public void testConstructor() {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    phases.add(createMockPhase("Phase1"));

    IslandAgent<TestSolution> agent =
        new IslandAgent<>(
            0, phases, initialSolution, globalState, sender, receiver, config, random, solverScope);

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
                solverScope));
  }

  @Test
  public void testConstructorWithNullSolution() {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    assertThrows(
        NullPointerException.class,
        () ->
            new IslandAgent<>(
                0, phases, null, globalState, sender, receiver, config, random, solverScope));
  }

  @Test
  public void testConstructorWithNullGlobalState() {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    assertThrows(
        NullPointerException.class,
        () ->
            new IslandAgent<>(
                0, phases, initialSolution, null, sender, receiver, config, random, solverScope));
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
                solverScope));
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
                solverScope));
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
                solverScope));
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
                solverScope));
  }

  @Test
  public void testConstructorWithNullSolverScope() {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    assertThrows(
        NullPointerException.class,
        () ->
            new IslandAgent<>(
                0, phases, initialSolution, globalState, sender, receiver, config, random, null));
  }

  @Test
  public void testAgentRunsWithNoPhases() throws InterruptedException {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    IslandAgent<TestSolution> agent =
        new IslandAgent<>(
            0, phases, initialSolution, globalState, sender, receiver, config, random, solverScope);

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
            0, phases, initialSolution, globalState, sender, receiver, config, random, solverScope);

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
            0, phases, initialSolution, globalState, sender, receiver, config, random, solverScope);

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
            0, phases, initialSolution, globalState, sender, receiver, config, random, solverScope);

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
            0, phases, initialSolution, globalState, sender, receiver, config, random, solverScope);

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
            0, phases, initialSolution, globalState, sender, receiver, config, random, solverScope);

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
            0, phases, initialSolution, globalState, sender, receiver, config, random, solverScope);

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
            0, phases, initialSolution, globalState, sender, receiver, config, random, solverScope);

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
            0, phases, initialSolution, globalState, sender, receiver, config, random, solverScope);
    IslandAgent<TestSolution> agent2 =
        new IslandAgent<>(
            1, phases, initialSolution, globalState, sender, receiver, config, random, solverScope);

    assertEquals(0, agent1.getAgentId());
    assertEquals(1, agent2.getAgentId());
  }

  @Test
  public void testStatus() {
    List<Phase<TestSolution>> phases = new ArrayList<>();
    phases.add(createMockPhase("Phase1"));

    IslandAgent<TestSolution> agent =
        new IslandAgent<>(
            0, phases, initialSolution, globalState, sender, receiver, config, random, solverScope);

    assertEquals(AgentStatus.ALIVE, agent.getStatus());
  }

  // Helper methods

  @SuppressWarnings("unchecked")
  private SolverScope<TestSolution> createMockSolverScope() {
    SolverScope<TestSolution> mockScope = Mockito.mock(SolverScope.class);
    SolverScope<TestSolution> childScope = Mockito.mock(SolverScope.class);

    Mockito.when(mockScope.createChildThreadSolverScope(ChildThreadType.MOVE_THREAD))
        .thenReturn(childScope);
    Mockito.when(mockScope.getBestSolution()).thenReturn(initialSolution);

    return mockScope;
  }

  private Phase<TestSolution> createMockPhase(String name) {
    return createMockPhase(name, null);
  }

  @SuppressWarnings("unchecked")
  private Phase<TestSolution> createMockPhase(String name, CountDownLatch latch) {
    Phase<TestSolution> mockPhase = Mockito.mock(Phase.class);
    Mockito.doAnswer(
            invocation -> {
              if (latch != null) {
                latch.countDown();
              }
              return null;
            })
        .when(mockPhase)
        .solve(Mockito.any());
    Mockito.when(mockPhase.toString()).thenReturn(name);
    return mockPhase;
  }

  @SuppressWarnings("unchecked")
  private Phase<TestSolution> createInterruptingPhase(CountDownLatch latch) {
    Phase<TestSolution> mockPhase = Mockito.mock(Phase.class);
    Mockito.doAnswer(
            invocation -> {
              latch.countDown();
              Thread.currentThread().interrupt();
              throw new InterruptedException("Test interrupt");
            })
        .when(mockPhase)
        .solve(Mockito.any());
    return mockPhase;
  }

  @SuppressWarnings("unchecked")
  private Phase<TestSolution> createFailingPhase() {
    Phase<TestSolution> mockPhase = Mockito.mock(Phase.class);
    Mockito.doAnswer(
            invocation -> {
              throw new RuntimeException("Test exception");
            })
        .when(mockPhase)
        .solve(Mockito.any());
    return mockPhase;
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

  static class TestScore implements Score<TestScore> {
    private final int score;

    public TestScore(int score) {
      this.score = score;
    }

    @Override
    public int compareTo(TestScore other) {
      return Integer.compare(this.score, other.score);
    }

    @Override
    public TestScore add(TestScore addend) {
      return new TestScore(this.score + addend.score);
    }

    @Override
    public TestScore subtract(TestScore subtrahend) {
      return new TestScore(this.score - subtrahend.score);
    }

    @Override
    public TestScore multiply(double multiplicand) {
      return new TestScore((int) (this.score * multiplicand));
    }

    @Override
    public TestScore divide(double divisor) {
      return new TestScore((int) (this.score / divisor));
    }

    @Override
    public TestScore power(double exponent) {
      return new TestScore((int) Math.pow(this.score, exponent));
    }

    @Override
    public TestScore abs() {
      return new TestScore(Math.abs(this.score));
    }

    @Override
    public TestScore zero() {
      return new TestScore(0);
    }

    @Override
    public Number[] toLevelNumbers() {
      return new Number[] {score};
    }

    @Override
    public boolean isFeasible() {
      return true;
    }

    @Override
    public String toShortString() {
      return String.valueOf(score);
    }

    @Override
    public String toString() {
      return String.valueOf(score);
    }
  }
}
