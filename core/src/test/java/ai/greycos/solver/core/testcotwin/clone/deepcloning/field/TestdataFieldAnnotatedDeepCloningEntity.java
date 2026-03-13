package ai.greycos.solver.core.testcotwin.clone.deepcloning.field;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.solution.cloner.DeepPlanningClone;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataFieldAnnotatedDeepCloningEntity extends TestdataObject {

  public static EntityDescriptor<TestdataFieldAnnotatedDeepCloningSolution>
      buildEntityDescriptor() {
    return TestdataFieldAnnotatedDeepCloningSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataFieldAnnotatedDeepCloningEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataFieldAnnotatedDeepCloningSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  private TestdataValue value;

  @DeepPlanningClone private List<String> shadowVariableList;

  @DeepPlanningClone private Map<String, String> shadowVariableMap;

  @DeepPlanningClone private Map<List<String>, String> stringListToStringMap = new HashMap<>();

  @DeepPlanningClone private Map<String, List<String>> stringToStringListMap = new HashMap<>();

  public TestdataFieldAnnotatedDeepCloningEntity() {}

  public TestdataFieldAnnotatedDeepCloningEntity(String code) {
    super(code);
  }

  public TestdataFieldAnnotatedDeepCloningEntity(String code, TestdataValue value) {
    this(code);
    this.value = value;
  }

  public TestdataValue getValue() {
    return value;
  }

  public void setValue(TestdataValue value) {
    this.value = value;
  }

  public List<String> getShadowVariableList() {
    return shadowVariableList;
  }

  public void setShadowVariableList(List<String> shadowVariableList) {
    this.shadowVariableList = shadowVariableList;
  }

  public Map<String, String> getShadowVariableMap() {
    return shadowVariableMap;
  }

  public void setShadowVariableMap(Map<String, String> shadowVariableMap) {
    this.shadowVariableMap = shadowVariableMap;
  }

  public Map<List<String>, String> getStringListToStringMap() {
    return stringListToStringMap;
  }

  public void setStringListToStringMap(Map<List<String>, String> stringListToStringMap) {
    this.stringListToStringMap = stringListToStringMap;
  }

  public Map<String, List<String>> getStringToStringListMap() {
    return stringToStringListMap;
  }

  public void setStringToStringListMap(Map<String, List<String>> stringToStringListMap) {
    this.stringToStringListMap = stringToStringListMap;
  }
}
