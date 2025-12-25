package ai.greycos.solver.core.impl.nodesharing;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Validates that a ConstraintProvider class meets requirements for automatic node sharing.
 *
 * <p>According to OptaPlanner documentation, the ConstraintProvider class must:
 *
 * <ul>
 *   <li>Not be final
 *   <li>Not have final methods
 *   <li>Not access protected classes, methods or fields
 * </ul>
 */
public final class NodeSharingValidator {

  /**
   * Validates a ConstraintProvider class for node sharing compatibility.
   *
   * @param constraintProviderClass the class to validate
   * @throws IllegalArgumentException if class doesn't meet requirements
   */
  public static void validate(Class<?> constraintProviderClass) {
    // Check if class is final
    if (Modifier.isFinal(constraintProviderClass.getModifiers())) {
      throw new IllegalArgumentException(
          "ConstraintProvider class %s must not be final for automatic node sharing."
              .formatted(constraintProviderClass.getName()));
    }

    // Check for final methods
    validateNoFinalMethods(constraintProviderClass);

    // Note: We cannot reliably check for protected access without
    // analyzing bytecode, which is done during transformation.
    // If transformation fails due to protected access, it will be caught
    // and handled gracefully.
  }

  /** Validates that the class has no final methods. */
  private static void validateNoFinalMethods(Class<?> clazz) {
    for (Method method : clazz.getDeclaredMethods()) {
      if (Modifier.isFinal(method.getModifiers())) {
        throw new IllegalArgumentException(
            "ConstraintProvider method %s.%s must not be final for automatic node sharing."
                .formatted(clazz.getName(), method.getName()));
      }
    }
  }

  private NodeSharingValidator() {
    // Utility class - prevent instantiation
  }
}
