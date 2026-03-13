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

public class InvalidFieldTestdataSpringEntity {

  @PlanningPin private boolean pin;

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  private String value;

  @PlanningListVariable private List<String> values;

  private String anchorShadow;

  @IndexShadowVariable(sourceVariableName = "source")
  private int indexShadow;

  @InverseRelationShadowVariable(sourceVariableName = "source")
  private String inverse;

  @NextElementShadowVariable(sourceVariableName = "source")
  private String next;

  @PreviousElementShadowVariable(sourceVariableName = "source")
  private String previous;

  @ShadowVariable(supplierName = "updateCustomShadow")
  private String customShadow;

  @ShadowSources("value")
  public String updateCustomShadow() {
    return "customShadow";
  }

  // ************************************************************************
  // Getters/setters
  // ************************************************************************

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public boolean isPin() {
    return pin;
  }

  public void setPin(boolean pin) {
    this.pin = pin;
  }

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

  public int getIndexShadow() {
    return indexShadow;
  }

  public void setIndexShadow(int indexShadow) {
    this.indexShadow = indexShadow;
  }

  public String getInverse() {
    return inverse;
  }

  public void setInverse(String inverse) {
    this.inverse = inverse;
  }

  public String getNext() {
    return next;
  }

  public void setNext(String next) {
    this.next = next;
  }

  public String getPrevious() {
    return previous;
  }

  public void setPrevious(String previous) {
    this.previous = previous;
  }

  public String getCustomShadow() {
    return customShadow;
  }

  public void setCustomShadow(String customShadow) {
    this.customShadow = customShadow;
  }
}
