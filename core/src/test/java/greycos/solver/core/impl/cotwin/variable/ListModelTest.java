package greycos.solver.core.impl.cotwin.variable;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.testcotwin.shadow.simple_list_next.TestdataDeclarativeSimpleNextListConstraintProvider;
import greycos.solver.core.testcotwin.shadow.simple_list_next.TestdataDeclarativeSimpleNextListEntity;
import greycos.solver.core.testcotwin.shadow.simple_list_next.TestdataDeclarativeSimpleNextListSolution;
import greycos.solver.core.testcotwin.shadow.simple_list_next.TestdataDeclarativeSimpleNextListValue;

import org.junit.jupiter.api.Test;

class ListModelTest {
  @Test
  void simpleNextList() {
    var solverConfig =
        new SolverConfig()
            .withSolutionClass(TestdataDeclarativeSimpleNextListSolution.class)
            .withEntityClasses(
                TestdataDeclarativeSimpleNextListEntity.class,
                TestdataDeclarativeSimpleNextListValue.class)
            .withConstraintProviderClass(TestdataDeclarativeSimpleNextListConstraintProvider.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withTerminationConfig(new TerminationConfig().withMoveCountLimit(2_000L));

    var entityList =
        List.of(
            new TestdataDeclarativeSimpleNextListEntity("e1", 0, 1000),
            new TestdataDeclarativeSimpleNextListEntity("e2", 0, 500));
    var valueList =
        List.of(
            new TestdataDeclarativeSimpleNextListValue("v1", 60),
            new TestdataDeclarativeSimpleNextListValue("v2", 120),
            new TestdataDeclarativeSimpleNextListValue("v3", 30),
            new TestdataDeclarativeSimpleNextListValue("v4", 90),
            new TestdataDeclarativeSimpleNextListValue("v5", 10));
    var problem = new TestdataDeclarativeSimpleNextListSolution(entityList, valueList);
    var solution =
        SolverFactory.<TestdataDeclarativeSimpleNextListSolution>create(solverConfig)
            .buildSolver()
            .solve(problem);

    // Values are scheduled backwards from the end of each list.
    for (var entity : solution.getEntityList()) {
      var endTime = entity.getStartTime();
      var values = entity.getValues();
      for (var i = values.size() - 1; i >= 0; i--) {
        var value = values.get(i);
        assertThat(value.getEndTime()).isEqualTo(endTime);
        assertThat(value.getStartTime()).isEqualTo(endTime - value.getDuration());
        endTime = value.getStartTime();
      }
    }
  }
}
