package ai.greycos.solver.core.impl.cotwin.variable.declarative;

import ai.greycos.solver.core.preview.api.cotwin.metamodel.VariableMetaModel;

final class EmptyVariableReferenceGraph implements VariableReferenceGraph {

  public static final EmptyVariableReferenceGraph INSTANCE = new EmptyVariableReferenceGraph();

  @Override
  public void updateChanged() {
    // No need to do anything.
  }

  @Override
  public void beforeVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity) {
    // No need to do anything.
  }

  @Override
  public void afterVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity) {
    // No need to do anything.
  }

  @Override
  public String toString() {
    return "{}";
  }
}
