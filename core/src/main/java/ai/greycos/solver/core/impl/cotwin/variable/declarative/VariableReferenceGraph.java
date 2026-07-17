package ai.greycos.solver.core.impl.cotwin.variable.declarative;

import ai.greycos.solver.core.preview.api.cotwin.metamodel.VariableMetaModel;

public sealed interface VariableReferenceGraph
    permits AbstractVariableReferenceGraph,
        EmptyVariableReferenceGraph,
        SingleDirectionalParentVariableReferenceGraph {

  /**
   * Update all declarative {@link ai.greycos.solver.core.api.cotwin.variable.ShadowVariable} that
   * has a source that was changed in either {@link #beforeVariableChanged(VariableMetaModel,
   * Object)} or {@link #afterVariableChanged(VariableMetaModel, Object)}.
   *
   * <p>Called after all {@link ai.greycos.solver.core.impl.cotwin.variable.VariableListener} are
   * triggered. Declarative {@link ai.greycos.solver.core.api.cotwin.variable.ShadowVariable} are
   * guaranteed to be the last variables to update.
   */
  void updateChanged();

  /**
   * Called before the variable corresponding to the {@link VariableMetaModel} on the given entity
   * changes.
   *
   * @param variableReference The variable to be changed.
   * @param entity The entity that has the variable.
   */
  void beforeVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity);

  /**
   * Called after the variable corresponding to the {@link VariableMetaModel} on the given entity
   * changes.
   *
   * @param variableReference The variable that changed.
   * @param entity The entity that has the variable.
   */
  void afterVariableChanged(VariableMetaModel<?, ?, ?> variableReference, Object entity);
}
