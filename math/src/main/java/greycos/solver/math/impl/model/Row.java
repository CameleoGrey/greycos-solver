package greycos.solver.math.impl.model;

import org.jspecify.annotations.NullMarked;

/** An immutable normalized row shared by scoring sessions. */
@NullMarked
public record Row(int id, String name, RowKind kind, long constant, long bound) {

  public boolean isObjective() {
    return kind == RowKind.MINIMIZE || kind == RowKind.MAXIMIZE;
  }

  public long activity(long sum) {
    return Math.addExact(sum, constant);
  }

  /** Returns the signed mathematical residual, activity minus the bound, for every relation. */
  public long residual(long activity) {
    return Math.subtractExact(activity, bound);
  }

  public long violation(long activity) {
    if (isObjective()) {
      return 0;
    }
    long residual = residual(activity);
    return switch (kind) {
      case LE -> Math.max(0, residual);
      case GE -> residual < 0 ? Math.negateExact(residual) : 0;
      case EQ -> residual < 0 ? Math.negateExact(residual) : residual;
      default -> throw new IllegalStateException("Unexpected constraint row kind (" + kind + ").");
    };
  }

  public long softImpact(long activity) {
    return switch (kind) {
      case MINIMIZE -> Math.negateExact(activity);
      case MAXIMIZE -> activity;
      default -> 0;
    };
  }
}
