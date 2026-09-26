package greycos.solver.core.impl.heuristic.thread;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.heuristic.move.NoChangeMove;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.preview.api.move.builtin.Moves;
import greycos.solver.core.testcotwin.shadow.no_inconsistent_field.TestdataDependencyNoInconsistentFieldConstraintProvider;
import greycos.solver.core.testcotwin.shadow.no_inconsistent_field.TestdataDependencyNoInconsistentFieldEntity;
import greycos.solver.core.testcotwin.shadow.no_inconsistent_field.TestdataDependencyNoInconsistentFieldSolution;
import greycos.solver.core.testcotwin.shadow.no_inconsistent_field.TestdataDependencyNoInconsistentFieldValue;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Timeout(30)
class MoveEvaluationPipelineStructuralTest {

  @ParameterizedTest
  @CsvSource({"1, false", "1, true", "2, false", "2, true"})
  void replaysStructuralCyclesAndRecovery(int workerCount, boolean supplyReplayScore)
      throws InterruptedException {
    var descriptor = TestdataDependencyNoInconsistentFieldSolution.buildSolutionDescriptor();
    var variableMetaModel =
        descriptor
            .getMetaModel()
            .genuineEntity(TestdataDependencyNoInconsistentFieldEntity.class)
            .listVariable("values", TestdataDependencyNoInconsistentFieldValue.class);
    var entity = new TestdataDependencyNoInconsistentFieldEntity("entity");
    var first = new TestdataDependencyNoInconsistentFieldValue("first");
    var second = new TestdataDependencyNoInconsistentFieldValue("second");
    second.setDependencies(List.of(first));
    entity.setValues(new ArrayList<>(List.of(first, second)));
    var solution =
        new TestdataDependencyNoInconsistentFieldSolution(List.of(entity), List.of(first, second));
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<
            TestdataDependencyNoInconsistentFieldSolution, HardSoftScore>(
            descriptor,
            new TestdataDependencyNoInconsistentFieldConstraintProvider(),
            EnvironmentMode.FULL_ASSERT);
    try (var director =
            new BavetConstraintStreamScoreDirector.Builder<>(factory, EnvironmentMode.FULL_ASSERT)
                .build();
        var executor = Executors.newFixedThreadPool(workerCount);
        var pipeline =
            new MoveEvaluationPipeline<TestdataDependencyNoInconsistentFieldSolution>(
                executor, workerCount, 2, 0, false, true, true, true, true, true)) {
      director.setWorkingSolution(solution);
      var initialScore = director.calculateScore();
      assertThat(initialScore.isStructurallyFlawed()).isFalse();
      var firstEndTime = first.getEndTime();
      var secondEndTime = second.getEndTime();
      pipeline.start(director);

      // The dependency and list predecessor now point in opposite directions, creating a cycle.
      var swap = Moves.swap(variableMetaModel, entity, 0, entity, 1);
      director.getMoveDirector().executeAllowingStructurallyFlawedSolutions(swap);
      var flawedScore = director.calculateScore();
      assertThat(flawedScore.isStructurallyFlawed()).isTrue();
      pipeline.applyState(1, swap, supplyReplayScore ? flawedScore : null);
      pipeline.startNextStep(1);
      pipeline.submit(0, NoChangeMove.getInstance());
      pipeline.submit(1, swap);
      assertThat(pipeline.takeScore(1, 0)).isEqualTo(flawedScore);
      assertThat(pipeline.takeScore(1, 1)).isEqualTo(initialScore);

      // Evaluating a repair must leave each worker at its flawed baseline after undo.
      pipeline.submit(2, NoChangeMove.getInstance());
      assertThat(pipeline.takeScore(1, 2)).isEqualTo(flawedScore);
      assertThat(entity.getValues()).containsExactly(second, first);
      assertThat(director.calculateScore()).isEqualTo(flawedScore);

      director.getMoveDirector().executeAllowingStructurallyFlawedSolutions(swap);
      var restoredScore = director.calculateScore();
      assertThat(restoredScore).isEqualTo(initialScore);
      pipeline.applyStep(2, swap, restoredScore);
      pipeline.startNextStep(2);
      pipeline.submit(0, NoChangeMove.getInstance());
      assertThat(pipeline.takeScore(2, 0)).isEqualTo(initialScore);
      assertThat(entity.getValues()).containsExactly(first, second);
      assertThat(first.getEntity()).isSameAs(entity);
      assertThat(second.getEntity()).isSameAs(entity);
      assertThat(first.getPreviousValue()).isNull();
      assertThat(second.getPreviousValue()).isSameAs(first);
      assertThat(first.getEndTime()).isEqualTo(firstEndTime);
      assertThat(second.getEndTime()).isEqualTo(secondEndTime);
    }
  }
}
