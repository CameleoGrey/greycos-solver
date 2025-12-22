package ai.greycos.solver.core.impl.domain.variable.listener.support;

import ai.greycos.solver.core.impl.domain.variable.BasicVariableChangeEvent;
import ai.greycos.solver.core.impl.domain.variable.InnerBasicVariableListener;

public interface BasicVariableNotification<Solution_>
    extends Notification<
        Solution_,
        BasicVariableChangeEvent<Object>,
        InnerBasicVariableListener<Solution_, Object>> {}
