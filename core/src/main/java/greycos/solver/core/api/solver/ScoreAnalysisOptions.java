package greycos.solver.core.api.solver;

import java.util.Objects;

import greycos.solver.core.api.score.analysis.ScoreAnalysis;

import org.jspecify.annotations.NullMarked;

/**
 * Explicitly selects the information collected by {@link SolutionManager#analyzeWithOptions(Object,
 * ScoreAnalysisOptions)}. Existing analysis overloads retain their existing behavior and do not
 * collect raw occurrences.
 *
 * @param fetchPolicy the depth of the grouped constraint analysis
 * @param indictmentPolicy the object-centric views to collect
 * @param includeMatchOccurrences whether to retain individual matching occurrences in {@link
 *     ScoreAnalysis#rawAnalysis()}; requires full analysis and at least terminal indictments
 */
@NullMarked
public record ScoreAnalysisOptions(
    ScoreAnalysisFetchPolicy fetchPolicy,
    IndictmentFetchPolicy indictmentPolicy,
    boolean includeMatchOccurrences) {

  public ScoreAnalysisOptions {
    Objects.requireNonNull(fetchPolicy, "fetchPolicy");
    Objects.requireNonNull(indictmentPolicy, "indictmentPolicy");
    if ((includeMatchOccurrences || indictmentPolicy != IndictmentFetchPolicy.NONE)
        && fetchPolicy != ScoreAnalysisFetchPolicy.FETCH_ALL) {
      throw new IllegalArgumentException(
          "The indictment policy (%s) and includeMatchOccurrences (%s) require FETCH_ALL, but the fetchPolicy was (%s)."
              .formatted(indictmentPolicy, includeMatchOccurrences, fetchPolicy));
    }
    if (includeMatchOccurrences && indictmentPolicy == IndictmentFetchPolicy.NONE) {
      throw new IllegalArgumentException(
          "The includeMatchOccurrences option requires TERMINAL or TERMINAL_AND_ORIGINAL indictments, but the indictmentPolicy was (%s)."
              .formatted(indictmentPolicy));
    }
  }
}
