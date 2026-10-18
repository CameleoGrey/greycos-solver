package greycos.solver.core.api.score.analysis;

import java.util.List;

import greycos.solver.core.api.score.Score;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One original scoring occurrence, without justification aggregation. Equal occurrences remain
 * distinct instances in a {@link RawScoreAnalysis}; their identities are local to that snapshot.
 *
 * <p>In a diff, an added occurrence has sign {@code +1} and a removed occurrence has sign {@code
 * -1}. The original {@link #matchScore()} and object associations remain unchanged. A changed score
 * or association is represented by removal and addition, not by a synthetic match.
 *
 * @param <Score_> the score type
 */
@NullMarked
public interface MatchOccurrenceAnalysis<Score_ extends Score<Score_>>
    extends MatchAnalysis<Score_> {

  /** The original weighted contribution, before applying the occurrence's diff sign. */
  Score_ matchScore();

  /** Exactly {@code +1} in regular snapshots; {@code +1} or {@code -1} in a diff. */
  int occurrenceSign();

  /**
   * This occurrence's signed contribution. For floating scores, summing raw delta contributions may
   * differ from subtracting the independently rounded totals in {@link ScoreAnalysis#score()}.
   */
  @Override
  default Score_ score() {
    return occurrenceSign() == 1 ? matchScore() : matchScore().negate();
  }

  /**
   * Immutable terminal selection in its original iteration order, retaining duplicates and
   * equal-but-distinct object references. Elements are never null.
   */
  List<Object> indictedObjects();

  /**
   * Immutable original source references, distinct by identity. Equal-but-distinct objects remain
   * separate elements. Null means original attribution was not fetched, whereas an empty list means
   * it was fetched and there were no original sources.
   */
  @Nullable List<Object> originalIndictedObjects();
}
