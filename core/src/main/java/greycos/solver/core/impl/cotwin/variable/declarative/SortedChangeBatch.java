package greycos.solver.core.impl.cotwin.variable.declarative;

import java.util.Arrays;
import java.util.Collection;

import greycos.solver.core.impl.cotwin.variable.ListVariableState;
import greycos.solver.core.impl.util.MutableInt;
import greycos.solver.core.impl.util.ShrinkingIdentityHashMap;
import greycos.solver.core.preview.api.cotwin.metamodel.PositionInList;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The changed entities of one update, sorted by index in their list and read in walk order.
 * Direction changes only the index ordering; equal indexes retain insertion order in both
 * directions. The batch is reused across updates to avoid allocation.
 */
@NullMarked
final class SortedChangeBatch {

  private static final int DEFAULT_SIZE = 16; // Arbitrarily chosen.

  private final boolean forward;
  // Parallel arrays, indexed by position in the loaded collection.
  private Object[] entityArray = new Object[DEFAULT_SIZE];
  private @Nullable Object[] ownerArray = new Object[DEFAULT_SIZE];

  /** Sorted ascending by direction-adjusted index, then insertion position. */
  private long[] sortKeyArray = new long[DEFAULT_SIZE];

  /**
   * Values for ownerToLastProcessedIndexMap; an owner first seen at sorted position i takes slot i.
   * Exists to avoid map put() where a get() + update of the mutable int suffices.
   */
  private MutableInt[] lastProcessedIndexArray = newMutableIntArray(DEFAULT_SIZE);

  private final ShrinkingIdentityHashMap<Object, MutableInt> ownerToLastProcessedIndexMap =
      new ShrinkingIdentityHashMap<>();
  private int size;

  /**
   * @param forward true to read in increasing index order, false to read in decreasing index order
   */
  SortedChangeBatch(boolean forward) {
    this.forward = forward;
  }

  void load(
      Collection<Object> changedEntities, ListVariableState<?, Object, Object> listVariableState) {
    size = changedEntities.size();
    if (sortKeyArray.length < size) {
      var capacity = Math.max(size, sortKeyArray.length * 2);
      entityArray = new Object[capacity];
      ownerArray = new Object[capacity];
      sortKeyArray = new long[capacity];
      lastProcessedIndexArray = newMutableIntArray(capacity);
    }
    var position = 0;
    for (var changedEntity : changedEntities) {
      Object owner = null;
      var index = 0;
      if (listVariableState.getElementPosition(changedEntity)
          instanceof PositionInList positionInList) {
        owner = positionInList.entity();
        index = positionInList.index();
      }
      entityArray[position] = changedEntity;
      ownerArray[position] = owner;
      sortKeyArray[position] = sortKey(forward ? index : Integer.MAX_VALUE - index, position);
      position++;
    }
    // Unique keys make this sort stable.
    Arrays.sort(sortKeyArray, 0, size);
  }

  int size() {
    return size;
  }

  Object entity(int i) {
    return entityArray[sortKeyPosition(keyAt(i))];
  }

  @Nullable Object owner(int i) {
    return ownerArray[sortKeyPosition(keyAt(i))];
  }

  int index(int i) {
    var orderedIndex = sortKeyIndex(keyAt(i));
    return forward ? orderedIndex : Integer.MAX_VALUE - orderedIndex;
  }

  private long keyAt(int i) {
    return sortKeyArray[i];
  }

  /**
   * @param i position in walk order of an assigned entity
   * @return null if an earlier entity of the same owner already processed past this one; otherwise
   *     the slot to store the index of the last processed entity in
   */
  @Nullable MutableInt lastProcessedIndexToAdvance(int i) {
    var owner = owner(i);
    var lastProcessedIndex = ownerToLastProcessedIndexMap.get(owner);
    if (lastProcessedIndex == null) {
      lastProcessedIndex = lastProcessedIndexArray[i];
      ownerToLastProcessedIndexMap.put(owner, lastProcessedIndex);
      return lastProcessedIndex;
    }
    var index = index(i);
    var notYetPassed =
        forward ? lastProcessedIndex.intValue() < index : lastProcessedIndex.intValue() > index;
    return notYetPassed ? lastProcessedIndex : null;
  }

  void clear() {
    Arrays.fill(entityArray, 0, size, null);
    Arrays.fill(ownerArray, 0, size, null);
    ownerToLastProcessedIndexMap.clear();
    size = 0;
  }

  private static MutableInt[] newMutableIntArray(int size) {
    var result = new MutableInt[size];
    for (var i = 0; i < size; i++) {
      result[i] = new MutableInt();
    }
    return result;
  }

  /** The index goes in the high bits, so keys sort by it first and by position for ties. */
  static long sortKey(int index, int position) {
    return ((long) index << Integer.SIZE) | position;
  }

  static int sortKeyIndex(long sortKey) {
    return (int) (sortKey >>> Integer.SIZE);
  }

  static int sortKeyPosition(long sortKey) {
    return (int) sortKey;
  }
}
