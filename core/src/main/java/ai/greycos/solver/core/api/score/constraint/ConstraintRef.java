package ai.greycos.solver.core.api.score.constraint;

import ai.greycos.solver.core.impl.score.stream.common.AbstractConstraintBuilder;

import org.jspecify.annotations.NullMarked;

/**
 * Represents a unique identifier of a constraint.
 *
 * <p>Users should have no need to create instances of this record. If necessary, use {@link
 * ConstraintRef#of(String)} and not the record's constructors.
 *
 * @param constraintName The constraint name. It must be unique.
 */
@NullMarked
public record ConstraintRef(String constraintName) implements Comparable<ConstraintRef> {

  private static final char PACKAGE_SEPARATOR = '/';

  public static ConstraintRef of(String constraintName) {
    return new ConstraintRef(constraintName);
  }

  @Deprecated(forRemoval = true, since = "1.13.0")
  public static ConstraintRef of(String constraintPackage, String constraintName) {
    return new ConstraintRef(constraintName);
  }

  @Deprecated(forRemoval = true, since = "1.13.0")
  public static ConstraintRef parseId(String constraintId) {
    return new ConstraintRef(
        constraintId.substring(constraintId.lastIndexOf(PACKAGE_SEPARATOR) + 1));
  }

  @Deprecated(forRemoval = true, since = "1.13.0")
  public static String composeConstraintId(String constraintPackage, String constraintName) {
    return constraintName;
  }

  public ConstraintRef {
    constraintName = AbstractConstraintBuilder.sanitize("constraintName", constraintName);
  }

  @Deprecated(forRemoval = true, since = "1.13.0")
  public String packageName() {
    return "";
  }

  @Deprecated(forRemoval = true, since = "1.13.0")
  public String constraintId() {
    return constraintName;
  }

  @Override
  public int compareTo(ConstraintRef other) {
    return constraintName.compareTo(other.constraintName);
  }
}
