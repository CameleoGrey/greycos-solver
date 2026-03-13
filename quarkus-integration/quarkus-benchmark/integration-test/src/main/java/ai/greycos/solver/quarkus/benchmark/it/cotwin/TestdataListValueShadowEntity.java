package ai.greycos.solver.quarkus.benchmark.it.cotwin;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowSources;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;

@PlanningEntity
public class TestdataListValueShadowEntity {

  private String value;

  @InverseRelationShadowVariable(sourceVariableName = "values")
  private TestdataStringLengthShadowEntity entity;

  @ShadowVariable(supplierName = "lengthSupplier")
  private Integer length;

  public TestdataListValueShadowEntity() {}

  public TestdataListValueShadowEntity(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public TestdataStringLengthShadowEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataStringLengthShadowEntity entity) {
    this.entity = entity;
  }

  public Integer getLength() {
    return length;
  }

  public void setLength(Integer length) {
    this.length = length;
  }

  @ShadowSources("entity.values[].value")
  public Integer lengthSupplier() {
    if (entity == null || entity.getValues() == null) {
      return 0;
    }
    return entity.getValues().stream()
        .map(TestdataListValueShadowEntity::getValue)
        .filter(value -> value != null)
        .mapToInt(String::length)
        .sum();
  }
}
