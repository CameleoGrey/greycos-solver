package greycos.solver.core.impl.multistage;

import java.util.HashMap;
import java.util.Map;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.multistage.BasicVariableMoveEvaluator;
import greycos.solver.core.api.solver.multistage.BasicVariableReference;
import greycos.solver.core.api.solver.multistage.CrossVariableMoveEvaluator;
import greycos.solver.core.api.solver.multistage.ListVariableMoveEvaluator;
import greycos.solver.core.api.solver.multistage.ListVariableReference;
import greycos.solver.core.api.solver.multistage.MultistageVariableReference;

/** Typed variable views sharing one stage lifetime, transaction and operation namespace. */
final class DefaultCrossVariableMoveEvaluator<Solution_, Score_ extends Score<Score_>>
    extends AbstractMultistageEvaluator<Solution_, Score_>
    implements CrossVariableMoveEvaluator<Solution_, Score_> {
  private final Map<
          MultistageVariableReference<?, ?>, AbstractMultistageEvaluator<Solution_, Score_>>
      views = new HashMap<>();

  DefaultCrossVariableMoveEvaluator(MultistageTransaction<Solution_, Score_> transaction) {
    super(transaction);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Entity_, Value_> BasicVariableMoveEvaluator<Solution_, Entity_, Value_, Score_> basic(
      BasicVariableReference<Entity_, Value_> variable) {
    checkActive();
    return (BasicVariableMoveEvaluator<Solution_, Entity_, Value_, Score_>)
        views.computeIfAbsent(
            variable,
            reference ->
                new DefaultBasicVariableMoveEvaluator<>(
                    transaction, transaction.context(reference), scope));
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Entity_, Value_> ListVariableMoveEvaluator<Solution_, Entity_, Value_, Score_> list(
      ListVariableReference<Entity_, Value_> variable) {
    checkActive();
    return (ListVariableMoveEvaluator<Solution_, Entity_, Value_, Score_>)
        views.computeIfAbsent(
            variable,
            reference ->
                new DefaultListVariableMoveEvaluator<>(
                    transaction, transaction.context(reference), scope));
  }
}
