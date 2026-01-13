package ai.greycos.solver.core.testcotwin.solutionproperties.autodiscover;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.autodiscover.AutoDiscoverMemberType;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution(autoDiscoverMemberType = AutoDiscoverMemberType.GETTER)
public class TestdataAutoDiscoverGetterOverrideSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataAutoDiscoverGetterOverrideSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataAutoDiscoverGetterOverrideSolution.class, TestdataEntity.class);
  }

  private TestdataObject singleProblemFactField;
  private List<TestdataValue> problemFactListField;
  private List<String> listProblemFactField;

  private List<TestdataEntity> entityListField;
  private TestdataEntity otherEntityField;

  private SimpleScore score;

  public TestdataAutoDiscoverGetterOverrideSolution() {}

  public TestdataAutoDiscoverGetterOverrideSolution(String code) {
    super(code);
  }

  public TestdataAutoDiscoverGetterOverrideSolution(
      String code,
      TestdataObject singleProblemFact,
      List<TestdataValue> problemFactList,
      List<TestdataEntity> entityList,
      TestdataEntity otherEntity,
      List<String> listFact) {
    super(code);
    this.singleProblemFactField = singleProblemFact;
    this.problemFactListField = problemFactList;
    this.entityListField = entityList;
    this.otherEntityField = otherEntity;
    this.listProblemFactField = listFact;
  }

  public TestdataObject getSingleProblemFact() {
    return singleProblemFactField;
  }

  @ValueRangeProvider(id = "valueRange")
  public List<TestdataValue> getProblemFactList() {
    return problemFactListField;
  }

  @ProblemFactProperty // would have been autodiscovered as @ProblemFactCollectionProperty
  public List<String> getListProblemFact() {
    return listProblemFactField;
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
