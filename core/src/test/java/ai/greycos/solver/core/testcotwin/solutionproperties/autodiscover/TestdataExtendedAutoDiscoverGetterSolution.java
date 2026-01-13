package ai.greycos.solver.core.testcotwin.solutionproperties.autodiscover;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.autodiscover.AutoDiscoverMemberType;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution(autoDiscoverMemberType = AutoDiscoverMemberType.GETTER)
public class TestdataExtendedAutoDiscoverGetterSolution extends TestdataAutoDiscoverGetterSolution {

  public static SolutionDescriptor<TestdataExtendedAutoDiscoverGetterSolution>
      buildSubclassSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataExtendedAutoDiscoverGetterSolution.class, TestdataEntity.class);
  }

  private TestdataObject singleProblemFactFieldOverride;
  private List<TestdataValue> problemFactListFieldOverride;

  private List<TestdataEntity> entityListFieldOverride;
  private TestdataEntity otherEntityFieldOverride;

  public TestdataExtendedAutoDiscoverGetterSolution() {}

  public TestdataExtendedAutoDiscoverGetterSolution(String code) {
    super(code);
  }

  public TestdataExtendedAutoDiscoverGetterSolution(
      String code,
      TestdataObject singleProblemFact,
      List<TestdataValue> problemFactList,
      List<TestdataEntity> entityList,
      TestdataEntity otherEntity) {
    super(code);
    this.singleProblemFactFieldOverride = singleProblemFact;
    this.problemFactListFieldOverride = problemFactList;
    this.entityListFieldOverride = entityList;
    this.otherEntityFieldOverride = otherEntity;
  }

  @Override
  public TestdataObject getSingleProblemFact() {
    return singleProblemFactFieldOverride;
  }

  @ProblemFactProperty // Override from a fact collection to a single fact
  @ValueRangeProvider(id = "valueRange")
  @Override
  public List<TestdataValue> getProblemFactList() {
    return problemFactListFieldOverride;
  }

  @Override
  public List<TestdataEntity> getEntityList() {
    return entityListFieldOverride;
  }

  @Override
  public TestdataEntity getOtherEntity() {
    return otherEntityFieldOverride;
  }
}
