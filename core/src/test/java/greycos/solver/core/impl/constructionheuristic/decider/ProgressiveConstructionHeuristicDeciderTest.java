package greycos.solver.core.impl.constructionheuristic.decider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicPickEarlyType;
import greycos.solver.core.impl.constructionheuristic.decider.forager.DefaultConstructionHeuristicForager;
import greycos.solver.core.impl.constructionheuristic.nearby.ProgressiveConstructionIterator;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationPipeline;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.preview.api.move.Move;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProgressiveConstructionHeuristicDeciderTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void consumesFeedbackBeforeWideningAndRetainsBestMoveAcrossBatches(boolean threaded)
      throws Exception {
    var fixture = new Fixture(threaded, ConstructionHeuristicPickEarlyType.NEVER);
    fixture.decider.decideNextStep(fixture.step, fixture.cursor);

    assertThat(fixture.cursor.scoreIndexes).containsExactly(0, 1, 2, 3);
    assertThat(fixture.cursor.advanceCount).isEqualTo(2);
    assertThat(fixture.cursor.closed).isTrue();
    assertThat(fixture.step.getStep()).isSameAs(fixture.cursor.moves.get(1));
    assertThat(fixture.step.getScore().raw()).isEqualTo(SimpleScore.of(5));
    assertThat(fixture.step.getSelectedMoveCount()).isEqualTo(4);
    assertThat(fixture.step.getNearbyWideningCount()).isZero();
    if (threaded) {
      verify(fixture.pipeline).submit(0, fixture.cursor.moves.get(0));
      verify(fixture.pipeline).submit(1, fixture.cursor.moves.get(1));
      verify(fixture.pipeline).submit(2, fixture.cursor.moves.get(2));
      verify(fixture.pipeline).submit(3, fixture.cursor.moves.get(3));
      verify(fixture.pipeline).cancelStep();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void explicitEarlyPickDoesNotOpenAnotherBatch(boolean threaded) throws Exception {
    var fixture =
        new Fixture(threaded, ConstructionHeuristicPickEarlyType.FIRST_NON_DETERIORATING_SCORE);
    fixture.decider.decideNextStep(fixture.step, fixture.cursor);

    assertThat(fixture.cursor.scoreIndexes).containsExactly(0, 1);
    assertThat(fixture.cursor.advanceCount).isZero();
    assertThat(fixture.cursor.closed).isTrue();
    assertThat(fixture.step.getStep()).isSameAs(fixture.cursor.moves.get(1));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void interruptionClosesCursorWithoutPickingAMove(boolean threaded) throws Exception {
    var fixture = new Fixture(threaded, ConstructionHeuristicPickEarlyType.NEVER);
    Thread.currentThread().interrupt();
    try {
      fixture.decider.decideNextStep(fixture.step, fixture.cursor);
      assertThat(fixture.cursor.scoreIndexes).isEmpty();
      assertThat(fixture.cursor.closed).isTrue();
      assertThat(fixture.step.getStep()).isNull();
      if (threaded) {
        verify(fixture.pipeline, never()).applyStep(anyInt(), any(), any());
      }
    } finally {
      Thread.interrupted();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void scoringFailureClosesCursor(boolean threaded) throws Exception {
    var fixture = new Fixture(threaded, ConstructionHeuristicPickEarlyType.NEVER);
    fixture.failScoring = true;
    assertThatThrownBy(() -> fixture.decider.decideNextStep(fixture.step, fixture.cursor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("score failure");
    assertThat(fixture.cursor.closed).isTrue();
    assertThat(fixture.step.getStep()).isNull();
    if (threaded) {
      verify(fixture.pipeline).cancelStep();
    }
  }

  private static final class Fixture {

    private final RecordingCursor cursor = new RecordingCursor();
    private final MoveEvaluationPipeline<Object> pipeline;
    private final ConstructionHeuristicDecider<Object> decider;
    private final ConstructionHeuristicStepScope<Object> step;
    private boolean failScoring;

    @SuppressWarnings("unchecked")
    private Fixture(boolean threaded, ConstructionHeuristicPickEarlyType pickEarly)
        throws Exception {
      var solverScope = new SolverScope<Object>(Clock.systemUTC());
      InnerScoreDirector<Object, SimpleScore> director = mock(InnerScoreDirector.class);
      solverScope.setScoreDirector(director);
      var phase = new ConstructionHeuristicPhaseScope<>(solverScope, 0);
      phase.getLastCompletedStepScope().setInitializedScore(SimpleScore.ZERO);
      step = new ConstructionHeuristicStepScope<>(phase, 0);
      PhaseTermination<Object> termination =
          PhaseTermination.bridge(new BasicPlumbingTermination<>(false));
      var forager = new DefaultConstructionHeuristicForager<Object>(pickEarly);
      pipeline = mock(MoveEvaluationPipeline.class);
      if (threaded) {
        var nextResultIndex = new AtomicInteger();
        doAnswer(
                invocation -> {
                  cursor.outstanding++;
                  return null;
                })
            .when(pipeline)
            .submit(anyInt(), any());
        when(pipeline.take())
            .thenAnswer(
                invocation -> {
                  int index = nextResultIndex.getAndIncrement();
                  var score = score(index);
                  cursor.outstanding--;
                  return new MoveEvaluationPipeline.Result<>(
                      0, index, cursor.moves.get(index), InnerScore.fullyAssigned(score));
                });
        decider =
            new MultiThreadedConstructionHeuristicDecider<>(
                "", termination, forager, Thread::new, 2, 2) {
              @Override
              protected ExecutorService createThreadPoolExecutor() {
                return mock(ExecutorService.class);
              }

              @Override
              protected MoveEvaluationPipeline<Object> createMoveEvaluationPipeline(
                  int phaseIndex) {
                return pipeline;
              }
            };
        decider.phaseStarted(phase);
      } else {
        decider =
            new ConstructionHeuristicDecider<>("", termination, forager) {
              @Override
              protected void doMove(ConstructionHeuristicMoveScope<Object> moveScope) {
                moveScope.setInitializedScore(score(moveScope.getMoveIndex()));
                forager.addMove(moveScope);
              }
            };
      }
      decider.stepStarted(step);
    }

    private SimpleScore score(int index) {
      if (failScoring) {
        throw new IllegalStateException("score failure");
      }
      return SimpleScore.of(index == 1 ? 5 : -1);
    }
  }

  private static final class RecordingCursor implements ProgressiveConstructionIterator<Object> {

    private final List<Move<Object>> moves = List.of(move(), move(), move(), move());
    private final List<Integer> scoreIndexes = new ArrayList<>();
    private Iterator<Move<Object>> current = moves.subList(0, 2).iterator();
    private int emitted;
    private int outstanding;
    private int advanceCount;
    private boolean closed;

    @Override
    public boolean hasNext() {
      return !closed && current.hasNext();
    }

    @Override
    public Move<Object> next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      emitted++;
      return current.next();
    }

    @Override
    public void recordScore(ConstructionHeuristicMoveScope<Object> moveScope) {
      assertThat(moveScope.getScore()).isNotNull();
      scoreIndexes.add(moveScope.getMoveIndex());
    }

    @Override
    public boolean advanceBatch() {
      assertThat(hasNext()).isFalse();
      assertThat(outstanding).isZero();
      assertThat(scoreIndexes).hasSize(emitted);
      advanceCount++;
      if (advanceCount == 1) {
        current = moves.subList(2, 4).iterator();
        return true;
      }
      return false;
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  @SuppressWarnings("unchecked")
  private static Move<Object> move() {
    return mock(Move.class);
  }
}
