package ai.greycos.solver.core.impl.cotwin.valuerange;

import ai.greycos.solver.core.api.cotwin.valuerange.ValueRange;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeFactory;
import ai.greycos.solver.core.impl.cotwin.valuerange.sort.SortableValueRange;
import ai.greycos.solver.core.impl.cotwin.valuerange.sort.ValueRangeSorter;

import org.jspecify.annotations.NullMarked;

/**
 * Abstract superclass for {@link ValueRange}.
 *
 * @see ValueRange
 * @see ValueRangeFactory
 */
@NullMarked
public abstract class AbstractValueRange<T> implements ValueRange<T>, SortableValueRange<T> {

  @Override
  public boolean isEmpty() {
    return getSize() == 0L;
  }

  @Override
  public ValueRange<T> sort(ValueRangeSorter<T> sorter) {
    throw new UnsupportedOperationException();
  }
}
