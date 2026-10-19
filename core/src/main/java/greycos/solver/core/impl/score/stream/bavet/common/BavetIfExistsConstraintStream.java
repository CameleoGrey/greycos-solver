package greycos.solver.core.impl.score.stream.bavet.common;

public interface BavetIfExistsConstraintStream<Solution_>
    extends BavetConstraintStreamBinaryOperation<Solution_> {

  /** Whether this operation only implements the implicit list-assignment filter of forEach. */
  default boolean isForEachAssignmentFilter() {
    return false;
  }
}
