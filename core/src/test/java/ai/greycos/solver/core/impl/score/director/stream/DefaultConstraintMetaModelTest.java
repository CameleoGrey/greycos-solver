package ai.greycos.solver.core.impl.score.director.stream;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.util.List;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.testconstraint.TestConstraint;
import ai.greycos.solver.core.testconstraint.TestConstraintFactory;
import ai.greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class DefaultConstraintMetaModelTest {

  @Test
  void test() {
    var solutionDescriptor = TestdataSolution.buildSolutionDescriptor();
    var constraintFactory =
        new TestConstraintFactory<TestdataSolution, SimpleScore>(solutionDescriptor);
    var constraint1 =
        new TestConstraint<>(constraintFactory, "Test Constraint 1", SimpleScore.of(1));
    var constraint2 =
        new TestConstraint<>(constraintFactory, "Test Constraint 2", SimpleScore.of(10));
    var constraint3 =
        new TestConstraint<>(constraintFactory, "Test Constraint 3", SimpleScore.of(100));
    var constraint4 =
        new TestConstraint<>(constraintFactory, "Test Constraint 4", SimpleScore.ZERO);
    var metaModel =
        DefaultConstraintMetaModel.of(List.of(constraint1, constraint2, constraint3, constraint4));

    assertSoftly(
        softly -> {
          softly
              .assertThat(metaModel.getConstraint(constraint1.getConstraintRef()))
              .isSameAs(constraint1);
          softly
              .assertThat(metaModel.getConstraint(constraint2.getConstraintRef()))
              .isSameAs(constraint2);
          softly
              .assertThat(metaModel.getConstraint(constraint3.getConstraintRef()))
              .isSameAs(constraint3);
          softly
              .assertThat(metaModel.getConstraint(constraint4.getConstraintRef()))
              .isSameAs(constraint4);
        });
  }
}
