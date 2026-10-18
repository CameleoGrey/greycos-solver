package greycos.solver.core.impl.bavet.common;

import java.util.IdentityHashMap;
import java.util.Objects;

import greycos.solver.core.impl.bavet.common.provenance.LineageHandle;
import greycos.solver.core.impl.bavet.common.tuple.InTupleStorePositionTracker;
import greycos.solver.core.impl.bavet.common.tuple.LeftTupleLifecycle;
import greycos.solver.core.impl.bavet.common.tuple.RightTupleLifecycle;
import greycos.solver.core.impl.bavet.common.tuple.Tuple;
import greycos.solver.core.impl.bavet.common.tuple.TupleLifecycle;
import greycos.solver.core.impl.bavet.common.tuple.UniTuple;
import greycos.solver.core.impl.util.ElementAwareLinkedList;

import org.jspecify.annotations.Nullable;

/**
 * There is a strong likelihood that any change made to this class should also be made to {@link
 * AbstractIndexedIfExistsNode}.
 *
 * @param <LeftTuple_>
 * @param <Right_>
 */
public abstract class AbstractUnindexedIfExistsNode<LeftTuple_ extends Tuple, Right_>
    extends AbstractIfExistsNode<LeftTuple_, Right_>
    implements LeftTupleLifecycle<LeftTuple_>, RightTupleLifecycle<UniTuple<Right_>> {

  private final int inputStoreIndexLeftCounterEntry;

  private final int inputStoreIndexRightEntry;

  // Acts as a leftTupleList too
  private final ElementAwareLinkedList<ExistsCounter<LeftTuple_>> counterList =
      new ElementAwareLinkedList<>();
  private final ElementAwareLinkedList<UniTuple<Right_>> rightTupleList =
      new ElementAwareLinkedList<>();
  private @Nullable LineageHandle sharedWitnesses;
  private @Nullable IdentityHashMap<Tuple, LineageHandle> sharedWitnessHandles;

  protected AbstractUnindexedIfExistsNode(
      boolean shouldExist,
      TupleLifecycle<LeftTuple_> nextNodesTupleLifecycle,
      boolean isFiltering,
      InTupleStorePositionTracker tupleStorePositionTracker) {
    super(shouldExist, nextNodesTupleLifecycle, isFiltering, tupleStorePositionTracker);
    this.inputStoreIndexLeftCounterEntry = tupleStorePositionTracker.reserveNextLeft();
    this.inputStoreIndexRightEntry = tupleStorePositionTracker.reserveNextRight();
  }

  @Override
  protected void initializeProvenance() {
    if (shouldExist && !isFiltering) {
      sharedWitnesses = LineageHandle.mutable();
      sharedWitnessHandles = new IdentityHashMap<>();
    }
  }

  @Override
  public final void insertLeft(LeftTuple_ leftTuple) {
    if (leftTuple.getStore(inputStoreIndexLeftCounterEntry) != null) {
      throw new IllegalStateException(
          "Impossible state: the input for the tuple (%s) was already added in the tupleStore."
              .formatted(leftTuple));
    }
    var counter = createCounter(leftTuple);
    if (sharedWitnesses != null) {
      counter.provenance.witnesses.add(sharedWitnesses);
    }
    leftTuple.setStore(inputStoreIndexLeftCounterEntry, counterList.add(counter));

    if (!isFiltering) {
      counter.countRight = rightTupleList.size();
      initCounterLeft(counter);
    } else {
      // Defer the cross-match (the opposite-side read) to this node's own layer turn instead of
      // computing it now,
      // at whatever layer the parent that produced leftTuple happens to be in.
      // See AbstractCrossMatchNode's pendingLeft/pendingRight javadoc.
      crossMatchLeft(leftTuple);
    }
  }

  @Override
  public final void updateLeft(LeftTuple_ leftTuple) {
    ElementAwareLinkedList.Entry<ExistsCounter<LeftTuple_>> counterEntry =
        leftTuple.getStore(inputStoreIndexLeftCounterEntry);
    if (counterEntry == null) {
      // No fail fast if null because we don't track which tuples made it through the filter
      // predicate(s)
      insertLeft(leftTuple);
      return;
    }
    var counter = counterEntry.element();
    // The indexers contain counters in the DEAD state, to track the rightCount.
    if (!isFiltering) {
      updateUnchangedCounterLeft(counter);
    } else {
      // Eager own-side cleanup, then defer the re-walk of the opposite side.
      // See AbstractCrossMatchNode's pendingLeft/pendingRight javadoc.
      clearLeftTrackerList(leftTuple);
      counter.countRight = 0;
      crossMatchLeft(leftTuple);
    }
  }

  @Override
  public final void retractLeft(LeftTuple_ leftTuple) {
    clearPendingLeft(
        leftTuple); // A tuple can be retracted before its turn to reconcile ever comes.
    ElementAwareLinkedList.Entry<ExistsCounter<LeftTuple_>> counterEntry =
        leftTuple.removeStore(inputStoreIndexLeftCounterEntry);
    if (counterEntry == null) {
      // No fail fast if null because we don't track which tuples made it through the filter
      // predicate(s)
      return;
    }
    var counter = counterEntry.element();
    counterEntry.remove();
    clearLeftTrackerList(leftTuple);
    killCounterLeft(counter);
    clearWitnesses(counter);
  }

  @Override
  public final void insertRight(UniTuple<Right_> rightTuple) {
    if (rightTuple.getStore(inputStoreIndexRightEntry) != null) {
      throw new IllegalStateException(
          "Impossible state: the input for the tuple (%s) was already added in the tupleStore."
              .formatted(rightTuple));
    }
    rightTuple.setStore(inputStoreIndexRightEntry, rightTupleList.add(rightTuple));
    if (sharedWitnesses != null) {
      var handle = provenance.right().get(rightTuple);
      sharedWitnessHandles.put(rightTuple, handle);
      sharedWitnesses.add(handle);
    }
    if (!isFiltering) {
      counterList.forEach(this::incrementCounterRight);
    } else {
      // Defer the cross-match (the opposite-side read) to this node's own layer turn instead of
      // computing it now,
      // at whatever layer the parent that produced rightTuple happens to be in.
      // See AbstractCrossMatchNode's pendingLeft/pendingRight javadoc.
      crossMatchRight(rightTuple);
    }
  }

  @Override
  public final void updateRight(UniTuple<Right_> rightTuple) {
    ElementAwareLinkedList.Entry<UniTuple<Right_>> rightEntry =
        rightTuple.getStore(inputStoreIndexRightEntry);
    if (rightEntry == null) {
      // No fail fast if null because we don't track which tuples made it through the filter
      // predicate(s)
      insertRight(rightTuple);
      return;
    }
    if (isFiltering) {
      // Eager own-side cleanup, then defer the re-walk of the opposite side.
      // See AbstractCrossMatchNode's pendingLeft/pendingRight javadoc.
      clearRightTrackerList(rightTuple);
      crossMatchRight(rightTuple);
    }
  }

  @Override
  public final void retractRight(UniTuple<Right_> rightTuple) {
    clearPendingRight(
        rightTuple); // A tuple can be retracted before its turn to reconcile ever comes.
    ElementAwareLinkedList.Entry<UniTuple<Right_>> rightEntry =
        rightTuple.removeStore(inputStoreIndexRightEntry);
    if (rightEntry == null) {
      // No fail fast if null because we don't track which tuples made it through the filter
      // predicate(s)
      return;
    }
    rightEntry.remove();
    if (sharedWitnesses != null) {
      sharedWitnesses.remove(Objects.requireNonNull(sharedWitnessHandles.remove(rightTuple)));
    }
    if (!isFiltering) {
      counterList.forEach(this::decrementCounterRight);
    } else {
      clearRightTrackerList(rightTuple);
    }
  }

  @Override
  protected void reconcilePendingLeft(LeftTuple_ leftTuple) {
    ElementAwareLinkedList.Entry<ExistsCounter<LeftTuple_>> counterEntry =
        leftTuple.getStore(inputStoreIndexLeftCounterEntry);
    var counter = counterEntry.element();
    clearLeftTrackerList(leftTuple);
    counter.countRight = 0;
    for (var rightTuple : rightTupleList) {
      updateCounterLeft(counter, rightTuple);
    }
    updateCounterLeft(counter);
  }

  @Override
  protected void reconcilePendingRight(UniTuple<Right_> rightTuple) {
    clearRightTrackerList(rightTuple);
    for (var counter : counterList) {
      updateCounterRight(counter, rightTuple);
    }
  }
}
