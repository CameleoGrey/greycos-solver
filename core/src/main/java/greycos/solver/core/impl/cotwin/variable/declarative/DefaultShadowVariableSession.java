package greycos.solver.core.impl.cotwin.variable.declarative;

import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.supply.Supply;
import greycos.solver.core.preview.api.cotwin.metamodel.VariableMetaModel;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DefaultShadowVariableSession<Solution_> implements Supply {
  final VariableReferenceGraph graph;

  public DefaultShadowVariableSession(VariableReferenceGraph graph) {
    this.graph = graph;
  }

  public void beforeVariableChanged(
      VariableDescriptor<Solution_> variableDescriptor, Object entity) {
    beforeVariableChanged(variableDescriptor.getVariableMetaModel(), entity);
  }

  public void afterVariableChanged(
      VariableDescriptor<Solution_> variableDescriptor, Object entity) {
    afterVariableChanged(variableDescriptor.getVariableMetaModel(), entity);
  }

  public void beforeVariableChanged(
      VariableMetaModel<Solution_, ?, ?> variableMetaModel, Object entity) {
    graph.beforeVariableChanged(variableMetaModel, entity);
  }

  public void afterVariableChanged(
      VariableMetaModel<Solution_, ?, ?> variableMetaModel, Object entity) {
    graph.afterVariableChanged(variableMetaModel, entity);
  }

  public void beforeListVariableChanged(
      ListVariableDescriptor<Solution_> variableDescriptor,
      Object entity,
      int fromIndex,
      int toIndex) {
    graph.beforeListVariableChanged(
        variableDescriptor.getVariableMetaModel(),
        entity,
        variableDescriptor.getValue(entity),
        fromIndex,
        toIndex);
  }

  public void afterListVariableChanged(
      ListVariableDescriptor<Solution_> variableDescriptor,
      Object entity,
      int fromIndex,
      int toIndex) {
    graph.afterListVariableChanged(
        variableDescriptor.getVariableMetaModel(),
        entity,
        variableDescriptor.getValue(entity),
        fromIndex,
        toIndex);
  }

  public void updateVariables() {
    graph.updateChanged();
  }
}
