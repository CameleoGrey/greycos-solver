package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.move.AbstractMove;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;

public class ListAssignMove<Solution_> extends AbstractMove<Solution_> {

  private final ListVariableDescriptor<Solution_> variableDescriptor;
  private final Object planningValue;
  private final Object destinationEntity;
  private final int destinationIndex;

  public ListAssignMove(
      ListVariableDescriptor<Solution_> variableDescriptor,
      Object planningValue,
      Object destinationEntity,
      int destinationIndex) {
    this.variableDescriptor = variableDescriptor;
    this.planningValue = planningValue;
    this.destinationEntity = destinationEntity;
    this.destinationIndex = destinationIndex;
  }

  public Object getDestinationEntity() {
    return destinationEntity;
  }

  public int getDestinationIndex() {
    return destinationIndex;
  }

  public Object getMovedValue() {
    return planningValue;
  }

  @Override
  public Collection<?> getPlanningEntities() {
    return List.of(destinationEntity);
  }

  @Override
  public Collection<?> getPlanningValues() {
    return List.of(planningValue);
  }

  @Override
  public boolean isMoveDoable(ScoreDirector<Solution_> scoreDirector) {
    return destinationIndex >= 0
        && variableDescriptor.getListSize(destinationEntity) >= destinationIndex;
  }

  @Override
  protected void doMoveOnGenuineVariables(ScoreDirector<Solution_> scoreDirector) {
    var variableDescriptorAwareScoreDirector =
        (VariableDescriptorAwareScoreDirector<Solution_>) scoreDirector;
    // Add planningValue to destinationEntity's list variable (at destinationIndex).
    variableDescriptorAwareScoreDirector.beforeListVariableElementAssigned(
        variableDescriptor, planningValue);
    variableDescriptorAwareScoreDirector.beforeListVariableChanged(
        variableDescriptor, destinationEntity, destinationIndex, destinationIndex);
    variableDescriptor.addElement(destinationEntity, destinationIndex, planningValue);
    variableDescriptorAwareScoreDirector.afterListVariableChanged(
        variableDescriptor, destinationEntity, destinationIndex, destinationIndex + 1);
    variableDescriptorAwareScoreDirector.afterListVariableElementAssigned(
        variableDescriptor, planningValue);
  }

  @Override
  public ListAssignMove<Solution_> rebase(ScoreDirector<Solution_> destinationScoreDirector) {
    return new ListAssignMove<>(
        variableDescriptor,
        destinationScoreDirector.lookUpWorkingObject(planningValue),
        destinationScoreDirector.lookUpWorkingObject(destinationEntity),
        destinationIndex);
  }

  @Override
  public String getSimpleMoveTypeDescription() {
    return getClass().getSimpleName()
        + "("
        + variableDescriptor.getSimpleEntityAndVariableName()
        + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ListAssignMove<?> other = (ListAssignMove<?>) o;
    return destinationIndex == other.destinationIndex
        && Objects.equals(variableDescriptor, other.variableDescriptor)
        && Objects.equals(planningValue, other.planningValue)
        && Objects.equals(destinationEntity, other.destinationEntity);
  }

  @Override
  public int hashCode() {
    return Objects.hash(variableDescriptor, planningValue, destinationEntity, destinationIndex);
  }

  @Override
  public String toString() {
    return String.format(
        "%s {null -> %s[%d]}", getMovedValue(), destinationEntity, destinationIndex);
  }
}
