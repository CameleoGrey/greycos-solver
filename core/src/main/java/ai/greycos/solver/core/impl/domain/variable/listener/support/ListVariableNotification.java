package ai.greycos.solver.core.impl.domain.variable.listener.support;

import ai.greycos.solver.core.impl.domain.variable.InnerListVariableListener;
import ai.greycos.solver.core.impl.domain.variable.ListElementsChangeEvent;

public interface ListVariableNotification<Solution_>
    extends Notification<
        Solution_,
        ListElementsChangeEvent<Object>,
        InnerListVariableListener<Solution_, Object, Object>> {}
