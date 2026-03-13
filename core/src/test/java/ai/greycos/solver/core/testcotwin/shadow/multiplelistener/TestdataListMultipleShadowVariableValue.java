package ai.greycos.solver.core.testcotwin.shadow.multiplelistener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.CascadingUpdateShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataListMultipleShadowVariableValue extends TestdataObject {

  public static EntityDescriptor<TestdataListMultipleShadowVariableSolution>
      buildEntityDescriptor() {
    return TestdataListMultipleShadowVariableSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataListMultipleShadowVariableValue.class);
  }

  @InverseRelationShadowVariable(sourceVariableName = "valueList")
  private TestdataListMultipleShadowVariableEntity entity;

  @IndexShadowVariable(sourceVariableName = "valueList")
  private Integer index;

  @PreviousElementShadowVariable(sourceVariableName = "valueList")
  private TestdataListMultipleShadowVariableValue previous;

  @NextElementShadowVariable(sourceVariableName = "valueList")
  private TestdataListMultipleShadowVariableValue next;

  @CascadingUpdateShadowVariable(targetMethodName = "updateCascadeValue")
  private Integer cascadeValue;

  private final List<TestdataListMultipleShadowVariableEntity> entityHistory = new ArrayList<>();
  private final List<Integer> indexHistory = new ArrayList<>();
  private final List<TestdataListMultipleShadowVariableValue> previousHistory = new ArrayList<>();
  private final List<TestdataListMultipleShadowVariableValue> nextHistory = new ArrayList<>();

  public TestdataListMultipleShadowVariableValue() {}

  public TestdataListMultipleShadowVariableValue(String code) {
    super(code);
  }

  public TestdataListMultipleShadowVariableEntity getEntity() {
    return entity;
  }

  public void setEntity(TestdataListMultipleShadowVariableEntity entity) {
    this.entity = entity;
    entityHistory.add(entity);
  }

  public Integer getIndex() {
    return index;
  }

  public void setIndex(Integer index) {
    this.index = index;
    indexHistory.add(index);
  }

  public TestdataListMultipleShadowVariableValue getPrevious() {
    return previous;
  }

  public void setPrevious(TestdataListMultipleShadowVariableValue previous) {
    this.previous = previous;
    previousHistory.add(previous);
  }

  public TestdataListMultipleShadowVariableValue getNext() {
    return next;
  }

  public void setNext(TestdataListMultipleShadowVariableValue next) {
    this.next = next;
    nextHistory.add(next);
  }

  public Integer getCascadeValue() {
    if (cascadeValue == null) {
      return 2;
    }
    return cascadeValue;
  }

  public void setCascadeValue(Integer cascadeValue) {
    this.cascadeValue = cascadeValue;
  }

  public void updateCascadeValue() {
    this.cascadeValue = index + 10;
  }

  public List<TestdataListMultipleShadowVariableEntity> getEntityHistory() {
    return Collections.unmodifiableList(entityHistory);
  }

  public List<Integer> getIndexHistory() {
    return Collections.unmodifiableList(indexHistory);
  }

  public List<TestdataListMultipleShadowVariableValue> getPreviousHistory() {
    return Collections.unmodifiableList(previousHistory);
  }

  public List<TestdataListMultipleShadowVariableValue> getNextHistory() {
    return Collections.unmodifiableList(nextHistory);
  }
}
