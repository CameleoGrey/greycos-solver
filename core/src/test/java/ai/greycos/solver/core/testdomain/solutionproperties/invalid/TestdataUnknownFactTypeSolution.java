package ai.greycos.solver.core.testdomain.solutionproperties.invalid;

import java.util.Collection;
import java.util.List;

import ai.greycos.solver.core.api.domain.autodiscover.AutoDiscoverMemberType;
import ai.greycos.solver.core.api.domain.solution.PlanningSolution;
import ai.greycos.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testdomain.TestdataEntity;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.TestdataValue;

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
