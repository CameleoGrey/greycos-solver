package greycos.solver.core.impl.heuristic.selector.value.mimic;

import java.util.Iterator;

import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbySelectionSource;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.ValueSelector;

import org.jspecify.annotations.Nullable;

public interface ValueMimicRecorder<Solution_> {

  /** Stable population and live filter captured by the source factory, when available. */
  default @Nullable NearbySelectionSource<Solution_, IterableValueSelector<Solution_>>
      getNearbySelectionSource() {
    return null;
  }

  /**
   * @param replayingValueSelector never null
   */
  void addMimicReplayingValueSelector(
      MimicReplayingValueSelector<Solution_> replayingValueSelector);

  /**
   * @return As defined by {@link ValueSelector#getVariableDescriptor()}
   * @see ValueSelector#getVariableDescriptor()
   */
  GenuineVariableDescriptor<Solution_> getVariableDescriptor();

  /**
   * @return As defined by {@link ValueSelector#isNeverEnding()}
   * @see ValueSelector#isNeverEnding()
   */
  boolean isNeverEnding();

  /**
   * @return As defined by {@link IterableValueSelector#getSize()}
   * @see IterableValueSelector#getSize()
   */
  long getSize();

  /**
   * @return As defined by {@link ValueSelector#getSize(Object)}
   * @see ValueSelector#getSize(Object)
   */
  long getSize(Object entity);

  /**
   * @return As defined by {@link ValueSelector#endingIterator(Object)}
   * @see ValueSelector#endingIterator(Object)
   */
  Iterator<Object> endingIterator(Object entity);
}
