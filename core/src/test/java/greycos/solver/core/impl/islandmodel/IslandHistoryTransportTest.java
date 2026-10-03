package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.BitSet;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.localsearch.decider.acceptor.AcceptorMigrationState;
import greycos.solver.core.impl.localsearch.decider.acceptor.LateAcceptanceHistory;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;

import org.junit.jupiter.api.Test;

class IslandHistoryTransportTest {

  @Test
  void pendingCompetitionKeepsEachSolutionPairedWithItsHistory() {
    var scope = new SolverScope<Object>();
    Move<Object> first = view -> {};
    Move<Object> better = view -> {};
    var firstHistory = history(-10);
    var betterHistory = history(-5);
    scope.setPendingMoveIfBetter(first, score(-10), true, firstHistory);
    scope.setPendingMoveIfBetter(better, score(-5), true, betterHistory);
    scope.setPendingMoveIfBetter(first, score(-5), true, firstHistory);
    scope.setPendingMoveIfBetter(first, score(-20), true, firstHistory);
    var pending = scope.consumePendingMove();
    assertThat(pending.move()).isSameAs(better);
    assertThat(pending.score()).isEqualTo(score(-5));
    assertThat(pending.acceptorState()).isSameAs(betterHistory);
    assertThat(scope.consumePendingMove()).isNull();

    scope.setPendingMoveIfBetter(first, score(-1), true);
    assertThat(scope.consumePendingMove().acceptorState())
        .isSameAs(AcceptorMigrationState.Empty.INSTANCE);
  }

  @Test
  void forwardingAndReplacementNeverMixHistoryFromDifferentBests() {
    var parent = new SharedGlobalState<Object>();
    var child = new SharedGlobalState<Object>();
    child.reset(Clock.systemUTC(), null, parent);
    var solution = new Object();
    var history = history(-10);
    child.tryUpdate(solution, score(-10), history);
    var retained = parent.getBestSnapshot();
    assertThat(retained.getSolution()).isSameAs(solution);
    assertThat(retained.getAcceptorState()).isSameAs(history);
    assertThat(child.tryUpdate(new Object(), score(-10), history(-1))).isFalse();
    assertThat(child.tryUpdate(new Object(), score(-20), history(-1))).isFalse();
    assertThat(parent.getBestSnapshot()).isSameAs(retained);

    var next = new Object();
    child.tryUpdate(next, score(-5));
    assertThat(parent.getBestSnapshot().getSolution()).isSameAs(next);
    assertThat(parent.getBestSnapshot().getAcceptorState())
        .isSameAs(AcceptorMigrationState.Empty.INSTANCE);
    assertThat(retained.getAcceptorState()).isSameAs(history);
    child.reset();
    assertThat(child.getBestSnapshot()).isNull();
  }

  @Test
  void replacingBestSolutionOrScoreClearsThePreviousAssociation() {
    var scope = new SolverScope<Object>();
    scope.setBestSolution(new Object());
    scope.setBestScore(score(-10));
    scope.setBestAcceptorMigrationState(history(-10));
    scope.setBestSolution(new Object());
    assertThat(scope.getBestAcceptorMigrationState())
        .isSameAs(AcceptorMigrationState.Empty.INSTANCE);
    scope.setBestAcceptorMigrationState(history(-5));
    scope.setBestScore(score(-5));
    assertThat(scope.getBestAcceptorMigrationState())
        .isSameAs(AcceptorMigrationState.Empty.INSTANCE);
  }

  @Test
  void ringEnvelopeIncludesHistoryInItsValueAndCoalescesAsOneMessage() {
    var solution = new Object();
    var history = history(-10);
    var bits = new BitSet();
    bits.set(0);
    var update = new AgentUpdate<>(0, solution, score(-10), bits, history);
    assertThat(update).isEqualTo(new AgentUpdate<>(0, solution, score(-10), bits, history));
    assertThat(update).isNotEqualTo(new AgentUpdate<>(0, solution, score(-10), bits));
    bits.clear();
    assertThat(update.getAliveBits().get(0)).isTrue();
    assertThat(update.getAcceptorState()).isSameAs(history);

    var channel = new BoundedChannel<AgentUpdate<Object>>(1);
    channel.replace(update);
    var better = new AgentUpdate<>(1, new Object(), score(-5), bits, history(-5));
    channel.replace(better);
    assertThat(channel.tryReceive()).isSameAs(better);
  }

  private static LateAcceptanceHistory history(long best) {
    return new LateAcceptanceHistory(List.of(score(best), score(-20)), 1, SimpleScore.class, 1, 0);
  }

  private static InnerScore<SimpleScore> score(long value) {
    return InnerScore.fullyAssigned(SimpleScore.of(value));
  }
}
