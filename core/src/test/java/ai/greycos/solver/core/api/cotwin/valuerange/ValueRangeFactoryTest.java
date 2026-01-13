package ai.greycos.solver.core.api.cotwin.valuerange;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.core.testcotwin.valuerange.TestdataValueRangeEntity;
import ai.greycos.solver.core.testcotwin.valuerange.TestdataValueRangeSolution;
import ai.greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;

class ValueRangeFactoryTest {

  @Test
  void solve() {
    SolverConfig solverConfig =
        PlannerTestUtils.buildSolverConfig(
            TestdataValueRangeSolution.class, TestdataValueRangeEntity.class);

    TestdataValueRangeSolution solution = new TestdataValueRangeSolution("s1");
    solution.setEntityList(
        Arrays.asList(new TestdataValueRangeEntity("e1"), new TestdataValueRangeEntity("e2")));

    solution = PlannerTestUtils.solve(solverConfig, solution);
    assertThat(solution).isNotNull();
  }
}
