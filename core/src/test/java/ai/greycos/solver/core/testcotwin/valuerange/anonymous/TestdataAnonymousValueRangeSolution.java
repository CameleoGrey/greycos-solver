package ai.greycos.solver.core.testcotwin.valuerange.anonymous;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRange;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeFactory;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningSolution
public class TestdataAnonymousValueRangeSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataAnonymousValueRangeSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataAnonymousValueRangeSolution.class, TestdataAnonymousValueRangeEntity.class);
  }

  private List<TestdataAnonymousValueRangeEntity> entityList;

  private SimpleScore score;

  public TestdataAnonymousValueRangeSolution() {}

  public TestdataAnonymousValueRangeSolution(String code) {
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
  public ValueRange<Integer> createIntValueRange() {
    return ValueRangeFactory.createIntValueRange(0, 3);
  }

  @ValueRangeProvider
  public ValueRange<Long> createLongValueRange() {
    return ValueRangeFactory.createLongValueRange(1_000L, 1_003L);
  }

  @ValueRangeProvider
  public ValueRange<BigInteger> createBigIntegerValueRange() {
    return ValueRangeFactory.createBigIntegerValueRange(
        BigInteger.valueOf(1_000_000L), BigInteger.valueOf(1_000_003L));
  }

  @ValueRangeProvider
  public ValueRange<BigDecimal> createBigDecimalValueRange() {
    return ValueRangeFactory.createBigDecimalValueRange(
        new BigDecimal("0.00"), new BigDecimal("0.03"));
  }
}
