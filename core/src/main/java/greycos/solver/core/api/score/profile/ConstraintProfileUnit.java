package greycos.solver.core.api.score.profile;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * One instrumentation boundary; identity does not depend on its source locations.
 *
 * @param generationId the owning session's identity
 * @param unitId unique identity across the entire solver invocation
 * @param kind display label for the measured work
 * @param role the instrumentation role
 * @param sourceLocations all available source locations, or an empty list
 * @param measurements rows ordered by section, epoch, and operation
 */
public record ConstraintProfileUnit(
    long generationId,
    long unitId,
    String kind,
    ConstraintProfile.Role role,
    List<ConstraintProfileSourceLocation> sourceLocations,
    List<ConstraintProfileMeasurement> measurements) {

  public ConstraintProfileUnit {
    ConstraintProfileValidation.nonnegative("generationId", generationId);
    ConstraintProfileValidation.nonnegative("unitId", unitId);
    ConstraintProfileValidation.label("kind", kind);
    Objects.requireNonNull(role, "role");
    sourceLocations =
        ConstraintProfileValidation.sortedCopy(
            "sourceLocations",
            sourceLocations,
            Comparator.comparing(ConstraintProfileSourceLocation::className)
                .thenComparing(ConstraintProfileSourceLocation::methodName)
                .thenComparing(
                    ConstraintProfileSourceLocation::lineNumber,
                    Comparator.nullsLast(Comparator.naturalOrder())));
    measurements =
        ConstraintProfileValidation.sortedCopy(
            "measurements",
            measurements,
            Comparator.comparing(ConstraintProfileMeasurement::stage)
                .thenComparingInt(ConstraintProfileMeasurement::ownershipEpoch)
                .thenComparing(ConstraintProfileMeasurement::operation));
    ConstraintProfileValidation.unit(measurements);
  }
}
