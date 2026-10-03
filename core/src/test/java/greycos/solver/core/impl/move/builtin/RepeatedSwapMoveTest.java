package greycos.solver.core.impl.move.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.localsearch.decider.acceptor.tabu.PlanningValueSnapshot;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.builtin.Moves;
import greycos.solver.core.preview.api.neighborhood.stream.dataset.sample.Sample;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarConstraintProvider;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarEntity;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarSolution;
import greycos.solver.core.testcotwin.multivar.TestdataOtherValue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RepeatedSwapMoveTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void repeatedEvaluationAndCommitRefreshValuesButRetainTabuSnapshots(boolean pillar) {
    var solution = TestdataMultiVarSolution.generateSolution(pillar ? 5 : 2, 4, 2);
    var entities = solution.getMultiVarEntityList();
    var left = pillar ? entities.subList(0, 2) : entities.subList(0, 1);
    var right = pillar ? entities.subList(2, 5) : entities.subList(1, 2);
    var values = solution.getValueList();
    var otherValues = solution.getOtherValueList();
    setValues(left, values.get(0), values.get(1), null);
    setValues(right, values.get(2), values.get(3), otherValues.get(0));

    var descriptor = TestdataMultiVarSolution.buildSolutionDescriptor();
    var entityModel = descriptor.getMetaModel().genuineEntity(TestdataMultiVarEntity.class);
    var primary = entityModel.basicVariable("primaryValue", TestdataValue.class);
    var secondary = entityModel.basicVariable("secondaryValue", TestdataValue.class);
    var tertiary =
        entityModel.basicVariable("tertiaryValueAllowedUnassigned", TestdataOtherValue.class);
    Move<TestdataMultiVarSolution> move =
        pillar
            ? Moves.pillarSwap(
                List.of(primary, secondary, tertiary), Sample.of(left), Sample.of(right))
            : Moves.swap(List.of(primary, secondary, tertiary), left.getFirst(), right.getFirst());

    // Early inspection must not freeze assignments used by a later execution.
    assertThat(move.toString()).isNotBlank();
    assertThat(move.getPlanningValues()).contains(null, otherValues.get(0));
    setValues(left, values.get(1), values.get(0), otherValues.get(1));
    setValues(right, values.get(3), values.get(2), null);
    var original = state(entities);
    var swapped = new ArrayList<List<Object>>();
    for (var ignored : left) swapped.add(Arrays.asList(values.get(3), values.get(2), null));
    for (var ignored : right)
      swapped.add(List.of(values.get(1), values.get(0), otherValues.get(1)));

    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataMultiVarSolution, SimpleScore>(
            descriptor, new TestdataMultiVarConstraintProvider(), EnvironmentMode.FULL_ASSERT);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var originalScore = director.calculateScore();
      var snapshots = new ArrayList<PlanningValueSnapshot>();
      for (int i = 0; i < 2; i++) {
        director.executeTemporaryMove(
            move,
            view -> {
              assertThat(state(entities)).isEqualTo(swapped);
              snapshots.add(PlanningValueSnapshot.capture(director, move));
            },
            true);
        assertThat(state(entities)).isEqualTo(original);
        assertThat(director.calculateScore()).isEqualTo(originalScore);
        assertThat(move.getPlanningValues())
            .containsExactly(
                values.get(1),
                values.get(3),
                values.get(0),
                values.get(2),
                otherValues.get(1),
                null);
      }
      director.executeMove(move);
      assertThat(state(entities)).isEqualTo(swapped);
      director.executeMove(move);
      assertThat(state(entities)).isEqualTo(original);

      // Introduce an unrelated change, then reuse the same swap and its private metadata storage.
      for (var entity : left)
        director.getMoveDirector().changeVariable(tertiary, entity, otherValues.get(0));
      director.executeTemporaryMove(move, true);
      assertThat(move.getPlanningValues())
          .contains(otherValues.get(0))
          .doesNotContain(otherValues.get(1));
      for (var snapshot : snapshots) {
        assertThat(snapshot.values())
            .containsExactly(
                values.get(1),
                values.get(3),
                values.get(0),
                values.get(2),
                otherValues.get(1),
                null);
      }
      director.assertWorkingScoreFromScratch(director.calculateScore(), move);
    }
  }

  private static void setValues(
      List<TestdataMultiVarEntity> entities,
      TestdataValue primary,
      TestdataValue secondary,
      TestdataOtherValue tertiary) {
    for (var entity : entities) {
      entity.setPrimaryValue(primary);
      entity.setSecondaryValue(secondary);
      entity.setTertiaryValueAllowedUnassigned(tertiary);
    }
  }

  private static List<List<Object>> state(List<TestdataMultiVarEntity> entities) {
    return entities.stream()
        .map(
            entity ->
                Arrays.<Object>asList(
                    entity.getPrimaryValue(),
                    entity.getSecondaryValue(),
                    entity.getTertiaryValueAllowedUnassigned()))
        .toList();
  }
}
