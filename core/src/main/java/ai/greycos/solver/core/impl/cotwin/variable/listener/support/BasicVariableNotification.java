package ai.greycos.solver.core.impl.cotwin.variable.listener.support;

import ai.greycos.solver.core.impl.cotwin.variable.BasicVariableChangeEvent;
import ai.greycos.solver.core.impl.cotwin.variable.InnerBasicVariableListener;

public interface BasicVariableNotification<Solution_>
    extends Notification<
        Solution_,
        BasicVariableChangeEvent<Object>,
        InnerBasicVariableListener<Solution_, Object>> {}
