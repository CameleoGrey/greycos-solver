package ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.thirdparty;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataValue;

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
