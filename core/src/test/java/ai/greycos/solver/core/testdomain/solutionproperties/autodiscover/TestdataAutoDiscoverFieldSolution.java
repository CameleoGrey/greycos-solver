package ai.greycos.solver.core.testdomain.solutionproperties.autodiscover;

import java.util.List;

import ai.greycos.solver.core.api.domain.autodiscover.AutoDiscoverMemberType;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;
import ai.greycos.solver.core.testdomain.constraintconfiguration.TestdataConstraintConfiguration;

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
