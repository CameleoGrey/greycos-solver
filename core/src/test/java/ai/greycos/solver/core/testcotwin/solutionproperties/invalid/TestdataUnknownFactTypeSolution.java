package ai.greycos.solver.core.testcotwin.solutionproperties.invalid;

import java.util.Collection;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.autodiscover.AutoDiscoverMemberType;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution(autoDiscoverMemberType = AutoDiscoverMemberType.FIELD)
public class TestdataUnknownFactTypeSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataUnknownFactTypeSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataUnknownFactTypeSolution.class, TestdataEntity.class);
  }

  private List<TestdataValue> valueList;
  private List<TestdataEntity> entityList;
  private SimpleScore score;
  // this can't work with autodiscovery because it's difficult/impossible to resolve the type of
  // collection elements
  private MyStringCollection facts;

  public TestdataUnknownFactTypeSolution() {}

  public TestdataUnknownFactTypeSolution(String code) {
    super(code);
  }

  @ValueRangeProvider(id = "valueRange")
  public List<TestdataValue> getValueList() {
    return valueList;
  }

  public static interface MyStringCollection extends Collection<String> {}
}
