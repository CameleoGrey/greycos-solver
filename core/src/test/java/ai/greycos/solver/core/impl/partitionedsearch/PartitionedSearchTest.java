package ai.greycos.solver.core.impl.partitionedsearch;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.domain.variable.PlanningVariable;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.partitionedsearch.partitioner.RoundRobinPartitioner;
import ai.greycos.solver.core.impl.partitionedsearch.partitioner.SolutionPartitioner;
import ai.greycos.solver.core.impl.partitionedsearch.queue.PartitionChangedEvent;
import ai.greycos.solver.core.impl.partitionedsearch.queue.PartitionQueue;
import ai.greycos.solver.core.impl.partitionedsearch.scope.PartitionChangeMove;
import ai.greycos.solver.core.impl.partitionedsearch.scope.PartitionedSearchPhaseScope;
import ai.greycos.solver.core.impl.partitionedsearch.scope.PartitionedSearchStepScope;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Comprehensive test for partitioned search functionality. */
public class PartitionedSearchTest {

  @Test
  void partitionedSearchPhaseConfig_buildsCorrectly() {
    PartitionedSearchPhaseConfig config = new PartitionedSearchPhaseConfig();
    config.setSolutionPartitionerClass(
        (Class<? extends SolutionPartitioner<?>>) (Class<?>) RoundRobinPartitioner.class);
    config.setRunnablePartThreadLimit("2");

    assertNotNull(config);
    assertEquals(RoundRobinPartitioner.class, config.getSolutionPartitionerClass());
    assertEquals("2", config.getRunnablePartThreadLimit());
  }

  @Test
  void partitionQueue_handlesEvents() throws InterruptedException {
    PartitionQueue<String> queue = new PartitionQueue<>(2);

    // Test move event
    PartitionChangeMove<String> move = Mockito.mock(PartitionChangeMove.class);
    queue.addMove(0, move);

    // Test finish event for partition 0
    queue.addFinish(0, 100L);

    // Only 1 of 2 partitions finished, so not all finished yet
    assertFalse(queue.areAllPartitionsFinished());

    // Test finish event for partition 1
    queue.addFinish(1, 200L);

    // Both partitions finished
    assertTrue(queue.areAllPartitionsFinished());
    assertEquals(300L, queue.getPartsCalculationCount()); // 100 + 200

    // Test that iterator returns move before throwing exception
    List<PartitionChangeMove<String>> moves = new ArrayList<>();
    try {
      for (PartitionChangeMove<String> m : queue) {
        moves.add(m);
      }
    } catch (IllegalStateException e) {
      // Expected: iterator throws when encountering exception event
      assertTrue(e.getMessage().contains("Partition solver failed"));
    }

    assertEquals(1, moves.size());
    assertEquals(move, moves.get(0));
  }

  @Test
  void partitionChangedEvent_types() {
    PartitionChangeMove<String> move = Mockito.mock(PartitionChangeMove.class);

    // Test MOVE event
    PartitionChangedEvent<String> moveEvent = new PartitionChangedEvent<>(0, 0, move);
    assertEquals(PartitionChangedEvent.PartitionChangedEventType.MOVE, moveEvent.getType());
    assertEquals(move, moveEvent.getMove());

    // Test FINISHED event
    PartitionChangedEvent<String> finishEvent = new PartitionChangedEvent<>(0, 1, 100L);
    assertEquals(PartitionChangedEvent.PartitionChangedEventType.FINISHED, finishEvent.getType());
    assertEquals(100L, finishEvent.getPartCalculationCount());

    // Test EXCEPTION_THROWN event
    Exception testException = new RuntimeException("Test");
    PartitionChangedEvent<String> exceptionEvent = new PartitionChangedEvent<>(0, 2, testException);
    assertEquals(
        PartitionChangedEvent.PartitionChangedEventType.EXCEPTION_THROWN, exceptionEvent.getType());
    assertEquals(testException, exceptionEvent.getThrowable());
  }

  @Test
  void roundRobinPartitioner_splitsEntities() {
    // Create mock solution with entities
    TestSolution solution = new TestSolution();
    solution.setEntities(
        Arrays.asList(
            new TestEntity("A"),
            new TestEntity("B"),
            new TestEntity("C"),
            new TestEntity("D"),
            new TestEntity("E"),
            new TestEntity("F")));
    // Add some values for the planning variable
    solution.setValues(Arrays.asList(new TestValue("V1"), new TestValue("V2")));

    InnerScoreDirector<TestSolution, ?> scoreDirector = Mockito.mock(InnerScoreDirector.class);
    Mockito.when(scoreDirector.getWorkingSolution()).thenReturn(solution);

    // Mock getSolutionDescriptor() to return a proper descriptor
    SolutionDescriptor<TestSolution> solutionDescriptor =
        SolutionDescriptor.buildSolutionDescriptor(TestSolution.class, TestEntity.class);
    Mockito.when(scoreDirector.getSolutionDescriptor()).thenReturn(solutionDescriptor);

    // Mock cloneSolution() to return cloned solutions
    Mockito.when(scoreDirector.cloneSolution(Mockito.any()))
        .thenAnswer(
            invocation -> {
              TestSolution original = invocation.getArgument(0);
              TestSolution clone = new TestSolution();
              clone.setEntities(new ArrayList<>(original.getEntities()));
              clone.setValues(new ArrayList<>(original.getValues()));
              return clone;
            });

    RoundRobinPartitioner<TestSolution> partitioner = new RoundRobinPartitioner<>(3);
    List<TestSolution> partitions = partitioner.splitWorkingSolution(scoreDirector, null);

    assertEquals(3, partitions.size());

    // Each partition should have all 6 entities (the partitioner keeps all entities but unassigns
    // some)
    for (TestSolution partition : partitions) {
      assertEquals(6, partition.getEntities().size(), "Each partition should have all 6 entities");
    }

    // Total entities across all partitions should be 18 (6 entities × 3 partitions)
    long totalEntities = partitions.stream().flatMap(p -> p.getEntities().stream()).count();
    assertEquals(
        18,
        totalEntities,
        "Total entities across all partitions should be 18 (6 entities × 3 partitions)");
  }

  @Test
  void phaseScope_managesState() {
    SolverScope<String> scope = Mockito.mock(SolverScope.class);
    PartitionedSearchPhaseScope<String> phaseScope = new PartitionedSearchPhaseScope<>(scope, 0);

    PartitionedSearchStepScope<String> stepScope = new PartitionedSearchStepScope<>(phaseScope);

    phaseScope.setLastCompletedStepScope(stepScope);
    assertEquals(stepScope, phaseScope.getLastCompletedStepScope());
  }

  @Test
  void configuration_withMethods() {
    PartitionedSearchPhaseConfig config =
        new PartitionedSearchPhaseConfig()
            .withSolutionPartitionerClass(
                (Class<? extends SolutionPartitioner<?>>) (Class<?>) RoundRobinPartitioner.class)
            .withRunnablePartThreadLimit("3")
            .withPhaseConfigs(new ConstructionHeuristicPhaseConfig(), new LocalSearchPhaseConfig());

    assertEquals(RoundRobinPartitioner.class, config.getSolutionPartitionerClass());
    assertEquals("3", config.getRunnablePartThreadLimit());
    assertEquals(2, config.getPhaseConfigList().size());
  }

  // Test data classes
  @PlanningSolution
  public static class TestSolution {
    private List<TestEntity> entities;
    private List<TestValue> values;
    private SimpleScore score;

    public TestSolution() {
      this.entities = new ArrayList<>();
      this.values = new ArrayList<>();
      this.score = SimpleScore.of(0);
    }

    @PlanningEntityCollectionProperty
    public List<TestEntity> getEntities() {
      return entities;
    }

    public void setEntities(List<TestEntity> entities) {
      this.entities = entities;
    }

    @ValueRangeProvider(id = "valueRange")
    @ProblemFactCollectionProperty
    public List<TestValue> getValues() {
      return values;
    }

    public void setValues(List<TestValue> values) {
      this.values = values;
    }

    @PlanningScore
    public SimpleScore getScore() {
      return score;
    }

    public void setScore(SimpleScore score) {
      this.score = score;
    }
  }

  @PlanningEntity
  public static class TestEntity {
    private String id;
    private TestValue value;

    public TestEntity(String id) {
      this.id = id;
    }

    @PlanningVariable(valueRangeProviderRefs = "valueRange")
    public TestValue getValue() {
      return value;
    }

    public void setValue(TestValue value) {
      this.value = value;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }
  }

  // Simple value class for planning variable
  public static class TestValue {
    private String id;

    public TestValue(String id) {
      this.id = id;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TestValue testValue = (TestValue) o;
      return id != null ? id.equals(testValue.id) : testValue.id == null;
    }

    @Override
    public int hashCode() {
      return id != null ? id.hashCode() : 0;
    }
  }
}
