package ai.greycos.solver.spring.boot.autoconfigure.invalid.type;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningPin;
import ai.greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowSources;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;

public class InvalidMethodTestdataSpringEntity {

  private boolean pin;

  private String value;

  private List<String> values;

  private String anchorShadow;

  private int indexShadow;

  private String inverse;

  private String next;

  private String previous;

  private String shadow;

  // ************************************************************************
  // Getters/setters
  // ************************************************************************
  @PlanningPin
  public boolean isPin() {
    return pin;
  }

  public void setPin(boolean pin) {
    this.pin = pin;
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  @PlanningListVariable
  public List<String> getValues() {
    return values;
  }

  public void setValues(List<String> values) {
    this.values = values;
  }

  public String getAnchorShadow() {
    return anchorShadow;
  }

  public void setAnchorShadow(String anchorShadow) {
    this.anchorShadow = anchorShadow;
  }

  @IndexShadowVariable(sourceVariableName = "source")
  public int getIndexShadow() {
    return indexShadow;
  }

  public void setIndexShadow(int indexShadow) {
    this.indexShadow = indexShadow;
  }

  @InverseRelationShadowVariable(sourceVariableName = "source")
  public String getInverse() {
    return inverse;
  }

  public void setInverse(String inverse) {
    this.inverse = inverse;
  }

  @NextElementShadowVariable(sourceVariableName = "source")
  public String getNext() {
    return next;
  }

  public void setNext(String next) {
    this.next = next;
  }

  @PreviousElementShadowVariable(sourceVariableName = "source")
  public String getPrevious() {
    return previous;
  }

  public void setPrevious(String previous) {
    this.previous = previous;
  }

  @ShadowVariable(supplierName = "updateShadow")
  public String getShadow() {
    return shadow;
  }

  public void setShadow(String shadow) {
    this.shadow = shadow;
  }

  @ShadowSources("value")
  public String updateShadow() {
    return "shadow";
  }
}
