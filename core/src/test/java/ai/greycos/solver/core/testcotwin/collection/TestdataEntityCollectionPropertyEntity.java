package ai.greycos.solver.core.testcotwin.collection;

import java.util.List;
import java.util.Map;
import java.util.Set;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataEntityCollectionPropertyEntity extends TestdataObject {

  public static EntityDescriptor<TestdataEntityCollectionPropertySolution> buildEntityDescriptor() {
    return TestdataEntityCollectionPropertySolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataEntityCollectionPropertyEntity.class);
  }

  private List<TestdataEntityCollectionPropertyEntity> entityList;
  private Set<TestdataEntityCollectionPropertyEntity> entitySet;
  private Map<String, TestdataEntityCollectionPropertyEntity> stringToEntityMap;
  private Map<TestdataEntityCollectionPropertyEntity, String> entityToStringMap;
  private Map<String, List<TestdataEntityCollectionPropertyEntity>> stringToEntityListMap;

  private TestdataValue value;

  public TestdataEntityCollectionPropertyEntity() {}

  public TestdataEntityCollectionPropertyEntity(String code) {
    super(code);
  }

  public TestdataEntityCollectionPropertyEntity(String code, TestdataValue value) {
    this(code);
    this.value = value;
  }

  public List<TestdataEntityCollectionPropertyEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataEntityCollectionPropertyEntity> entityList) {
    this.entityList = entityList;
  }

  public Set<TestdataEntityCollectionPropertyEntity> getEntitySet() {
    return entitySet;
  }

  public void setEntitySet(Set<TestdataEntityCollectionPropertyEntity> entitySet) {
    this.entitySet = entitySet;
  }

  public Map<String, TestdataEntityCollectionPropertyEntity> getStringToEntityMap() {
    return stringToEntityMap;
  }

  public void setStringToEntityMap(
      Map<String, TestdataEntityCollectionPropertyEntity> stringToEntityMap) {
    this.stringToEntityMap = stringToEntityMap;
  }

  public Map<TestdataEntityCollectionPropertyEntity, String> getEntityToStringMap() {
    return entityToStringMap;
  }

  public void setEntityToStringMap(
      Map<TestdataEntityCollectionPropertyEntity, String> entityToStringMap) {
    this.entityToStringMap = entityToStringMap;
  }

  public Map<String, List<TestdataEntityCollectionPropertyEntity>> getStringToEntityListMap() {
    return stringToEntityListMap;
  }

  public void setStringToEntityListMap(
      Map<String, List<TestdataEntityCollectionPropertyEntity>> stringToEntityListMap) {
    this.stringToEntityListMap = stringToEntityListMap;
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public TestdataValue getValue() {
    return value;
  }

  public void setValue(TestdataValue value) {
    this.value = value;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
