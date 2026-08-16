package greycos.solver.core.testcotwin.solutionproperties;

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
public class TestdataWildcardSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataWildcardSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataWildcardSolution.class, TestdataEntity.class);
  }

  private List<? extends TestdataValue> extendsValueList;
  private List<? super TestdataValue> supersValueList;
  private List<? extends TestdataEntity> extendsEntityList;

  private SimpleScore score;

  public TestdataWildcardSolution() {}

  public TestdataWildcardSolution(String code) {
    super(code);
  }

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public List<? extends TestdataValue> getExtendsValueList() {
    return extendsValueList;
  }

  public void setExtendsValueList(List<? extends TestdataValue> extendsValueList) {
    this.extendsValueList = extendsValueList;
  }

  @ProblemFactCollectionProperty
  public List<? super TestdataValue> getSupersValueList() {
    return supersValueList;
  }

  public void setSupersValueList(List<? super TestdataValue> supersValueList) {
    this.supersValueList = supersValueList;
  }

  @PlanningEntityCollectionProperty
  public List<? extends TestdataEntity> getExtendsEntityList() {
    return extendsEntityList;
  }

  public void setExtendsEntityList(List<? extends TestdataEntity> extendsEntityList) {
    this.extendsEntityList = extendsEntityList;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
