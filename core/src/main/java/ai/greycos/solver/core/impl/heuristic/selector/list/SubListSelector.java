package ai.greycos.solver.core.impl.heuristic.selector.list;

import java.util.Iterator;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.selector.IterableSelector;

public interface SubListSelector<Solution_> extends IterableSelector<Solution_, SubList> {

  ListVariableDescriptor<Solution_> getVariableDescriptor();

  Iterator<Object> endingValueIterator();

  long getValueCount();
}
