package greycos.solver.core.impl.move.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.valuerange.buildin.collection.ListValueRange;
import greycos.solver.core.impl.move.MoveDirector;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;
import greycos.solver.core.preview.api.neighborhood.stream.dataset.sample.Sample;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedEasyScoreCalculator;
import greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedEntity;
import greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedSolution;
import greycos.solver.core.testcotwin.valuerange.entityproviding.unassignedvar.TestdataAllowsUnassignedEntityProvidingEntity;
import greycos.solver.core.testcotwin.valuerange.entityproviding.unassignedvar.TestdataAllowsUnassignedEntityProvidingScoreCalculator;
import greycos.solver.core.testcotwin.valuerange.entityproviding.unassignedvar.TestdataAllowsUnassignedEntityProvidingSolution;

import org.junit.jupiter.api.Test;

class SampleValueRangesTest {

  @Test
  void of_sampleBased_equalSizesRetainFirstEncounteredRange() {
    try (var fixture = new SampleValueRangesTestSupport()) {
      // Both orders must work in every JVM; this does not depend on observing different set salts.
      assertThat(fixture.ranges(fixture.entityA, fixture.entityB).smallestRange())
          .isSameAs(fixture.rangeOf(fixture.entityA));
      assertThat(fixture.ranges(fixture.entityB, fixture.entityA).smallestRange())
          .isSameAs(fixture.rangeOf(fixture.entityB));
    }
  }

  @Test
  void of_sampleBased_laterSmallerRangeWins() {
    try (var fixture = new SampleValueRangesTestSupport()) {
      var ranges = fixture.ranges(fixture.entityA, fixture.entityB, fixture.smaller);

      assertThat(ranges.smallestRange()).isSameAs(fixture.rangeOf(fixture.smaller));
      assertThat(ranges.distinctRangeSet()).hasSize(3);
    }
  }

  @Test
  void of_sampleBased_duplicatesRetainFirstMinimumAndDistinctMembership() {
    try (var fixture = new SampleValueRangesTestSupport()) {
      var tiedRanges = fixture.ranges(fixture.entityA, fixture.duplicateA, fixture.entityB);
      assertThat(tiedRanges.smallestRange()).isSameAs(fixture.rangeOf(fixture.entityA));
      assertThat(tiedRanges.distinctRangeSet()).hasSize(2);

      var smallerRanges =
          fixture.ranges(
              fixture.entityA, fixture.smaller, fixture.duplicateSmaller, fixture.entityB);
      assertThat(smallerRanges.smallestRange()).isSameAs(fixture.rangeOf(fixture.smaller));
      assertThat(smallerRanges.distinctRangeSet()).hasSize(3);
    }
  }

  @Test
  void of_sampleBased_equalityAndHashIgnoreTieOrder() {
    try (var fixture = new SampleValueRangesTestSupport()) {
      var ab = fixture.ranges(fixture.entityA, fixture.entityB);
      var ba = fixture.ranges(fixture.entityB, fixture.entityA);

      assertThat(ab.smallestRange()).isNotSameAs(ba.smallestRange());
      assertThat(ab).isEqualTo(ba).hasSameHashCodeAs(ba);
    }
  }

  @Test
  void of_orderedSet_equalSizesRetainFirstEncounteredRange() {
    var a = new ListValueRange<>(List.of("a", "b", "c"));
    var b = new ListValueRange<>(List.of("c", "b", "d"));

    assertThat(SampleValueRanges.of(new LinkedHashSet<>(List.of(a, b))).smallestRange())
        .isSameAs(a);
    assertThat(SampleValueRanges.of(new LinkedHashSet<>(List.of(b, a))).smallestRange())
        .isSameAs(b);
  }

  @Test
  void of_sampleBased_excludesNullAndChecksLegalityAcrossEveryMember() {
    var solutionDescriptor =
        TestdataAllowsUnassignedEntityProvidingSolution.buildSolutionDescriptor();
    var variableMetaModel =
        solutionDescriptor
            .getMetaModel()
            .genuineEntity(TestdataAllowsUnassignedEntityProvidingEntity.class)
            .basicVariable();
    var scoreDirectorFactory =
        new EasyScoreDirectorFactory<>(
            solutionDescriptor,
            new TestdataAllowsUnassignedEntityProvidingScoreCalculator(),
            EnvironmentMode.PHASE_ASSERT);
    var scoreDirector = scoreDirectorFactory.buildScoreDirector();

    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    var v3 = new TestdataValue("v3");
    var entityA = new TestdataAllowsUnassignedEntityProvidingEntity("a", List.of(v1, v2));
    var entityB =
        new TestdataAllowsUnassignedEntityProvidingEntity(
            "b", List.of(v1, v2)); // content-equal to A's range
    var entityC =
        new TestdataAllowsUnassignedEntityProvidingEntity(
            "c", List.of(v1, v3)); // genuinely different

    var solution = new TestdataAllowsUnassignedEntityProvidingSolution("s");
    solution.setEntityList(List.of(entityA, entityB, entityC));
    scoreDirector.setWorkingSolution(solution);

    var moveDirector = new MoveDirector<>(scoreDirector);
    var sample = Sample.of(List.of(entityA, entityB, entityC));

    var ranges = SampleValueRanges.of(sample, variableMetaModel, moveDirector);

    // v1 is legal for every member; v2 is not (out of range for C).
    assertThat(ranges.containsInEvery(v1)).isTrue();
    assertThat(ranges.containsInEvery(v2)).isFalse();
    // The variable allows unassigned, so the cached range is null-wrapped;
    // containsInEvery(...) must reject null directly, or it would be treated as a legal
    // destination.
    assertThat(ranges.containsInEvery(null)).isFalse();
  }

  @Test
  void of_solutionScoped_readsOneRangeForTheWholeSample() {
    var solutionDescriptor = TestdataAllowsUnassignedSolution.buildSolutionDescriptor();
    var variableMetaModel =
        solutionDescriptor
            .getMetaModel()
            .genuineEntity(TestdataAllowsUnassignedEntity.class)
            .basicVariable();
    var scoreDirectorFactory =
        new EasyScoreDirectorFactory<>(
            solutionDescriptor,
            new TestdataAllowsUnassignedEasyScoreCalculator(),
            EnvironmentMode.PHASE_ASSERT);
    var scoreDirector = scoreDirectorFactory.buildScoreDirector();

    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    var entityA = new TestdataAllowsUnassignedEntity("a", v1);
    var entityB = new TestdataAllowsUnassignedEntity("b", v1);
    var entityC = new TestdataAllowsUnassignedEntity("c", v1);

    var solution = new TestdataAllowsUnassignedSolution("s");
    solution.setValueList(List.of(v1, v2));
    solution.setEntityList(List.of(entityA, entityB, entityC));
    scoreDirector.setWorkingSolution(solution);

    var moveDirector = spy(new MoveDirector<>(scoreDirector));
    var sample = Sample.of(List.of(entityA, entityB, entityC));

    var ranges = SampleValueRanges.of(sample, variableMetaModel, moveDirector);

    // Every member shares one solution-scoped range instance: one lookup, not one per member.
    assertThat(ranges.distinctRangeSet()).hasSize(1);
    assertThat(ranges.containsInEvery(v1)).isTrue();
    assertThat(ranges.containsInEvery(v2)).isTrue();
    assertThat(ranges.containsInEvery(null)).isFalse();
    verify(moveDirector, times(1)).getValueRange(eq(variableMetaModel), any());
  }

  @Test
  void bailOutSizeOf_clampsHugeSizeInsteadOfOverflowing() {
    // Long.MAX_VALUE * BAIL_OUT_SAFETY_MULTIPLIER would overflow negative,
    // and FilteringIterator reads a negative bailOutSize as "bail-out disabled" - turning hasNext()
    // into an infinite loop.
    assertThat(SampleValueRanges.bailOutSizeOf(new HugeValueRange())).isPositive();
  }

  @Test
  void pickExactly_returnsNullWhenIntersectionIsEmpty() {
    var smallest = new ListValueRange<>(List.of("a", "b"));
    var other = new ListValueRange<>(List.of("c", "d"));
    var random = new Random(0);

    var ranges = SampleValueRanges.of(Set.of(smallest, other));

    assertThat(ranges.pickExactly(random, null)).isNull();
  }

  @Test
  void pickExactly_excludesTheGivenValue() {
    var only = new ListValueRange<>(List.of("only"));
    var random = new Random(0);

    var ranges = SampleValueRanges.of(Set.of(only));

    assertThat(ranges.pickExactly(random, "only")).isNull();
  }

  @Test
  void findDestination_singleRangeReturnsAMember() {
    var range = new ListValueRange<>(List.of("a", "b", "c"));
    var random = new Random(0);

    var ranges = SampleValueRanges.of(Set.of(range));

    assertThat(ranges.findTarget(random, null)).isIn("a", "b", "c");
  }

  @Test
  void findDestination_singleRangeExcludesTheGivenValueEvenAsTheOnlyCandidate() {
    var only = new ListValueRange<>(List.of("only"));
    var random = new Random(0);

    // The single-distinct-range case still has to honor the exclusion (MassChange's "not the
    // current value" rule):
    // here the range's one element IS the excluded value,
    // so no destination exists at all - this must come back null, not the excluded value itself.
    var ranges = SampleValueRanges.of(Set.of(only));

    assertThat(ranges.findTarget(random, "only")).isNull();
  }

  @Test
  void findDestination_multiRangeReturnsAnIntersectionMemberOrNullWhenDisjoint() {
    var overlapping = new ListValueRange<>(List.of("a", "b"));
    var other = new ListValueRange<>(List.of("b", "c"));
    var random = new Random(0);

    var overlappingRanges = SampleValueRanges.of(Set.of(overlapping, other));
    assertThat(overlappingRanges.findTarget(random, null)).isEqualTo("b");

    var disjointA = new ListValueRange<>(List.of("a"));
    var disjointB = new ListValueRange<>(List.of("b"));
    var disjointRanges = SampleValueRanges.of(Set.of(disjointA, disjointB));
    assertThat(disjointRanges.findTarget(random, null)).isNull();
  }

  @Test
  void equals_ignoresOrderButNotMembership() {
    var rangeA = new ListValueRange<>(List.of("a"));
    var rangeB = new ListValueRange<>(List.of("b"));
    var rangeC = new ListValueRange<>(List.of("c"));

    assertThat(SampleValueRanges.of(Set.of(rangeA, rangeB)))
        .isEqualTo(SampleValueRanges.of(Set.of(rangeB, rangeA)));
    assertThat(SampleValueRanges.of(Set.of(rangeA, rangeB)))
        .isNotEqualTo(SampleValueRanges.of(Set.of(rangeA, rangeC)));
    assertThat(SampleValueRanges.of(Set.of(rangeA)))
        .isNotEqualTo(SampleValueRanges.of(Set.of(rangeA, rangeB)));
  }

  /**
   * A range too large for {@code getSize() * BAIL_OUT_SAFETY_MULTIPLIER} to fit in a {@code long}.
   */
  private static final class HugeValueRange implements ValueRange<Long> {

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean contains(Long value) {
      return true;
    }

    @Override
    public long getSize() {
      return Long.MAX_VALUE;
    }

    @Override
    public Long get(long index) {
      return index;
    }

    @Override
    public Iterator<Long> createOriginalIterator() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Long> createRandomIterator(RandomGenerator workingRandom) {
      throw new UnsupportedOperationException();
    }
  }
}
