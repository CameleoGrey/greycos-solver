package ai.greycos.solver.core.testcotwin.invalid.badconfiguration;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.constraintweight.ConstraintConfiguration;
import ai.greycos.solver.core.api.cotwin.constraintweight.ConstraintConfigurationProvider;
import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataEntity;

@PlanningSolution
public class TestdataBadConfigurationSolution {

  public static SolutionDescriptor<TestdataBadConfigurationSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(TestdataBadConfigurationSolution.class);
  }

  @ConstraintConfigurationProvider private ConstraintConfiguration configuration;

  @PlanningEntityCollectionProperty private List<TestdataEntity> entityList;

  @PlanningScore private SimpleScore score;

  @ConstraintConfigurationProvider
  public ConstraintConfiguration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(ConstraintConfiguration configuration) {
    this.configuration = configuration;
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
