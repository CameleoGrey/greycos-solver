package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import static greycos.solver.core.testutil.PlannerTestUtils.mockRebasingScoreDirector;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.score.trend.InitializingScoreTrendLevel;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.constructionheuristic.DefaultConstructionHeuristicPhaseFactory;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.move.generic.RuinRecreateConstructionHeuristicPhaseBuilder;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ruin.SelectorBasedListRuinRecreateMove;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.list.TestdataListEntity;
import greycos.solver.core.testcotwin.list.TestdataListSolution;
import greycos.solver.core.testcotwin.list.TestdataListValue;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListEntity;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListSolution;
import greycos.solver.core.testcotwin.list.pinned.index.TestdataPinnedWithIndexListValue;

import org.junit.jupiter.api.Test;

class SelectorBasedListRuinRecreateMoveTest {

  @SuppressWarnings("unchecked")
  @Test
  void rebase() {
    var variableDescriptor = TestdataListEntity.buildVariableDescriptorForValueList();

    var v1 = new TestdataListValue("v1");
    var v2 = new TestdataListValue("v2");
    var e1 = TestdataListEntity.createWithValues("e1", v1);
    var e2 = new TestdataListEntity("e2");
    var e3 = TestdataListEntity.createWithValues("e3", v1);

    var destinationV1 = new TestdataListValue("v1");
    var destinationV2 = new TestdataListValue("v2");
    var destinationE1 = TestdataListEntity.createWithValues("e1", destinationV1);
    var destinationE2 = new TestdataListEntity("e2");
    var destinationE3 = TestdataListEntity.createWithValues("e3", destinationV1);

    var destinationScoreDirector =
        mockRebasingScoreDirector(
            variableDescriptor.getEntityDescriptor().getSolutionDescriptor(),
            new Object[][] {
              {v1, destinationV1},
              {v2, destinationV2},
              {e1, destinationE1},
              {e2, destinationE2},
              {e3, destinationE3},
            });

    var move =
        new SelectorBasedListRuinRecreateMove<TestdataListSolution>(
            mock(ListVariableDescriptor.class),
            mock(RuinRecreateConstructionHeuristicPhaseBuilder.class),
            mock(SolverScope.class),
            Arrays.asList(v1, v2),
            new LinkedHashSet<>(Set.of(e1, e2, e3)));
    var rebasedMove = move.rebase(destinationScoreDirector);

    assertThat(rebasedMove).isInstanceOf(SelectorBasedListRuinRecreateMove.class);
    assertSoftly(
        softly -> {
          softly
              .assertThat((Iterable) rebasedMove.getPlanningEntities())
              .containsExactlyInAnyOrderElementsOf(
                  Set.of(destinationE1, destinationE2, destinationE3));
          softly
              .assertThat((Iterable) rebasedMove.getPlanningValues())
              .containsExactlyElementsOf(List.of(destinationV1, destinationV2));
        });
  }

  @SuppressWarnings("unchecked")
  @Test
  void equality() {
    var v1 = new TestdataListValue("v1");
    var v2 = new TestdataListValue("v2");
    var e1 = TestdataListEntity.createWithValues("e1", v1);
    var e2 = new TestdataListEntity("e2");

    var descriptor = mock(ListVariableDescriptor.class);
    var move =
        new SelectorBasedListRuinRecreateMove<TestdataListSolution>(
            descriptor,
            mock(RuinRecreateConstructionHeuristicPhaseBuilder.class),
            mock(SolverScope.class),
            List.of(v1),
            new LinkedHashSet<>(Set.of(e1)));
    var sameMove =
        new SelectorBasedListRuinRecreateMove<TestdataListSolution>(
            descriptor,
            mock(RuinRecreateConstructionHeuristicPhaseBuilder.class),
            mock(SolverScope.class),
            List.of(v1),
            new LinkedHashSet<>(Set.of(e1)));
    assertThat(move).isEqualTo(sameMove);

    var differentMove =
        new SelectorBasedListRuinRecreateMove<TestdataListSolution>(
            descriptor,
            mock(RuinRecreateConstructionHeuristicPhaseBuilder.class),
            mock(SolverScope.class),
            List.of(v2),
            new LinkedHashSet<>(Set.of(e1)));
    assertThat(move).isNotEqualTo(differentMove);

    var anotherDifferentMove =
        new SelectorBasedListRuinRecreateMove<TestdataListSolution>(
            descriptor,
            mock(RuinRecreateConstructionHeuristicPhaseBuilder.class),
            mock(SolverScope.class),
            List.of(v1),
            new LinkedHashSet<>(Set.of(e2)));
    assertThat(move).isNotEqualTo(anotherDifferentMove);
  }

  @SuppressWarnings("unchecked")
  @Test
  void executeAndUndoNewDestinationEntityWithPinnedPrefix() {
    var listVariableDescriptor =
        TestdataPinnedWithIndexListEntity.buildVariableDescriptorForValueList();
    var solutionDescriptor = listVariableDescriptor.getEntityDescriptor().getSolutionDescriptor();

    var aPin = new TestdataPinnedWithIndexListValue("aPin");
    var special1 = new TestdataPinnedWithIndexListValue("special1");
    var special2 = new TestdataPinnedWithIndexListValue("special2");
    var special3 = new TestdataPinnedWithIndexListValue("special3");
    var bPin = new TestdataPinnedWithIndexListValue("bPin");

    var entityA = new TestdataPinnedWithIndexListEntity("A", aPin, special1, special2, special3);
    entityA.setPinIndex(1); // aPin is pinned; special1..3 are ruined below.
    var entityB = new TestdataPinnedWithIndexListEntity("B", bPin);
    entityB.setPinIndex(1); // bPin is pinned; B never has any of its own values ruined.

    var solution = new TestdataPinnedWithIndexListSolution();
    solution.setEntityList(new ArrayList<>(List.of(entityA, entityB)));
    solution.setValueList(new ArrayList<>(List.of(aPin, special1, special2, special3, bPin)));
    SolutionManager.updateShadowVariables(solution);

    // Heavily penalize any "special" value that does not end up on entity B, so the nested
    // construction heuristic is forced to recreate all 3 ruined values onto entity B.
    var scoreDirectorFactory =
        new EasyScoreDirectorFactory<TestdataPinnedWithIndexListSolution, SimpleScore>(
            solutionDescriptor,
            s -> {
              var penalty = 0;
              for (var value : s.getValueList()) {
                if (value.getCode().startsWith("special")
                    && value.getEntity() != null
                    && !value.getEntity().getCode().equals("B")) {
                  penalty++;
                }
              }
              return SimpleScore.of(-penalty);
            },
            EnvironmentMode.PHASE_ASSERT);
    var scoreDirector =
        (InnerScoreDirector<TestdataPinnedWithIndexListSolution, SimpleScore>)
            scoreDirectorFactory.buildScoreDirector();
    scoreDirector.setWorkingSolution(solution);

    var solverConfigPolicy =
        new HeuristicConfigPolicy.Builder<TestdataPinnedWithIndexListSolution>()
            .withSolutionDescriptor(solutionDescriptor)
            .withInitializingScoreTrend(
                InitializingScoreTrend.buildUniformTrend(InitializingScoreTrendLevel.ANY, 1))
            .build();
    var entityPlacerConfig =
        DefaultConstructionHeuristicPhaseFactory.buildListVariableQueuedValuePlacerConfig(
            solverConfigPolicy, listVariableDescriptor);
    var constructionHeuristicPhaseConfig =
        new ConstructionHeuristicPhaseConfig().withEntityPlacerConfig(entityPlacerConfig);
    var constructionHeuristicPhaseBuilder =
        RuinRecreateConstructionHeuristicPhaseBuilder.create(
            solverConfigPolicy, constructionHeuristicPhaseConfig);

    var solverScope = new SolverScope<TestdataPinnedWithIndexListSolution>();
    solverScope.setScoreDirector(scoreDirector);

    var move =
        new SelectorBasedListRuinRecreateMove<TestdataPinnedWithIndexListSolution>(
            listVariableDescriptor,
            constructionHeuristicPhaseBuilder,
            solverScope,
            List.of(special1, special2, special3),
            new LinkedHashSet<>(Set.of(entityA)),
            0L);

    // Execute the move and immediately undo it, exactly like local search does to evaluate a
    // candidate move.
    scoreDirector.getMoveDirector().executeTemporary(move);

    assertThat(entityA.getValueList()).containsExactly(aPin, special1, special2, special3);
    assertThat(entityB.getValueList()).containsExactly(bPin);
  }
}
