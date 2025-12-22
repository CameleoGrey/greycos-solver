package ai.greycos.solver.core.testdomain.list.composite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ai.greycos.solver.core.api.domain.entity.PlanningEntity;
import ai.greycos.solver.core.api.domain.variable.PlanningListVariable;
import ai.greycos.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.domain.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.testdomain.TestdataObject;
import ai.greycos.solver.core.testdomain.list.TestdataListValue;

@PlanningEntity
public class TestdataListCompositeEntity extends TestdataObject {

  public static EntityDescriptor<TestdataListCompositeSolution> buildEntityDescriptor() {
    return TestdataListCompositeSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataListCompositeEntity.class);
  }

  public static ListVariableDescriptor<TestdataListCompositeSolution>
      buildVariableDescriptorForValueList() {
    return (ListVariableDescriptor<TestdataListCompositeSolution>)
        buildEntityDescriptor().getGenuineVariableDescriptor("valueList");
  }

  @PlanningListVariable(valueRangeProviderRefs = {"valueRange1", "valueRange2"})
  private List<TestdataListValue> valueList;

  public TestdataListCompositeEntity() {
    // Required for cloning
  }

  public TestdataListCompositeEntity(String code, List<TestdataListValue> valueList) {
    super(code);
    this.valueList = valueList;
  }

  public TestdataListCompositeEntity(String code, TestdataListValue... values) {
    this(code, new ArrayList<>(Arrays.asList(values)));
  }

  public List<TestdataListValue> getValueList() {
    return valueList;
  }
}
