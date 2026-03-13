package ai.greycos.solver.core.impl.neighborhood.stream;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.random.RandomGenerator;

import ai.greycos.solver.core.impl.bavet.common.tuple.UniTuple;
import ai.greycos.solver.core.impl.neighborhood.stream.enumerating.common.DefaultUniqueRandomSequence;
import ai.greycos.solver.core.impl.neighborhood.stream.enumerating.common.UniqueRandomSequence;
import ai.greycos.solver.core.preview.api.move.Move;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * An iterator for the bi-move stream which returns (A,B) pairs in random order. This iterator
 * implements sampling without replacement, meaning that once a particular (A,B) pair has been
 * returned, it will never be returned again by this iterator. This means that this random move
 * iterator will eventually end.
 *
 * <p>This iterator's implementation is determined by the following considerations:
 *
 * <ol>
 *   <li>The left and right datasets need to support efficient random access.
 *   <li>The left and right datasets are possibly large, which makes their copying and mutation
 *       prohibitively expensive.
 *   <li>Keeping all possible pairs in memory is prohibitively expensive, for the same reason.
 *       (Cartesian product of A x B.)
 *   <li>The solver will never require all possible pairs of (A,B). Instead, it will terminate the
 *       iteration after selecting just a handful, as the chance of accepting a move grows more and
 *       more with each new move.
 * </ol>
 *
 * <p>From the above, the key design decisions are:
 *
 * <ul>
 *   <li>Both left and right datasets are kept in the {@link ArrayList} in which they came. This
 *       list will never be copied, nor will it be mutated.
 *   <li>When an item needs to be selected from either list, it is wrapped in {@link
 *       DefaultUniqueRandomSequence}, which allows to pick random elements and remembers which
 *       elements were already picked, never to pick them again.
 *   <li>This type is only created when needed. Once A is picked, a sequence for B is created and
 *       stored for later use in case A is picked again. Once the B sequence is exhausted, it is
 *       removed and A is discarded.
 *   <li>Filtering of (A,B) pair only happens after both A and B have been randomly selected. This
 *       guarantees that filtering is only applied when necessary, as opposed to pre-filtering the
 *       entire dataset, which could be prohibitively expensive.
 *   <li>If the filter rejects the pair, (A,B) is discarded and a new B is selected. This guarantees
 *       that A keeps its selection probability of (1/A).
 * </ul>
 *
 * This implementation is somewhat expensive in terms of CPU and memory, but it is likely the best
 * we can do given the constraints.
 */
@NullMarked
final class BiRandomMoveIterator<Solution_, A, B> implements Iterator<Move<Solution_>> {

  private final BiMoveStreamContext<Solution_, A, B> context;
  private final RandomGenerator workingRandom;

  // Fields required for iteration.
  private final DefaultUniqueRandomSequence<UniTuple<A>> leftTupleSequence;
  private final int rightSequenceStoreIndex;
  private @Nullable Move<Solution_> nextMove;

  public BiRandomMoveIterator(
      BiMoveStreamContext<Solution_, A, B> context, RandomGenerator workingRandom) {
    this.context = Objects.requireNonNull(context);
    this.workingRandom = Objects.requireNonNull(workingRandom);
    var leftDatasetInstance = context.getLeftDatasetInstance();
    this.rightSequenceStoreIndex = leftDatasetInstance.getRightSequenceStoreIndex();
    this.leftTupleSequence = leftDatasetInstance.buildRandomSequence();
  }

  private Iterator<UniTuple<B>> createRightTupleIterator(UniTuple<A> leftTuple) {
    var rightDatasetInstance = context.getRightDatasetInstance();
    var compositeKey = rightDatasetInstance.produceCompositeKey(leftTuple);
    var filter = rightDatasetInstance.getFilter();
    if (filter == null) {
      return rightDatasetInstance.randomIterator(compositeKey, workingRandom);
    }
    var leftFact = leftTuple.getA();
    var solutionView = context.neighborhoodSession().getSolutionView();
    return rightDatasetInstance.randomIterator(
        compositeKey,
        workingRandom,
        rightTuple -> filter.test(solutionView, leftFact, rightTuple.getA()));
  }

  @Override
  public boolean hasNext() {
    if (nextMove != null) {
      return true;
    }

    while (!leftTupleSequence.isEmpty()) {
      var leftElement = leftTupleSequence.pick(workingRandom);
      var rightEmpty = pickNextMove(leftElement);
      if (rightEmpty) {
        leftTupleSequence.remove(leftElement.index());
        leftElement.value().setStore(rightSequenceStoreIndex, null);
      }
      if (nextMove != null) {
        if (nextMove
            instanceof ai.greycos.solver.core.impl.heuristic.move.Move<Solution_> legacyMove) {
          throw new UnsupportedOperationException(
              """
                            Neighborhoods do not support legacy moves.
                            Please refactor your code (%s) to use the new Move API."""
                  .formatted(legacyMove.getClass().getCanonicalName()));
        }
        return true;
      }
    }
    return false;
  }

  private boolean pickNextMove(UniqueRandomSequence.SequenceElement<UniTuple<A>> leftElement) {
    var leftTuple = leftElement.value();
    @SuppressWarnings("unchecked")
    var rightTupleIterator = (Iterator<UniTuple<B>>) leftTuple.getStore(rightSequenceStoreIndex);
    if (rightTupleIterator == null) {
      rightTupleIterator = createRightTupleIterator(leftTuple);
      leftTuple.setStore(rightSequenceStoreIndex, rightTupleIterator);
    }
    if (!rightTupleIterator.hasNext()) {
      return true;
    }
    var bTuple = rightTupleIterator.next();
    nextMove = context.buildMove(leftTuple.getA(), bTuple.getA());
    rightTupleIterator.remove();
    return false;
  }

  @Override
  public Move<Solution_> next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    var result = Objects.requireNonNull(nextMove);
    nextMove = null;
    return result;
  }
}
