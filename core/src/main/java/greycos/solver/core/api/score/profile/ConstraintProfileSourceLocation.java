package greycos.solver.core.api.score.profile;

import org.jspecify.annotations.Nullable;

/**
 * Source metadata that does not retain a provider class or participate in unit identity.
 *
 * @param className declaring class name
 * @param methodName method name
 * @param lineNumber positive source line, or null when unknown
 */
public record ConstraintProfileSourceLocation(
    String className, String methodName, @Nullable Integer lineNumber) {

  public ConstraintProfileSourceLocation {
    ConstraintProfileValidation.label("className", className);
    ConstraintProfileValidation.label("methodName", methodName);
    if (lineNumber != null && lineNumber <= 0) {
      throw new IllegalArgumentException(
          "The lineNumber (" + lineNumber + ") must be positive, or null when unknown.");
    }
  }
}
