package greycos.solver.core.testcotwin.pinned.entityvalue;

import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataPinnedValueEntity extends TestdataObject {

  private boolean pinned;
  private List<TestdataPinnedValueEntity> entityRange;
  private TestdataPinnedValueEntity solutionValue;
  private TestdataPinnedValueEntity nullableSolutionValue;
  private TestdataPinnedValueEntity entityValue;
  private TestdataPinnedValueEntity nullableEntityValue;

  public TestdataPinnedValueEntity() {}

  public TestdataPinnedValueEntity(String code, boolean pinned) {
    super(code);
    this.pinned = pinned;
  }

  @PlanningPin
  public boolean isPinned() {
    return pinned;
  }

  public void setPinned(boolean pinned) {
    this.pinned = pinned;
  }

  @ValueRangeProvider(id = "entityRange")
  public List<TestdataPinnedValueEntity> getEntityRange() {
    return entityRange;
  }

  public void setEntityRange(List<TestdataPinnedValueEntity> entityRange) {
    this.entityRange = entityRange;
  }

  @PlanningVariable(valueRangeProviderRefs = "solutionRange")
  public TestdataPinnedValueEntity getSolutionValue() {
    return solutionValue;
  }

  public void setSolutionValue(TestdataPinnedValueEntity solutionValue) {
    this.solutionValue = solutionValue;
  }

  @PlanningVariable(valueRangeProviderRefs = "solutionRange", allowsUnassigned = true)
  public TestdataPinnedValueEntity getNullableSolutionValue() {
    return nullableSolutionValue;
  }

  public void setNullableSolutionValue(TestdataPinnedValueEntity nullableSolutionValue) {
    this.nullableSolutionValue = nullableSolutionValue;
  }

  @PlanningVariable(valueRangeProviderRefs = "entityRange")
  public TestdataPinnedValueEntity getEntityValue() {
    return entityValue;
  }

  public void setEntityValue(TestdataPinnedValueEntity entityValue) {
    this.entityValue = entityValue;
  }

  @PlanningVariable(valueRangeProviderRefs = "entityRange", allowsUnassigned = true)
  public TestdataPinnedValueEntity getNullableEntityValue() {
    return nullableEntityValue;
  }

  public void setNullableEntityValue(TestdataPinnedValueEntity nullableEntityValue) {
    this.nullableEntityValue = nullableEntityValue;
  }
}
