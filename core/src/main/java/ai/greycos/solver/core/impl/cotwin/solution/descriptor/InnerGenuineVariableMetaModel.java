package ai.greycos.solver.core.impl.cotwin.solution.descriptor;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;

import org.jspecify.annotations.NullMarked;

@NullMarked
public sealed interface InnerGenuineVariableMetaModel<Solution_>
    extends InnerVariableMetaModel<Solution_>
    permits DefaultPlanningVariableMetaModel, DefaultPlanningListVariableMetaModel {

  @Override
  GenuineVariableDescriptor<Solution_> variableDescriptor();
}
