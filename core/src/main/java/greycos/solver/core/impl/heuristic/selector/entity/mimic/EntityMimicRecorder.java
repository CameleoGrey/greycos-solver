package greycos.solver.core.impl.heuristic.selector.entity.mimic;

import java.util.Iterator;

import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbySelectionSource;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;

import org.jspecify.annotations.Nullable;

public interface EntityMimicRecorder<Solution_> {

  /** Stable population and live filter captured by the source factory, when available. */
  default @Nullable NearbySelectionSource<Solution_, EntitySelector<Solution_>>
      getNearbySelectionSource() {
    return null;
  }

  /**
   * @param replayingEntitySelector never null
   */
  void addMimicReplayingEntitySelector(
      MimicReplayingEntitySelector<Solution_> replayingEntitySelector);

  /**
   * @return As defined by {@link EntitySelector#getEntityDescriptor()}
   * @see EntitySelector#getEntityDescriptor()
   */
  EntityDescriptor<Solution_> getEntityDescriptor();

  /**
   * @return As defined by {@link EntitySelector#isNeverEnding()}
   * @see EntitySelector#isNeverEnding()
   */
  boolean isNeverEnding();

  /**
   * @return As defined by {@link EntitySelector#getSize()}
   * @see EntitySelector#getSize()
   */
  long getSize();

  /**
   * @return As defined by {@link EntitySelector#endingIterator()}
   * @see EntitySelector#endingIterator()
   */
  Iterator<Object> endingIterator();
}
