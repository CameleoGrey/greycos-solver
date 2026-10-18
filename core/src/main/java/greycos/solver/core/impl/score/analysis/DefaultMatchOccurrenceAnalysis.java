package greycos.solver.core.impl.score.analysis;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.analysis.MatchAnalysis;
import greycos.solver.core.api.score.analysis.MatchOccurrenceAnalysis;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Identity distinguishes physical occurrences, including value-equal duplicates. */
@NullMarked
public final class DefaultMatchOccurrenceAnalysis<Score_ extends Score<Score_>>
    implements MatchOccurrenceAnalysis<Score_> {

  private final ConstraintRef constraintRef;
  private final Score_ matchScore;
  private final ConstraintJustification justification;
  private final int occurrenceSign;
  private final List<Object> indictedObjects;
  private final @Nullable List<Object> originalIndictedObjects;

  public DefaultMatchOccurrenceAnalysis(
      ConstraintRef constraintRef,
      Score_ matchScore,
      ConstraintJustification justification,
      int occurrenceSign,
      List<?> indictedObjects,
      @Nullable List<?> originalIndictedObjects) {
    this.constraintRef = Objects.requireNonNull(constraintRef, "constraintRef");
    this.matchScore = Objects.requireNonNull(matchScore, "matchScore");
    this.justification = Objects.requireNonNull(justification, "justification");
    if (occurrenceSign != 1 && occurrenceSign != -1) {
      throw new IllegalArgumentException(
          "The occurrenceSign (%d) must be +1 or -1.".formatted(occurrenceSign));
    }
    this.occurrenceSign = occurrenceSign;
    this.indictedObjects = List.copyOf(indictedObjects);
    this.originalIndictedObjects =
        originalIndictedObjects == null ? null : List.copyOf(originalIndictedObjects);
    if (this.originalIndictedObjects != null) {
      int size = this.originalIndictedObjects.size();
      boolean duplicate =
          size == 2 && this.originalIndictedObjects.get(0) == this.originalIndictedObjects.get(1);
      if (size > 2) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        for (var object : this.originalIndictedObjects) {
          if (!seen.add(object)) {
            duplicate = true;
            break;
          }
        }
      }
      if (duplicate) {
        throw new IllegalArgumentException(
            "The original indicted objects contain the same source reference more than once.");
      }
    }
  }

  @Override
  public ConstraintRef constraintRef() {
    return constraintRef;
  }

  @Override
  public Score_ matchScore() {
    return matchScore;
  }

  @Override
  public ConstraintJustification justification() {
    return justification;
  }

  @Override
  public int occurrenceSign() {
    return occurrenceSign;
  }

  @Override
  public List<Object> indictedObjects() {
    return indictedObjects;
  }

  @Override
  public @Nullable List<Object> originalIndictedObjects() {
    return originalIndictedObjects;
  }

  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public int compareTo(MatchAnalysis<Score_> other) {
    int constraintComparison = constraintRef.compareTo(other.constraintRef());
    if (constraintComparison != 0) {
      return constraintComparison;
    }
    int scoreComparison = score().compareTo(other.score());
    if (scoreComparison != 0) {
      return scoreComparison;
    }
    if (justification instanceof Comparable comparable
        && other.justification() instanceof Comparable otherComparable) {
      return comparable.compareTo(otherComparable);
    }
    return 0;
  }

  @Override
  public String toString() {
    return "%s: %s (%+d occurrence)".formatted(constraintRef, matchScore, occurrenceSign);
  }
}
