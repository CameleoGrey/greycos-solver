package ai.greycos.solver.core.impl.cotwin.variable.listener;

import ai.greycos.solver.core.impl.cotwin.variable.ChangeEvent;
import ai.greycos.solver.core.impl.cotwin.variable.InnerVariableListener;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;

import org.jspecify.annotations.NullMarked;

@NullMarked
public sealed interface SourcedVariableListener<Solution_, Event_ extends ChangeEvent>
    extends InnerVariableListener<Solution_, Event_>
    permits SourcedBasicVariableListener, SourcedListVariableListener {
  VariableDescriptor<Solution_> getSourceVariableDescriptor();
}
