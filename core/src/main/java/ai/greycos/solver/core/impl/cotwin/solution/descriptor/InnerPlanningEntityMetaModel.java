package ai.greycos.solver.core.impl.cotwin.solution.descriptor;

import ai.greycos.solver.core.preview.api.cotwin.metamodel.VariableMetaModel;

import org.jspecify.annotations.NullMarked;

@NullMarked
sealed interface InnerPlanningEntityMetaModel<Solution_, Entity_>
    permits DefaultGenuineEntityMetaModel, DefaultShadowEntityMetaModel {

  void addVariable(VariableMetaModel<Solution_, Entity_, ?> variable);
}
