package greycos.solver.core.impl.cotwin.variable.listener.support;

import greycos.solver.core.impl.cotwin.variable.ChangeEvent;
import greycos.solver.core.impl.cotwin.variable.InnerVariableListener;

public interface EntityNotification<Solution_, ChangeEvent_ extends ChangeEvent>
    extends Notification<Solution_, ChangeEvent_, InnerVariableListener<Solution_, ChangeEvent_>> {}
