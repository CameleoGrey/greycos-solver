package greycos.solver.quarkus.testcotwin.cascade;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.CascadingUpdateShadowVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;

@PlanningEntity
public class TestdataQuarkusDuplicateCascadingValue {
  String id;

  public TestdataQuarkusDuplicateCascadingValue() {}

  public TestdataQuarkusDuplicateCascadingValue(String id) {
    this.id = id;
  }

  @PreviousElementShadowVariable(sourceVariableName = "valueList")
  private TestdataQuarkusDuplicateCascadingValue previousValue;

  @CascadingUpdateShadowVariable(targetMethodName = "updateCalculationValue")
  private Integer chainLength;

  @CascadingUpdateShadowVariable(targetMethodName = "updateCalculationValue")
  private Integer chainProduct;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public TestdataQuarkusDuplicateCascadingValue getPreviousValue() {
    return previousValue;
  }

  public void setPreviousValue(TestdataQuarkusDuplicateCascadingValue previousValue) {
    this.previousValue = previousValue;
  }

  public Integer getChainLength() {
    return chainLength;
  }

  public void setChainLength(Integer chainLength) {
    this.chainLength = chainLength;
  }

  public Integer getChainProduct() {
    return chainProduct;
  }

  public void setChainProduct(Integer chainProduct) {
    this.chainProduct = chainProduct;
  }

  public void updateCalculationValue() {
    if (previousValue == null) {
      chainLength = 0;
      chainProduct = 1;
    } else {
      chainLength = previousValue.chainLength + 1;
      chainProduct = previousValue.chainProduct * 2;
    }
  }
}
