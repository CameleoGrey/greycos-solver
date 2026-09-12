package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListRuinRecreateMoveSelectorConfig;
import greycos.solver.core.config.score.trend.InitializingScoreTrendLevel;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ruin.ListRuinRecreateMoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ruin.SelectorBasedListRuinRecreateMove;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;
import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListEntity;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListSolution;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListValue;

import org.junit.jupiter.api.Test;

class ListRuinRecreateEligibilityTest {

  @Test
  void excludesPinnedPrefixAndClampsToEligibleCount() {
    try (var fixture = fixture(false, false)) {
      var a = fixture.solution().getEntityList().get(0);
      var b = fixture.solution().getEntityList().get(1);
      var originalA = List.copyOf(a.getValueList());
      var originalB = List.copyOf(b.getValueList());
      var originalScore = fixture.director().calculateScore();
      assertThat(fixture.selector().getSize())
          .isEqualTo(2); // Two ordered choices of both free values.
      var iterator = fixture.selector().iterator();
      int nonemptyMoves = 0;
      for (int i = 0; i < 100; i++) {
        var move = iterator.next();
        assertThat(move.getPlanningValues()).doesNotContain(originalA.get(0));
        if (!move.getPlanningValues().isEmpty()) {
          nonemptyMoves++;
          assertThat(move.getPlanningValues()).hasSize(2);
          fixture.director().getMoveDirector().executeTemporary(move);
          assertThat(a.getValueList()).containsExactlyElementsOf(originalA);
          assertThat(b.getValueList()).containsExactlyElementsOf(originalB);
          assertThat(fixture.director().calculateScore()).isEqualTo(originalScore);
          assertThat(
                  fixture
                      .director()
                      .getListVariableStateSupply(
                          fixture.director().getSolutionDescriptor().getListVariableDescriptor())
                      .getIndexOrFail(originalA.get(0)))
              .isZero();
          assertThat(originalA.get(0).getEntity()).isSameAs(a);
        }
      }
      assertThat(nonemptyMoves).isPositive();
    }
  }

  @Test
  void excludesFullyPinnedEntitiesAndHandlesEmptyPool() {
    try (var fixture = fixture(true, false)) {
      assertThat(fixture.selector().getSize()).isEqualTo(1);
      assertThat(fixture.selector().iterator().next().getPlanningValues())
          .containsExactly(fixture.solution().getEntityList().get(1).getValueList().get(0));
    }
    try (var fixture = fixture(true, true)) {
      assertThat(fixture.selector().getSize()).isZero();
      assertThat(fixture.selector().iterator()).isExhausted();
    }
  }

  @Test
  void invalidDirectMoveFailsBeforeAnyMutation() {
    try (var fixture = fixture(false, false)) {
      var entity = fixture.solution().getEntityList().get(0);
      var original = List.copyOf(entity.getValueList());
      for (var values :
          List.of(List.of(original.get(0)), List.of(original.get(1), original.get(1)))) {
        var move =
            new SelectorBasedListRuinRecreateMove<TestdataPinnedWithIndexListSolution>(
                fixture.director().getSolutionDescriptor().getListVariableDescriptor(),
                null,
                null,
                new ArrayList<Object>(values),
                new LinkedHashSet<>(List.of(entity)));
        assertThat(move.isMoveDoable(fixture.director())).isFalse();
        assertThatIllegalStateException()
            .isThrownBy(() -> fixture.director().getMoveDirector().executeTemporary(move))
            .withMessageContaining("distinct, assigned, unpinned");
        assertThat(entity.getValueList()).containsExactlyElementsOf(original);
      }
    }
  }

  private Fixture fixture(boolean pinA, boolean pinB) {
    var descriptor = TestdataPinnedWithIndexListSolution.buildSolutionDescriptor();
    var pinned = new TestdataPinnedWithIndexListValue("pinned");
    var freeA = new TestdataPinnedWithIndexListValue("freeA");
    var freeB = new TestdataPinnedWithIndexListValue("freeB");
    var a = new TestdataPinnedWithIndexListEntity("A", pinned, freeA);
    a.setPinIndex(1);
    a.setPinned(pinA);
    var b = new TestdataPinnedWithIndexListEntity("B", freeB);
    b.setPinned(pinB);
    var solution = new TestdataPinnedWithIndexListSolution();
    solution.setEntityList(new ArrayList<>(List.of(a, b)));
    solution.setValueList(new ArrayList<>(List.of(pinned, freeA, freeB)));
    SolutionManager.updateShadowVariables(solution);
    var factory =
        new EasyScoreDirectorFactory<TestdataPinnedWithIndexListSolution, SimpleScore>(
            descriptor,
            s -> SimpleScore.of(-s.getEntityList().get(0).getValueList().size()),
            EnvironmentMode.TRACKED_FULL_ASSERT);
    var director =
        (InnerScoreDirector<TestdataPinnedWithIndexListSolution, SimpleScore>)
            factory.buildScoreDirector();
    director.setWorkingSolution(solution);
    var policy =
        new HeuristicConfigPolicy.Builder<TestdataPinnedWithIndexListSolution>()
            .withSolutionDescriptor(descriptor)
            .withInitializingScoreTrend(
                InitializingScoreTrend.buildUniformTrend(InitializingScoreTrendLevel.ANY, 1))
            .build();
    var selector =
        new ListRuinRecreateMoveSelectorFactory<TestdataPinnedWithIndexListSolution>(
                new ListRuinRecreateMoveSelectorConfig())
            .buildMoveSelector(
                policy, SelectionCacheType.JUST_IN_TIME, SelectionOrder.RANDOM, true);
    var scope = new SolverScope<TestdataPinnedWithIndexListSolution>();
    scope.setScoreDirector(director);
    scope.setWorkingRandom(DefaultRandomSource.seeded(0L));
    selector.solvingStarted(scope);
    selector.phaseStarted(new LocalSearchPhaseScope<>(scope, 0));
    return new Fixture(solution, director, selector);
  }

  private record Fixture(
      TestdataPinnedWithIndexListSolution solution,
      InnerScoreDirector<TestdataPinnedWithIndexListSolution, SimpleScore> director,
      MoveSelector<TestdataPinnedWithIndexListSolution> selector)
      implements AutoCloseable {
    @Override
    public void close() {
      director.close();
    }
  }
}
