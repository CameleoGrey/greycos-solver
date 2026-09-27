package greycos.solver.core.impl.partitionedsearch.partitioner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningEntityProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.list.TestdataListSolution;

import org.junit.jupiter.api.Test;

/** Tests for {@link RoundRobinPartitioner}. */
class RoundRobinPartitionerTest {

  @Test
  void constructorValidatesPartCount() {
    assertThatThrownBy(() -> new RoundRobinPartitioner<Object>(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Partition count must be at least 1");

    assertThatThrownBy(() -> new RoundRobinPartitioner<Object>(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Partition count must be at least 1");
  }

  @Test
  void getPartCount() {
    var partitioner = new RoundRobinPartitioner<Object>(3);
    assertThat(partitioner.getPartCount()).isEqualTo(3);
  }

  @Test
  void partitionsCoverEntitiesExactlyOnceAndKeepIndependentClones() {
    var problem = TestdataSolution.generateSolution(2, 7);
    var partitions =
        new RoundRobinPartitioner<TestdataSolution>(3)
            .splitWorkingSolution(
                director(TestdataSolution.buildSolutionDescriptor(), problem), null);

    assertThat(partitions).extracting(part -> part.getEntityList().size()).containsExactly(3, 2, 2);
    for (int partIndex = 0; partIndex < partitions.size(); partIndex++) {
      var partition = partitions.get(partIndex);
      int currentPartIndex = partIndex;
      assertThat(partition.getEntityList())
          .extracting(TestdataEntity::getCode)
          .containsExactlyElementsOf(
              IntStream.range(0, 7)
                  .filter(index -> index % 3 == currentPartIndex)
                  .mapToObj(index -> problem.getEntityList().get(index).getCode())
                  .toList());
      assertThat(partition.getValueList()).containsExactlyElementsOf(problem.getValueList());
      for (var entity : partition.getEntityList()) {
        var original =
            problem.getEntityList().stream()
                .filter(candidate -> candidate.getCode().equals(entity.getCode()))
                .findFirst()
                .orElseThrow();
        assertThat(entity).isNotSameAs(original);
        assertThat(entity.getValue()).isSameAs(original.getValue());
      }
    }
    partitions.getFirst().getEntityList().getFirst().setValue(null);
    assertThat(problem.getEntityList())
        .hasSize(7)
        .allSatisfy(entity -> assertThat(entity.getValue()).isNotNull());
  }

  @Test
  void runnableThreadLimitCapsPartitionCount() {
    var problem = TestdataSolution.generateSolution(2, 7);
    var partitions =
        new RoundRobinPartitioner<TestdataSolution>(3)
            .splitWorkingSolution(director(TestdataSolution.buildSolutionDescriptor(), problem), 2);
    assertThat(partitions).extracting(part -> part.getEntityList().size()).containsExactly(4, 3);
  }

  @Test
  void preservesRequestedCountForSmallAndEmptyProblems() {
    for (int entityCount : new int[] {0, 1}) {
      var problem = TestdataSolution.generateSolution(2, entityCount);
      var partitions =
          new RoundRobinPartitioner<TestdataSolution>(3)
              .splitWorkingSolution(
                  director(TestdataSolution.buildSolutionDescriptor(), problem), null);
      assertThat(partitions).hasSize(3);
      assertThat(partitions.stream().mapToInt(part -> part.getEntityList().size()).sum())
          .isEqualTo(entityCount);
    }
  }

  @Test
  void rejectsNonpositiveRunnableThreadLimit() {
    var problem = TestdataSolution.generateSolution();
    for (int limit : new int[] {0, -1}) {
      assertThatThrownBy(
              () ->
                  new RoundRobinPartitioner<TestdataSolution>(3)
                      .splitWorkingSolution(
                          director(TestdataSolution.buildSolutionDescriptor(), problem), limit))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("runnablePartThreadLimit", "at least 1");
    }
  }

  @Test
  void rejectsListVariablesWithPartitionerAdvice() {
    var problem = TestdataListSolution.generateUninitializedSolution(4, 2);
    assertThatThrownBy(
            () ->
                new RoundRobinPartitioner<TestdataListSolution>(2)
                    .splitWorkingSolution(
                        director(TestdataListSolution.buildSolutionDescriptor(), problem), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "TestdataListEntity.valueList", "list variable", "cotwin-specific SolutionPartitioner");
  }

  @Test
  void rejectsEntityValuedRangesIncludingChains() {
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(LinkedSolution.class, LinkedEntity.class);
    assertThatThrownBy(
            () ->
                new RoundRobinPartitioner<LinkedSolution>(2)
                    .splitWorkingSolution(director(descriptor, new LinkedSolution()), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("LinkedEntity.previous", "entity-valued range", "chains");
  }

  @Test
  void rejectsMissingAndDuplicatePlanningIds() {
    var problem = TestdataSolution.generateSolution(2, 2);
    problem.getEntityList().getFirst().setCode(null);
    assertThatThrownBy(
            () ->
                new RoundRobinPartitioner<TestdataSolution>(2)
                    .splitWorkingSolution(
                        director(TestdataSolution.buildSolutionDescriptor(), problem), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non-null @PlanningId");
    problem.getEntityList().getFirst().setCode(problem.getEntityList().getLast().getCode());
    assertThatThrownBy(
            () ->
                new RoundRobinPartitioner<TestdataSolution>(2)
                    .splitWorkingSolution(
                        director(TestdataSolution.buildSolutionDescriptor(), problem), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate");
  }

  @Test
  void partitionsEntityPropertiesArraysAndSets() {
    var problem = new MixedLayoutSolution();
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(MixedLayoutSolution.class, TestdataEntity.class);
    var partitions =
        new RoundRobinPartitioner<MixedLayoutSolution>(3)
            .splitWorkingSolution(director(descriptor, problem), null);
    assertThat(partitions)
        .extracting(part -> descriptor.getGenuineEntityCount(part))
        .containsExactly(2, 2, 2);
    var entities =
        partitions.stream()
            .flatMap(descriptor::extractAllEntitiesStream)
            .map(TestdataEntity.class::cast)
            .toList();
    assertThat(entities)
        .extracting(TestdataEntity::getCode)
        .containsExactlyInAnyOrder("a", "b", "c", "d", "e", "f");
    assertThat(partitions.getFirst().single).isNotNull().isNotSameAs(problem.single);
    assertThat(partitions.get(1).single).isNull();
    assertThat(partitions.get(2).single).isNull();
    assertThat(problem.array).hasSize(3);
    assertThat(problem.set).hasSize(2);
  }

  @Test
  void rejectsClonersThatShareWorkingEntities() {
    var problem = TestdataSolution.generateSolution(2, 3);
    var director = director(TestdataSolution.buildSolutionDescriptor(), problem);
    var invalidClone = new TestdataSolution();
    invalidClone.setEntityList(List.copyOf(problem.getEntityList()));
    invalidClone.setValueList(problem.getValueList());
    doReturn(invalidClone).when(director).cloneSolution(any());
    assertThatThrownBy(
            () ->
                new RoundRobinPartitioner<TestdataSolution>(2).splitWorkingSolution(director, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("independent cloned entities", "solution cloner");
    assertThat(problem.getEntityList()).hasSize(3);
  }

  @SuppressWarnings("unchecked")
  private static <Solution_> InnerScoreDirector<Solution_, SimpleScore> director(
      SolutionDescriptor<Solution_> descriptor, Solution_ solution) {
    InnerScoreDirector<Solution_, SimpleScore> director = mock(InnerScoreDirector.class);
    when(director.getSolutionDescriptor()).thenReturn(descriptor);
    when(director.getWorkingSolution()).thenReturn(solution);
    when(director.cloneSolution(any()))
        .thenAnswer(
            invocation -> descriptor.getSolutionCloner().cloneSolution(invocation.getArgument(0)));
    return director;
  }

  @PlanningSolution
  public static class MixedLayoutSolution {
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "valueRange")
    public List<TestdataValue> values = List.of(new TestdataValue("v"));

    @PlanningEntityProperty public TestdataEntity single = new TestdataEntity("a");

    @PlanningEntityCollectionProperty
    public TestdataEntity[] array = {
      new TestdataEntity("b"), new TestdataEntity("c"), new TestdataEntity("d")
    };

    @PlanningEntityCollectionProperty
    public Set<TestdataEntity> set =
        new HashSet<>(List.of(new TestdataEntity("e"), new TestdataEntity("f")));

    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class LinkedEntity {
    @PlanningId public String id;

    @PlanningVariable(valueRangeProviderRefs = "entityRange")
    public LinkedEntity previous;
  }

  @PlanningSolution
  public static class LinkedSolution {
    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "entityRange")
    public List<LinkedEntity> entities = List.of();

    @PlanningScore public SimpleScore score;
  }
}
