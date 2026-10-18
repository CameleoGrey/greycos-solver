package greycos.solver.core.api.score.analysis;

import java.util.HashSet;
import java.util.List;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.ConstraintRef;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * @see ScoreAnalysis Description of score analysis and the purpose of this class.
 */
@NullMarked
public interface ConstraintAnalysis<Score_ extends Score<Score_>> {

  ConstraintRef constraintRef();

  Score_ weight();

  Score_ score();

  /**
   * @return null if analysis not available; empty if constraint has no matches, but still non-zero
   *     constraint weight; non-empty if constraint has matches. This is a {@link List} to simplify
   *     access to individual elements, but it contains no duplicates just like {@link HashSet}
   *     wouldn't.
   */
  @Nullable List<MatchAnalysis<Score_>> matches();

  /**
   * @return
   *     <ul>
   *       <li>For regular constraint analysis: -1 if the count is unavailable, 0 if constraint has
   *           no matches, positive if constraint has matches. Equal to the size of the {@link
   *           #matches} list when that list is available.
   *       <li>For a {@link ScoreAnalysis#diff(ScoreAnalysis) diff of constraint analyses}: positive
   *           if the constraint has more matches in the new analysis, zero if the number of matches
   *           is the same in both, negative otherwise. Need not be equal to the size of the {@link
   *           #matches} list. An unavailable count remains -1; use {@link #isMatchCountAvailable()}
   *           to distinguish it from a signed count difference of -1.
   *     </ul>
   */
  int matchCount();

  /**
   * Whether {@link #matchCount()} is available, independently of its sign. A fetched count remains
   * available in a diff even when its value is -1.
   *
   * <p>The default preserves the legacy unavailable sentinel when matches are unavailable and the
   * count is -1. Custom implementations with an available count-only difference of -1 must override
   * this method to return true.
   */
  default boolean isMatchCountAvailable() {
    return matches() != null || matchCount() != -1;
  }

  /**
   * Number of original scoring occurrences, available only when raw analysis was requested. Unlike
   * {@link #matchCount()}, this counts value-equal duplicate occurrences separately even with full
   * grouped matches. In a diff it is the signed count difference, including {@code -1}; null
   * denotes unavailable information.
   */
  default @Nullable Long rawMatchCount() {
    return null;
  }

  /**
   * Return id of the constraint that this analysis is for.
   *
   * @return equal to {@code constraintRef.id()}
   */
  default String constraintId() {
    return constraintRef().id();
  }

  /**
   * Returns a diagnostic text that explains part of the score quality through the {@link
   * ConstraintAnalysis} API. The summary will be limited to the first 3 matches per constraint; see
   * {@link #summarize(int)} to specify a custom limit. The summary is built fresh every time the
   * method is called.
   */
  String summarize();

  /**
   * Return a diagnostic text that explains part of the score quality as defined by {@link
   * #summarize()}, but allows specifying the maximum number of constraint matches to display per
   * constraint.
   *
   * @param topLimit maximum number of constraint matches to display per constraint Use {@link
   *     Integer#MAX_VALUE} to show all matches.
   */
  String summarize(int topLimit);
}
