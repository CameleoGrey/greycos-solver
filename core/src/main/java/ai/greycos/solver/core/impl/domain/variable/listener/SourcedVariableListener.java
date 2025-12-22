package ai.greycos.solver.core.impl.domain.variable.listener;

import ai.greycos.solver.core.impl.domain.variable.ChangeEvent;
import ai.greycos.solver.core.impl.domain.variable.InnerVariableListener;
import ai.greycos.solver.core.impl.domain.variable.descriptor.VariableDescriptor;

import org.jspecify.annotations.NullMarked;

@NullMarked
public sealed interface SourcedVariableListener<Solution_, Event_ extends ChangeEvent>
    extends InnerVariableListener<Solution_, Event_>
    permits SourcedBasicVariableListener, SourcedListVariableListener {
  VariableDescriptor<Solution_> getSourceVariableDescriptor();
}
