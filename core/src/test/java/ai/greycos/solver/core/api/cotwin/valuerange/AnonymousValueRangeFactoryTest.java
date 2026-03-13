package ai.greycos.solver.core.api.cotwin.valuerange;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.config.solver.SolverConfig;
import ai.greycos.solver.core.testcotwin.valuerange.anonymous.TestdataAnonymousArraySolution;
import ai.greycos.solver.core.testcotwin.valuerange.anonymous.TestdataAnonymousListSolution;
import ai.greycos.solver.core.testcotwin.valuerange.anonymous.TestdataAnonymousValueRangeEntity;
import ai.greycos.solver.core.testcotwin.valuerange.anonymous.TestdataAnonymousValueRangeSolution;
import ai.greycos.solver.core.testutil.PlannerTestUtils;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class AnonymousValueRangeFactoryTest {

  @Test
  void solveValueRange() {
    SolverConfig solverConfig =
        PlannerTestUtils.buildSolverConfig(
            TestdataAnonymousValueRangeSolution.class, TestdataAnonymousValueRangeEntity.class);

    TestdataAnonymousValueRangeSolution solution = new TestdataAnonymousValueRangeSolution("s1");
    solution.setEntityList(
        Arrays.asList(
            new TestdataAnonymousValueRangeEntity("e1"),
            new TestdataAnonymousValueRangeEntity("e2")));

    TestdataAnonymousValueRangeSolution result = PlannerTestUtils.solve(solverConfig, solution);
    TestdataAnonymousValueRangeEntity entity1 = result.getEntityList().get(0);
    TestdataAnonymousValueRangeEntity entity2 = result.getEntityList().get(1);
    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
          assertEntity(softly, entity1);
          assertEntity(softly, entity2);
        });
    assertThat(solution).isNotNull();
  }

  @Test
  void solveArray() {
    SolverConfig solverConfig =
        PlannerTestUtils.buildSolverConfig(
            TestdataAnonymousArraySolution.class, TestdataAnonymousValueRangeEntity.class);

    TestdataAnonymousArraySolution solution = new TestdataAnonymousArraySolution("s1");
    solution.setEntityList(
        Arrays.asList(
            new TestdataAnonymousValueRangeEntity("e1"),
            new TestdataAnonymousValueRangeEntity("e2")));

    TestdataAnonymousArraySolution result = PlannerTestUtils.solve(solverConfig, solution);
    TestdataAnonymousValueRangeEntity entity1 = result.getEntityList().get(0);
    TestdataAnonymousValueRangeEntity entity2 = result.getEntityList().get(1);
    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
          assertEntity(softly, entity1);
          assertEntity(softly, entity2);
        });
    assertThat(solution).isNotNull();
  }

  @Test
  void solveList() {
    SolverConfig solverConfig =
        PlannerTestUtils.buildSolverConfig(
            TestdataAnonymousListSolution.class, TestdataAnonymousValueRangeEntity.class);

    TestdataAnonymousListSolution solution = new TestdataAnonymousListSolution("s1");
    solution.setEntityList(
        Arrays.asList(
            new TestdataAnonymousValueRangeEntity("e1"),
            new TestdataAnonymousValueRangeEntity("e2")));

    TestdataAnonymousListSolution result = PlannerTestUtils.solve(solverConfig, solution);
    TestdataAnonymousValueRangeEntity entity1 = result.getEntityList().get(0);
    TestdataAnonymousValueRangeEntity entity2 = result.getEntityList().get(1);
    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(result.getScore()).isEqualTo(SimpleScore.ZERO);
          assertEntity(softly, entity1);
          assertEntity(softly, entity2);
        });
    assertThat(solution).isNotNull();
  }

  private static void assertEntity(
      SoftAssertions softly, TestdataAnonymousValueRangeEntity entity) {
    softly.assertThat(entity.getNumberValue()).isNotNull();
    softly.assertThat(entity.getIntegerValue()).isNotNull();
    softly.assertThat(entity.getLongValue()).isNotNull();
    softly.assertThat(entity.getBigIntegerValue()).isNotNull();
    softly.assertThat(entity.getBigDecimalValue()).isNotNull();
  }
}
