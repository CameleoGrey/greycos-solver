package greycos.solver.core.testcotwin.solutionproperties;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.solution.ProblemFactProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataProblemFactPropertySolution extends TestdataObject {

  public static SolutionDescriptor<TestdataProblemFactPropertySolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataProblemFactPropertySolution.class, TestdataEntity.class);
  }

  private List<TestdataValue> valueList;
  private List<Object> otherProblemFactList;
  private Object extraObject;
  private List<TestdataEntity> entityList;

  private SimpleScore score;

  public TestdataProblemFactPropertySolution() {}

  public TestdataProblemFactPropertySolution(String code) {
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

  @ProblemFactCollectionProperty
  public List<Object> getOtherProblemFactList() {
    return otherProblemFactList;
  }

  public void setOtherProblemFactList(List<Object> otherProblemFactList) {
    this.otherProblemFactList = otherProblemFactList;
  }

  @ProblemFactProperty
  public Object getExtraObject() {
    return extraObject;
  }

  public void setExtraObject(Object extraObject) {
    this.extraObject = extraObject;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
