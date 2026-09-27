package greycos.solver.core.impl.partitionedsearch.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeFactory;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListSolution;
import greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingEntity;
import greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;

class PartitionOwnershipTest {

  @Test
  void acceptsRawParentReferencesAndRejectsMissingOrOverlappingMovableEntities() {
    var scope =
        PartitionChangeMoveTest.<TestdataListSolution>scope(PartitionChangeMoveTest.listConfig());
    scope.setInitialSolution(TestdataListSolution.generateInitializedSolution(4, 2));
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      director.calculateScore();
      var first = part(director.getWorkingSolution(), 0);
      var second = part(director.getWorkingSolution(), 1);
      assertThatCode(() -> PartitionOwnership.validate(director, List.of(first, second)))
          .doesNotThrowAnyException();
      assertThatThrownBy(() -> PartitionOwnership.validate(director, List.of(first)))
          .hasMessageContaining("movable entity")
          .hasMessageContaining("missing");
      assertThatThrownBy(() -> PartitionOwnership.validate(director, List.of(first, first, second)))
          .hasMessageContaining("also owned");
    }
  }

  @Test
  void rejectsOverlappingMissingAndCrossPartitionListRanges() {
    var scope =
        PartitionChangeMoveTest.<TestdataListSolution>scope(PartitionChangeMoveTest.listConfig());
    scope.setInitialSolution(TestdataListSolution.generateInitializedSolution(4, 2));
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      director.calculateScore();
      var working = director.getWorkingSolution();
      var first = part(working, 0);
      var second = part(working, 1);
      second.setValueList(working.getValueList());
      var overlapping = second;
      assertThatThrownBy(() -> PartitionOwnership.validate(director, List.of(first, overlapping)))
          .hasMessageContaining("assignable list value")
          .hasMessageContaining("also owned");
      second = part(working, 1);
      var incomplete = director.cloneSolution(first);
      incomplete.getEntityList().getFirst().getValueList().clear();
      incomplete.setValueList(List.of());
      var finalSecond = second;
      assertThatThrownBy(
              () -> PartitionOwnership.validate(director, List.of(incomplete, finalSecond)))
          .hasMessageContaining("missing from all assignable");
      var swapped = director.cloneSolution(working);
      swapped.getEntityList().forEach(entity -> entity.getValueList().clear());
      var swappedFirst = part(swapped, 0);
      var swappedSecond = part(swapped, 1);
      var firstRange = swappedFirst.getValueList();
      swappedFirst.setValueList(swappedSecond.getValueList());
      swappedSecond.setValueList(firstRange);
      assertThatThrownBy(
              () -> PartitionOwnership.validate(director, List.of(swappedFirst, swappedSecond)))
          .hasMessageContaining("another partition than its current entity");
    }
  }

  @Test
  void rejectsForeignAndDuplicateTargetsBeforeChangingWorkingAssignments() {
    var scope =
        PartitionChangeMoveTest.<TestdataListSolution>scope(PartitionChangeMoveTest.listConfig());
    scope.setInitialSolution(TestdataListSolution.generateInitializedSolution(4, 2));
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      var before = director.calculateScore();
      var working = director.getWorkingSolution();
      var ownership =
          PartitionOwnership.validate(director, List.of(part(working, 0), part(working, 1)));
      var source = director.cloneWorkingSolution();
      source.setEntityList(List.of(source.getEntityList().getFirst()));
      source
          .getEntityList()
          .getFirst()
          .setValueList(new ArrayList<>(List.of(source.getValueList().get(1))));
      var foreign = PartitionChangeMove.createMove(director, source, 0).rebase(director);
      assertThatThrownBy(() -> ownership.validateMove(foreign, director))
          .hasMessageContaining("outside the range");
      source
          .getEntityList()
          .getFirst()
          .setValueList(
              new ArrayList<>(List.of(source.getValueList().get(0), source.getValueList().get(0))));
      var duplicate = PartitionChangeMove.createMove(director, source, 0).rebase(director);
      assertThatThrownBy(() -> ownership.validateMove(duplicate, director))
          .hasMessageContaining("duplicate list value");
      assertThat(director.calculateScore()).isEqualTo(before);
      assertThat(working.getEntityList().getFirst().getValueList())
          .containsExactly(working.getValueList().get(0), working.getValueList().get(2));
    }
  }

  @Test
  void pinnedBoundaryCopiesMayShareTheirListRanges() {
    var scope =
        PartitionChangeMoveTest.<TestdataPinnedWithIndexListSolution>scope(
            PartitionChangeMoveTest.pinnedConfig());
    var initial = TestdataPinnedWithIndexListSolution.generateInitializedSolution(6, 3);
    initial.getEntityList().get(2).setPinned(true);
    initial.getEntityList().get(0).setPinIndex(1);
    scope.setInitialSolution(initial);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      director.calculateScore();
      // Each child owns one entity and sees the others as pinned boundary context.
      var first = director.cloneWorkingSolution();
      first.getEntityList().get(1).setPinned(true);
      var second = director.cloneWorkingSolution();
      second.getEntityList().get(0).setPinned(true);
      assertThatCode(() -> PartitionOwnership.validate(director, List.of(first, second)))
          .doesNotThrowAnyException();
      second.getEntityList().get(2).setPinned(false);
      assertThatThrownBy(() -> PartitionOwnership.validate(director, List.of(first, second)))
          .hasMessageContaining("pinned parent entity");
    }
  }

  @Test
  void missingPinnedBoundaryCannotOfferItsValuesAsAssignable() {
    var scope =
        PartitionChangeMoveTest.<TestdataPinnedWithIndexListSolution>scope(
            PartitionChangeMoveTest.pinnedConfig());
    var initial = TestdataPinnedWithIndexListSolution.generateInitializedSolution(4, 2);
    initial.getEntityList().getLast().setPinned(true);
    scope.setInitialSolution(initial);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      director.calculateScore();
      var source = director.cloneWorkingSolution();
      source.setEntityList(List.of(source.getEntityList().getFirst()));
      assertThatThrownBy(() -> PartitionOwnership.validate(director, List.of(source)))
          .hasMessageContaining("without its pinned entity context");
    }
  }

  @Test
  void emptyRequiredBasicRangeFailsButPartialConstructionNullIsAllowed() {
    var scope =
        PartitionChangeMoveTest.<TestdataSolution>scope(
            PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class));
    scope.setInitialSolution(TestdataSolution.generateUninitializedSolution(2, 2));
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      director.calculateScore();
      var source = director.cloneWorkingSolution();
      var empty = director.cloneWorkingSolution();
      empty.setValueList(List.of());
      assertThatThrownBy(() -> PartitionOwnership.validate(director, List.of(empty)))
          .hasMessageContaining("empty value range");
      source.setValueList(List.of(source.getValueList().getFirst()));
      var ownership = PartitionOwnership.validate(director, List.of(source));
      var partial = director.cloneSolution(source);
      partial.getEntityList().getFirst().setValue(partial.getValueList().getFirst());
      var partialMove = PartitionChangeMove.createMove(director, partial, 0).rebase(director);
      assertThatCode(() -> ownership.validateMove(partialMove, director))
          .doesNotThrowAnyException();
      director.executeMove(partialMove);
      assertThat(director.getWorkingInitScore()).isEqualTo(-1);
      partial
          .getEntityList()
          .getLast()
          .setValue(director.getWorkingSolution().getValueList().getLast());
      var foreign = PartitionChangeMove.createMove(director, partial, 0).rebase(director);
      assertThatThrownBy(() -> ownership.validateMove(foreign, director))
          .hasMessageContaining("outside the range");
    }
  }

  @Test
  void entitySpecificBasicDomainsCannotBeExpandedOrCrossed() {
    var scope =
        PartitionChangeMoveTest.<TestdataEntityProvidingSolution>scope(
            PlannerTestUtils.buildSolverConfig(
                TestdataEntityProvidingSolution.class, TestdataEntityProvidingEntity.class));
    scope.setInitialSolution(TestdataEntityProvidingSolution.generateUninitializedSolution(4, 2));
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      director.calculateScore();
      var source = director.cloneWorkingSolution();
      var ownership = PartitionOwnership.validate(director, List.of(source));
      var foreign = source.getEntityList().getLast().getValueRange().getFirst();
      source.getEntityList().getFirst().setValue(foreign);
      var move = PartitionChangeMove.createMove(director, source, 0).rebase(director);
      assertThatThrownBy(() -> ownership.validateMove(move, director))
          .hasMessageContaining("outside the range");
      source.getEntityList().getFirst().setValueRange(List.of(foreign));
      assertThatThrownBy(() -> PartitionOwnership.validate(director, List.of(source)))
          .hasMessageContaining("outside its parent range");
    }
  }

  @Test
  void equalImmutableListValuesRebaseToParentRangeInstances() {
    var scope =
        PartitionChangeMoveTest.<StringListSolution>scope(
            PlannerTestUtils.buildSolverConfig(StringListSolution.class, StringListEntity.class));
    var initial = new StringListSolution();
    initial.values = List.of(new String("first"), new String("second"));
    initial.entities = List.of(new StringListEntity("entity"));
    scope.setInitialSolution(initial);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      director.calculateScore();
      var source = director.cloneWorkingSolution();
      source.values = List.of(new String("first"), new String("second"));
      source.entities.getFirst().values.addAll(source.values);
      var ownership = PartitionOwnership.validate(director, List.of(source));
      var move = PartitionChangeMove.createMove(director, source, 0).rebase(director);
      ownership.validateMove(move, director);
      director.executeMove(move);
      var working = director.getWorkingSolution();
      assertThat(working.entities.getFirst().values.getFirst()).isSameAs(working.values.getFirst());
      assertThat(working.entities.getFirst().values.getLast()).isSameAs(working.values.getLast());
      assertThat(director.getWorkingInitScore()).isZero();
      assertThat(
              director
                  .getListVariableState(
                      director.getSolutionDescriptor().getListVariableDescriptor())
                  .getUnassignedCount())
          .isZero();
    }
  }

  @Test
  void compactBasicRangesAreNeverEnumeratedByOwnershipValidation() {
    var scope =
        PartitionChangeMoveTest.<LongRangeSolution>scope(
            PlannerTestUtils.buildSolverConfig(LongRangeSolution.class, LongRangeEntity.class));
    var initial = new LongRangeSolution();
    var entity = new LongRangeEntity();
    entity.id = "entity";
    entity.value = 0L;
    initial.entities = List.of(entity);
    scope.setInitialSolution(initial);
    try (var director = scope.<SimpleScore>getScoreDirector()) {
      director.calculateScore();
      var source = director.cloneWorkingSolution();
      var ownership = PartitionOwnership.validate(director, List.of(source));
      source.entities.getFirst().value = 999_999_999L;
      var move = PartitionChangeMove.createMove(director, source, 0).rebase(director);
      ownership.validateMove(move, director);
      director.executeMove(move);
      assertThat(director.getWorkingSolution().entities.getFirst().value).isEqualTo(999_999_999L);
      source.entities.getFirst().value = 1_000_000_000L;
      var invalid = PartitionChangeMove.createMove(director, source, 0).rebase(director);
      assertThatThrownBy(() -> ownership.validateMove(invalid, director))
          .hasMessageContaining("outside the range");
    }
  }

  @PlanningSolution
  public static class LongRangeSolution {
    @PlanningEntityCollectionProperty public List<LongRangeEntity> entities;
    @PlanningScore public SimpleScore score;

    @ValueRangeProvider
    public ValueRange<Long> range() {
      return new ValueRange<>() {
        private final ValueRange<Long> delegate =
            ValueRangeFactory.createLongValueRange(0, 1_000_000_000L);

        @Override
        public boolean isEmpty() {
          return false;
        }

        @Override
        public boolean contains(Long value) {
          return delegate.contains(value);
        }

        @Override
        public long getSize() {
          return delegate.getSize();
        }

        @Override
        public Long get(long index) {
          return delegate.get(index);
        }

        @Override
        public Iterator<Long> createOriginalIterator() {
          throw new AssertionError("Compact basic range must not be enumerated");
        }

        @Override
        public Iterator<Long> createRandomIterator(RandomGenerator random) {
          return delegate.createRandomIterator(random);
        }
      };
    }
  }

  @PlanningEntity
  public static class LongRangeEntity {
    @PlanningId public String id;
    @PlanningVariable public Long value;
  }

  @PlanningSolution
  public static class StringListSolution {
    @PlanningEntityCollectionProperty public List<StringListEntity> entities;
    @ProblemFactCollectionProperty @ValueRangeProvider public List<String> values;
    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class StringListEntity {
    @PlanningId public String id;
    @PlanningListVariable public List<String> values = new ArrayList<>();

    public StringListEntity() {}

    StringListEntity(String id) {
      this.id = id;
    }
  }

  static TestdataListSolution part(TestdataListSolution source, int index) {
    var part = new TestdataListSolution();
    part.setEntityList(List.of(source.getEntityList().get(index)));
    part.setValueList(
        List.of(source.getValueList().get(index), source.getValueList().get(index + 2)));
    return part;
  }
}
