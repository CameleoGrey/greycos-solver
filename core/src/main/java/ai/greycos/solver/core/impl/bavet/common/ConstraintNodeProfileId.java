package ai.greycos.solver.core.impl.bavet.common;

import java.util.SortedSet;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record ConstraintNodeProfileId(
    long key,
    StreamKind streamKind,
    Qualifier qualifier,
    SortedSet<ConstraintNodeLocation> locationSet)
    implements Comparable<ConstraintNodeProfileId> {

  public ConstraintNodeProfileId(
      long key, StreamKind streamKind, SortedSet<ConstraintNodeLocation> locationSet) {
    this(key, streamKind, Qualifier.NONE, locationSet);
  }

  @Override
  public boolean equals(Object object) {
    return object instanceof ConstraintNodeProfileId that && key == that.key;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(key);
  }

  @Override
  public int compareTo(ConstraintNodeProfileId other) {
    return Long.compare(key, other.key);
  }

  @Override
  public String toString() {
    if (qualifier == Qualifier.NONE) {
      return "%s %d".formatted(streamKind, key);
    }
    var qualifierString =
        switch (qualifier) {
          case NODE -> "Node";
          case LEFT_INPUT -> "Left Input";
          case RIGHT_INPUT -> "Right Input";
          case NONE -> throw new IllegalStateException("Unexpected NONE qualifier.");
        };
    return "%s %s %d".formatted(streamKind, qualifierString, key);
  }

  public String toVerboseString() {
    var toString = toString();
    if (locationSet.size() == 1) {
      return "%s defined at location %s".formatted(toString, locationSet.first());
    }
    return "%s shared at locations %s".formatted(toString, locationSet);
  }

  public enum Qualifier {
    NODE,
    LEFT_INPUT,
    RIGHT_INPUT,
    NONE
  }
}
