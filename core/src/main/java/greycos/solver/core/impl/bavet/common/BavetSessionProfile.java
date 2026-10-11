package greycos.solver.core.impl.bavet.common;

import java.util.Set;
import java.util.function.Consumer;

import greycos.solver.core.impl.bavet.common.DefaultConstraintProfiler.Generation;
import greycos.solver.core.impl.bavet.common.DefaultConstraintProfiler.Unit;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Profiling state belonging to one Bavet session, never retained by the run collector. */
@NullMarked
public final class BavetSessionProfile {
  private final DefaultConstraintProfiler profiler;
  private final Generation generation;
  private Unit[] rootUnits = new Unit[0];
  private @Nullable Consumer<Set<AbstractNode>> activationListener;

  public BavetSessionProfile(DefaultConstraintProfiler profiler, Generation generation) {
    this.profiler = profiler;
    this.generation = generation;
  }

  public DefaultConstraintProfiler profiler() {
    return profiler;
  }

  public Generation generation() {
    return generation;
  }

  public void setRootUnits(Unit[] rootUnits) {
    this.rootUnits = rootUnits;
  }

  public Unit rootUnit(AbstractRootNode<?> node) {
    // Session-local IDs index the node list and therefore fit in an int.
    return rootUnits[(int) node.getId()];
  }

  public void setActivationListener(Consumer<Set<AbstractNode>> activationListener) {
    this.activationListener = activationListener;
  }

  public void activationCompleted(Set<AbstractNode> activeNodes) {
    if (activationListener != null) {
      activationListener.accept(activeNodes);
      activationListener = null;
    }
  }
}
