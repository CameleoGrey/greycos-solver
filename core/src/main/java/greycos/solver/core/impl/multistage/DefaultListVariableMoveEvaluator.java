package greycos.solver.core.impl.multistage;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.multistage.ListVariableMoveEvaluator;
import greycos.solver.core.api.solver.multistage.MultistageOperation;
import greycos.solver.core.api.solver.multistage.MultistagePosition;

final class DefaultListVariableMoveEvaluator<
        Solution_, Entity_, Value_, Score_ extends Score<Score_>>
    extends AbstractMultistageEvaluator<Solution_, Score_>
    implements ListVariableMoveEvaluator<Solution_, Entity_, Value_, Score_> {

  DefaultListVariableMoveEvaluator(MultistageTransaction<Solution_, Score_> transaction) {
    super(transaction);
  }

  @Override
  @SuppressWarnings("unchecked")
  public MultistagePosition<Entity_> position(Value_ value) {
    checkActive();
    transaction.requireKnownListValue(value);
    var position = transaction.position(value);
    return position == null
        ? MultistagePosition.unassigned()
        : MultistagePosition.assigned((Entity_) position.entity(), position.index());
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<MultistagePosition<Entity_>> legalPositions(Value_ value) {
    checkTermination();
    transaction.requireKnownListValue(value);
    var source = transaction.position(value);
    if (source != null
        && (!transaction.movable(source.entity())
            || source.index() < transaction.firstUnpinnedIndex(source.entity()))) {
      checkTermination();
      return List.of();
    }
    var positions = new ArrayList<MultistagePosition<Entity_>>();
    int traversed = 0;
    for (var entity : transaction.entities) {
      if ((++traversed & 63) == 0) checkTermination();
      if (!transaction.movable(entity) || !transaction.range(entity).contains(value)) continue;
      int size =
          transaction.listVariable().getListSize(entity)
              - (source != null && source.entity() == entity ? 1 : 0);
      for (int index = transaction.firstUnpinnedIndex(entity); index <= size; index++) {
        if ((++traversed & 63) == 0) checkTermination();
        positions.add(MultistagePosition.assigned((Entity_) entity, index));
      }
    }
    if (transaction.listVariable().allowsUnassignedValues())
      positions.add(MultistagePosition.unassigned());
    checkTermination();
    return List.copyOf(positions);
  }

  @Override
  public MultistageOperation<Solution_> place(
      Value_ value, Entity_ destinationEntity, int destinationIndex) {
    return operation(
        MultistageOperationImpl.Kind.PLACE, value, destinationEntity, 0, 0, destinationIndex);
  }

  @Override
  public MultistageOperation<Solution_> unassign(Value_ value) {
    return operation(MultistageOperationImpl.Kind.UNASSIGN_LIST, value, null, 0, 0, 0);
  }

  @Override
  public MultistageOperation<Solution_> swap(Value_ leftValue, Value_ rightValue) {
    return operation(MultistageOperationImpl.Kind.SWAP_LIST, leftValue, rightValue, 0, 0, 0);
  }

  @Override
  public MultistageOperation<Solution_> reverse(
      Entity_ entity, int fromInclusive, int toExclusive) {
    return operation(
        MultistageOperationImpl.Kind.REVERSE, entity, null, fromInclusive, toExclusive, 0);
  }

  @Override
  public MultistageOperation<Solution_> relocateSubList(
      Entity_ sourceEntity,
      int fromInclusive,
      int toExclusive,
      Entity_ destinationEntity,
      int destinationIndex) {
    return operation(
        MultistageOperationImpl.Kind.RELOCATE,
        sourceEntity,
        destinationEntity,
        fromInclusive,
        toExclusive,
        destinationIndex);
  }
}
