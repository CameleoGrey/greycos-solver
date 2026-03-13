package ai.greycos.solver.core.impl.cotwin.valuerange.buildin.composite;

import java.util.Iterator;
import java.util.List;
import java.util.random.RandomGenerator;

import ai.greycos.solver.core.api.cotwin.valuerange.ValueRange;
import ai.greycos.solver.core.impl.cotwin.valuerange.AbstractCountableValueRange;
import ai.greycos.solver.core.impl.cotwin.valuerange.AbstractValueRange;
import ai.greycos.solver.core.impl.cotwin.valuerange.ValueRangeCache;
import ai.greycos.solver.core.impl.cotwin.valuerange.sort.ValueRangeSorter;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class CompositeCountableValueRange<T> extends AbstractCountableValueRange<T> {

  private final List<? extends AbstractValueRange<T>> valueRangeList;
  private final ValueRangeCache<T> cache;

  public CompositeCountableValueRange(List<? extends AbstractValueRange<T>> childValueRangeList) {
    var maximumSize = 0L;
    for (AbstractValueRange<T> childValueRange : childValueRangeList) {
      // We choose to select the larger size instead of summing all sizes, as they may not be
      // distinct.
      // This approach opts for the cost of resizing instead of allocating larger chunks of memory.
      var size = childValueRange.getSize();
      if (size > maximumSize) {
        maximumSize = size;
      }
    }
    // To eliminate duplicates, we immediately expand the child value ranges into a cache.
    this.cache = ValueRangeCache.of((int) maximumSize);
    for (var childValueRange : childValueRangeList) {
      // If the child value range includes nulls, we will ignore them.
      // They will be added later by the wrapper, if necessary.
      if (childValueRange
          instanceof NullAllowingCountableValueRange<T> nullAllowingCountableValueRange) {
        childValueRange = nullAllowingCountableValueRange.getChildValueRange();
      }
      childValueRange.createOriginalIterator().forEachRemaining(cache::add);
    }
    this.valueRangeList = childValueRangeList;
  }

  private CompositeCountableValueRange(
      List<? extends AbstractValueRange<T>> valueRangeList, ValueRangeCache<T> cache) {
    this.valueRangeList = valueRangeList;
    this.cache = cache;
  }

  @Override
  public long getSize() {
    return cache.getSize();
  }

  @Override
  public T get(long index) {
    return cache.get((int) index);
  }

  @Override
  public ValueRange<T> sort(ValueRangeSorter<T> sorter) {
    var sortedCache = this.cache.sort(sorter);
    return new CompositeCountableValueRange<>(valueRangeList, sortedCache);
  }

  @Override
  public boolean contains(@Nullable T value) {
    return cache.contains(value);
  }

  @Override
  public Iterator<T> createOriginalIterator() {
    return cache.iterator();
  }

  @Override
  public Iterator<T> createRandomIterator(RandomGenerator workingRandom) {
    return cache.iterator(workingRandom);
  }

  @Override
  public boolean equals(Object o) {
    // We do not use Objects.equals(...) due to https://bugs.openjdk.org/browse/JDK-8015417.
    if (this == o) {
      return true;
    }
    return o instanceof CompositeCountableValueRange<?> that
        && valueRangeList.equals(that.valueRangeList);
  }

  @Override
  public int hashCode() {
    // We do not use Objects.hash(...) because it creates an array each time.
    // We do not use Objects.hashCode() due to https://bugs.openjdk.org/browse/JDK-8015417.
    return 31 * valueRangeList.hashCode();
  }
}
