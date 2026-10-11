package greycos.solver.core.impl.score.director;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeFactory;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ValueRangeStatisticsTest {

  static Stream<Arguments> valueCounts() {
    return Stream.of(
        Arguments.of(7L, 11L, 18L),
        Arguments.of(Long.MAX_VALUE / 2, Long.MAX_VALUE / 2, Long.MAX_VALUE - 1),
        Arguments.of(Long.MAX_VALUE - 1, 1L, Long.MAX_VALUE),
        Arguments.of(Long.MAX_VALUE, 1L, Long.MAX_VALUE),
        Arguments.of(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));
  }

  @ParameterizedTest
  @MethodSource("valueCounts")
  void entityProvidedValueCountsSaturateWithoutEnumeratingRanges(
      long firstCount, long secondCount, long expectedTotal) {
    assertTimeoutPreemptively(
        Duration.ofSeconds(5),
        () -> {
          var descriptor =
              SolutionDescriptor.buildSolutionDescriptor(
                  EntityRangeSolution.class, EntityRangeEntity.class);
          var solution = new EntityRangeSolution(firstCount, secondCount);
          var manager = ValueRangeManager.of(descriptor, solution);

          assertThat(manager.getInitializationStatistics())
              .isEqualTo(new SolutionInitializationStatistics(2, 0, 0, 0, 0, 0));
          var statistics = manager.getProblemSizeStatistics();
          assertThat(statistics.entityCount()).isEqualTo(2);
          assertThat(statistics.variableCount()).isEqualTo(2);
          assertThat(statistics.approximateValueCount()).isEqualTo(expectedTotal);
          assertThat(statistics.genuineEntityClassToVariableToValueCount())
              .isEqualTo(Map.of(EntityRangeEntity.class, Map.of("value", expectedTotal)));
          assertThat(statistics.approximateProblemSizeLog()).isFinite().isPositive();
          assertThat(manager.getStatistics().getApproximateValueCount()).isEqualTo(expectedTotal);
          assertThat(manager.computeInitializationStatistics(solution, null).isInitialized())
              .isTrue();
          assertThat(manager.getStatistics().getApproximateValueCount()).isEqualTo(expectedTotal);
        });
  }

  @ParameterizedTest
  @MethodSource("valueCounts")
  void solutionProvidedValueCountsSaturateWithoutEnumeratingRanges(
      long firstCount, long secondCount, long expectedTotal) {
    assertTimeoutPreemptively(
        Duration.ofSeconds(5),
        () -> {
          var descriptor =
              SolutionDescriptor.buildSolutionDescriptor(
                  SolutionRangeSolution.class, SolutionRangeEntity.class);
          var solution = new SolutionRangeSolution(firstCount, secondCount);
          var manager = ValueRangeManager.of(descriptor, solution);

          assertThat(manager.getInitializationStatistics())
              .isEqualTo(new SolutionInitializationStatistics(1, 0, 0, 0, 0, 0));
          var statistics = manager.getProblemSizeStatistics();
          assertThat(statistics.entityCount()).isEqualTo(1);
          assertThat(statistics.variableCount()).isEqualTo(2);
          assertThat(statistics.approximateValueCount()).isEqualTo(expectedTotal);
          assertThat(statistics.genuineEntityClassToVariableToValueCount())
              .isEqualTo(
                  Map.of(
                      SolutionRangeEntity.class,
                      Map.of("first", firstCount, "second", secondCount)));
          assertThat(statistics.approximateProblemSizeLog()).isFinite().isPositive();
          assertThat(manager.getStatistics().getApproximateValueCount()).isEqualTo(expectedTotal);
        });
  }

  @Test
  void saturatedStatisticsDoNotAffectCountsAfterReset() {
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            EntityRangeSolution.class, EntityRangeEntity.class);
    var manager =
        ValueRangeManager.of(descriptor, new EntityRangeSolution(Long.MAX_VALUE, Long.MAX_VALUE));
    assertThat(manager.getProblemSizeStatistics().approximateValueCount())
        .isEqualTo(Long.MAX_VALUE);

    manager.reset(new EntityRangeSolution(7, 11));

    assertThat(manager.getInitializationStatistics().isInitialized()).isTrue();
    assertThat(manager.getProblemSizeStatistics().approximateValueCount()).isEqualTo(18);
    assertThat(manager.getProblemSizeStatistics().genuineEntityClassToVariableToValueCount())
        .isEqualTo(Map.of(EntityRangeEntity.class, Map.of("value", 18L)));
  }

  @PlanningEntity
  public static class EntityRangeEntity {
    @PlanningVariable(valueRangeProviderRefs = "range")
    public Long value = 0L;

    @ValueRangeProvider(id = "range")
    public ValueRange<Long> range;

    public EntityRangeEntity() {}

    EntityRangeEntity(long count) {
      range = ValueRangeFactory.createLongValueRange(0, count);
    }
  }

  @PlanningSolution
  public static class EntityRangeSolution {
    @PlanningEntityCollectionProperty public List<EntityRangeEntity> entities;

    @PlanningScore public SimpleScore score;

    public EntityRangeSolution() {}

    EntityRangeSolution(long firstCount, long secondCount) {
      entities = List.of(new EntityRangeEntity(firstCount), new EntityRangeEntity(secondCount));
    }
  }

  @PlanningEntity
  public static class SolutionRangeEntity {
    @PlanningVariable(valueRangeProviderRefs = "firstRange")
    public Long first = 0L;

    @PlanningVariable(valueRangeProviderRefs = "secondRange")
    public Long second = 0L;

    public SolutionRangeEntity() {}
  }

  @PlanningSolution
  public static class SolutionRangeSolution {
    @PlanningEntityCollectionProperty public List<SolutionRangeEntity> entities;

    @ValueRangeProvider(id = "firstRange")
    public ValueRange<Long> firstRange;

    @ValueRangeProvider(id = "secondRange")
    public ValueRange<Long> secondRange;

    @PlanningScore public SimpleScore score;

    public SolutionRangeSolution() {}

    SolutionRangeSolution(long firstCount, long secondCount) {
      entities = List.of(new SolutionRangeEntity());
      firstRange = ValueRangeFactory.createLongValueRange(0, firstCount);
      secondRange = ValueRangeFactory.createLongValueRange(0, secondCount);
    }
  }
}
