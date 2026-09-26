package greycos.solver.core.impl.cotwin.variable.violation;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.impl.cotwin.variable.ListVariableChangeHandler;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;

import org.jspecify.annotations.NullMarked;

/** Tracks variable change events for a given {@link PlanningListVariable}. */
@NullMarked
public final class ListVariableTracker<Solution_> implements ListVariableChangeHandler<Solution_> {

  private final ListVariableDescriptor<Solution_> variableDescriptor;
  private final List<Object> beforeVariableChangedEntityList;
  private final List<Object> afterVariableChangedEntityList;

  public ListVariableTracker(ListVariableDescriptor<Solution_> variableDescriptor) {
    this.variableDescriptor = variableDescriptor;
    beforeVariableChangedEntityList = new ArrayList<>();
    afterVariableChangedEntityList = new ArrayList<>();
  }

  @Override
  public VariableDescriptor<Solution_> getSourceVariableDescriptor() {
    return variableDescriptor;
  }

  @Override
  public void resetWorkingSolution(InnerScoreDirector<Solution_, ?> scoreDirector) {
    beforeVariableChangedEntityList.clear();
    afterVariableChangedEntityList.clear();
  }

  @Override
  public void beforeListVariableChanged(
      InnerScoreDirector<Solution_, ?> scoreDirector, Object entity, int fromIndex, int toIndex) {
    beforeVariableChangedEntityList.add(entity);
  }

  @Override
  public void afterListVariableChanged(
      InnerScoreDirector<Solution_, ?> scoreDirector, Object entity, int fromIndex, int toIndex) {
    afterVariableChangedEntityList.add(entity);
  }

  public List<String> getEntitiesMissingBeforeAfterEvents(
      List<VariableId<Solution_>> changedVariables) {
    List<String> out = new ArrayList<>();
    for (var changedVariable : changedVariables) {
      if (!variableDescriptor.equals(changedVariable.variableDescriptor())) {
        continue;
      }
      Object entity = changedVariable.entity();
      if (!beforeVariableChangedEntityList.contains(entity)) {
        out.add(
            "Entity ("
                + entity
                + ") is missing a beforeListVariableChanged call for list variable ("
                + variableDescriptor.getVariableName()
                + ").");
      }
      if (!afterVariableChangedEntityList.contains(entity)) {
        out.add(
            "Entity ("
                + entity
                + ") is missing a afterListVariableChanged call for list variable ("
                + variableDescriptor.getVariableName()
                + ").");
      }
    }
    beforeVariableChangedEntityList.clear();
    afterVariableChangedEntityList.clear();
    return out;
  }

  @Override
  public void afterListElementUnassigned(
      InnerScoreDirector<Solution_, ?> scoreDirector, Object unassignedElement) {
    // Do nothing
  }
}
