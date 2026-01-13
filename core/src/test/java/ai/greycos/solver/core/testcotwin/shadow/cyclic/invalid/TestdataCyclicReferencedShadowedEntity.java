package ai.greycos.solver.core.testcotwin.shadow.cyclic.invalid;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PiggybackShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;
import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import ai.greycos.solver.core.testcotwin.DummyVariableListener;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;

import org.jspecify.annotations.NonNull;

@PlanningEntity
public class TestdataCyclicReferencedShadowedEntity extends TestdataObject {

  public static EntityDescriptor<TestdataCyclicReferencedShadowedSolution> buildEntityDescriptor() {
    return TestdataCyclicReferencedShadowedSolution.buildSolutionDescriptor()
        .findEntityDescriptorOrFail(TestdataCyclicReferencedShadowedEntity.class);
  }

  public static GenuineVariableDescriptor<TestdataCyclicReferencedShadowedSolution>
      buildVariableDescriptorForValue() {
    return buildEntityDescriptor().getGenuineVariableDescriptor("value");
  }

  private TestdataValue value;
  private boolean barber;
  private boolean cutsOwnHair;

  public TestdataCyclicReferencedShadowedEntity() {}

  public TestdataCyclicReferencedShadowedEntity(String code) {
    super(code);
  }

  public TestdataCyclicReferencedShadowedEntity(String code, TestdataValue value) {
    this(code);
    this.value = value;
  }

  @PlanningVariable(valueRangeProviderRefs = "valueRange")
  public TestdataValue getValue() {
    return value;
  }

  public void setValue(TestdataValue value) {
    this.value = value;
  }

  @ShadowVariable(
      variableListenerClass = BarberAndCutsOwnHairUpdatingVariableListener.class,
      sourceVariableName = "value")
  @ShadowVariable(
      variableListenerClass = BarberAndCutsOwnHairUpdatingVariableListener.class,
      sourceVariableName = "cutsOwnHair")
  public boolean isBarber() {
    return barber;
  }

  public void setBarber(boolean barber) {
    this.barber = barber;
  }

  @PiggybackShadowVariable(shadowVariableName = "barber")
  public boolean isCutsOwnHair() {
    return cutsOwnHair;
  }

  public void setCutsOwnHair(boolean cutsOwnHair) {
    this.cutsOwnHair = cutsOwnHair;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

  // ************************************************************************
  // Static inner classes
  // ************************************************************************

  public static class BarberAndCutsOwnHairUpdatingVariableListener
      extends DummyVariableListener<
          TestdataCyclicReferencedShadowedSolution, TestdataCyclicReferencedShadowedEntity> {

    @Override
    public void afterEntityAdded(
        @NonNull ScoreDirector<TestdataCyclicReferencedShadowedSolution> scoreDirector,
        @NonNull TestdataCyclicReferencedShadowedEntity entity) {
      updateShadow(entity, scoreDirector);
    }

    @Override
    public void afterVariableChanged(
        @NonNull ScoreDirector<TestdataCyclicReferencedShadowedSolution> scoreDirector,
        @NonNull TestdataCyclicReferencedShadowedEntity entity) {
      updateShadow(entity, scoreDirector);
    }

    private void updateShadow(
        TestdataCyclicReferencedShadowedEntity entity,
        ScoreDirector<TestdataCyclicReferencedShadowedSolution> scoreDirector) {
      // The barber cuts the hair of everyone in the village who does not cut his/her own hair
      // Does the barber cut his own hair?
      TestdataValue value = entity.getValue();
      boolean barber = !entity.isCutsOwnHair();
      scoreDirector.beforeVariableChanged(entity, "barber");
      entity.setBarber(value != null && barber);
      scoreDirector.afterVariableChanged(entity, "barber");
      scoreDirector.beforeVariableChanged(entity, "cutsOwnHair");
      entity.setCutsOwnHair(value != null && !barber);
      scoreDirector.afterVariableChanged(entity, "cutsOwnHair");
    }
  }
}
