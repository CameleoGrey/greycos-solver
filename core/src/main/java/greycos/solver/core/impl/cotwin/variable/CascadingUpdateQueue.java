package greycos.solver.core.impl.cotwin.variable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Reusable, identity-based work storage for one director's cascading shadow updates. */
@NullMarked
final class CascadingUpdateQueue {

  private final IdentityHashMap<Object, Boolean> changedElementSet = new IdentityHashMap<>();
  private final ArrayList<Object> changedElements = new ArrayList<>();
  private final IdentityHashMap<Object, ListUpdates> updatesByEntity = new IdentityHashMap<>();
  private final ArrayList<ListUpdates> updatePool = new ArrayList<>();
  private int updateCount;
  private final ArrayList<Object> unassignedElements = new ArrayList<>();

  void addChangedElement(Object element) {
    if (changedElementSet.put(element, Boolean.TRUE) == null) {
      changedElements.add(element);
    }
  }

  void addRange(Object entity, int fromIndex, int toIndex) {
    var updates = updatesByEntity.get(entity);
    if (updates == null) {
      if (updateCount == updatePool.size()) {
        updates = new ListUpdates();
        updatePool.add(updates);
      } else {
        updates = updatePool.get(updateCount);
      }
      updateCount++;
      updates.entity = entity;
      updatesByEntity.put(entity, updates);
    }
    updates.addRange(fromIndex, toIndex);
  }

  void prepareRanges() {
    for (var i = 0; i < updateCount; i++) {
      updatePool.get(i).prepareRanges();
    }
  }

  boolean isEmpty() {
    return updateCount == 0 && changedElements.isEmpty();
  }

  int changedElementCount() {
    return changedElements.size();
  }

  Object changedElement(int index) {
    return changedElements.get(index);
  }

  int updateCount() {
    return updateCount;
  }

  ListUpdates updates(int index) {
    return updatePool.get(index);
  }

  void addUnassignedElement(Object element) {
    unassignedElements.add(element);
  }

  int unassignedElementCount() {
    return unassignedElements.size();
  }

  Object unassignedElement(int index) {
    return unassignedElements.get(index);
  }

  void clear() {
    // Remove only active entries. IdentityHashMap.clear() would scan the capacity retained from
    // the largest initialization, even when this move touched only one value or one list.
    for (var i = 0; i < changedElements.size(); i++) {
      changedElementSet.remove(changedElements.get(i));
    }
    changedElements.clear();
    for (var i = 0; i < updateCount; i++) {
      var updates = updatePool.get(i);
      updatesByEntity.remove(updates.entity);
      updates.entity = null;
      updates.rangeCount = 0;
    }
    updateCount = 0;
    unassignedElements.clear();
  }

  void close() {
    clear();
    updatePool.clear();
  }

  static final class ListUpdates {
    private @Nullable Object entity;
    private long[] ranges = new long[2];
    private int rangeCount;

    @Nullable Object entity() {
      return entity;
    }

    int rangeCount() {
      return rangeCount;
    }

    int fromIndex(int index) {
      return (int) (ranges[index] >>> 32);
    }

    int toIndex(int index) {
      return (int) ranges[index];
    }

    private void addRange(int fromIndex, int toIndex) {
      if (rangeCount == 1) {
        // The usual list move already describes every changed index in one range.
        var firstFrom = fromIndex(0);
        var firstTo = toIndex(0);
        if (firstFrom <= fromIndex && firstTo >= toIndex) {
          return;
        }
        if (firstFrom <= toIndex && firstTo >= fromIndex) {
          ranges[0] = pack(Math.min(firstFrom, fromIndex), Math.max(firstTo, toIndex));
          return;
        }
      }
      if (rangeCount == ranges.length) {
        ranges = Arrays.copyOf(ranges, ranges.length * 2);
      }
      // Batched disjoint changes are O(1) to append, regardless of input order.
      ranges[rangeCount++] = pack(fromIndex, toIndex);
    }

    private void prepareRanges() {
      if (rangeCount < 2) {
        return;
      }
      Arrays.sort(ranges, 0, rangeCount);
      var output = 0;
      var from = fromIndex(0);
      var to = toIndex(0);
      for (var i = 1; i < rangeCount; i++) {
        var nextFrom = fromIndex(i);
        var nextTo = toIndex(i);
        if (nextFrom <= to) {
          to = Math.max(to, nextTo);
        } else {
          ranges[output++] = pack(from, to);
          from = nextFrom;
          to = nextTo;
        }
      }
      ranges[output++] = pack(from, to);
      rangeCount = output;
    }

    private static long pack(int fromIndex, int toIndex) {
      return ((long) fromIndex << 32) | (toIndex & 0xffffffffL);
    }
  }
}
