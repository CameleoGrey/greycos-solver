package greycos.solver.core.impl.constructionheuristic.nearby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.HardMediumSoftScore;
import greycos.solver.core.api.score.HardSoftBigDecimalScore;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import greycos.solver.core.impl.score.definition.BendableScoreDefinition;
import greycos.solver.core.impl.score.definition.HardMediumSoftScoreDefinition;
import greycos.solver.core.impl.score.definition.HardSoftBigDecimalScoreDefinition;
import greycos.solver.core.impl.score.definition.HardSoftScoreDefinition;
import greycos.solver.core.impl.score.definition.SimpleScoreDefinition;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.preview.api.move.Move;

import org.junit.jupiter.api.Test;

class AdaptiveConstructionIteratorTest {

  @Test
  void doublesCumulativeLimitWithoutRepeatingCandidates() {
    var moves = moves(197);
    var cursor = cursor(moves, HardSoftScore.ZERO);
    var consumed = new ArrayList<Move<Object>>();
    for (var expectedBatchSize : List.of(40, 40, 80, 37)) {
      int previousSize = consumed.size();
      consume(cursor, consumed, index -> HardSoftScore.of(-1, 0));
      assertThat(consumed).hasSize(previousSize + expectedBatchSize);
      assertThat(cursor.hasNext()).isFalse();
      if (consumed.size() < moves.size()) {
        assertThat(cursor.advanceBatch()).isTrue();
      }
    }
    assertThat(cursor.advanceBatch()).isFalse();
    assertThat(consumed).containsExactlyElementsOf(moves);
  }

  @Test
  void wideningCountExcludesTailAndSurvivesClose() {
    var noChange = move();
    var cursor =
        new AdaptiveConstructionIterator<>(
            moves(160).iterator(),
            40,
            List.of(noChange).iterator(),
            candidate -> candidate != noChange,
            new HardSoftScoreDefinition(),
            HardSoftScore.ZERO);
    var consumed = new ArrayList<Move<Object>>();
    assertThat(cursor.getWideningCount()).isZero();
    consume(cursor, consumed, index -> HardSoftScore.of(-1, 0));
    assertThat(cursor.advanceBatch()).isTrue();
    assertThat(cursor.getWideningCount()).isEqualTo(1);
    consume(cursor, consumed, index -> HardSoftScore.of(-1, 0));
    assertThat(cursor.advanceBatch()).isTrue();
    assertThat(cursor.getWideningCount()).isEqualTo(2);
    consume(cursor, consumed, index -> HardSoftScore.of(-1, 0));
    assertThat(cursor.advanceBatch()).isTrue();
    assertThat(cursor.getWideningCount()).isEqualTo(2);
    assertThat(cursor.next()).isSameAs(noChange);
    assertThat(cursor.advanceBatch()).isFalse();
    cursor.close();
    assertThat(cursor.getWideningCount()).isEqualTo(2);
  }

  @Test
  void hasNextNeverOpensAnotherBatchAndAdvanceRequiresTheBatchToBeConsumed() {
    var cursor = cursor(moves(90), HardSoftScore.ZERO);
    assertThatThrownBy(cursor::advanceBatch).isInstanceOf(IllegalStateException.class);
    var consumed = new ArrayList<Move<Object>>();
    consume(cursor, consumed, index -> HardSoftScore.of(-1, 0));
    assertThat(cursor.hasNext()).isFalse();
    assertThat(cursor.hasNext()).isFalse();
    assertThat(consumed).hasSize(40);
    assertThat(cursor.advanceBatch()).isTrue();
  }

  @Test
  void acceptableAssignmentSettlesTheWholeBatchBeforeOptionalTail() {
    var moves = moves(100);
    var noChange = move();
    var cursor =
        new AdaptiveConstructionIterator<>(
            moves.iterator(),
            40,
            List.of(noChange).iterator(),
            candidate -> candidate != noChange,
            new HardSoftScoreDefinition(),
            HardSoftScore.ZERO);
    var consumed = new ArrayList<Move<Object>>();
    consume(cursor, consumed, index -> HardSoftScore.of(-1, 0));
    assertThat(cursor.advanceBatch()).isTrue();
    consume(cursor, consumed, index -> HardSoftScore.of(index == 45 ? 0 : -1, -100));
    assertThat(consumed).containsExactlyElementsOf(moves.subList(0, 80));
    assertThat(cursor.advanceBatch()).isTrue();
    assertThat(cursor.next()).isSameAs(noChange);
    assertThat(cursor.hasNext()).isFalse();
    assertThat(cursor.advanceBatch()).isFalse();
    assertThat(cursor.advanceBatch()).isFalse();
  }

  @Test
  void nullAlternativesAndStructuralViolationsDoNotStopWidening() {
    var moves = moves(81);
    var noChange = move();
    var cursor =
        new AdaptiveConstructionIterator<>(
            moves.iterator(),
            40,
            List.of(noChange).iterator(),
            candidate -> candidate != moves.getFirst() && candidate != noChange,
            new HardSoftScoreDefinition(),
            HardSoftScore.ZERO);
    var consumed = new ArrayList<Move<Object>>();
    consume(
        cursor, consumed, index -> index == 0 ? HardSoftScore.ZERO : new HardSoftScore(-1, 0, 0));
    assertThat(cursor.advanceBatch()).isTrue();
    consume(cursor, consumed, index -> HardSoftScore.of(-1, 0));
    assertThat(cursor.advanceBatch()).isTrue();
    consume(cursor, consumed, index -> HardSoftScore.of(-1, 0));
    assertThat(consumed).hasSize(81);
    assertThat(cursor.advanceBatch()).isTrue();
    assertThat(cursor.next()).isSameAs(noChange);
    assertThat(cursor.advanceBatch()).isFalse();
  }

  @Test
  void wideningComparesRawHardScoreAndIgnoresInitializationMediumAndSoft() {
    var cursor =
        new AdaptiveConstructionIterator<>(
            moves(100).iterator(),
            40,
            Collections.emptyIterator(),
            candidate -> true,
            new HardMediumSoftScoreDefinition(),
            HardMediumSoftScore.of(-1, -500, -500));
    var consumed = new ArrayList<Move<Object>>();
    consume(cursor, consumed, index -> HardMediumSoftScore.of(-2, 0, 0));
    assertThat(cursor.advanceBatch()).isTrue();
    consume(cursor, consumed, index -> HardMediumSoftScore.of(-1, -1_000, -1_000));
    assertThat(consumed).hasSize(80);
    assertThat(cursor.advanceBatch()).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void derivesBaselineFromRawScoreInsteadOfInitializationProgress() {
    InnerScoreDirector<Object, HardSoftScore> director = mock(InnerScoreDirector.class);
    when(director.getScoreDefinition()).thenReturn(new HardSoftScoreDefinition());
    ConstructionHeuristicPhaseScope<Object> phase = mock(ConstructionHeuristicPhaseScope.class);
    var lastStep = new ConstructionHeuristicStepScope<>(phase, -1);
    lastStep.setScore(InnerScore.withUnassignedCount(HardSoftScore.ZERO, 8));
    when(phase.getLastCompletedStepScope()).thenReturn(lastStep);
    doReturn(director).when(phase).getScoreDirector();
    var step = new ConstructionHeuristicStepScope<>(phase, 0);
    var cursor =
        new AdaptiveConstructionIterator<>(
            moves(100).iterator(), 40, Collections.emptyIterator(), candidate -> true);
    int index = 0;
    while (cursor.hasNext()) {
      var scope = new ConstructionHeuristicMoveScope<>(step, index++, cursor.next());
      scope.setScore(InnerScore.withUnassignedCount(HardSoftScore.of(-1, 1_000), 7));
      cursor.recordScore(scope);
    }
    // The improved assignment count must not hide the deteriorating raw hard score.
    assertThat(cursor.advanceBatch()).isTrue();
    while (cursor.hasNext()) {
      var scope = new ConstructionHeuristicMoveScope<>(step, index++, cursor.next());
      scope.setScore(InnerScore.withUnassignedCount(HardSoftScore.of(0, -1_000), 7));
      cursor.recordScore(scope);
    }
    assertThat(cursor.advanceBatch()).isFalse();
    assertThat(index).isEqualTo(80);
  }

  @Test
  void comparesLongHardLevelsExactlyBeyondDoublePrecision() {
    long baseline = -9_007_199_254_740_992L;
    var cursor = cursor(moves(100), HardSoftScore.of(baseline, 0));
    var consumed = new ArrayList<Move<Object>>();
    consume(cursor, consumed, index -> HardSoftScore.of(baseline - 1, Long.MAX_VALUE));
    assertThat(cursor.advanceBatch()).isTrue();
    consume(cursor, consumed, index -> HardSoftScore.of(baseline, Long.MIN_VALUE));
    assertThat(cursor.advanceBatch()).isFalse();
    assertThat(consumed).hasSize(80);
  }

  @Test
  void comparesBigDecimalHardLevelsWithoutRounding() {
    var baseline = new BigDecimal("1.00000000000000000001");
    var cursor =
        new AdaptiveConstructionIterator<>(
            moves(100).iterator(),
            40,
            Collections.emptyIterator(),
            candidate -> true,
            new HardSoftBigDecimalScoreDefinition(),
            HardSoftBigDecimalScore.of(baseline, BigDecimal.ZERO));
    var consumed = new ArrayList<Move<Object>>();
    consume(cursor, consumed, index -> HardSoftBigDecimalScore.of(BigDecimal.ONE, BigDecimal.TEN));
    assertThat(cursor.advanceBatch()).isTrue();
    consume(
        cursor, consumed, index -> HardSoftBigDecimalScore.of(baseline, BigDecimal.TEN.negate()));
    assertThat(cursor.advanceBatch()).isFalse();
  }

  @Test
  void comparesMultipleHardLevelsLexicographically() {
    var cursor =
        new AdaptiveConstructionIterator<>(
            moves(100).iterator(),
            40,
            Collections.emptyIterator(),
            candidate -> true,
            new BendableScoreDefinition(2, 1),
            BendableScore.of(new long[] {-1, -10}, new long[] {0}));
    var consumed = new ArrayList<Move<Object>>();
    consume(cursor, consumed, index -> BendableScore.of(new long[] {-1, -11}, new long[] {1_000}));
    assertThat(cursor.advanceBatch()).isTrue();
    consume(cursor, consumed, index -> BendableScore.of(new long[] {0, -100}, new long[] {-1_000}));
    assertThat(cursor.advanceBatch()).isFalse();
  }

  @Test
  void scoreWithoutHardLevelsSettlesAtFirstMeaningfulAssignment() {
    var cursor =
        new AdaptiveConstructionIterator<>(
            moves(100).iterator(),
            40,
            Collections.emptyIterator(),
            candidate -> true,
            new SimpleScoreDefinition(),
            SimpleScore.ZERO);
    var consumed = new ArrayList<Move<Object>>();
    consume(cursor, consumed, index -> SimpleScore.of(-1_000));
    assertThat(cursor.advanceBatch()).isFalse();
    assertThat(consumed).hasSize(40);
  }

  @Test
  void emptyRankedStreamExposesOptionalTailBeforeDeciderFeedback() {
    var noChange = move();
    var cursor =
        new AdaptiveConstructionIterator<>(
            Collections.<Move<Object>>emptyIterator(),
            40,
            List.of(noChange).iterator(),
            candidate -> false);
    assertThat(cursor.hasNext()).isTrue();
    assertThat(cursor.next()).isSameAs(noChange);
    assertThat(cursor.hasNext()).isFalse();
    assertThat(cursor.advanceBatch()).isFalse();
  }

  @Test
  void closesRankedScratchOnSettlementAndOptionalScratchOnClose() {
    var ranked = new CloseableMoves(moves(100));
    var tail = new CloseableMoves(List.of(move()));
    var cursor =
        new AdaptiveConstructionIterator<>(
            ranked, 40, tail, candidate -> true, new HardSoftScoreDefinition(), HardSoftScore.ZERO);
    consume(cursor, new ArrayList<>(), index -> HardSoftScore.ZERO);
    assertThat(cursor.advanceBatch()).isTrue();
    assertThat(ranked.closeCount).isEqualTo(1);
    assertThat(tail.closeCount).isZero();
    cursor.close();
    cursor.close();
    assertThat(ranked.closeCount).isEqualTo(1);
    assertThat(tail.closeCount).isEqualTo(1);
  }

  @Test
  void closePreventsFurtherUse() {
    var cursor = cursor(moves(100), HardSoftScore.ZERO);
    cursor.close();
    assertThat(cursor.hasNext()).isFalse();
    assertThat(cursor.advanceBatch()).isFalse();
    assertThatThrownBy(cursor::next).isInstanceOf(java.util.NoSuchElementException.class);
  }

  private static AdaptiveConstructionIterator<Object> cursor(
      List<Move<Object>> moves, HardSoftScore baseline) {
    return new AdaptiveConstructionIterator<>(
        moves.iterator(),
        40,
        Collections.emptyIterator(),
        candidate -> true,
        new HardSoftScoreDefinition(),
        baseline);
  }

  private static <Score_ extends Score<Score_>> void consume(
      AdaptiveConstructionIterator<Object> cursor,
      List<Move<Object>> consumed,
      IntFunction<Score_> scoreFunction) {
    while (cursor.hasNext()) {
      var candidate = cursor.next();
      var scope = new ConstructionHeuristicMoveScope<Object>(null, consumed.size(), candidate);
      scope.setScore(InnerScore.withUnassignedCount(scoreFunction.apply(consumed.size()), 0));
      consumed.add(candidate);
      cursor.recordScore(scope);
    }
  }

  private static List<Move<Object>> moves(int count) {
    return IntStream.range(0, count).mapToObj(index -> move()).toList();
  }

  @SuppressWarnings("unchecked")
  private static Move<Object> move() {
    return mock(Move.class);
  }

  private static final class CloseableMoves implements Iterator<Move<Object>>, AutoCloseable {

    private final Iterator<Move<Object>> delegate;
    private int closeCount;

    private CloseableMoves(List<Move<Object>> moves) {
      delegate = moves.iterator();
    }

    @Override
    public boolean hasNext() {
      return delegate.hasNext();
    }

    @Override
    public Move<Object> next() {
      return delegate.next();
    }

    @Override
    public void close() {
      closeCount++;
    }
  }
}
