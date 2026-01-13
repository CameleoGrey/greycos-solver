package ai.greycos.solver.core.testcotwin.valuerange.anonymous;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningSolution
public class TestdataAnonymousListSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataAnonymousListSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataAnonymousListSolution.class, TestdataAnonymousValueRangeEntity.class);
  }

  private List<TestdataAnonymousValueRangeEntity> entityList;

  private SimpleScore score;

  public TestdataAnonymousListSolution() {}

  public TestdataAnonymousListSolution(String code) {
    super(code);
  }

  @PlanningEntityCollectionProperty
  public List<TestdataAnonymousValueRangeEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataAnonymousValueRangeEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

  @ValueRangeProvider
  public List<Integer> createIntegerList() {
    return List.of(0, 1);
  }

  @ValueRangeProvider
  public List<Long> createLongList() {
    return List.of(0L, 1L);
  }

  @ValueRangeProvider
  public List<? super Number> createNumberList() { // Test the wildcards too.
    return List.of(0, BigInteger.TEN);
  }

  @ValueRangeProvider
  public List<BigInteger> createBigIntegerList() {
    return List.of(BigInteger.ZERO, BigInteger.TEN);
  }

  @ValueRangeProvider
  public List<BigDecimal> createBigDecimalList() {
    return List.of(BigDecimal.ZERO, BigDecimal.TEN);
  }
}
