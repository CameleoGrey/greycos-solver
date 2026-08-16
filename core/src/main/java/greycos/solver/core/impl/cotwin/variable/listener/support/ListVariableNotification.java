package greycos.solver.core.impl.cotwin.variable.listener.support;

import greycos.solver.core.impl.cotwin.variable.InnerListVariableListener;
import greycos.solver.core.impl.cotwin.variable.ListElementsChangeEvent;

public interface ListVariableNotification<Solution_>
    extends Notification<
        Solution_,
        ListElementsChangeEvent<Object>,
        InnerListVariableListener<Solution_, Object, Object>> {}
