package ai.greycos.solver.core.impl.cotwin.valuerange.buildin.composite;

import java.util.Iterator;
import java.util.random.RandomGenerator;

import ai.greycos.solver.core.api.cotwin.valuerange.ValueRange;
import ai.greycos.solver.core.impl.cotwin.valuerange.AbstractCountableValueRange;
import ai.greycos.solver.core.impl.cotwin.valuerange.AbstractValueRange;
import ai.greycos.solver.core.impl.cotwin.valuerange.sort.ValueRangeSorter;
import ai.greycos.solver.core.impl.cotwin.valuerange.util.ValueRangeIterator;
import ai.greycos.solver.core.impl.solver.random.RandomUtils;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class NullAllowingCountableValueRange<T> extends AbstractCountableValueRange<T> {

  private final AbstractValueRange<T> childValueRange;
  private final long size;

  public NullAllowingCountableValueRange(ValueRange<T> childValueRange) {
    this.childValueRange = (AbstractValueRange<T>) childValueRange;
    if (childValueRange instanceof NullAllowingCountableValueRange<T>) {
      throw new IllegalArgumentException(
          "Impossible state: The childValueRange (%s) must not be a %s, because it is already wrapped in one."
              .formatted(childValueRange, NullAllowingCountableValueRange.class.getSimpleName()));
    }
    size = childValueRange.getSize() + 1L;
  }

  AbstractValueRange<T> getChildValueRange() {
    return childValueRange;
  }

  @Override
  public long getSize() {
    return size;
  }

  @Override
  public T get(long index) {
    if (index == 0) { // Consistent with the iterator.
      return null;
    } else {
      return childValueRange.get(index - 1L);
    }
  }

  @Override
  public boolean contains(@Nullable T value) {
    if (value == null) {
      return true;
    }
    return childValueRange.contains(value);
  }

  @Override
  public ValueRange<T> sort(ValueRangeSorter<T> sorter) {
    return childValueRange.sort(sorter);
  }

  @Override
  public Iterator<T> createOriginalIterator() {
    return new OriginalNullValueRangeIterator(childValueRange.createOriginalIterator());
  }

  private class OriginalNullValueRangeIterator extends ValueRangeIterator<T> {

    private boolean nullReturned = false;
    private final Iterator<T> childIterator;

    public OriginalNullValueRangeIterator(Iterator<T> childIterator) {
      this.childIterator = childIterator;
    }

    @Override
    public boolean hasNext() {
      return !nullReturned || childIterator.hasNext();
    }

    @Override
    public T next() {
      if (!nullReturned) {
        nullReturned = true;
        return null;
      } else {
        return childIterator.next();
      }
    }
  }

  @Override
  public Iterator<T> createRandomIterator(RandomGenerator workingRandom) {
    return new RandomNullValueRangeIterator(workingRandom);
  }

  private class RandomNullValueRangeIterator extends ValueRangeIterator<T> {

    private final RandomGenerator workingRandom;

    public RandomNullValueRangeIterator(RandomGenerator workingRandom) {
      this.workingRandom = workingRandom;
    }

    @Override
    public boolean hasNext() {
      return true;
    }

    @Override
    public T next() {
      long index = RandomUtils.nextLong(workingRandom, size);
      return get(index);
    }
  }

  @Override
  public boolean equals(Object o) {
    // We do not use Objects.equals(...) due to https://bugs.openjdk.org/browse/JDK-8015417.
    if (this == o) {
      return true;
    }
    return o instanceof NullAllowingCountableValueRange<?> that
        && size == that.size
        && childValueRange.equals(that.childValueRange);
  }

  @Override
  public int hashCode() {
    // We do not use Objects.hash(...) because it creates an array each time.
    // We do not use Objects.hashCode() due to https://bugs.openjdk.org/browse/JDK-8015417.
    var hash = 1;
    hash = 31 * hash + Long.hashCode(size);
    return 31 * hash + childValueRange.hashCode();
  }

  @Override
  public String toString() {
    return "[null]∪" + childValueRange; // Formatting: interval (mathematics) ISO 31-11
  }
}
