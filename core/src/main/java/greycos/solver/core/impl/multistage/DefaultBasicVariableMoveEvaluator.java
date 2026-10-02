package greycos.solver.core.impl.multistage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.multistage.BasicVariableMoveEvaluator;
import greycos.solver.core.api.solver.multistage.MultistageOperation;

final class DefaultBasicVariableMoveEvaluator<
        Solution_, Entity_, Value_, Score_ extends Score<Score_>>
    extends AbstractMultistageEvaluator<Solution_, Score_>
    implements BasicVariableMoveEvaluator<Solution_, Entity_, Value_, Score_> {

  DefaultBasicVariableMoveEvaluator(MultistageTransaction<Solution_, Score_> transaction) {
    super(transaction);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Value_ currentValue(Entity_ entity) {
    checkActive();
    transaction.requireEntity(entity);
    return (Value_) transaction.variable.getValue(entity);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Value_> legalValues(Entity_ entity) {
    checkTermination();
    transaction.requireMovable(entity);
    var range = transaction.range(entity);
    var values = new ArrayList<Value_>();
    var iterator = range.createOriginalIterator();
    int traversed = 0;
    while (iterator.hasNext()) {
      if ((++traversed & 63) == 0) checkTermination();
      values.add((Value_) iterator.next());
    }
    checkTermination();
    // Optional variables legitimately include null.
    return Collections.unmodifiableList(values);
  }

  @Override
  public MultistageOperation<Solution_> assign(Entity_ entity, Value_ value) {
    return operation(MultistageOperationImpl.Kind.ASSIGN, entity, value, 0, 0, 0);
  }

  @Override
  public MultistageOperation<Solution_> unassign(Entity_ entity) {
    return operation(MultistageOperationImpl.Kind.UNASSIGN_BASIC, entity, null, 0, 0, 0);
  }

  @Override
  public MultistageOperation<Solution_> swap(Entity_ leftEntity, Entity_ rightEntity) {
    return operation(MultistageOperationImpl.Kind.SWAP_BASIC, leftEntity, rightEntity, 0, 0, 0);
  }
}
