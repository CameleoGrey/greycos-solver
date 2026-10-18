package greycos.solver.core.impl.cotwin.variable.declarative;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.testcotwin.shadow.sibling.AbstractTestdataDeclarativeSiblingValue;
import greycos.solver.core.testcotwin.shadow.sibling.TestdataDeclarativeSiblingConstraintProvider;
import greycos.solver.core.testcotwin.shadow.sibling.TestdataDeclarativeSiblingEntity;
import greycos.solver.core.testcotwin.shadow.sibling.TestdataDeclarativeSiblingFirstValue;
import greycos.solver.core.testcotwin.shadow.sibling.TestdataDeclarativeSiblingSecondValue;
import greycos.solver.core.testcotwin.shadow.sibling.TestdataDeclarativeSiblingSolution;

import org.junit.jupiter.api.Test;

class DeclarativeShadowVariablesSiblingValuesTest {

  /**
   * Both siblings override the inherited source getter, so their source metamodels are equal but
   * carry different entity classes; changes to either sibling must reach the graph.
   */
  @Test
  void siblingValues() {
    var solverConfig =
        new SolverConfig()
            .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
            .withSolutionClass(TestdataDeclarativeSiblingSolution.class)
            .withEntityClasses(
                TestdataDeclarativeSiblingEntity.class,
                AbstractTestdataDeclarativeSiblingValue.class,
                TestdataDeclarativeSiblingFirstValue.class,
                TestdataDeclarativeSiblingSecondValue.class)
            .withConstraintProviderClass(TestdataDeclarativeSiblingConstraintProvider.class)
            .withTerminationConfig(new TerminationConfig().withScoreCalculationCountLimit(1000L));
    var solver =
        SolverFactory.<TestdataDeclarativeSiblingSolution>create(solverConfig).buildSolver();

    var problem = new TestdataDeclarativeSiblingSolution();
    problem.setEntities(
        List.of(
            new TestdataDeclarativeSiblingEntity("A"), new TestdataDeclarativeSiblingEntity("B")));
    problem.setValues(
        List.of(
            new TestdataDeclarativeSiblingFirstValue("1"),
            new TestdataDeclarativeSiblingSecondValue("22"),
            new TestdataDeclarativeSiblingFirstValue("333"),
            new TestdataDeclarativeSiblingSecondValue("4444")));

    var solution = solver.solve(problem);

    for (var value : solution.getValues()) {
      var previousCode =
          value instanceof TestdataDeclarativeSiblingFirstValue firstValue
              ? firstValue.getPreviousCode()
              : ((TestdataDeclarativeSiblingSecondValue) value).getPreviousCode();
      assertThat(previousCode)
          .isEqualTo(value.getPrevious() == null ? "" : value.getPrevious().getCode());
    }
  }
}
