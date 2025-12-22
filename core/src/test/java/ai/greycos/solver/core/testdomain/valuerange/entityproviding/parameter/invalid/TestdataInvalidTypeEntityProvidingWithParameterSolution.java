package ai.greycos.solver.core.testdomain.valuerange.entityproviding.parameter.invalid;

import java.util.List;

import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;

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
