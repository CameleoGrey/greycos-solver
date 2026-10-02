package greycos.solver.core.api.solver.multistage;

import java.util.List;

import greycos.solver.core.api.score.Score;

/**
 * Evaluates changes to the single list variable bound to the enclosing selector. Values identify
 * elements by working-object identity. All source elements and destination indices must respect
 * pinning and the destination's value range.
 */
public interface ListVariableMoveEvaluator<Solution_, Entity_, Value_, Score_ extends Score<Score_>>
    extends MultistageMoveEvaluator<Solution_, Score_> {

  /** Returns the value's current placement, or an unassigned position. */
  MultistagePosition<Entity_> position(Value_ value);

  /**
   * Returns legal destinations in entity and index order. Indices refer to the destination after
   * removing the value from its current position. Optional unassignment, when allowed, is last.
   */
  List<MultistagePosition<Entity_>> legalPositions(Value_ value);

  /**
   * Assigns or relocates the value. The insertion index refers to the destination after removing
   * this value from its current position, including when source and destination are the same.
   */
  MultistageOperation<Solution_> place(
      Value_ value, Entity_ destinationEntity, int destinationIndex);

  /**
   * Temporarily removes the value, including when unassigned values are mandatory to repair. A
   * completed candidate must assign every mandatory value.
   */
  MultistageOperation<Solution_> unassign(Value_ value);

  /** Exchanges two currently assigned movable values. Both destination ranges must accept them. */
  MultistageOperation<Solution_> swap(Value_ leftValue, Value_ rightValue);

  /** Reverses the movable half-open sublist {@code [fromInclusive, toExclusive)}. */
  MultistageOperation<Solution_> reverse(Entity_ entity, int fromInclusive, int toExclusive);

  /**
   * Relocates the movable half-open sublist without changing its order. The destination index
   * refers to the destination after the entire sublist is removed, including within one list.
   */
  MultistageOperation<Solution_> relocateSubList(
      Entity_ sourceEntity,
      int fromInclusive,
      int toExclusive,
      Entity_ destinationEntity,
      int destinationIndex);
}
