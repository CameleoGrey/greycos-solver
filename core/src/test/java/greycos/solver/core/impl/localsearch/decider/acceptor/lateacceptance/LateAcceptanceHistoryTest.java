package greycos.solver.core.impl.localsearch.decider.acceptor.lateacceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.score.BendableScore;
import greycos.solver.core.api.score.HardMediumSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.localsearch.decider.acceptor.AcceptorMigrationState;
import greycos.solver.core.impl.localsearch.decider.acceptor.CompositeAcceptor;
import greycos.solver.core.impl.localsearch.decider.acceptor.LateAcceptanceHistory;
import greycos.solver.core.impl.localsearch.decider.acceptor.hillclimbing.HillClimbingAcceptor;
import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.score.definition.BendableScoreDefinition;
import greycos.solver.core.impl.score.definition.HardMediumSoftScoreDefinition;
import greycos.solver.core.impl.score.definition.ScoreDefinition;
import greycos.solver.core.impl.score.definition.SimpleScoreDefinition;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LateAcceptanceHistoryTest {

  @Test
  void snapshotResolvesLazyResetsAndOwnsItsStorage() {
    var buffer = new LateAcceptanceScoreBuffer(3, score(-100));
    buffer.update(score(-80));
    buffer.tryReset(score(-20));
    buffer.update(score(-30));
    var snapshot = buffer.snapshot(new SimpleScoreDefinition());
    assertThat(snapshot.scores()).containsExactly(score(-20), score(-30), score(-20));
    assertThat(snapshot.nextIndex()).isEqualTo(2);
    assertThatThrownBy(() -> snapshot.scores().set(0, score(0)))
        .isInstanceOf(UnsupportedOperationException.class);

    var imported = new LateAcceptanceScoreBuffer(snapshot);
    imported.update(score(-10));
    buffer.tryReset(score(0));
    assertThat(snapshot.scores()).containsExactly(score(-20), score(-30), score(-20));
    assertThat(imported.snapshot(new SimpleScoreDefinition()).scores())
        .containsExactly(score(-20), score(-30), score(-10));
    assertThat(imported.snapshot(new SimpleScoreDefinition()).nextIndex()).isZero();

    var mutable = new ArrayList<InnerScore<?>>(List.of(score(-5), score(-2)));
    var copied = new LateAcceptanceHistory(mutable, 1, SimpleScore.class, 1, 0);
    mutable.clear();
    assertThat(copied.scores()).containsExactly(score(-5), score(-2));
  }

  @Test
  void adoptsFullHistoryAndLengthWithoutAppendingOrAdvancingTwice() {
    var fixture = new Fixture<>(new SimpleScoreDefinition(), SimpleScore.of(-100), 2);
    fixture.acceptor.setHillClimbingEnabled(false);
    fixture.complete(SimpleScore.of(-80), null);
    var donor =
        new LateAcceptanceHistory(
            List.of(score(-20), score(-60), score(-40)), 1, SimpleScore.class, 1, 0);

    fixture.complete(SimpleScore.of(-20), donor);
    assertThat(fixture.history()).isEqualTo(donor);
    assertThat(fixture.accepts(SimpleScore.of(-50))).isTrue();
    assertThat(fixture.accepts(SimpleScore.of(-70))).isFalse();
    fixture.complete(SimpleScore.of(-50), null);
    assertThat(fixture.history().scores()).containsExactly(score(-20), score(-50), score(-40));
    assertThat(fixture.history().nextIndex()).isEqualTo(2);
    assertThat(fixture.accepts(SimpleScore.of(-45))).isFalse();

    fixture.acceptor.phaseEnded(fixture.phase);
    fixture.acceptor.phaseStarted(fixture.phase);
    assertThat(fixture.history().scores()).containsExactly(score(-20), score(-20));
    assertThat(fixture.history().nextIndex()).isZero();
    assertThat(donor.scores()).containsExactly(score(-20), score(-60), score(-40));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void importingHardOrMediumImprovementRebasesLevelTracking(boolean hardImprovement) {
    var initial = HardMediumSoftScore.of(-2, -5, -100);
    var fixture = new Fixture<>(new HardMediumSoftScoreDefinition(), initial, 2);
    fixture.complete(HardMediumSoftScore.of(-2, -5, -80), null);
    long hard = hardImprovement ? -1 : -2;
    long medium = hardImprovement ? -5 : -4;
    var best = HardMediumSoftScore.of(hard, medium, -20);
    var donor =
        new LateAcceptanceHistory(
            List.of(
                InnerScore.fullyAssigned(best),
                InnerScore.fullyAssigned(HardMediumSoftScore.of(hard, medium, -60)),
                InnerScore.fullyAssigned(HardMediumSoftScore.of(hard, medium, -40))),
            1,
            HardMediumSoftScore.class,
            3,
            1);
    fixture.complete(best, donor);
    assertThat(fixture.history()).isEqualTo(donor);

    var softImprovement = HardMediumSoftScore.of(hard, medium, -10);
    fixture.complete(softImprovement, null);
    assertThat(fixture.history().scores())
        .containsExactly(
            donor.scores().get(0),
            InnerScore.fullyAssigned(softImprovement),
            donor.scores().get(2));
    // Ordinary local improvements to a higher level still use the existing LA reset rule.
    var hardBest = HardMediumSoftScore.of(0, medium, -100);
    fixture.complete(hardBest, null);
    assertThat(fixture.history().scores()).containsOnly(InnerScore.fullyAssigned(hardBest));
  }

  @Test
  void incompatibleOrAbsentHistoryKeepsReceiverHistory() {
    var incompatible =
        new LateAcceptanceHistory(
            List.of(InnerScore.fullyAssigned(HardMediumSoftScore.ZERO)),
            0,
            HardMediumSoftScore.class,
            3,
            1);
    for (var state : List.of(AcceptorMigrationState.Empty.INSTANCE, incompatible)) {
      var fixture = new Fixture<>(new SimpleScoreDefinition(), SimpleScore.of(-100), 3);
      fixture.complete(SimpleScore.of(-80), null);
      fixture.complete(SimpleScore.of(-20), state);
      assertThat(fixture.history().scores()).containsExactly(score(-80), score(-20), score(-100));
      assertThat(fixture.history().nextIndex()).isEqualTo(2);
    }
    var bendable =
        new LateAcceptanceHistory(
            List.of(InnerScore.fullyAssigned(BendableScore.zero(1, 2))),
            0,
            BendableScore.class,
            3,
            1);
    assertThat(bendable.isCompatible(new BendableScoreDefinition(1, 2))).isTrue();
    assertThat(bendable.isCompatible(new BendableScoreDefinition(2, 1))).isFalse();
    assertThat(bendable.isCompatible(new BendableScoreDefinition(1, 1))).isFalse();
  }

  @Test
  void compositeImportsOnlyMatchingChildrenAndResetsOtherAcceptors() {
    var fixture = new Fixture<>(new SimpleScoreDefinition(), SimpleScore.of(-100), 3);
    var other = new RestartCountingAcceptor();
    var composite = new CompositeAcceptor<>(other, new CompositeAcceptor<>(fixture.acceptor));
    var donor =
        new LateAcceptanceHistory(List.of(score(-20), score(-50)), 1, SimpleScore.class, 1, 0);
    var state =
        new AcceptorMigrationState.Composite(
            List.of(
                AcceptorMigrationState.Empty.INSTANCE,
                new AcceptorMigrationState.Composite(List.of(donor))));
    var step = new LocalSearchStepScope<>(fixture.phase);
    step.setInitializedScore(SimpleScore.of(-20));
    fixture.solver.setInitializedBestScore(SimpleScore.of(-20));
    composite.migrationStepEnded(step, state);
    composite.resetAfterMigration(fixture.phase);
    assertThat(fixture.history()).isEqualTo(donor);
    assertThat(other.restarts).isEqualTo(1);
    assertThat(composite.snapshotMigrationState(fixture.phase)).isEqualTo(state);

    // A standalone payload cannot be matched to a nested LA child.
    step.setInitializedScore(SimpleScore.of(-10));
    composite.migrationStepEnded(step, donor);
    assertThat(fixture.history().scores()).containsExactly(score(-20), score(-10));
    assertThat(fixture.history().nextIndex()).isZero();
  }

  private static InnerScore<SimpleScore> score(long value) {
    return InnerScore.fullyAssigned(SimpleScore.of(value));
  }

  private static final class RestartCountingAcceptor extends HillClimbingAcceptor<Object> {
    int restarts;

    @Override
    public void phaseStarted(LocalSearchPhaseScope<Object> phase) {
      restarts++;
    }
  }

  private static final class Fixture<Score_ extends Score<Score_>> {
    final SolverScope<Object> solver = new SolverScope<>();
    final LocalSearchPhaseScope<Object> phase = new LocalSearchPhaseScope<>(solver, 0);
    final LateAcceptanceAcceptor<Object> acceptor = new LateAcceptanceAcceptor<>();

    @SuppressWarnings("unchecked")
    Fixture(ScoreDefinition<Score_> definition, Score_ initial, int size) {
      InnerScoreDirector<Object, Score_> director = mock(InnerScoreDirector.class);
      when(director.getScoreDefinition()).thenReturn(definition);
      solver.setScoreDirector(director);
      solver.setInitializedBestScore(initial);
      phase.reset();
      acceptor.setLateAcceptanceSize(size);
      acceptor.phaseStarted(phase);
    }

    void complete(Score_ score, AcceptorMigrationState state) {
      var step = new LocalSearchStepScope<>(phase);
      acceptor.stepStarted(step);
      step.setInitializedScore(score);
      if (score.compareTo(solver.<Score_>getBestScore().raw()) > 0) {
        solver.setInitializedBestScore(score);
        phase.setBestSolutionStepIndex(step.getStepIndex());
      }
      if (state == null) {
        acceptor.stepEnded(step);
      } else {
        acceptor.migrationStepEnded(step, state);
        acceptor.resetAfterMigration(phase);
      }
      phase.setLastCompletedStepScope(step);
    }

    LateAcceptanceHistory history() {
      return (LateAcceptanceHistory) acceptor.snapshotMigrationState(phase);
    }

    boolean accepts(Score_ score) {
      var move = new LocalSearchMoveScope<>(new LocalSearchStepScope<>(phase), 0, null);
      move.setInitializedScore(score);
      return acceptor.isAccepted(move);
    }
  }
}
