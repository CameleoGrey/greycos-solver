package ai.greycos.solver.core.impl.cotwin.variable.declarative;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.supply.Supply;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.VariableMetaModel;

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

  public void updateVariables() {
    graph.updateChanged();
  }
}
