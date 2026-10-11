package greycos.solver.math.api;

import org.jspecify.annotations.NullMarked;

/**
 * A model-owned integer variable with inclusive bounds and a stable declaration-order ID. Handles
 * have identity equality and are also leaves of the original expression graph.
 */
@NullMarked
public sealed class IntVar implements LinearExpression permits BinaryVar {

  private final Object owner;
  private final int id;
  private final String name;
  private final long lowerBound;
  private final long upperBound;

  IntVar(Object owner, int id, String name, long lowerBound, long upperBound) {
    this.owner = owner;
    this.id = id;
    this.name = name;
    this.lowerBound = lowerBound;
    this.upperBound = upperBound;
  }

  /** Returns the zero-based declaration-order ID within the owning model. */
  public final int id() {
    return id;
  }

  /** Returns the unique variable name within the owning model. */
  public final String name() {
    return name;
  }

  /** Returns the inclusive lower bound. */
  public final long lowerBound() {
    return lowerBound;
  }

  /** Returns the inclusive upper bound. */
  public final long upperBound() {
    return upperBound;
  }

  final boolean belongsTo(Object expectedOwner) {
    return owner == expectedOwner;
  }

  @Override
  public final String toString() {
    return name;
  }
}
