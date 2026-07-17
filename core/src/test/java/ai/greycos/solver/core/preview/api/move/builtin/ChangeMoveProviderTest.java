package ai.greycos.solver.core.preview.api.move.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.Collections;
import java.util.stream.StreamSupport;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.stream.Constraint;
import ai.greycos.solver.core.api.score.stream.ConstraintFactory;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.neighborhood.stream.DefaultMoveStreamFactory;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.score.director.SessionContext;
import ai.greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import ai.greycos.solver.core.preview.api.move.Move;
import ai.greycos.solver.core.preview.api.neighborhood.MoveProvider;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataSolution;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testcotwin.pinned.TestdataPinnedEntity;
import ai.greycos.solver.core.testcotwin.pinned.TestdataPinnedSolution;
import ai.greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedEntity;
import ai.greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedSolution;
import ai.greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingEntity;
import ai.greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingSolution;
import ai.greycos.solver.core.testcotwin.valuerange.entityproviding.unassignedvar.TestdataAllowsUnassignedEntityProvidingEntity;
import ai.greycos.solver.core.testcotwin.valuerange.entityproviding.unassignedvar.TestdataAllowsUnassignedEntityProvidingSolution;
import ai.greycos.solver.core.testcotwin.valuerange.incomplete.TestdataIncompleteValueRangeEntity;
import ai.greycos.solver.core.testcotwin.valuerange.incomplete.TestdataIncompleteValueRangeSolution;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class ChangeMoveProviderTest {

  @Test
  void fromSolution() {
    var solutionDescriptor = TestdataSolution.buildSolutionDescriptor();
    var variableMetaModel =
        solutionDescriptor.getMetaModel().genuineEntity(TestdataEntity.class).basicVariable();

    var solution = TestdataSolution.generateSolution(2, 2);
    var firstEntity = solution.getEntityList().get(0);
    firstEntity.setValue(null);
    var secondEntity = solution.getEntityList().get(1);
    secondEntity.setValue(null);
    var firstValue = solution.getValueList().get(0);
    var secondValue = solution.getValueList().get(1);

    var moveIterable =
        createMoveIterable(
            new ChangeMoveProvider<>(variableMetaModel), solutionDescriptor, solution);
    assertThat(moveIterable).hasSize(4);

    var moveList =
        StreamSupport.stream(moveIterable.spliterator(), false)
            .map(m -> (ChangeMove<TestdataSolution, TestdataEntity, TestdataValue>) m)
            .toList();
    assertThat(moveList).hasSize(4);

    var firstMove = moveList.get(0);
    assertSoftly(
        softly -> {
          softly.assertThat(firstMove.getPlanningEntities()).containsExactly(firstEntity);
          softly.assertThat(firstMove.getPlanningValues()).containsExactly(firstValue);
        });

    var secondMove = moveList.get(1);
    assertSoftly(
        softly -> {
          softly.assertThat(secondMove.getPlanningEntities()).containsExactly(firstEntity);
          softly.assertThat(secondMove.getPlanningValues()).containsExactly(secondValue);
        });

    var thirdMove = moveList.get(2);
    assertSoftly(
        softly -> {
          softly.assertThat(thirdMove.getPlanningEntities()).containsExactly(secondEntity);
          softly.assertThat(thirdMove.getPlanningValues()).containsExactly(firstValue);
        });

    var fourthMove = moveList.get(3);
    assertSoftly(
        softly -> {
          softly.assertThat(fourthMove.getPlanningEntities()).containsExactly(secondEntity);
          softly.assertThat(fourthMove.getPlanningValues()).containsExactly(secondValue);
        });
  }

  @Test
  void fromSolutionIncompleteValueRange() {
    var solutionDescriptor = TestdataIncompleteValueRangeSolution.buildSolutionDescriptor();
    var variableMetaModel =
        solutionDescriptor
            .getMetaModel()
            .genuineEntity(TestdataIncompleteValueRangeEntity.class)
            .basicVariable();

    // The point of this test is to ensure that the move provider skips values that are not in the
    // value range.
    var solution = TestdataIncompleteValueRangeSolution.generateSolution(2, 2);
    var valueNotInValueRange = new TestdataValue("third");
    solution.setValueListNotInValueRange(Collections.singletonList(valueNotInValueRange));

    var firstEntity = solution.getEntityList().get(0);
    firstEntity.setValue(null);
    var secondEntity = solution.getEntityList().get(1);
    secondEntity.setValue(null);
    var firstValue = solution.getValueList().get(0);
    var secondValue = solution.getValueList().get(1);

    var moveIterable =
        createMoveIterable(
            new ChangeMoveProvider<>(variableMetaModel), solutionDescriptor, solution);
    assertThat(moveIterable).hasSize(4);

    var moveList =
        StreamSupport.stream(moveIterable.spliterator(), false)
            .map(
                m ->
                    (ChangeMove<
                            TestdataIncompleteValueRangeSolution,
                            TestdataIncompleteValueRangeEntity,
                            TestdataValue>)
                        m)
            .toList();
    assertThat(moveList).hasSize(4);

    var firstMove = moveList.get(0);
    assertSoftly(
        softly -> {
          softly.assertThat(firstMove.getPlanningEntities()).containsExactly(firstEntity);
          softly.assertThat(firstMove.getPlanningValues()).containsExactly(firstValue);
        });

    var secondMove = moveList.get(1);
    assertSoftly(
        softly -> {
          softly.assertThat(secondMove.getPlanningEntities()).containsExactly(firstEntity);
          softly.assertThat(secondMove.getPlanningValues()).containsExactly(secondValue);
        });

    var thirdMove = moveList.get(2);
    assertSoftly(
        softly -> {
          softly.assertThat(thirdMove.getPlanningEntities()).containsExactly(secondEntity);
          softly.assertThat(thirdMove.getPlanningValues()).containsExactly(firstValue);
        });

    var fourthMove = moveList.get(3);
    assertSoftly(
        softly -> {
          softly.assertThat(fourthMove.getPlanningEntities()).containsExactly(secondEntity);
          softly.assertThat(fourthMove.getPlanningValues()).containsExactly(secondValue);
        });
  }

  @Test
  void fromEntity() {
    var solutionDescriptor = TestdataEntityProvidingSolution.buildSolutionDescriptor();
    var variableMetaModel =
        solutionDescriptor
            .getMetaModel()
            .genuineEntity(TestdataEntityProvidingEntity.class)
            .basicVariable();

    var solution = TestdataEntityProvidingSolution.generateSolution(2, 2);
    var firstEntity = solution.getEntityList().get(0);
    var secondEntity = solution.getEntityList().get(1);
    var firstValue = firstEntity.getValueRange().get(0);

    // One move is expected:
    // - firstEntity is already assigned to firstValue, the only possible value; skip.
    // - Assign secondEntity to firstValue,
    //   as it is currently assigned to secondValue, and the value range only contains firstValue.
    var moveIterable =
        createMoveIterable(
            new ChangeMoveProvider<>(variableMetaModel), solutionDescriptor, solution);
    assertThat(moveIterable).hasSize(1);

    var moveList =
        StreamSupport.stream(moveIterable.spliterator(), false)
            .map(
                m ->
                    (ChangeMove<
                            TestdataEntityProvidingSolution,
                            TestdataEntityProvidingEntity,
                            TestdataValue>)
                        m)
            .toList();
    assertThat(moveList).hasSize(1);

    var firstMove = moveList.get(0);
    assertSoftly(
        softly -> {
          softly.assertThat(firstMove.getPlanningEntities()).containsExactly(secondEntity);
          softly.assertThat(firstMove.getPlanningValues()).hasSize(1).containsExactly(firstValue);
        });
  }

  @Test
  void fromEntityAllowsUnassigned() {
    var solutionDescriptor =
        TestdataAllowsUnassignedEntityProvidingSolution.buildSolutionDescriptor();
    var variableMetaModel =
        solutionDescriptor
            .getMetaModel()
            .genuineEntity(TestdataAllowsUnassignedEntityProvidingEntity.class)
            .basicVariable();

    var solution = TestdataAllowsUnassignedEntityProvidingSolution.generateSolution(2, 2);
    var secondEntity = solution.getEntityList().get(1);
    var firstValue = solution.getEntityList().get(0).getValueRange().get(0);

    // secondEntity is assigned to secondValue and can change to firstValue.
    // firstEntity already has its only non-null value. Unassign moves are generated separately.
    var moveIterable =
        createMoveIterable(
            new ChangeMoveProvider<>(variableMetaModel), solutionDescriptor, solution);
    assertThat(moveIterable).hasSize(1);

    var moveList =
        StreamSupport.stream(moveIterable.spliterator(), false)
            .map(
                m ->
                    (ChangeMove<
                            TestdataAllowsUnassignedEntityProvidingSolution,
                            TestdataAllowsUnassignedEntityProvidingEntity,
                            TestdataValue>)
                        m)
            .toList();
    assertThat(moveList).hasSize(1);

    var firstMove = moveList.get(0);
    assertSoftly(
        softly -> {
          softly.assertThat(firstMove.getPlanningEntities()).containsExactly(secondEntity);
          softly.assertThat(firstMove.getPlanningValues()).containsExactly(firstValue);
        });
  }

  @Test
  void pinnedEntitySkipped() {
    var solutionDescriptor = TestdataPinnedSolution.buildSolutionDescriptor();
    var variableMetaModel =
        solutionDescriptor.getMetaModel().genuineEntity(TestdataPinnedEntity.class).basicVariable();

    var solution = TestdataPinnedSolution.generateSolution(2, 2);
    var firstEntity = solution.getEntityList().get(0);
    var secondEntity = solution.getEntityList().get(1);
    var firstValue = solution.getValueList().get(0);
    firstEntity.setPinned(true);

    var moveIterable =
        createMoveIterable(
            new ChangeMoveProvider<>(variableMetaModel), solutionDescriptor, solution);
    var moveList =
        StreamSupport.stream(moveIterable.spliterator(), false)
            .map(
                move ->
                    (ChangeMove<TestdataPinnedSolution, TestdataPinnedEntity, TestdataValue>) move)
            .toList();
    assertThat(moveList).hasSize(1);

    var move = moveList.get(0);
    assertSoftly(
        softly -> {
          softly.assertThat(move.getPlanningEntities()).containsExactly(secondEntity);
          softly.assertThat(move.getPlanningValues()).containsExactly(firstValue);
        });
  }

  @Test
  void fromSolutionAllowsUnassigned() {
    var solutionDescriptor = TestdataAllowsUnassignedSolution.buildSolutionDescriptor();
    var variableMetaModel =
        solutionDescriptor
            .getMetaModel()
            .genuineEntity(TestdataAllowsUnassignedEntity.class)
            .basicVariable();

    var solution = TestdataAllowsUnassignedSolution.generateSolution(2, 2);
    var secondEntity = solution.getEntityList().get(1); // Assigned to secondValue.
    var firstValue = solution.getValueList().get(0); // Not assigned to any entity.

    // The unassigned first entity is handled by AssignMoveProvider. The second entity can change
    // from secondValue to firstValue. Unassign moves are generated separately.
    var moveIterable =
        createMoveIterable(
            new ChangeMoveProvider<>(variableMetaModel), solutionDescriptor, solution);
    var moveList =
        StreamSupport.stream(moveIterable.spliterator(), false)
            .map(
                m ->
                    (ChangeMove<
                            TestdataAllowsUnassignedSolution,
                            TestdataAllowsUnassignedEntity,
                            TestdataValue>)
                        m)
            .toList();
    assertThat(moveList).hasSize(1);

    var firstMove = moveList.get(0);
    assertSoftly(
        softly -> {
          softly.assertThat(firstMove.getPlanningEntities()).containsExactly(secondEntity);
          softly.assertThat(firstMove.getPlanningValues()).containsExactly(firstValue);
        });
  }

  private <Solution_> Iterable<Move<Solution_>> createMoveIterable(
      MoveProvider<Solution_> moveProvider,
      SolutionDescriptor<Solution_> solutionDescriptor,
      Solution_ solution) {
    var moveStreamFactory =
        new DefaultMoveStreamFactory<>(solutionDescriptor, EnvironmentMode.TRACKED_FULL_ASSERT);
    var moveStream = moveProvider.build(moveStreamFactory);
    var scoreDirector = createScoreDirector(solutionDescriptor, solution);
    var neighborhoodSession = moveStreamFactory.createSession(new SessionContext<>(scoreDirector));
    solutionDescriptor.visitAll(scoreDirector.getWorkingSolution(), neighborhoodSession::insert);
    neighborhoodSession.settle();
    return moveStream.getMoveIterable(neighborhoodSession);
  }

  private <Solution_> InnerScoreDirector<Solution_, ?> createScoreDirector(
      SolutionDescriptor<Solution_> solutionDescriptor, Solution_ solution) {
    var firstEntityClass = solutionDescriptor.getMetaModel().genuineEntities().get(0).type();
    var constraintProvider = new TestingConstraintProvider(firstEntityClass);
    var scoreDirectorFactory =
        new BavetConstraintStreamScoreDirectorFactory<>(
            solutionDescriptor, constraintProvider, EnvironmentMode.TRACKED_FULL_ASSERT, false);
    var scoreDirector = scoreDirectorFactory.buildScoreDirector();
    scoreDirector.setWorkingSolution(solution);
    return scoreDirector;
  }

  // The specifics of the constraint provider are not important for this test,
  // as the score will never be calculated.
  private record TestingConstraintProvider(Class<?> entityClass) implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
      return new Constraint[] {alwaysPenalizingConstraint(constraintFactory)};
    }

    private Constraint alwaysPenalizingConstraint(ConstraintFactory constraintFactory) {
      return constraintFactory
          .forEach(entityClass)
          .penalize(SimpleScore.ONE)
          .asConstraint("Always penalize");
    }
  }
}
