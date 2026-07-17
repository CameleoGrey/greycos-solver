package ai.greycos.solver.core.impl.score.stream.collector;

import ai.greycos.solver.core.impl.util.MutableLong;

public abstract class AbstractCountSlot {

  private final MutableLong state;

  public AbstractCountSlot(MutableLong state) {
    this.state = state;
  }

  protected void addMapped() {
    state.increment();
  }

  protected void replaceWithMapped() {
    // count is unchanged
  }

  protected void removeMapped() {
    state.decrement();
  }
}
