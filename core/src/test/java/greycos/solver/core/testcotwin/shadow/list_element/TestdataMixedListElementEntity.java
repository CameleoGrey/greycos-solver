package greycos.solver.core.testcotwin.shadow.list_element;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataMixedListElementEntity extends TestdataObject {

  @PlanningListVariable List<TestdataMixedListElementValue> values = new ArrayList<>();

  @ShadowVariable(supplierName = "totalDurationSupplier")
  Integer totalDuration;

  public TestdataMixedListElementEntity() {}

  public TestdataMixedListElementEntity(String code) {
    super(code);
  }

  @ShadowSources("values[].paddedDuration")
  public Integer totalDurationSupplier() {
    var total = 0;
    for (var value : values) {
      if (value.getPaddedDuration() == null) {
        return null;
      }
      total += value.getPaddedDuration();
    }
    return total;
  }

  public List<TestdataMixedListElementValue> getValues() {
    return values;
  }

  public void setValues(List<TestdataMixedListElementValue> values) {
    this.values = values;
  }

  public Integer getTotalDuration() {
    return totalDuration;
  }

  public void setTotalDuration(Integer totalDuration) {
    this.totalDuration = totalDuration;
  }
}
