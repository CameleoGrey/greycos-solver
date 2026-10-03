package greycos.solver.core.impl.localsearch.decider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationPipeline;
import greycos.solver.core.impl.localsearch.decider.acceptor.AbstractAcceptor;
import greycos.solver.core.impl.localsearch.decider.acceptor.CompositeAcceptor;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.neighborhood.MoveSelectorBasedMoveRepository;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.MutableSolutionView;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeciderAcceptorFeedbackTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  @SuppressWarnings("unchecked")
  void finalAcceptanceFeedbackPrecedesForagingForConsumedCandidates(boolean threaded)
      throws InterruptedException {
    var events = new ArrayList<String>();
    var acceptor =
        new CompositeAcceptor<>(
            new RecordingAcceptor("first", index -> index != 1, events),
            new RecordingAcceptor("second", index -> index != 3, events));
    LocalSearchForager<Object> forager = mock(LocalSearchForager.class);
    var foraged = new ArrayList<LocalSearchMoveScope<Object>>();
    doAnswer(
            invocation -> {
              LocalSearchMoveScope<Object> moveScope = invocation.getArgument(0);
              events.add("forager:" + moveScope.getMoveIndex() + ":" + moveScope.getAccepted());
              foraged.add(moveScope);
              return null;
            })
        .when(forager)
        .addMove(any());
    when(forager.isQuitEarly()).thenAnswer(ignored -> foraged.size() == 5);

    var candidates = IntStream.range(0, 12).<Move<Object>>mapToObj(Candidate::new).toList();
    MoveSelectorBasedMoveRepository<Object> repository =
        mock(MoveSelectorBasedMoveRepository.class);
    when(repository.iterator()).thenReturn(candidates.iterator());
    InnerScoreDirector<Object, SimpleScore> director = mock(InnerScoreDirector.class);
    when(director.executeTemporaryMove(any(), anyBoolean()))
        .thenAnswer(invocation -> candidateScore(((Candidate) invocation.getArgument(0)).index()));
    var solverScope = new SolverScope<Object>();
    solverScope.setScoreDirector(director);
    var phaseScope = new LocalSearchPhaseScope<>(solverScope, 0);
    var stepScope = new LocalSearchStepScope<>(phaseScope, 0);
    var termination = PhaseTermination.bridge(new BasicPlumbingTermination<Object>(false));

    MoveEvaluationPipeline<Object> pipeline = mock(MoveEvaluationPipeline.class);
    var readyResults = new ArrayDeque<MoveEvaluationPipeline.Result<Object>>();
    // Every submitted candidate is already evaluated, including speculative results left after
    // early foraging. Only results taken by the coordinator may trigger acceptance feedback.
    doAnswer(
            invocation -> {
              int index = invocation.getArgument(0);
              Move<Object> move = invocation.getArgument(1);
              readyResults.addLast(
                  new MoveEvaluationPipeline.Result<>(0, index, move, candidateScore(index)));
              return null;
            })
        .when(pipeline)
        .submit(anyInt(), any());
    when(pipeline.take()).thenAnswer(ignored -> readyResults.removeFirst());

    LocalSearchDecider<Object> decider;
    if (threaded) {
      var threadedDecider =
          new MultiThreadedLocalSearchDecider<>(
              "", termination, repository, acceptor, forager, Thread::new, 2, 8);
      threadedDecider.moveEvaluationPipeline = pipeline;
      decider = threadedDecider;
    } else {
      decider = new LocalSearchDecider<>("", termination, repository, acceptor, forager);
    }
    decider.stepStarted(stepScope);
    decider.decideNextStep(stepScope);

    assertThat(events)
        .containsExactly(
            "first.accept:0",
            "second.accept:0",
            "first.feedback:0:true",
            "second.feedback:0:true",
            "forager:0:true",
            "first.accept:1",
            "first.feedback:1:false",
            "second.feedback:1:false",
            "forager:1:false",
            // AbstractAcceptor rejects the structurally invalid candidate without calling either
            // acceptance predicate. Both children still receive its final rejected outcome.
            "first.feedback:2:false",
            "second.feedback:2:false",
            "forager:2:false",
            "first.accept:3",
            "second.accept:3",
            "first.feedback:3:false",
            "second.feedback:3:false",
            "forager:3:false",
            "first.accept:4",
            "second.accept:4",
            "first.feedback:4:true",
            "second.feedback:4:true",
            "forager:4:true");
    assertThat(foraged)
        .extracting(LocalSearchMoveScope::getMoveIndex)
        .containsExactly(0, 1, 2, 3, 4);
    assertThat(solverScope.getMoveEvaluationCount()).isEqualTo(5L);
    verify(forager).pickMove(stepScope);
    if (threaded) {
      verify(pipeline, times(5)).take();
      verify(pipeline).cancelStep();
      assertThat(readyResults)
          .extracting(MoveEvaluationPipeline.Result::moveIndex)
          .containsExactly(5, 6, 7, 8, 9, 10, 11);
    } else {
      verify(director, times(5)).executeTemporaryMove(any(), anyBoolean());
    }
  }

  private static InnerScore<SimpleScore> candidateScore(int index) {
    return InnerScore.fullyAssigned(index == 2 ? new SimpleScore(-1, 0) : SimpleScore.ZERO);
  }

  private record Candidate(int index) implements Move<Object> {
    @Override
    public void execute(MutableSolutionView<Object> solutionView) {
      throw new UnsupportedOperationException("The test supplies candidate scores directly.");
    }
  }

  private static final class RecordingAcceptor extends AbstractAcceptor<Object> {
    private final String name;
    private final IntPredicate acceptance;
    private final List<String> events;

    private RecordingAcceptor(String name, IntPredicate acceptance, List<String> events) {
      this.name = name;
      this.acceptance = acceptance;
      this.events = events;
    }

    @Override
    protected boolean isStructurallyValidSolutionAccepted(LocalSearchMoveScope<Object> moveScope) {
      events.add(name + ".accept:" + moveScope.getMoveIndex());
      return acceptance.test(moveScope.getMoveIndex());
    }

    @Override
    public void moveEvaluated(LocalSearchMoveScope<Object> moveScope) {
      events.add(name + ".feedback:" + moveScope.getMoveIndex() + ":" + moveScope.getAccepted());
    }
  }
}
