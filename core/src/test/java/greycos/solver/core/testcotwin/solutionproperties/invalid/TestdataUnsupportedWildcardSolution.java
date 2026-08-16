package greycos.solver.core.testcotwin.solutionproperties.invalid;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataObject;
import greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataUnsupportedWildcardSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataUnsupportedWildcardSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataUnsupportedWildcardSolution.class, TestdataEntity.class);
  }

  private List<TestdataValue> valueList;
  private List<? super TestdataEntity> supersEntityList;

  private SimpleScore score;

  public TestdataUnsupportedWildcardSolution() {}

  public TestdataUnsupportedWildcardSolution(String code) {
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

  @PlanningEntityCollectionProperty
  public List<? super TestdataEntity> getSupersEntityList() {
    return supersEntityList;
  }

  public void setSupersEntityList(List<? super TestdataEntity> supersEntityList) {
    this.supersEntityList = supersEntityList;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
