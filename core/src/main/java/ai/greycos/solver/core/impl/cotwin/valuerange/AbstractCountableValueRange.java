package ai.greycos.solver.core.impl.cotwin.valuerange;

import java.time.OffsetDateTime;

import ai.greycos.solver.core.api.cotwin.valuerange.CountableValueRange;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRange;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeFactory;
import ai.greycos.solver.core.impl.cotwin.valuerange.sort.SortableValueRange;
import ai.greycos.solver.core.impl.cotwin.valuerange.sort.ValueRangeSorter;

import org.jspecify.annotations.NullMarked;

/**
 * Abstract superclass for {@link CountableValueRange} (and therefore {@link ValueRange}).
 *
 * @see CountableValueRange
 * @see ValueRange
 * @see ValueRangeFactory
 */
@NullMarked
public abstract class AbstractCountableValueRange<T>
    implements CountableValueRange<T>, SortableValueRange<T> {

  /**
   * Certain optimizations can be applied if {@link Object#equals(Object)} can be relied upon to
   * determine that two objects are the same. Typically, this is not guaranteed for user-provided
   * objects, but is true for many builtin types and classes, such as {@link String}, {@link
   * Integer}, {@link OffsetDateTime}, etc.
   *
   * @return true if we should trust {@link Object#equals(Object)} in this value range
   */
  public boolean isValueImmutable() {
    return true; // Override in subclasses if needed.
  }

  @Override
  public boolean isEmpty() {
    return getSize() == 0L;
  }

  @Override
  public ValueRange<T> sort(ValueRangeSorter<T> sorter) {
    // The sorting operation is not supported by default
    // and must be explicitly implemented by the child classes if needed.
    throw new UnsupportedOperationException();
  }
}
