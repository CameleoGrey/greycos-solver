package greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThat;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListFirstValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSecondValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.api.Test;

class MultipleListAssignmentRecommendationTest {

  @Test
  void selectsTheConstructionPhaseForEachElementFamily() {
    var factory =
        SolverFactory.<TestdataMultipleListSolution>create(
            new SolverConfig()
                .withSolutionClass(TestdataMultipleListSolution.class)
                .withEntityClasses(
                    TestdataMultipleListEntity.class,
                    TestdataMultipleListFirstValue.class,
                    TestdataMultipleListSecondValue.class)
                .withConstraintProviderClass(RecommendationConstraints.class));
    var manager = SolutionManager.<TestdataMultipleListSolution, SimpleScore>create(factory);
    var solution = TestdataMultipleListSolution.generateInitializedSolution(2, 2, 2);
    var first = solution.getFirstValueList().getFirst();
    var second = solution.getSecondValueList().getFirst();
    var owner = solution.getEntityList().getFirst();

    var firstRecommendations =
        manager.recommendAssignment(
            solution, first, value -> value.getEntity().getCode() + ":" + value.getIndex());
    var secondRecommendations =
        manager.recommendAssignment(
            solution, second, value -> value.getEntity().getCode() + ":" + value.getIndex());

    assertThat(firstRecommendations)
        .extracting(recommendation -> recommendation.proposition())
        .containsExactlyInAnyOrder("entity-0:0", "entity-1:0", "entity-1:1");
    assertThat(secondRecommendations)
        .extracting(recommendation -> recommendation.proposition())
        .containsExactlyInAnyOrder("entity-0:0", "entity-1:0", "entity-1:1");
    assertThat(owner.getFirstValueList()).containsExactly(first);
    assertThat(owner.getSecondValueList()).containsExactly(second);
    assertThat(first.getEntity()).isSameAs(owner);
    assertThat(second.getEntity()).isSameAs(owner);
    assertThat(second.getQuantity()).isZero();
  }

  public static final class RecommendationConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataMultipleListFirstValue.class)
            .penalize(SimpleScore.ONE, TestdataMultipleListFirstValue::getIndex)
            .asConstraint("first positions"),
        factory
            .forEach(TestdataMultipleListSecondValue.class)
            .penalize(SimpleScore.ONE, TestdataMultipleListSecondValue::getIndex)
            .asConstraint("second positions")
      };
    }
  }
}
