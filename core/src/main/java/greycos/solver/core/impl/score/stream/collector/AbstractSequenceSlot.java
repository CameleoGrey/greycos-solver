package greycos.solver.core.impl.score.stream.collector;

import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.ToIntFunction;

import greycos.solver.core.api.score.stream.common.SequenceChain;
import greycos.solver.core.impl.score.stream.collector.consecutive.ConsecutiveSetTree;

import org.jspecify.annotations.Nullable;

public abstract class AbstractSequenceSlot<Result_> {

  public static final class State<Result_> {
    private static final BinaryOperator<Integer> DIFFERENCE = (a, b) -> b - a;
    private final ConsecutiveSetTree<Result_, Integer, Integer> context =
        new ConsecutiveSetTree<>(DIFFERENCE, Integer::sum, 1, 0);
    private final ToIntFunction<Result_> toIndexFunction;

    public State(ToIntFunction<Result_> toIndexFunction) {
      this.toIndexFunction = Objects.requireNonNull(toIndexFunction);
    }

    public SequenceChain<Result_, Integer> result() {
      return context;
    }
  }

  private final State<Result_> state;
  private @Nullable Result_ cachedValue;
  private int cachedIndex;

  public AbstractSequenceSlot(State<Result_> state) {
    this.state = state;
  }

  protected void addMapped(Result_ result) {
    var index = state.toIndexFunction.applyAsInt(result);
    state.context.add(result, index);
    cachedValue = result;
    cachedIndex = index;
  }

  protected void replaceWithMapped(Result_ input) {
    var index = state.toIndexFunction.applyAsInt(input);
    if (input == cachedValue && index == cachedIndex) {
      return;
    }
    state.context.remove(cachedValue, cachedIndex);
    state.context.add(input, index);
    cachedValue = input;
    cachedIndex = index;
  }

  protected void removeMapped() {
    state.context.remove(cachedValue, cachedIndex);
    cachedValue = null;
    cachedIndex = 0;
  }
}
