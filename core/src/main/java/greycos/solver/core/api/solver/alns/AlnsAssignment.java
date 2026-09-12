package greycos.solver.core.api.solver.alns;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * A proposed basic value or list placement. For a basic binding, entity must be the target entity
 * and index must be -1. For a list binding, value must be the target value; a null entity and index
 * -1 represent optional unassignment. An insertion index refers to the destination list after the
 * target's old placement has been removed, including when the source and destination are the same.
 * The context validates ownership, ranges and pinning before applying the assignment.
 */
public record AlnsAssignment<Solution_>(
    AlnsTarget<Solution_> target, @Nullable Object entity, @Nullable Object value, int index) {
  public AlnsAssignment {
    Objects.requireNonNull(target);
    if (index < -1) throw new IllegalArgumentException("Assignment index must be at least -1.");
  }

  public boolean isUnassigned() {
    return target.isList() ? entity == null : value == null;
  }
}
