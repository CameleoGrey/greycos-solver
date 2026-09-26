package greycos.solver.core.preview.api.move.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import greycos.solver.core.impl.move.builtin.MassChangeMove;
import greycos.solver.core.preview.api.neighborhood.stream.dataset.sample.Sample;
import greycos.solver.core.preview.api.neighborhood.stream.dataset.sample.Samplers;
import greycos.solver.core.preview.api.neighborhood.test.NeighborhoodTester;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.pinned.TestdataPinnedEntity;
import greycos.solver.core.testcotwin.pinned.TestdataPinnedSolution;
import greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedEntity;
import greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedSolution;
import greycos.solver.core.testcotwin.valuerange.entityproviding.unassignedvar.TestdataAllowsUnassignedEntityProvidingEntity;
import greycos.solver.core.testcotwin.valuerange.entityproviding.unassignedvar.TestdataAllowsUnassignedEntityProvidingSolution;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@NullMarked
class SubPillarChangeMoveProviderTest {

  @Test
  void subpillarMembersAreAlwaysASubsetOfTheFullPillar() {
    var solutionMetaModel = TestdataSolution.buildMetaModel();
    var variableMetaModel = solutionMetaModel.genuineEntity(TestdataEntity.class).basicVariable();

    var solution = TestdataSolution.generateSolution(2, 5);
    var entityList = solution.getEntityList();
    var sharedValue = solution.getValueList().getFirst();
    for (var entity : entityList) {
      entity.setValue(sharedValue); // All 5 entities share one value -> one pillar of size 5.
    }

    var context =
        NeighborhoodTester.build(
                new SubPillarChangeMoveProvider<>(
                    variableMetaModel, Samplers.pillar(Samplers.exactly(2))),
                solutionMetaModel)
            .using(solution);

    var moves =
        context
            .getMovesAsStream(
                move -> (MassChangeMove<TestdataSolution, TestdataEntity, TestdataValue>) move)
            .limit(200)
            .toList();
    var fullPillarMembers = new HashSet<>(entityList);
    assertThat(moves)
        .isNotEmpty()
        .allSatisfy(
            move ->
                assertThat(move.getPlanningEntities())
                    .hasSizeLessThanOrEqualTo(2)
                    .allMatch(member -> fullPillarMembers.contains((TestdataEntity) member)));
  }

  @Test
  void differentDrawsProduceDifferentSubpillars() {
    var solutionMetaModel = TestdataSolution.buildMetaModel();
    var variableMetaModel = solutionMetaModel.genuineEntity(TestdataEntity.class).basicVariable();

    var solution = TestdataSolution.generateSolution(2, 5);
    var entityList = solution.getEntityList();
    var sharedValue = solution.getValueList().getFirst();
    for (var entity : entityList) {
      entity.setValue(sharedValue);
    }

    var context =
        NeighborhoodTester.build(
                new SubPillarChangeMoveProvider<>(
                    variableMetaModel, Samplers.pillar(Samplers.exactly(2))),
                solutionMetaModel)
            .using(solution);

    var distinctMemberSets =
        context
            .getMovesAsStream(
                move -> (MassChangeMove<TestdataSolution, TestdataEntity, TestdataValue>) move)
            .limit(200)
            .map(move -> new HashSet<>(move.getPlanningEntities()))
            .collect(Collectors.toCollection(HashSet::new));
    assertThat(distinctMemberSets).hasSizeGreaterThan(1);
  }

  @Test
  void pinnedEntityExcludedFromSubpillar() {
    var solutionMetaModel = TestdataPinnedSolution.buildMetaModel();
    var variableMetaModel =
        solutionMetaModel.genuineEntity(TestdataPinnedEntity.class).basicVariable();

    var v0 = new TestdataValue("v0");
    var v1 = new TestdataValue("v1");
    // pinnedEntity shares v0 with free1 and free2,
    // but forEach(..., false) excludes pinned entities from the entity source,
    // so it must never join their subpillar, regardless of rule.
    var pinnedEntity = new TestdataPinnedEntity("pinned", v0, false, true);
    var free1 = new TestdataPinnedEntity("free1", v0, false, false);
    var free2 = new TestdataPinnedEntity("free2", v0, false, false);

    var solution = new TestdataPinnedSolution("s");
    solution.setValueList(List.of(v0, v1));
    solution.setEntityList(List.of(pinnedEntity, free1, free2));

    var context =
        NeighborhoodTester.build(
                new SubPillarChangeMoveProvider<>(
                    variableMetaModel, Samplers.pillar(Samplers.exactly(2))),
                solutionMetaModel)
            .using(solution);

    var moves =
        context
            .getMovesAsStream(
                move ->
                    (MassChangeMove<TestdataPinnedSolution, TestdataPinnedEntity, TestdataValue>)
                        move)
            .limit(50)
            .toList();
    assertThat(moves)
        .isNotEmpty()
        .flatExtracting(MassChangeMove::getPlanningEntities)
        .doesNotContain(pinnedEntity);
  }

  @Test
  void crossingNullDefaultTrueAlsoUnassignsSubpillar() {
    var solutionMetaModel = TestdataAllowsUnassignedSolution.buildMetaModel();
    var variableMetaModel =
        solutionMetaModel.genuineEntity(TestdataAllowsUnassignedEntity.class).basicVariable();

    var solution = TestdataAllowsUnassignedSolution.generateSolution(3, 6);
    var entityList = solution.getEntityList();
    var sharedValue = solution.getValueList().getFirst();
    for (var entity : entityList) {
      entity.setValue(sharedValue); // All entities share one value -> one pillar.
    }

    // Default constructor: crossingNull is true, because this variable allows unassigned values.
    var context =
        NeighborhoodTester.build(
                new SubPillarChangeMoveProvider<>(
                    variableMetaModel, Samplers.pillar(Samplers.exactly(2))),
                solutionMetaModel)
            .using(solution);

    var moves =
        context
            .getMovesAsStream(
                move ->
                    (MassChangeMove<
                            TestdataAllowsUnassignedSolution,
                            TestdataAllowsUnassignedEntity,
                            TestdataValue>)
                        move)
            .limit(500)
            .toList();
    assertThat(moves).anyMatch(move -> move.getPlanningValues().getFirst() == null);
  }

  @Test
  void crossingNullFalseNeverUnassignsSubpillar() {
    var solutionMetaModel = TestdataAllowsUnassignedSolution.buildMetaModel();
    var variableMetaModel =
        solutionMetaModel.genuineEntity(TestdataAllowsUnassignedEntity.class).basicVariable();

    var solution = TestdataAllowsUnassignedSolution.generateSolution(3, 6);
    var entityList = solution.getEntityList();
    var sharedValue = solution.getValueList().getFirst();
    for (var entity : entityList) {
      entity.setValue(sharedValue);
    }

    var context =
        NeighborhoodTester.build(
                new SubPillarChangeMoveProvider<>(
                    variableMetaModel, Samplers.pillar(Samplers.exactly(2)), false),
                solutionMetaModel)
            .using(solution);

    var moves =
        context
            .getMovesAsStream(
                move ->
                    (MassChangeMove<
                            TestdataAllowsUnassignedSolution,
                            TestdataAllowsUnassignedEntity,
                            TestdataValue>)
                        move)
            .limit(200)
            .toList();
    assertThat(moves).isNotEmpty().noneMatch(move -> move.getPlanningValues().getFirst() == null);
  }

  @Test
  void noAlternativeNonNullDestinationStillUnassignsAndUndoes() {
    var solutionMetaModel = TestdataAllowsUnassignedEntityProvidingSolution.buildMetaModel();
    var variableMetaModel =
        solutionMetaModel
            .genuineEntity(TestdataAllowsUnassignedEntityProvidingEntity.class)
            .basicVariable();
    var currentValue = new TestdataValue("current");
    var solution = solutionWithRangeIntersection(List.of(currentValue), 99);
    var context =
        NeighborhoodTester.build(
                new SubPillarChangeMoveProvider<>(
                    variableMetaModel, Samplers.pillar(Samplers.exactly(2)), true),
                solutionMetaModel)
            .using(solution);

    // Seed 0 misses the ordinary null coin flip on all three probes of these 100-value ranges.
    // Their intersection contains only the current value, so unassignment is the sole legal move.
    var iterator = context.getMovesAsIterator();
    assertThat(iterator.hasNext()).isTrue();
    var move = iterator.next();
    assertThat(move)
        .isEqualTo(Moves.massChange(variableMetaModel, Sample.of(solution.getEntityList()), null));
    context
        .getMoveTestContext()
        .executeTemporarily(
            move,
            view ->
                assertThat(solution.getEntityList())
                    .allSatisfy(entity -> assertThat(entity.getValue()).isNull()));
    assertThat(solution.getEntityList())
        .allSatisfy(entity -> assertThat(entity.getValue()).isSameAs(currentValue));
  }

  @Test
  void noAlternativeNonNullDestinationEndsWhenCrossingNullIsDisabled() {
    var solutionMetaModel = TestdataAllowsUnassignedEntityProvidingSolution.buildMetaModel();
    var variableMetaModel =
        solutionMetaModel
            .genuineEntity(TestdataAllowsUnassignedEntityProvidingEntity.class)
            .basicVariable();
    var currentValue = new TestdataValue("current");
    var solution = solutionWithRangeIntersection(List.of(currentValue), 99);
    var context =
        NeighborhoodTester.build(
                new SubPillarChangeMoveProvider<>(
                    variableMetaModel, Samplers.pillar(Samplers.exactly(2)), false),
                solutionMetaModel)
            .using(solution);

    assertThat(context.getMovesAsIterator().hasNext()).isFalse();
    assertThat(solution.getEntityList())
        .allSatisfy(entity -> assertThat(entity.getValue()).isSameAs(currentValue));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void alternativeNonNullDestinationRemainsAvailable(boolean crossingNull) {
    var solutionMetaModel = TestdataAllowsUnassignedEntityProvidingSolution.buildMetaModel();
    var variableMetaModel =
        solutionMetaModel
            .genuineEntity(TestdataAllowsUnassignedEntityProvidingEntity.class)
            .basicVariable();
    var currentValue = new TestdataValue("current");
    var destinationValue = new TestdataValue("destination");
    var solution = solutionWithRangeIntersection(List.of(currentValue, destinationValue), 1);
    var context =
        NeighborhoodTester.build(
                new SubPillarChangeMoveProvider<>(
                    variableMetaModel, Samplers.pillar(Samplers.exactly(2)), crossingNull),
                solutionMetaModel)
            .using(solution);

    var sample = Sample.of(solution.getEntityList());
    var changeMove = Moves.massChange(variableMetaModel, sample, destinationValue);
    var unassignMove = Moves.massChange(variableMetaModel, sample, null);
    var moves = context.getMovesAsStream().limit(100).toList();
    if (crossingNull) {
      assertThat(moves).containsOnly(changeMove, unassignMove).contains(changeMove, unassignMove);
    } else {
      assertThat(moves).hasSize(100).containsOnly(changeMove);
    }
    context
        .getMoveTestContext()
        .executeTemporarily(
            changeMove,
            view ->
                assertThat(solution.getEntityList())
                    .allSatisfy(
                        entity -> assertThat(entity.getValue()).isSameAs(destinationValue)));
    assertThat(solution.getEntityList())
        .allSatisfy(entity -> assertThat(entity.getValue()).isSameAs(currentValue));
  }

  private static TestdataAllowsUnassignedEntityProvidingSolution solutionWithRangeIntersection(
      List<TestdataValue> sharedValues, int distinctValuesPerEntity) {
    var entities = new ArrayList<TestdataAllowsUnassignedEntityProvidingEntity>();
    for (var entityIndex = 0; entityIndex < 2; entityIndex++) {
      var range = new ArrayList<>(sharedValues);
      for (var valueIndex = 0; valueIndex < distinctValuesPerEntity; valueIndex++) {
        range.add(new TestdataValue("entity" + entityIndex + "-value" + valueIndex));
      }
      entities.add(
          new TestdataAllowsUnassignedEntityProvidingEntity(
              "entity" + entityIndex, range, sharedValues.getFirst()));
    }
    var solution = new TestdataAllowsUnassignedEntityProvidingSolution("s");
    solution.setEntityList(entities);
    return solution;
  }

  @Test
  void constructorRejectsExplicitCrossingNullOnNonUnassignedVariable() {
    var solutionMetaModel = TestdataSolution.buildMetaModel();
    var variableMetaModel = solutionMetaModel.genuineEntity(TestdataEntity.class).basicVariable();
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new SubPillarChangeMoveProvider<>(
                    variableMetaModel, Samplers.pillar(Samplers.exactly(2)), true));
  }
}
