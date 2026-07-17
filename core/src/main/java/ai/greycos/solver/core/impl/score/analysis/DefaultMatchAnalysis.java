package ai.greycos.solver.core.impl.score.analysis;

import java.util.Objects;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.analysis.MatchAnalysis;
import ai.greycos.solver.core.api.score.stream.ConstraintJustification;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.api.score.stream.ConstraintRef;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record DefaultMatchAnalysis<Score_ extends Score<Score_>>(
    ConstraintRef constraintRef, Score_ score, ConstraintJustification justification)
    implements MatchAnalysis<Score_> {

  public DefaultMatchAnalysis {
    Objects.requireNonNull(constraintRef, "constraintRef");
    Objects.requireNonNull(score, "score");
    Objects.requireNonNull(
        justification,
        () ->
            """
                Impossible state: Received a null justification.
                Maybe check your %s's justifyWith() implementation for that constraint?"""
                .formatted(ConstraintProvider.class.getSimpleName()));
  }

  DefaultMatchAnalysis<Score_> negate() {
    return new DefaultMatchAnalysis<>(constraintRef, score.negate(), justification);
  }

  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public int compareTo(MatchAnalysis<Score_> other) {
    var constraintRefComparison = constraintRef.compareTo(other.constraintRef());
    if (constraintRefComparison != 0) {
      return constraintRefComparison;
    }
    var scoreComparison = score.compareTo(other.score());
    if (scoreComparison != 0) {
      return scoreComparison;
    }
    if (justification instanceof Comparable comparableJustification
        && other.justification() instanceof Comparable comparableOtherJustification) {
      return comparableJustification.compareTo(comparableOtherJustification);
    }
    return 0;
  }
}
