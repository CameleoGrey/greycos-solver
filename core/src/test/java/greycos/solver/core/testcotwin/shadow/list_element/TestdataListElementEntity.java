package greycos.solver.core.testcotwin.shadow.list_element;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.testcotwin.TestdataObject;

/**
 * An entity whose declarative shadow variable {@link #lastEndTime} aggregates a declarative shadow
 * variable of its planning list variable's elements, using a {@code "values[].endTime"} source.
 */
@PlanningEntity
public class TestdataListElementEntity extends TestdataObject {

  @PlanningListVariable(allowsUnassignedValues = true)
  List<TestdataListElementValue> values = new ArrayList<>();

  int startTime;

  @ShadowVariable(supplierName = "lastEndTimeSupplier")
  Integer lastEndTime;

  public TestdataListElementEntity() {}

  public TestdataListElementEntity(String code) {
    super(code);
  }

  public TestdataListElementEntity(String code, int startTime) {
    super(code);
    this.startTime = startTime;
  }

  @ShadowSources("values[].endTime")
  public Integer lastEndTimeSupplier() {
    if (values.isEmpty()) {
      return startTime;
    }
    return values.get(values.size() - 1).getEndTime();
  }

  public List<TestdataListElementValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataListElementValue> values) {
    this.values = values;
  }

  public int getStartTime() {
    return startTime;
  }

  public void setStartTime(int startTime) {
    this.startTime = startTime;
  }

  public Integer getLastEndTime() {
    return lastEndTime;
  }

  public void setLastEndTime(Integer lastEndTime) {
    this.lastEndTime = lastEndTime;
  }
}
