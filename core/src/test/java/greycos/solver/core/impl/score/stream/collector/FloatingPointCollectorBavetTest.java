package greycos.solver.core.impl.score.stream.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.heuristic.move.Move;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;

class FloatingPointCollectorBavetTest {

  @Test
  void groupedFloatingCollectorsHandleVariableUpdatesTemporaryUndoAndEntityChanges() {
    ConstraintProvider provider =
        factory ->
            new Constraint[] {
              factory
                  .forEach(TestdataEntity.class)
                  .groupBy(
                      ConstraintCollectors.<TestdataEntity>sumFloat(
                          entity -> Float.parseFloat(entity.getValue().getCode())),
                      ConstraintCollectors.<TestdataEntity>sumDouble(
                          entity -> Double.parseDouble(entity.getValue().getCode())),
                      ConstraintCollectors.<TestdataEntity>averageFloat(
                          entity -> Float.parseFloat(entity.getValue().getCode())),
                      ConstraintCollectors.<TestdataEntity>averageDouble(
                          entity -> Double.parseDouble(entity.getValue().getCode())))
                  .penalize(
                      SimpleScore.ONE,
                      (sumFloat, sumDouble, averageFloat, averageDouble) ->
                          3L * sumFloat.longValue()
                              + 3L * sumDouble.longValue()
                              + (averageFloat == null ? 0L : Math.round(3.0 * averageFloat))
                              + (averageDouble == null ? 0L : Math.round(3.0 * averageDouble)))
                  .asConstraint("Exact floating groups")
            };
    var scoreDirectorFactory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataSolution, SimpleScore>(
            TestdataSolution.buildSolutionDescriptor(), provider, EnvironmentMode.FULL_ASSERT);
    try (var director = scoreDirectorFactory.createScoreDirectorBuilder().build()) {
      var large = new TestdataValue("0x1p100");
      var opposite = new TestdataValue("-0x1p100");
      var one = new TestdataValue("1");
      var three = new TestdataValue("3");
      var five = new TestdataValue("5");
      var first = new TestdataEntity("first", large);
      var middle = new TestdataEntity("middle", one);
      var last = new TestdataEntity("last", opposite);
      var solution = new TestdataSolution("floating-groups");
      solution.setValueList(List.of(large, opposite, one, three, five));
      solution.setEntityList(new ArrayList<>(List.of(first, middle, last)));
      director.setWorkingSolution(solution);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-8));

      director.beforeVariableChanged(middle, "value");
      middle.setValue(three);
      director.afterVariableChanged(middle, "value");
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-24));

      var variable =
          director
              .getSolutionDescriptor()
              .findEntityDescriptorOrFail(TestdataEntity.class)
              .getGenuineVariableDescriptor("value");
      Move<TestdataSolution> temporary = new ChangeMove<>(variable, middle, five);
      assertThat(director.executeTemporaryMove(temporary, true).raw())
          .isEqualTo(SimpleScore.of(-40));
      assertThat(middle.getValue()).isSameAs(three);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-24));

      director.beforeVariableChanged(middle, "value");
      middle.setValue(one);
      director.afterVariableChanged(middle, "value");
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-8));

      for (var entity : List.of(first, last)) {
        director.beforeEntityRemoved(entity);
        solution.getEntityList().remove(entity);
        director.afterEntityRemoved(entity);
      }
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-12));
      for (var entity : List.of(last, first)) {
        director.beforeEntityAdded(entity);
        solution.getEntityList().add(entity);
        director.afterEntityAdded(entity);
      }
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-8));
      for (var entity : List.copyOf(solution.getEntityList())) {
        director.beforeEntityRemoved(entity);
        solution.getEntityList().remove(entity);
        director.afterEntityRemoved(entity);
      }
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.ZERO);
    }
  }
}
