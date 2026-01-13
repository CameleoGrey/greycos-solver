package ai.greycos.solver.core.testcotwin.invalid.variablemap;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.autodiscover.AutoDiscoverMemberType;
import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataEntity;

@PlanningSolution(autoDiscoverMemberType = AutoDiscoverMemberType.FIELD)
public class TestdataMapConfigurationSolution {

  public static SolutionDescriptor<TestdataMapConfigurationSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(TestdataMapConfigurationSolution.class);
  }

  private DummyMapConstraintConfiguration configuration;

  @PlanningEntityCollectionProperty private List<TestdataEntity> entityList;

  @PlanningScore private SimpleScore score;

  public DummyMapConstraintConfiguration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(DummyMapConstraintConfiguration configuration) {
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
