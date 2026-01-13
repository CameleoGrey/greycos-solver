package ai.greycos.solver.core.testcotwin.shadow.corrupted;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;
import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.testcotwin.DummyVariableListener;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

import org.jspecify.annotations.NonNull;

@PlanningEntity
public class TestdataCorruptedShadowedEntity extends TestdataObject {

  public static EntityDescriptor<TestdataCorruptedShadowedSolution> buildEntityDescriptor() {
    return TestdataCorruptedShadowedSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataCorruptedShadowedEntity.class);
  }

  private TestdataValue value;
  private Integer count;

  public TestdataCorruptedShadowedEntity(String code) {
    super(code);
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public TestdataValue getValue() {
    return value;
  }

  public void setValue(TestdataValue value) {
    this.value = value;
  }

  @ShadowVariable(
      variableListenerClass = CountUpdatingVariableListener.class,
      sourceVariableName = "value")
  public Integer getCount() {
    return count;
  }

  public void setCount(Integer count) {
    this.count = count;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

  // ************************************************************************
  // Static inner classes
  // ************************************************************************

  public static class CountUpdatingVariableListener
      extends DummyVariableListener<
          TestdataCorruptedShadowedSolution, TestdataCorruptedShadowedEntity> {

    @Override
    public void afterEntityAdded(
        @NonNull ScoreDirector<TestdataCorruptedShadowedSolution> scoreDirector,
        @NonNull TestdataCorruptedShadowedEntity entity) {
      updateShadow(entity, scoreDirector);
    }

    @Override
    public void afterVariableChanged(
        @NonNull ScoreDirector<TestdataCorruptedShadowedSolution> scoreDirector,
        @NonNull TestdataCorruptedShadowedEntity entity) {
      updateShadow(entity, scoreDirector);
    }

    private void updateShadow(
        TestdataCorruptedShadowedEntity entity,
        ScoreDirector<TestdataCorruptedShadowedSolution> scoreDirector) {
      TestdataValue primaryValue = entity.getValue();
      Integer count;
      if (primaryValue == null) {
        count = null;
      } else {
        count = (entity.getCount() == null) ? 0 : entity.getCount();
        count++;
      }
      scoreDirector.beforeVariableChanged(entity, "count");
      entity.setCount(count);
      scoreDirector.afterVariableChanged(entity, "count");
    }
  }
}
