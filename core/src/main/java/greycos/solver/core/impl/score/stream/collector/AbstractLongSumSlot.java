package greycos.solver.core.impl.score.stream.collector;

import greycos.solver.core.impl.util.MutableLong;

public abstract class AbstractLongSumSlot {

  private final MutableLong state;
  private long cachedInput;

  public AbstractLongSumSlot(MutableLong state) {
    this.state = state;
  }

  protected void addMapped(long input) {
    cachedInput = input;
    state.add(input);
  }

  protected void replaceWithMapped(long input) {
    state.add(Math.subtractExact(input, cachedInput));
    cachedInput = input;
  }

  protected void removeMapped() {
    state.subtract(cachedInput);
  }
}
