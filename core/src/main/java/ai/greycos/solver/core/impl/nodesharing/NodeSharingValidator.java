package ai.greycos.solver.core.impl.nodesharing;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Validates ConstraintProvider classes for automatic node sharing compatibility.
 *
 * <p>Why: Node sharing requires bytecode transformation, which has prerequisites.
 * How: Validates class is not final and has no final methods.
 * What: Ensures transformation can succeed before attempting bytecode modification.
 */
public final class NodeSharingValidator {

  public static void validate(Class<?> constraintProviderClass) {
    if (Modifier.isFinal(constraintProviderClass.getModifiers())) {
      throw new IllegalArgumentException(
          "ConstraintProvider class %s must not be final for automatic node sharing."
              .formatted(constraintProviderClass.getName()));
    }

    validateNoFinalMethods(constraintProviderClass);
  }

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
  }
}
