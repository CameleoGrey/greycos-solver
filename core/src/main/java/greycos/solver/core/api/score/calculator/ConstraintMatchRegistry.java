package greycos.solver.core.api.score.calculator;

import java.util.Collection;
import java.util.Objects;

import greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.api.score.stream.DefaultConstraintJustification;
import greycos.solver.core.api.solver.IndictmentFetchPolicy;

import org.jspecify.annotations.NullMarked;

/**
 * @param <Score_>
 * @see AnalyzableIncrementalScoreCalculator Adding explainability to incremental score calculator.
 */
@NullMarked
public interface ConstraintMatchRegistry<Score_ extends Score<Score_>> {

  /** The object-centric metadata requested for this calculation; legacy registries request none. */
  default IndictmentFetchPolicy indictmentFetchPolicy() {
    return IndictmentFetchPolicy.NONE;
  }

  /**
   * Registers an occurrence with an explicit terminal-object selection. Empty selection means no
   * terminal objects are indicted. The collection and its elements must be non-null; equal objects
   * are counted once per occurrence. The selection is copied when its view is requested.
   *
   * <p>This does not supply original-contributor metadata. Use the overload taking both collections
   * when {@link #indictmentFetchPolicy()} requests original contributors.
   */
  default ConstraintMatchRegistration<Score_> registerConstraintMatchWithIndictments(
      ConstraintRef constraintRef,
      Score_ score,
      ConstraintJustification justification,
      Collection<?> indictedObjects) {
    Objects.requireNonNull(indictedObjects, "indictedObjects");
    if (indictmentFetchPolicy() == IndictmentFetchPolicy.NONE) {
      return registerConstraintMatch(constraintRef, score, justification);
    }
    throw new UnsupportedOperationException(
        "This constraint match registry does not support indictments.");
  }

  /**
   * Registers an occurrence with independent terminal and original-contributor selections. The
   * collections are copied only for requested views; objects themselves are not deep-copied. Their
   * equality and hash codes must remain stable while the registration or analysis is used.
   */
  default ConstraintMatchRegistration<Score_> registerConstraintMatchWithIndictments(
      ConstraintRef constraintRef,
      Score_ score,
      ConstraintJustification justification,
      Collection<?> indictedObjects,
      Collection<?> originalIndictedObjects) {
    Objects.requireNonNull(originalIndictedObjects, "originalIndictedObjects");
    if (indictmentFetchPolicy() != IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL) {
      return registerConstraintMatchWithIndictments(
          constraintRef, score, justification, indictedObjects);
    }
    throw new UnsupportedOperationException(
        "This constraint match registry does not support original indictments.");
  }

  /**
   * @param constraintRef identification of the constraint; obtain from {@link
   *     ConstraintRef#of(String)}.
   * @param score full impact of the match; Any {@link ConstraintWeightOverrides} will be ignored.
   * @param justification the justification of the match, which will be used in {@link
   *     ScoreAnalysis}; use {@link #registerConstraintMatch(ConstraintRef, Score_, Object...)} if
   *     you prefer to not provide a specific justification type.
   * @throws IllegalStateException if constraint matching is not enabled
   * @return the handler to cancel the match later if needed
   */
  ConstraintMatchRegistration<Score_> registerConstraintMatch(
      ConstraintRef constraintRef, Score_ score, ConstraintJustification justification);

  /**
   * @param constraintRef identification of the constraint; obtain from {@link
   *     ConstraintRef#of(String)}.
   * @param score full impact of the match; Any {@link ConstraintWeightOverrides} will be ignored.
   * @param justifications objects justifying the match, which will be used in {@link
   *     ScoreAnalysis}; these objects will be wrapped by a default internal {@link
   *     ConstraintJustification} implementation.
   * @throws IllegalStateException if constraint matching is not enabled
   * @return the handler to cancel the match later if needed
   */
  default ConstraintMatchRegistration<Score_> registerConstraintMatch(
      ConstraintRef constraintRef, Score_ score, Object... justifications) {
    return registerConstraintMatch(
        constraintRef, score, DefaultConstraintJustification.of(score, justifications));
  }

  /**
   * @return the total score of all registered and non-canceled matches
   */
  Score_ totalScore();
}
