package greycos.solver.core.impl.score.constraint;

import java.util.Objects;

import greycos.solver.core.api.solver.IndictmentFetchPolicy;
import greycos.solver.core.api.solver.ScoreAnalysisFetchPolicy;
import greycos.solver.core.api.solver.ScoreAnalysisOptions;

import org.jspecify.annotations.NullMarked;

/**
 * Determines whether constraint match is enabled and whether constraint match justification is
 * enabled.
 *
 * @see ConstraintMatch
 * @see greycos.solver.core.api.score.stream.ConstraintJustification
 */
@NullMarked
public enum ConstraintMatchPolicy {
  DISABLED(false, false, IndictmentFetchPolicy.NONE),
  ENABLED_WITHOUT_JUSTIFICATIONS(true, false, IndictmentFetchPolicy.NONE),
  ENABLED(true, true, IndictmentFetchPolicy.NONE),
  ENABLED_WITH_INDICTMENTS(true, true, IndictmentFetchPolicy.TERMINAL),
  ENABLED_WITH_ORIGINAL_INDICTMENTS(true, true, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL),
  ENABLED_WITH_RAW_INDICTMENTS(true, true, IndictmentFetchPolicy.TERMINAL, true),
  ENABLED_WITH_RAW_ORIGINAL_INDICTMENTS(
      true, true, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL, true);

  /**
   * To achieve the most performance out of the underlying solver, the policy should match whatever
   * policy was used for score analysis. For example, if the fetch policy specifies that only match
   * counts are necessary and not matches themselves ({@link
   * ScoreAnalysisFetchPolicy#FETCH_MATCH_COUNT}), we can configure the solver to not produce
   * justifications ({@link #ENABLED_WITHOUT_JUSTIFICATIONS}).
   *
   * @param scoreAnalysisFetchPolicy
   * @return Match policy best suited for the given fetch policy.
   */
  public static ConstraintMatchPolicy match(ScoreAnalysisFetchPolicy scoreAnalysisFetchPolicy) {
    return switch (scoreAnalysisFetchPolicy) {
      case FETCH_MATCH_COUNT, FETCH_SHALLOW -> ENABLED_WITHOUT_JUSTIFICATIONS;
      case FETCH_ALL -> ENABLED;
    };
  }

  public static ConstraintMatchPolicy match(
      ScoreAnalysisFetchPolicy fetchPolicy, IndictmentFetchPolicy indictmentPolicy) {
    Objects.requireNonNull(fetchPolicy, "fetchPolicy");
    Objects.requireNonNull(indictmentPolicy, "indictmentPolicy");
    if (indictmentPolicy == IndictmentFetchPolicy.NONE) {
      return match(fetchPolicy);
    }
    if (fetchPolicy != ScoreAnalysisFetchPolicy.FETCH_ALL) {
      throw new IllegalArgumentException(
          "The indictment fetch policy (%s) requires ScoreAnalysisFetchPolicy.FETCH_ALL, but was (%s)."
              .formatted(indictmentPolicy, fetchPolicy));
    }
    return indictmentPolicy == IndictmentFetchPolicy.TERMINAL
        ? ENABLED_WITH_INDICTMENTS
        : ENABLED_WITH_ORIGINAL_INDICTMENTS;
  }

  public static ConstraintMatchPolicy match(ScoreAnalysisOptions options) {
    Objects.requireNonNull(options, "options");
    if (!options.includeMatchOccurrences()) {
      return match(options.fetchPolicy(), options.indictmentPolicy());
    }
    if (options.fetchPolicy() != ScoreAnalysisFetchPolicy.FETCH_ALL
        || options.indictmentPolicy() == IndictmentFetchPolicy.NONE) {
      throw new IllegalArgumentException(
          "Raw match occurrences require FETCH_ALL and terminal or original indictments.");
    }
    return options.indictmentPolicy() == IndictmentFetchPolicy.TERMINAL
        ? ENABLED_WITH_RAW_INDICTMENTS
        : ENABLED_WITH_RAW_ORIGINAL_INDICTMENTS;
  }

  private final boolean enabled;
  private final boolean justificationEnabled;
  private final IndictmentFetchPolicy indictmentFetchPolicy;
  private final boolean matchOccurrenceEnabled;

  ConstraintMatchPolicy(
      boolean enabled, boolean justificationEnabled, IndictmentFetchPolicy indictmentFetchPolicy) {
    this(enabled, justificationEnabled, indictmentFetchPolicy, false);
  }

  ConstraintMatchPolicy(
      boolean enabled,
      boolean justificationEnabled,
      IndictmentFetchPolicy indictmentFetchPolicy,
      boolean matchOccurrenceEnabled) {
    this.matchOccurrenceEnabled = matchOccurrenceEnabled;
    this.enabled = enabled;
    this.justificationEnabled = justificationEnabled;
    this.indictmentFetchPolicy = indictmentFetchPolicy;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean isJustificationEnabled() {
    return justificationEnabled;
  }

  public IndictmentFetchPolicy getIndictmentFetchPolicy() {
    return indictmentFetchPolicy;
  }

  public boolean isMatchOccurrenceEnabled() {
    return matchOccurrenceEnabled;
  }
}
