package ai.greycos.solver.core.impl.domain.variable.declarative;

import ai.greycos.solver.core.preview.api.domain.metamodel.VariableMetaModel;

public sealed interface VariableReferenceGraph
    permits AbstractVariableReferenceGraph,
        EmptyVariableReferenceGraph,
        SingleDirectionalParentVariableReferenceGraph {

  void updateChanged();

  void beforeVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity);

  void afterVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity);
}
