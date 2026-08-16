package greycos.solver.quarkus.it.devui.cotwin;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;

@PlanningEntity
public class TestdataStringLengthShadowEntity {

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  private String value;

  @ShadowVariable(supplierName = "lengthSupplier")
  private Integer length;

  // ************************************************************************
  // Getters/setters
  // ************************************************************************

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public Integer getLength() {
    return length;
  }

  public void setLength(Integer length) {
    this.length = length;
  }

  @ShadowSources("value")
  public Integer lengthSupplier() {
    return value == null ? 0 : value.length();
  }
}
