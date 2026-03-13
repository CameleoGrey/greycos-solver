package ai.greycos.solver.core.testcotwin.multivar;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataMultiVarSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataMultiVarSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataMultiVarSolution.class, TestdataMultiVarEntity.class);
  }

  public static TestdataMultiVarSolution generateSolution(
      int entityListSize, int valueListSize, int otherValueListSize) {
    return generateSolution(entityListSize, valueListSize, otherValueListSize, true);
  }

  public static TestdataMultiVarSolution generateUninitializedSolution(
      int entityListSize, int valueListSize) {
    return generateSolution(entityListSize, valueListSize, valueListSize, false);
  }

  private static TestdataMultiVarSolution generateSolution(
      int entityListSize, int valueListSize, int otherValueListSize, boolean initialize) {
    var solution = new TestdataMultiVarSolution();
    var valueList = new ArrayList<TestdataValue>(valueListSize);
    var otherValueList = new ArrayList<TestdataOtherValue>(otherValueListSize);
    for (int i = 0; i < valueListSize; i++) {
      valueList.add(new TestdataValue("Generated Value " + i));
    }
    for (int i = 0; i < otherValueListSize; i++) {
      otherValueList.add(new TestdataOtherValue("Generated Other Value " + i));
    }
    solution.setValueList(valueList);
    solution.setOtherValueList(otherValueList);
    var entityList = new ArrayList<TestdataMultiVarEntity>(entityListSize);
    for (int i = 0; i < entityListSize; i++) {
      var entity = new TestdataMultiVarEntity("Entity " + i);
      if (initialize) {
        entity.setPrimaryValue(valueList.get(i % valueListSize));
        entity.setSecondaryValue(valueList.get((i + 1) % valueListSize));
        entity.setTertiaryValueAllowedUnassigned(otherValueList.get(i % otherValueList.size()));
      }
      entityList.add(entity);
    }
    solution.setMultiVarEntityList(entityList);
    return solution;
  }

  private List<TestdataValue> valueList;
  private List<TestdataOtherValue> otherValueList;
  private List<TestdataMultiVarEntity> multiVarEntityList;

  private SimpleScore score;

  public TestdataMultiVarSolution() {}

  public TestdataMultiVarSolution(String code) {
    super(code);
  }

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public List<TestdataValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataValue> valueList) {
    this.valueList = valueList;
  }

  @ValueRangeProvider(id = "otherValueRange")
  @ProblemFactCollectionProperty
  public List<TestdataOtherValue> getOtherValueList() {
    return otherValueList;
  }

  public void setOtherValueList(List<TestdataOtherValue> otherValueList) {
    this.otherValueList = otherValueList;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataMultiVarEntity> getMultiVarEntityList() {
    return multiVarEntityList;
  }

  public void setMultiVarEntityList(List<TestdataMultiVarEntity> multiVarEntityList) {
    this.multiVarEntityList = multiVarEntityList;
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
