package ai.greycos.solver.core.impl.heuristic.selector.list.mimic;

import java.util.Iterator;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;

public interface SubListMimicRecorder<Solution_> {

  void addMimicReplayingSubListSelector(
      MimicReplayingSubListSelector<Solution_> replayingSubListSelector);

  ListVariableDescriptor<Solution_> getVariableDescriptor();

  boolean isCountable();

  boolean isNeverEnding();

  long getSize();

  Iterator<Object> endingValueIterator();

  long getValueCount();
}
