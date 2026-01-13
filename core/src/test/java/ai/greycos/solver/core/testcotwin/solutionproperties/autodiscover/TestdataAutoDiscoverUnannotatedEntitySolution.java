package ai.greycos.solver.core.testcotwin.solutionproperties.autodiscover;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.autodiscover.AutoDiscoverMemberType;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childnot.TestdataOnlyBaseAnnotatedChildEntity;

@PlanningSolution(autoDiscoverMemberType = AutoDiscoverMemberType.GETTER)
public class TestdataAutoDiscoverUnannotatedEntitySolution extends TestdataObject {

  public static SolutionDescriptor<TestdataAutoDiscoverUnannotatedEntitySolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataAutoDiscoverUnannotatedEntitySolution.class,
        TestdataOnlyBaseAnnotatedChildEntity.class);
  }

  private TestdataObject singleProblemFactField;
  private List<TestdataValue> problemFactListField;

  private List<TestdataOnlyBaseAnnotatedChildEntity> entityListField;
  private TestdataOnlyBaseAnnotatedChildEntity otherEntityField;

  private SimpleScore score;

  public TestdataAutoDiscoverUnannotatedEntitySolution() {}

  public TestdataAutoDiscoverUnannotatedEntitySolution(String code) {
    super(code);
  }

  public TestdataAutoDiscoverUnannotatedEntitySolution(
      String code,
      TestdataObject singleProblemFact,
      List<TestdataValue> problemFactList,
      List<TestdataOnlyBaseAnnotatedChildEntity> entityList,
      TestdataOnlyBaseAnnotatedChildEntity otherEntity) {
    super(code);
    this.singleProblemFactField = singleProblemFact;
    this.problemFactListField = problemFactList;
    this.entityListField = entityList;
    this.otherEntityField = otherEntity;
  }

  public TestdataObject getSingleProblemFact() {
    return singleProblemFactField;
  }

  @ValueRangeProvider(id = "valueRange")
  public List<TestdataValue> getProblemFactList() {
    return problemFactListField;
  }

  // should be auto discovered as an entity collection
  public List<TestdataOnlyBaseAnnotatedChildEntity> getEntityList() {
    return entityListField;
  }

  // should be auto discovered as a single entity property
  public TestdataOnlyBaseAnnotatedChildEntity getOtherEntity() {
    return otherEntityField;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
