package greycos.solver.core.testcotwin.valuerange.entityproviding.parameter.invalid;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningSolution
public class TestdataInvalidCountEntityProvidingWithParameterSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataInvalidCountEntityProvidingWithParameterSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataInvalidCountEntityProvidingWithParameterSolution.class,
        TestdataInvalidCountEntityProvidingWithParameterEntity.class);
  }

  private List<TestdataInvalidCountEntityProvidingWithParameterEntity> entityList;

  private SimpleScore score;

  public TestdataInvalidCountEntityProvidingWithParameterSolution() {
    // Required for cloning
  }

  public TestdataInvalidCountEntityProvidingWithParameterSolution(String code) {
    super(code);
  }

  @PlanningEntityCollectionProperty
  public List<TestdataInvalidCountEntityProvidingWithParameterEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(
      List<TestdataInvalidCountEntityProvidingWithParameterEntity> entityList) {
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
