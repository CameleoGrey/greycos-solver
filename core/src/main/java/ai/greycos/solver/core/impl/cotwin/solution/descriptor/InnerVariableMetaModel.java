package ai.greycos.solver.core.impl.cotwin.solution.descriptor;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;

import org.jspecify.annotations.NullMarked;

@NullMarked
public sealed interface InnerVariableMetaModel<Solution_>
    permits DefaultShadowVariableMetaModel, InnerGenuineVariableMetaModel {

  VariableDescriptor<Solution_> variableDescriptor();
}
