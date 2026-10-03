package greycos.solver.core.impl.multistage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.multistage.MultistageEvaluation;
import greycos.solver.core.api.solver.multistage.MultistageMoveEvaluator;
import greycos.solver.core.api.solver.multistage.MultistageOperation;

abstract class AbstractMultistageEvaluator<Solution_, Score_ extends Score<Score_>>
    implements MultistageMoveEvaluator<Solution_, Score_> {
  final MultistageTransaction<Solution_, Score_> transaction;
  final MultistageStageScope scope;

  AbstractMultistageEvaluator(MultistageTransaction<Solution_, Score_> transaction) {
    this(transaction, new MultistageStageScope());
  }

  AbstractMultistageEvaluator(
      MultistageTransaction<Solution_, Score_> transaction, MultistageStageScope scope) {
    this.transaction = transaction;
    this.scope = scope;
  }

  @Override
  public final Solution_ workingSolution() {
    checkActive();
    return transaction.director.getWorkingSolution();
  }

  @Override
  public final MultistageEvaluation<Score_> evaluate(MultistageOperation<Solution_> operation) {
    return transaction.evaluate(owned(operation));
  }

  @Override
  public final MultistageEvaluation<Score_> currentEvaluation() {
    checkActive();
    return transaction.currentEvaluation();
  }

  @Override
  public final MultistageOperation<Solution_> sequence(
      List<? extends MultistageOperation<Solution_>> operations) {
    checkTermination();
    var intents = new ArrayList<MultistageOperationImpl.Intent<Solution_>>();
    int traversed = 0;
    for (var operation : Objects.requireNonNull(operations)) {
      if ((++traversed & 63) == 0) checkTermination();
      for (var intent : owned(operation).intents()) {
        if ((++traversed & 63) == 0) checkTermination();
        intents.add(intent);
      }
    }
    checkTermination();
    return new MultistageOperationImpl<>(scope, intents);
  }

  @Override
  public final void checkTermination() {
    checkActive();
    transaction.checkpoint();
  }

  final void checkActive() {
    if (Thread.currentThread() != scope.owner) {
      throw new IllegalStateException(
          "A multistage evaluator may only be used by its stage callback thread.");
    }
    if (!scope.active)
      throw new IllegalStateException(
          "A multistage evaluator is only valid during its stage callback.");
    transaction.checkActive();
  }

  final MultistageOperationImpl<Solution_> owned(MultistageOperation<Solution_> operation) {
    checkActive();
    if (!(operation instanceof MultistageOperationImpl<Solution_> internal)
        || internal.owner() != scope) {
      throw new IllegalArgumentException(
          "The multistage operation belongs to another stage or was not created by its evaluator.");
    }
    return internal;
  }

  final MultistageOperation<Solution_> operation(
      MultistageVariableContext<Solution_> context,
      MultistageOperationImpl.Kind kind,
      Object first,
      Object second,
      int from,
      int to,
      int index) {
    checkActive();
    return new MultistageOperationImpl<>(
        scope,
        List.of(
            new MultistageOperationImpl.Intent<>(context, kind, first, second, from, to, index)));
  }

  final void invalidate() {
    scope.active = false;
  }
}
