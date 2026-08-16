package greycos.solver.core.testcotwin.valuerange.anonymous;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningSolution
public class TestdataAnonymousArraySolution extends TestdataObject {

  public static SolutionDescriptor<TestdataAnonymousArraySolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataAnonymousArraySolution.class, TestdataAnonymousValueRangeEntity.class);
  }

  private List<TestdataAnonymousValueRangeEntity> entityList;

  private SimpleScore score;

  public TestdataAnonymousArraySolution() {}

  public TestdataAnonymousArraySolution(String code) {
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
  public Integer[] createIntegerArray() {
    return new Integer[] {0, 1};
  }

  @ValueRangeProvider
  public Long[] createLongArray() {
    return new Long[] {0L, 1L};
  }

  @ValueRangeProvider
  public Number[] createNumberArray() {
    return new Number[] {0L, 1L};
  }

  @ValueRangeProvider
  public BigInteger[] createBigIntegerArray() {
    return new BigInteger[] {BigInteger.ZERO, BigInteger.TEN};
  }

  @ValueRangeProvider
  public BigDecimal[] createBigDecimalArray() {
    return new BigDecimal[] {BigDecimal.ZERO, BigDecimal.TEN};
  }
}
