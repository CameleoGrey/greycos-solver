package ai.greycos.solver.core.testdomain.invalid.constraintconfiguration;

import java.util.List;

import ai.greycos.solver.core.api.domain.autodiscover.AutoDiscoverMemberType;
import ai.greycos.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.domain.solution.PlanningScore;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.constraintconfiguration.TestdataConstraintConfiguration;

@PlanningSolution(autoDiscoverMemberType = AutoDiscoverMemberType.FIELD)
public class TestdataInvalidConfigurationSolution {

  public static SolutionDescriptor<TestdataInvalidConfigurationSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(TestdataInvalidConfigurationSolution.class);
  }

  private TestdataConstraintConfiguration[] configuration;

  @PlanningEntityCollectionProperty private List<TestdataEntity> entityList;

  @PlanningScore private SimpleScore score;

  public TestdataConstraintConfiguration[] getConfiguration() {
    return configuration;
  }

  public void setConfiguration(TestdataConstraintConfiguration[] configuration) {
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
