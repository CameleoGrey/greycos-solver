package ai.greycos.solver.core.impl.bavet.common;

import ai.greycos.solver.core.impl.bavet.common.tuple.Tuple;
import ai.greycos.solver.core.impl.bavet.common.tuple.TupleState;

/**
 * {@link DynamicPropagationQueue} requires the items it carries to extend this class, in order to
 * be able to store metadata on them. This metadata is necessary for efficient operation of the
 * queue.
 */
abstract sealed class AbstractPropagationMetadataCarrier<Tuple_ extends Tuple>
    permits Group, ExistsCounter {

  public int positionInDirtyList = -1;

  public abstract Tuple_ getTuple();

  public abstract TupleState getState();

  public abstract void setState(TupleState state);
}
