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

@PlanningSolution(autoDiscoverMemberType = AutoDiscoverMemberType.FIELD)
public class TestdataAutoDiscoverFieldSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataAutoDiscoverFieldSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataAutoDiscoverFieldSolution.class, TestdataEntity.class);
  }

  private TestdataConstraintConfiguration constraintConfiguration;
  private TestdataObject singleProblemFact;

  @ValueRangeProvider(id = "valueRange")
  private List<TestdataValue> problemFactList;

  private List<TestdataEntity> entityList;
  private TestdataEntity otherEntity;

  private SimpleScore score;

  public TestdataAutoDiscoverFieldSolution() {}

  public TestdataAutoDiscoverFieldSolution(String code) {
    super(code);
  }

  public TestdataAutoDiscoverFieldSolution(
      String code,
      TestdataObject singleProblemFact,
      List<TestdataValue> problemFactList,
      List<TestdataEntity> entityList,
      TestdataEntity otherEntity) {
    super(code);
    this.singleProblemFact = singleProblemFact;
    this.problemFactList = problemFactList;
    this.entityList = entityList;
    this.otherEntity = otherEntity;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
