package greycos.solver.core.testcotwin.valuerange.entityproviding.parameter.invalid;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningSolution
public class TestdataInvalidTypeEntityProvidingWithParameterSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataInvalidTypeEntityProvidingWithParameterSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataInvalidTypeEntityProvidingWithParameterSolution.class,
        TestdataInvalidTypeEntityProvidingWithParameterEntity.class);
  }

  private List<TestdataInvalidTypeEntityProvidingWithParameterEntity> entityList;

  private SimpleScore score;

  public TestdataInvalidTypeEntityProvidingWithParameterSolution() {
    // Required for cloning
  }

  public TestdataInvalidTypeEntityProvidingWithParameterSolution(String code) {
    super(code);
  }

  @PlanningEntityCollectionProperty
  public List<TestdataInvalidTypeEntityProvidingWithParameterEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(
      List<TestdataInvalidTypeEntityProvidingWithParameterEntity> entityList) {
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
