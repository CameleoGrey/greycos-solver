package greycos.solver.math.impl.cotwin;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.cotwin.valuerange.ValueRange;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Finite inclusive range, including a representable {@link Long#MAX_VALUE} endpoint. */
@NullMarked
final class InclusiveLongValueRange implements ValueRange<Long> {

  private final long lower;
  private final long upper;
  private final long size;

  InclusiveLongValueRange(long lower, long upper) {
    var cardinality =
        BigInteger.valueOf(upper).subtract(BigInteger.valueOf(lower)).add(BigInteger.ONE);
    if (cardinality.signum() <= 0
        || cardinality.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
      throw new IllegalArgumentException(
          "The inclusive domain [%d, %d] has cardinality %s; it must be between 1 and %d."
              .formatted(lower, upper, cardinality, Long.MAX_VALUE));
    }
    this.lower = lower;
    this.upper = upper;
    this.size = cardinality.longValueExact();
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public boolean contains(@Nullable Long value) {
    return value != null && value >= lower && value <= upper;
  }

  @Override
  public long getSize() {
    return size;
  }

  @Override
  public Long get(long index) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException(
          "The domain index (%d) must be between 0 and %d exclusive.".formatted(index, size));
    }
    return Math.addExact(lower, index);
  }

  @Override
  public Iterator<Long> createOriginalIterator() {
    return new Iterator<>() {
      private long index;

      @Override
      public boolean hasNext() {
        return index < size;
      }

      @Override
      public Long next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        return get(index++);
      }
    };
  }

  @Override
  public Iterator<Long> createRandomIterator(RandomGenerator workingRandom) {
    Objects.requireNonNull(workingRandom);
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return true;
      }

      @Override
      public Long next() {
        return get(workingRandom.nextLong(size));
      }
    };
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof InclusiveLongValueRange range
        && lower == range.lower
        && upper == range.upper;
  }

  @Override
  public int hashCode() {
    return 31 * Long.hashCode(lower) + Long.hashCode(upper);
  }

  @Override
  public String toString() {
    return "[%d, %d]".formatted(lower, upper);
  }
}
