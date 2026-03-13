package ai.greycos.solver.core.testcotwin.pinned;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.entity.PlanningPin;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataPinnedEntity extends TestdataObject {

  public static EntityDescriptor<TestdataPinnedSolution> buildEntityDescriptor() {
    return TestdataPinnedSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataPinnedEntity.class);
  }

  private TestdataValue value;
  private boolean locked;
  private boolean pinned;

  public TestdataPinnedEntity() {}

  public TestdataPinnedEntity(String code) {
    super(code);
  }

  public TestdataPinnedEntity(String code, boolean locked, boolean pinned) {
    this(code);
    this.locked = locked;
    this.pinned = pinned;
  }

  public TestdataPinnedEntity(String code, TestdataValue value) {
    this(code);
    this.value = value;
  }

  public TestdataPinnedEntity(String code, TestdataValue value, boolean locked, boolean pinned) {
    this(code, value);
    this.locked = locked;
    this.pinned = pinned;
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public TestdataValue getValue() {
    return value;
  }

  public void setValue(TestdataValue value) {
    this.value = value;
  }

  public boolean isLocked() {
    return locked;
  }

  public void setLocked(boolean locked) {
    this.locked = locked;
  }

  @PlanningPin
  public boolean isPinned() {
    return pinned;
  }

  public void setPinned(boolean pinned) {
    this.pinned = pinned;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
