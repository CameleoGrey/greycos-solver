package ai.greycos.solver.quarkus.benchmark.it.cotwin;

import java.util.Objects;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowSources;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;

@PlanningEntity
public class TestdataListValueShadowEntity {

  private String value;

  @InverseRelationShadowVariable(sourceVariableName = "values")
  private TestdataStringLengthShadowEntity entity;

  @PreviousElementShadowVariable(sourceVariableName = "values")
  private TestdataListValueShadowEntity previousValue;

  @ShadowVariable(supplierName = "lengthSupplier")
  private int length;

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

  public int getLength() {
    return length;
  }

  public void setLength(int length) {
    this.length = length;
  }

  public TestdataListValueShadowEntity getPreviousValue() {
    return previousValue;
  }

  public void setPreviousValue(TestdataListValueShadowEntity previousValue) {
    this.previousValue = previousValue;
  }

  @ShadowSources("previousValue.length")
  public int lengthSupplier() {
    int out = Objects.requireNonNullElse(value, "").length();
    if (previousValue != null) {
      out += previousValue.getLength();
    }
    return out;
  }
}
