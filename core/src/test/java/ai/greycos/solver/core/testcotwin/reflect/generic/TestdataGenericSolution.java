package ai.greycos.solver.core.testcotwin.reflect.generic;

import java.util.List;
import java.util.Map;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningSolution
public class TestdataGenericSolution<T> extends TestdataObject {

  public static SolutionDescriptor<TestdataGenericSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataGenericSolution.class, TestdataGenericEntity.class);
  }

  private List<TestdataGenericValue<T>> valueList;
  private List<? extends TestdataGenericValue<T>> subTypeValueList;
  private List<TestdataGenericValue<Map<T, TestdataGenericValue<T>>>> complexGenericValueList;
  private List<TestdataGenericEntity<T>> entityList;

  private SimpleScore score;

  public TestdataGenericSolution() {}

  public TestdataGenericSolution(String code) {
    super(code);
  }

  public TestdataGenericSolution(
      String code,
      List<TestdataGenericValue<T>> valueList,
      List<TestdataGenericValue<Map<T, TestdataGenericValue<T>>>> complexGenericValueList,
      List<TestdataGenericEntity<T>> entityList) {
    super(code);
    this.valueList = valueList;
    this.complexGenericValueList = complexGenericValueList;
    this.entityList = entityList;
  }

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public List<TestdataGenericValue<T>> getValueList() {
    return valueList;
  }

  @ValueRangeProvider(id = "complexGenericValueRange")
  @ProblemFactCollectionProperty
  public List<TestdataGenericValue<Map<T, TestdataGenericValue<T>>>> getComplexGenericValueList() {
    return complexGenericValueList;
  }

  @ValueRangeProvider(id = "subTypeValueRange")
  @ProblemFactCollectionProperty
  public List<? extends TestdataGenericValue<T>> getSubTypeValueList() {
    return subTypeValueList;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataGenericEntity<T>> getEntityList() {
    return entityList;
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

}
