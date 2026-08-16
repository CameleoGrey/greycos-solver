package greycos.solver.core.impl.cotwin.variable.listener.support;

import greycos.solver.core.impl.cotwin.variable.BasicVariableChangeEvent;
import greycos.solver.core.impl.cotwin.variable.InnerBasicVariableListener;

public interface BasicVariableNotification<Solution_>
    extends Notification<
        Solution_,
        BasicVariableChangeEvent<Object>,
        InnerBasicVariableListener<Solution_, Object>> {}
