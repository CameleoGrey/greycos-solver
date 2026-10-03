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

  private final MultistageVariableContext<Solution_> context;

  DefaultBasicVariableMoveEvaluator(MultistageTransaction<Solution_, Score_> transaction) {
    this(transaction, transaction.defaultContext, new MultistageStageScope());
  }

  DefaultBasicVariableMoveEvaluator(
      MultistageTransaction<Solution_, Score_> transaction,
      MultistageVariableContext<Solution_> context,
      MultistageStageScope scope) {
    super(transaction, scope);
    this.context = context;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Value_ currentValue(Entity_ entity) {
    checkActive();
    context.requireEntity(entity);
    return (Value_) context.variable.getValue(entity);
  }

  @Override
  @SuppressWarnings("unchecked")
  public List<Value_> legalValues(Entity_ entity) {
    checkTermination();
    context.requireMovable(entity);
    var range = context.range(entity);
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
    return operation(context, MultistageOperationImpl.Kind.ASSIGN, entity, value, 0, 0, 0);
  }

  @Override
  public MultistageOperation<Solution_> unassign(Entity_ entity) {
    return operation(context, MultistageOperationImpl.Kind.UNASSIGN_BASIC, entity, null, 0, 0, 0);
  }

  @Override
  public MultistageOperation<Solution_> swap(Entity_ leftEntity, Entity_ rightEntity) {
    return operation(
        context, MultistageOperationImpl.Kind.SWAP_BASIC, leftEntity, rightEntity, 0, 0, 0);
  }
}
