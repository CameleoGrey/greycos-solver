package greycos.solver.core.api.score.analysis;

import java.util.List;
import java.util.Objects;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.impl.score.analysis.ScoreAnalysisSummary;

import org.jspecify.annotations.NullMarked;

/**
 * An immutable breakdown of the score contributions attributed to one object. Each matching
 * occurrence contributes its full score once to each distinct selected object. Consequently, the
 * scores of all indictments need not add up to the solution score.
 *
 * <p>Object keys use {@link Object#equals(Object)} and {@link Object#hashCode()}; these must remain
 * stable while the analysis is used. The object and justification references are not deep copies.
 *
 * @param <Score_> the score type
 */
@NullMarked
public interface IndictmentAnalysis<Score_ extends Score<Score_>> {

  Object indictedObject();

  /**
   * The sum of the original contributing scores. Floating totals and match subtotals are rounded
   * independently; adding the rounded match subtotals may differ from this total.
   */
  Score_ score();

  /**
   * Contributions grouped by constraint and justification equality, in encounter order. The list is
   * immutable. In a diff, groups are retained when either their score or occurrence count changes,
   * including count-only changes with a zero score.
   */
  List<IndictmentMatchAnalysis<Score_>> matches();

  /**
   * The number of original contributing occurrences; equal to the sum of {@link
   * IndictmentMatchAnalysis#matchCount()} over {@link #matches()}. In a diff this is the signed
   * count difference. This is not necessarily the number of groups in {@link #matches()}.
   */
  long matchCount();

  /** Justifications distinct by equality, preserving their first encounter order. */
  default List<ConstraintJustification> justifications() {
    return matches().stream().map(IndictmentMatchAnalysis::justification).distinct().toList();
  }

  /** Filters {@link #justifications()} by type, including subclasses. */
  default <Justification_ extends ConstraintJustification> List<Justification_> justifications(
      Class<Justification_> justificationClass) {
    Objects.requireNonNull(justificationClass, "justificationClass");
    return justifications().stream()
        .filter(justificationClass::isInstance)
        .map(justificationClass::cast)
        .toList();
  }

  /** A diagnostic summary showing at most three grouped matches. Do not parse this text. */
  default String summarize() {
    return summarize(3);
  }

  /** A diagnostic summary with a positive limit on the number of grouped matches shown. */
  default String summarize(int matchLimit) {
    return ScoreAnalysisSummary.summarize(this, matchLimit);
  }
}
