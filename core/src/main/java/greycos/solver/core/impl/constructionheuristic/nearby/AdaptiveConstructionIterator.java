package greycos.solver.core.impl.constructionheuristic.nearby;

import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Predicate;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import greycos.solver.core.impl.score.definition.ScoreDefinition;
import greycos.solver.core.preview.api.move.Move;

/**
 * Scores increasingly wide prefixes of a ranked assignment stream. Each prefix doubles the
 * cumulative limit until a meaningful, structurally valid assignment does not deteriorate the raw
 * hard score, or the stream is exhausted. Optional alternatives are evaluated once at the end.
 */
public final class AdaptiveConstructionIterator<Solution_>
    implements ProgressiveConstructionIterator<Solution_> {

  public static final int DEFAULT_INITIAL_LIMIT = 40;

  private Iterator<Move<Solution_>> rankedMoves;
  private Iterator<Move<Solution_>> tailMoves;
  private final Predicate<Move<Solution_>> meaningfulAssignment;
  private ScoreDefinition<?> scoreDefinition;
  private Score<?> baseline;
  private long cumulativeLimit;
  private long rankedMoveCount;
  private long wideningCount;
  private boolean acceptableAssignment;
  private boolean evaluatingTail;
  private boolean closed;

  public AdaptiveConstructionIterator(
      Iterator<Move<Solution_>> rankedMoves,
      int initialLimit,
      Iterator<Move<Solution_>> tailMoves,
      Predicate<Move<Solution_>> meaningfulAssignment) {
    this(rankedMoves, initialLimit, tailMoves, meaningfulAssignment, null, null);
  }

  public AdaptiveConstructionIterator(
      Iterator<Move<Solution_>> rankedMoves,
      int initialLimit,
      Iterator<Move<Solution_>> tailMoves,
      Predicate<Move<Solution_>> meaningfulAssignment,
      ScoreDefinition<?> scoreDefinition,
      Score<?> baseline) {
    if (initialLimit <= 0) {
      throw new IllegalArgumentException(
          "The initialLimit (%d) must be positive.".formatted(initialLimit));
    }
    if ((scoreDefinition == null) != (baseline == null)) {
      throw new IllegalArgumentException(
          "The scoreDefinition (%s) and baseline (%s) must both be supplied or both be absent."
              .formatted(scoreDefinition, baseline));
    }
    this.rankedMoves = Objects.requireNonNull(rankedMoves);
    this.tailMoves = Objects.requireNonNull(tailMoves);
    this.meaningfulAssignment = Objects.requireNonNull(meaningfulAssignment);
    this.scoreDefinition = scoreDefinition;
    this.baseline = baseline;
    this.cumulativeLimit = initialLimit;
  }

  @Override
  public boolean hasNext() {
    if (closed) {
      return false;
    }
    if (!evaluatingTail && rankedMoveCount == 0 && !rankedMoves.hasNext()) {
      // A placer asks hasNext before handing the cursor to the decider. When there are no ranked
      // assignments at all, its optional alternative is already the entire placement.
      beginTail();
    }
    return evaluatingTail
        ? tailMoves.hasNext()
        : rankedMoveCount < cumulativeLimit && rankedMoves.hasNext();
  }

  @Override
  public Move<Solution_> next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    if (evaluatingTail) {
      return tailMoves.next();
    }
    rankedMoveCount++;
    return rankedMoves.next();
  }

  @Override
  public void recordScore(ConstructionHeuristicMoveScope<Solution_> moveScope) {
    if (closed
        || acceptableAssignment
        || evaluatingTail
        || !meaningfulAssignment.test(moveScope.getMove())
        || moveScope.getScore().isStructurallyFlawed()) {
      return;
    }
    if (scoreDefinition == null) {
      scoreDefinition = moveScope.getScoreDirector().getScoreDefinition();
      baseline =
          moveScope.getStepScope().getPhaseScope().getLastCompletedStepScope().getScore().raw();
    }
    acceptableAssignment =
        doesNotDeteriorateHard(scoreDefinition, baseline, moveScope.getScore().raw());
  }

  private static <Score_ extends Score<Score_>> boolean doesNotDeteriorateHard(
      ScoreDefinition<Score_> scoreDefinition, Score<?> baseline, Score<?> candidate) {
    // Use the score's native comparison; converting long or BigDecimal levels to double loses
    // differences that can decide whether widening is necessary. Rebuilding also removes the
    // structural component, while the caller independently rejects structural violations.
    return hardProjection(scoreDefinition, candidate)
            .compareTo(hardProjection(scoreDefinition, baseline))
        >= 0;
  }

  private static <Score_ extends Score<Score_>> Score_ hardProjection(
      ScoreDefinition<Score_> scoreDefinition, Score<?> score) {
    var levelNumbers = scoreDefinition.getZeroScore().toLevelNumbers();
    System.arraycopy(
        score.toLevelNumbers(), 0, levelNumbers, 0, scoreDefinition.getFeasibleLevelsSize());
    return scoreDefinition.fromLevelNumbers(levelNumbers);
  }

  @Override
  public boolean advanceBatch() {
    if (closed) {
      return false;
    }
    if (hasNext()) {
      throw new IllegalStateException("The current construction batch still has unconsumed moves.");
    }
    if (evaluatingTail) {
      return false;
    }
    if (!acceptableAssignment && rankedMoves.hasNext()) {
      cumulativeLimit = cumulativeLimit > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : cumulativeLimit * 2;
      wideningCount++;
      return true;
    }
    beginTail();
    return tailMoves.hasNext();
  }

  @Override
  public long getWideningCount() {
    return wideningCount;
  }

  private void beginTail() {
    evaluatingTail = true;
    try {
      closeIterator(rankedMoves);
    } finally {
      rankedMoves = Collections.emptyIterator();
    }
  }

  private static void closeIterator(Iterator<?> iterator) {
    if (iterator instanceof AutoCloseable closeable) {
      try {
        closeable.close();
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to release the construction candidate iterator (%s).".formatted(iterator), e);
      }
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    try {
      try {
        closeIterator(rankedMoves);
      } finally {
        closeIterator(tailMoves);
      }
    } finally {
      rankedMoves = Collections.emptyIterator();
      tailMoves = Collections.emptyIterator();
      scoreDefinition = null;
      baseline = null;
    }
  }
}
