package ai.greycos.solver.core.impl.cotwin.variable.declarative;

import ai.greycos.solver.core.preview.api.cotwin.metamodel.VariableMetaModel;

public sealed interface VariableReferenceGraph
    permits AbstractVariableReferenceGraph,
        EmptyVariableReferenceGraph,
        SingleDirectionalParentVariableReferenceGraph {

  void updateChanged();

  void beforeVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity);

  void afterVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity);
}
