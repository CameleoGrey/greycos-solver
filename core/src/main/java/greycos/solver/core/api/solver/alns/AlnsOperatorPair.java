package greycos.solver.core.api.solver.alns;

import java.util.Objects;

/** Stable IDs of one compatible destroy/repair pair. */
public record AlnsOperatorPair(String destroyId, String repairId) {
  public AlnsOperatorPair {
    Objects.requireNonNull(destroyId);
    Objects.requireNonNull(repairId);
    if (destroyId.isBlank() || repairId.isBlank())
      throw new IllegalArgumentException("Operator IDs must not be blank.");
  }
}
