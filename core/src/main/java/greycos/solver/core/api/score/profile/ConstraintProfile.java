package greycos.solver.core.api.score.profile;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A final, immutable profile of one solver invocation. Section totals include score-engine work
 * only; combined totals sum initialization and search without collecting additional events.
 *
 * @param schemaVersion the wire schema version, currently {@code 1}
 * @param outcome the outcome after solver cleanup
 * @param initialization work before each session's first successful settlement
 * @param search subsequent score-engine work
 * @param combined the sum of both sections
 * @param generations metadata for every session constructed during the invocation
 * @param units callback measurements with run-local identities
 */
public record ConstraintProfile(
    int schemaVersion,
    Outcome outcome,
    ConstraintProfileSection initialization,
    ConstraintProfileSection search,
    ConstraintProfileSection combined,
    List<ConstraintProfileGeneration> generations,
    List<ConstraintProfileUnit> units) {

  public ConstraintProfile {
    if (schemaVersion != 1) {
      throw new IllegalArgumentException("The schemaVersion (" + schemaVersion + ") must be 1.");
    }
    Objects.requireNonNull(outcome, "outcome");
    Objects.requireNonNull(initialization, "initialization");
    Objects.requireNonNull(search, "search");
    Objects.requireNonNull(combined, "combined");
    generations =
        ConstraintProfileValidation.sortedCopy(
            "generations",
            generations,
            Comparator.comparingLong(ConstraintProfileGeneration::generationId));
    units =
        ConstraintProfileValidation.sortedCopy(
            "units", units, Comparator.comparingLong(ConstraintProfileUnit::unitId));
    ConstraintProfileValidation.profile(initialization, search, combined, generations, units);
  }

  /** Failure, including cleanup failure, takes precedence over other outcomes. */
  public enum Outcome {
    COMPLETED,
    TERMINATED_EARLY,
    FAILED
  }

  /** The section in which a measured interval occurred. */
  public enum Stage {
    INITIALIZATION,
    SEARCH
  }

  /** The instrumentation boundary represented by a unit. */
  public enum Role {
    ROOT,
    LIFECYCLE,
    LEFT_INPUT,
    RIGHT_INPUT,
    PROPAGATOR,
    PREPARATION
  }

  /** Callback operations; counts describe invocations, not tuples, matches, or moves. */
  public enum Operation {
    INSERT,
    UPDATE,
    RETRACT,
    PREPARE
  }

  /** How an invocation's recorded owners were established at entry. */
  public enum OwnershipState {
    ENABLED_CANDIDATES,
    ACTIVE,
    UNRESOLVED
  }

  /** The final activity of a declared constraint within a network generation. */
  public enum Activity {
    DISABLED_WEIGHT,
    UNRESOLVED,
    ACTIVE,
    INACTIVE
  }
}
