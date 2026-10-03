package greycos.solver.core.impl.heuristic.move;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.heuristic.selector.move.generic.SelectorBasedChangeMove;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.MutableSolutionView;

import org.junit.jupiter.api.Test;

class CompositeMoveShadowVariableTest {

  @Test
  void nestedChildrenAndDoabilitySeeFreshShadowsWithoutUpdatingUntouchedEntities() {
    var solution = new CountedSolution();
    var first = new CountedEntity(0);
    var second = new CountedEntity(1);
    var untouched = new CountedEntity(2);
    solution.entities = List.of(first, second, untouched);
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(CountedSolution.class, CountedEntity.class);
    var variable =
        descriptor
            .findEntityDescriptorOrFail(CountedEntity.class)
            .getGenuineVariableDescriptor("value");
    var meta =
        descriptor
            .getMetaModel()
            .genuineEntity(CountedEntity.class)
            .basicVariable("value", Integer.class);
    var skipped =
        new AbstractSelectorBasedMove<CountedSolution>() {
          @Override
          public boolean isMoveDoable(ScoreDirector<CountedSolution> director) {
            assertThat(first.doubled).isEqualTo(2);
            return false;
          }

          @Override
          protected void execute(
              MutableSolutionView<CountedSolution> view,
              VariableDescriptorAwareScoreDirector<CountedSolution> director) {
            throw new AssertionError("A non-doable child must be skipped.");
          }
        };
    Move<CountedSolution> preview =
        view -> {
          assertThat(first.doubled).isEqualTo(2);
          assertThat(second.doubled).isEqualTo(4);
          view.changeVariable(meta, first, 3);
        };
    var nested =
        SelectorBasedCompositeMove.buildMove(
            new SelectorBasedChangeMove<>(variable, second, 2), preview);
    var composite =
        SelectorBasedCompositeMove.buildMove(
            new SelectorBasedChangeMove<>(variable, first, 1),
            skipped,
            nested,
            new SelectorBasedChangeMove<>(variable, second, 3));
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<CountedSolution, SimpleScore>(
            descriptor, new CountedConstraintProvider(), EnvironmentMode.FULL_ASSERT);
    try (var director = factory.createScoreDirectorBuilder().build()) {
      director.setWorkingSolution(solution);
      var originalScore = director.calculateScore();
      resetCounts(solution);
      var trialScore =
          director.executeTemporaryMove(
              composite,
              view -> {
                assertChangedState(solution);
                assertThat(first.updateCount).isEqualTo(2);
                assertThat(second.updateCount).isEqualTo(2);
                assertThat(untouched.updateCount).isZero();
              },
              false);
      assertThat(solution.entities.stream().map(e -> e.value).toList()).containsExactly(0, 1, 2);
      assertThat(solution.entities.stream().map(e -> e.doubled).toList()).containsExactly(0, 2, 4);
      assertThat(director.calculateScore()).isEqualTo(originalScore);
      resetCounts(solution);
      director.executeMove(composite);
      assertChangedState(solution);
      assertThat(director.calculateScore()).isEqualTo(trialScore);
      assertThat(first.updateCount).isEqualTo(2);
      assertThat(second.updateCount).isEqualTo(2);
      assertThat(untouched.updateCount).isZero();
      director.assertWorkingScoreFromScratch(trialScore, composite);
    }
  }

  private static void resetCounts(CountedSolution solution) {
    solution.entities.forEach(entity -> entity.updateCount = 0);
  }

  private static void assertChangedState(CountedSolution solution) {
    assertThat(solution.entities.stream().map(e -> e.value).toList()).containsExactly(3, 3, 2);
    assertThat(solution.entities.stream().map(e -> e.doubled).toList()).containsExactly(6, 6, 4);
  }

  @PlanningSolution
  public static class CountedSolution {
    @PlanningEntityCollectionProperty public List<CountedEntity> entities;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "range")
    public List<Integer> values = List.of(0, 1, 2, 3);

    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class CountedEntity {
    @PlanningVariable(valueRangeProviderRefs = "range")
    public Integer value;

    @ShadowVariable(supplierName = "doubleValue")
    public Integer doubled;

    public int updateCount;

    public CountedEntity() {}

    CountedEntity(Integer value) {
      this.value = value;
    }

    @ShadowSources("value")
    public Integer doubleValue() {
      updateCount++;
      return value == null ? null : 2 * value;
    }
  }

  public static class CountedConstraintProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(CountedEntity.class)
            .reward(SimpleScore.ONE, entity -> entity.doubled)
            .asConstraint("Doubled value")
      };
    }
  }
}
