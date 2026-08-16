package greycos.solver.core.testcotwin.inheritance.solution.baseannotated.thirdparty;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataExtendedThirdPartySolution extends TestdataThirdPartySolutionPojo {

  public static SolutionDescriptor<TestdataExtendedThirdPartySolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataExtendedThirdPartySolution.class, TestdataExtendedThirdPartyEntity.class);
  }

  private Object extraObject;

  private SimpleScore score;

  public TestdataExtendedThirdPartySolution() {}

  public TestdataExtendedThirdPartySolution(String code) {
    super(code);
  }

  public TestdataExtendedThirdPartySolution(String code, Object extraObject) {
    super(code);
    this.extraObject = extraObject;
  }

  public Object getExtraObject() {
    return extraObject;
  }

  public void setExtraObject(Object extraObject) {
    this.extraObject = extraObject;
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

  @Override
  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public List<TestdataValue> getValueList() {
    return super.getValueList();
  }

  @Override
  @PlanningEntityCollectionProperty
  public List<TestdataThirdPartyEntityPojo> getEntityList() {
    return super.getEntityList();
  }
}
