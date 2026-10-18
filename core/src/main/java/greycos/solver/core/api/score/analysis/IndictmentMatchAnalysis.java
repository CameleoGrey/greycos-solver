package greycos.solver.core.api.score.analysis;

import greycos.solver.core.api.score.Score;

import org.jspecify.annotations.NullMarked;

/**
 * The contribution of equal justifications of one constraint to one indicted object. Unlike a
 * constraint's {@link MatchAnalysis}, this also retains the number of original matching
 * occurrences. Separate occurrences count separately even when their justifications are equal.
 *
 * @param <Score_> the score type
 */
@NullMarked
public interface IndictmentMatchAnalysis<Score_ extends Score<Score_>>
    extends MatchAnalysis<Score_> {

  /**
   * The number of contributing occurrences, positive in a regular analysis. In a diff this is the
   * signed count difference, and may be zero when the score changed without changing the count.
   */
  long matchCount();
}
