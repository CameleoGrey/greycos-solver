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

@PlanningSolution(autoDiscoverMemberType = AutoDiscoverMemberType.FIELD)
public class TestdataAutoDiscoverFieldOverrideSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataAutoDiscoverFieldOverrideSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataAutoDiscoverFieldOverrideSolution.class, TestdataEntity.class);
  }

  private TestdataObject singleProblemFact;

  @ValueRangeProvider(id = "valueRange")
  private List<TestdataValue> problemFactList;

  @ProblemFactProperty // would have been autodiscovered as @ProblemFactCollectionProperty
  private List<String> listProblemFact;

  private List<TestdataEntity> entityList;
  private TestdataEntity otherEntity;

  private SimpleScore score;

  public TestdataAutoDiscoverFieldOverrideSolution() {}

  public TestdataAutoDiscoverFieldOverrideSolution(String code) {
    super(code);
  }

  public TestdataAutoDiscoverFieldOverrideSolution(
      String code,
      TestdataObject singleProblemFact,
      List<TestdataValue> problemFactList,
      List<TestdataEntity> entityList,
      TestdataEntity otherEntity,
      List<String> listFact) {
    super(code);
    this.singleProblemFact = singleProblemFact;
    this.problemFactList = problemFactList;
    this.entityList = entityList;
    this.otherEntity = otherEntity;
    this.listProblemFact = listFact;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
