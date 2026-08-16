package greycos.solver.core.impl.cotwin.solution.descriptor;

import greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;

import org.jspecify.annotations.NullMarked;

@NullMarked
public sealed interface InnerVariableMetaModel<Solution_>
    permits DefaultShadowVariableMetaModel, InnerGenuineVariableMetaModel {

  VariableDescriptor<Solution_> variableDescriptor();
}
