package ai.greycos.solver.core.testcotwin.solutionproperties.autodiscover;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.autodiscover.AutoDiscoverMemberType;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testcotwin.constraintconfiguration.TestdataConstraintConfiguration;

@PlanningSolution(autoDiscoverMemberType = AutoDiscoverMemberType.GETTER)
public class TestdataAutoDiscoverGetterSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataAutoDiscoverGetterSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataAutoDiscoverGetterSolution.class, TestdataEntity.class);
  }

  private TestdataConstraintConfiguration constraintConfiguration;
  private TestdataObject singleProblemFactField;
  private List<TestdataValue> problemFactListField;

  private List<TestdataEntity> entityListField;
  private TestdataEntity otherEntityField;

  private SimpleScore score;

  public TestdataAutoDiscoverGetterSolution() {}

  public TestdataAutoDiscoverGetterSolution(String code) {
    super(code);
  }

  public TestdataAutoDiscoverGetterSolution(
      String code,
      TestdataObject singleProblemFact,
      List<TestdataValue> problemFactList,
      List<TestdataEntity> entityList,
      TestdataEntity otherEntity) {
    super(code);
    this.singleProblemFactField = singleProblemFact;
    this.problemFactListField = problemFactList;
    this.entityListField = entityList;
    this.otherEntityField = otherEntity;
  }

  public TestdataConstraintConfiguration getConstraintConfiguration() {
    return constraintConfiguration;
  }

  public void setConstraintConfiguration(TestdataConstraintConfiguration constraintConfiguration) {
    this.constraintConfiguration = constraintConfiguration;
  }

  public TestdataObject getSingleProblemFact() {
    return singleProblemFactField;
  }

  @ValueRangeProvider(id = "valueRange")
  public List<TestdataValue> getProblemFactList() {
    return problemFactListField;
  }

  public List<TestdataEntity> getEntityList() {
    return entityListField;
  }

  public TestdataEntity getOtherEntity() {
    return otherEntityField;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
