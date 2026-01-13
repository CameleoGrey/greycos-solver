package ai.greycos.solver.core.impl.cotwin.variable.listener;

import ai.greycos.solver.core.impl.cotwin.variable.InnerListVariableListener;
import ai.greycos.solver.core.impl.cotwin.variable.ListElementsChangeEvent;
import ai.greycos.solver.core.impl.cotwin.variable.supply.Supply;

import org.jspecify.annotations.NullMarked;

/** Used to externalize data for a {@link Supply} from the cotwin model itself. */
@NullMarked
public non-sealed interface SourcedListVariableListener<Solution_, Entity_, Element_>
    extends SourcedVariableListener<Solution_, ListElementsChangeEvent<Entity_>>,
        InnerListVariableListener<Solution_, Entity_, Element_>,
        Supply {}
