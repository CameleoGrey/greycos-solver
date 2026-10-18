package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import static greycos.solver.core.testutil.PlannerTestUtils.mockRebasingScoreDirector;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.LinkedHashSet;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.score.trend.InitializingScoreTrendLevel;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.constructionheuristic.DefaultConstructionHeuristicPhaseFactory;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.move.generic.RuinRecreateConstructionHeuristicPhaseBuilder;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ruin.ListRuinRecreateMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ruin.SelectorBasedListRuinRecreateMove;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MultiListRuinRecreateMoveTest {

  @ParameterizedTest
  @CsvSource({
    "firstValueList, false",
    "secondValueList, false",
    "firstValueList, true",
    "secondValueList, true"
  })
  void repairAndUndoPreserveOtherListAndBasicVariables(
      String variableName, boolean enclosingMoveThreads) {
    var solutionDescriptor = TestdataMultipleListSolution.buildSolutionDescriptor();
    var targetDescriptor =
        solutionDescriptor.resolveListVariableDescriptor(
            TestdataMultipleListEntity.class, variableName);
    var solution = TestdataMultipleListSolution.generateInitializedSolution(2, 2, 2);
    var source = solution.getEntityList().getFirst();
    var destination = solution.getEntityList().getLast();
    var sourceFirst = List.copyOf(source.getFirstValueList());
    var sourceSecond = List.copyOf(source.getSecondValueList());
    var destinationFirst = List.copyOf(destination.getFirstValueList());
    var destinationSecond = List.copyOf(destination.getSecondValueList());
    var ruinedValue = targetDescriptor.getValue(source).getFirst();
    var retainedValue = targetDescriptor.getValue(destination).getFirst();
    var scoreDirectorFactory =
        new EasyScoreDirectorFactory<TestdataMultipleListSolution, SimpleScore>(
            solutionDescriptor,
            s -> SimpleScore.of(-targetDescriptor.getListSize(s.getEntityList().getFirst())),
            EnvironmentMode.PHASE_ASSERT);
    try (var scoreDirector = scoreDirectorFactory.buildScoreDirector()) {
      scoreDirector.setWorkingSolution(solution);
      var initialScore = scoreDirector.calculateScore();
      var policy =
          new HeuristicConfigPolicy.Builder<TestdataMultipleListSolution>()
              .withEnvironmentMode(EnvironmentMode.PHASE_ASSERT)
              .withSolutionDescriptor(solutionDescriptor)
              .withMoveThreadCount(enclosingMoveThreads ? 2 : null)
              .withInitializingScoreTrend(
                  InitializingScoreTrend.buildUniformTrend(InitializingScoreTrendLevel.ANY, 1))
              .build();
      var placerConfig =
          DefaultConstructionHeuristicPhaseFactory.buildListVariableQueuedValuePlacerConfig(
              policy, targetDescriptor);
      var builder =
          RuinRecreateConstructionHeuristicPhaseBuilder.create(
              policy,
              new ConstructionHeuristicPhaseConfig()
                  .withEntityPlacerConfig(placerConfig)
                  .withMoveThreadCount("NONE"),
              targetDescriptor);
      var solverScope = new SolverScope<TestdataMultipleListSolution>();
      solverScope.setScoreDirector(scoreDirector);
      var move =
          new SelectorBasedListRuinRecreateMove<>(
              targetDescriptor,
              builder,
              solverScope,
              List.of(ruinedValue),
              new LinkedHashSet<>(List.of(source)),
              0L);

      assertThat(move.isMoveDoable(scoreDirector)).isTrue();
      scoreDirector
          .getMoveDirector()
          .executeTemporary(
              move,
              score -> {
                assertThat(targetDescriptor.getValue(source)).isEmpty();
                assertThat(targetDescriptor.getValue(destination))
                    .containsExactlyInAnyOrder(ruinedValue, retainedValue);
                if (variableName.equals("firstValueList")) {
                  assertThat(source.getSecondValueList()).containsExactlyElementsOf(sourceSecond);
                  assertThat(destination.getSecondValueList())
                      .containsExactlyElementsOf(destinationSecond);
                } else {
                  assertThat(source.getFirstValueList()).containsExactlyElementsOf(sourceFirst);
                  assertThat(destination.getFirstValueList())
                      .containsExactlyElementsOf(destinationFirst);
                }
                assertShadowsAndBasicVariables(solution);
                return score;
              });

      assertThat(source.getFirstValueList()).containsExactlyElementsOf(sourceFirst);
      assertThat(source.getSecondValueList()).containsExactlyElementsOf(sourceSecond);
      assertThat(destination.getFirstValueList()).containsExactlyElementsOf(destinationFirst);
      assertThat(destination.getSecondValueList()).containsExactlyElementsOf(destinationSecond);
      assertShadowsAndBasicVariables(solution);
      assertThat(scoreDirector.calculateScore()).isEqualTo(initialScore);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "firstValueList, false",
    "secondValueList, false",
    "firstValueList, true",
    "secondValueList, true"
  })
  @SuppressWarnings("unchecked")
  void rebaseResolvesOriginalDeclarationInReceivingDescriptor(
      String variableName, boolean selectorBased) {
    var sourceDescriptor = TestdataMultipleListSolution.buildSolutionDescriptor();
    var destinationDescriptor = TestdataMultipleListSolution.buildSolutionDescriptor();
    var sourceVariable =
        sourceDescriptor.resolveListVariableDescriptor(
            TestdataMultipleListEntity.class, variableName);
    var destinationVariable =
        destinationDescriptor.resolveListVariableDescriptor(
            TestdataMultipleListEntity.class, variableName);
    assertThat(destinationVariable).isNotSameAs(sourceVariable);
    var sourceSolution = TestdataMultipleListSolution.generateInitializedSolution(1, 1, 1);
    var destinationSolution = TestdataMultipleListSolution.generateInitializedSolution(1, 1, 1);
    var sourceEntity = sourceSolution.getEntityList().getFirst();
    var destinationEntity = destinationSolution.getEntityList().getFirst();
    var sourceValue = sourceVariable.getValue(sourceEntity).getFirst();
    var destinationValue = destinationVariable.getValue(destinationEntity).getFirst();
    var scoreDirector =
        mockRebasingScoreDirector(
            destinationDescriptor,
            new Object[][] {{sourceEntity, destinationEntity}, {sourceValue, destinationValue}});
    RuinRecreateConstructionHeuristicPhaseBuilder<TestdataMultipleListSolution> builder =
        mock(RuinRecreateConstructionHeuristicPhaseBuilder.class);
    var solverScope = new SolverScope<TestdataMultipleListSolution>();
    var move =
        selectorBased
            ? new SelectorBasedListRuinRecreateMove<>(
                sourceVariable,
                builder,
                solverScope,
                List.of(sourceValue),
                new LinkedHashSet<>(List.of(sourceEntity)))
            : new ListRuinRecreateMove<>(
                sourceVariable,
                builder,
                solverScope,
                List.of(sourceValue),
                new LinkedHashSet<>(List.of(sourceEntity)));

    var rebased = move.rebase(scoreDirector);

    assertThat(rebased).isExactlyInstanceOf(move.getClass());
    assertThat(rebased)
        .isEqualTo(
            new ListRuinRecreateMove<>(
                destinationVariable,
                builder,
                solverScope,
                List.of(destinationValue),
                new LinkedHashSet<>(List.of(destinationEntity))));
    assertThat(rebased.getPlanningEntities()).containsExactly(destinationEntity);
    assertThat(rebased.getPlanningValues()).containsExactly(destinationValue);
  }

  private static void assertShadowsAndBasicVariables(TestdataMultipleListSolution solution) {
    for (var entity : solution.getEntityList()) {
      assertThat(entity.getBasicValue()).isZero();
      var firstList = entity.getFirstValueList();
      for (int i = 0; i < firstList.size(); i++) {
        var value = firstList.get(i);
        assertThat(value.getEntity()).isSameAs(entity);
        assertThat(value.getIndex()).isEqualTo(i);
        assertThat(value.getPrevious()).isSameAs(i == 0 ? null : firstList.get(i - 1));
        assertThat(value.getNext())
            .isSameAs(i + 1 == firstList.size() ? null : firstList.get(i + 1));
      }
      var secondList = entity.getSecondValueList();
      for (int i = 0; i < secondList.size(); i++) {
        var value = secondList.get(i);
        assertThat(value.getQuantity()).isZero();
        assertThat(value.getEntity()).isSameAs(entity);
        assertThat(value.getIndex()).isEqualTo(i);
        assertThat(value.getPrevious()).isSameAs(i == 0 ? null : secondList.get(i - 1));
        assertThat(value.getNext())
            .isSameAs(i + 1 == secondList.size() ? null : secondList.get(i + 1));
      }
    }
  }
}
