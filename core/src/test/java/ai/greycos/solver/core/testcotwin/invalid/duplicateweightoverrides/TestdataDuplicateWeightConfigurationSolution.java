package ai.greycos.solver.core.testcotwin.invalid.duplicateweightoverrides;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.constraintweight.ConstraintConfiguration;
import ai.greycos.solver.core.api.cotwin.constraintweight.ConstraintConfigurationProvider;
import ai.greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides;
import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataEntity;

@PlanningSolution
public class TestdataDuplicateWeightConfigurationSolution {

  public static SolutionDescriptor<TestdataDuplicateWeightConfigurationSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataDuplicateWeightConfigurationSolution.class);
  }

  @ConstraintConfigurationProvider private ConstraintConfiguration configuration;
  private ConstraintWeightOverrides<SimpleScore> constraintWeightOverrides;

  @PlanningEntityCollectionProperty private List<TestdataEntity> entityList;

  @PlanningScore private SimpleScore score;

  public ConstraintConfiguration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(ConstraintConfiguration configuration) {
    this.configuration = configuration;
  }

  public ConstraintWeightOverrides<SimpleScore> getConstraintWeightOverrides() {
    return constraintWeightOverrides;
  }

  public void setConstraintWeightOverrides(
      ConstraintWeightOverrides<SimpleScore> constraintWeightOverrides) {
    this.constraintWeightOverrides = constraintWeightOverrides;
  }

  public List<TestdataEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataEntity> entityList) {
    this.entityList = entityList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
