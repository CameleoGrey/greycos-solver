package greycos.solver.core.impl.score.stream.collector.consecutive;

import java.util.TreeMap;
import java.util.TreeSet;

import org.jspecify.annotations.NullMarked;

/**
 * Each {@link #value()} is associated with a point ({@link #index()}) on the number line.
 * Comparisons are made using the points on the number line, not the actual values.
 *
 * <p>Used as a key in a {@link TreeSet} or {@link TreeMap}. At the same index, entries retain
 * identity-hash order, with a tree-local insertion number distinguishing colliding hashes. Each
 * distinct value/index pair in a tree has one canonical entry and insertion number.
 *
 * @param value the value to be put on the number line
 * @param index position of the value on the number line
 * @param insertionOrder unique entry creation order within the owning tree
 * @param <Value_> generic type of the value
 * @param <Point_> generic type of the point on the number line
 */
@NullMarked
record ComparableValue<Value_, Point_ extends Comparable<Point_>>(
    Value_ value, Point_ index, long insertionOrder)
    implements Comparable<ComparableValue<Value_, Point_>> {

  @Override
  public int compareTo(ComparableValue<Value_, Point_> other) {
    if (this == other) {
      return 0;
    }
    var out = index.compareTo(other.index);
    if (out == 0) {
      return compareValueOrder(other);
    }
    return out;
  }

  int compareValueOrder(ComparableValue<Value_, Point_> other) {
    var comparison =
        Integer.compare(System.identityHashCode(value), System.identityHashCode(other.value));
    return comparison == 0 ? Long.compare(insertionOrder, other.insertionOrder) : comparison;
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof ComparableValue<?, ?> that)) {
      return false;
    }
    return value == that.value && index.equals(that.index) && insertionOrder == that.insertionOrder;
  }

  @Override
  public int hashCode() {
    return 31 * (31 * System.identityHashCode(value) + index.hashCode())
        + Long.hashCode(insertionOrder);
  }
}
