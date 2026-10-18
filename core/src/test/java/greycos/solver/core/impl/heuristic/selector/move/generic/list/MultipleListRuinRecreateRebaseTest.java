package greycos.solver.core.impl.heuristic.selector.move.generic.list;

import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.assertState;
import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.config;
import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.recompute;
import static greycos.solver.core.testutil.MultipleListLifecycleTestUtils.snapshot;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.score.trend.InitializingScoreTrendLevel;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.constructionheuristic.DefaultConstructionHeuristicPhaseFactory;
import greycos.solver.core.impl.constructionheuristic.placer.QueuedValuePlacer;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.move.generic.RuinRecreateConstructionHeuristicPhaseBuilder;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ruin.ListRuinRecreateMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ruin.SelectorBasedListRuinRecreateMove;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.trend.InitializingScoreTrend;
import greycos.solver.core.impl.solver.DefaultSolverFactory;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(30)
class MultipleListRuinRecreateRebaseTest {

  static Stream<Arguments> configurations() {
    return Stream.of("firstValueList", "secondValueList")
        .flatMap(
            variable ->
                Stream.of(false, true)
                    .flatMap(
                        selectorBased ->
                            Stream.of(false, true)
                                .flatMap(
                                    independentModel ->
                                        Stream.of(false, true)
                                            .map(
                                                threaded ->
                                                    Arguments.of(
                                                        variable,
                                                        selectorBased,
                                                        independentModel,
                                                        threaded)))));
  }

  @ParameterizedTest
  @MethodSource("configurations")
  void executeRebasedRepairAndUndoWithRealSelectors(
      String variableName,
      boolean selectorBased,
      boolean independentModel,
      boolean enclosingMoveThreads)
      throws Exception {
    var sourceFactory =
        new DefaultSolverFactory<TestdataMultipleListSolution>(config())
            .<SimpleScore>getScoreDirectorFactory();
    var destinationFactory =
        independentModel
            ? new DefaultSolverFactory<TestdataMultipleListSolution>(config())
                .<SimpleScore>getScoreDirectorFactory()
            : sourceFactory;
    try (var source = sourceFactory.createScoreDirectorBuilder().withLookUpEnabled(true).build();
        var destination =
            destinationFactory.createScoreDirectorBuilder().withLookUpEnabled(true).build()) {
      var sourceVariable = variable(source, variableName);
      var destinationVariable = variable(destination, variableName);
      assertThat(sourceVariable == destinationVariable).isEqualTo(!independentModel);
      source.setWorkingSolution(problem(sourceVariable));
      destination.setWorkingSolution(problem(destinationVariable));
      var sourceSolution = source.getWorkingSolution();
      var destinationSolution = destination.getWorkingSolution();
      var policy =
          new HeuristicConfigPolicy.Builder<TestdataMultipleListSolution>()
              .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
              .withSolutionDescriptor(source.getSolutionDescriptor())
              .withMoveThreadCount(enclosingMoveThreads ? 2 : null)
              .withInitializingScoreTrend(
                  InitializingScoreTrend.buildUniformTrend(InitializingScoreTrendLevel.ANY, 1))
              .build();
      var builder =
          RuinRecreateConstructionHeuristicPhaseBuilder.create(
              policy,
              new ConstructionHeuristicPhaseConfig()
                  .withEntityPlacerConfig(
                      DefaultConstructionHeuristicPhaseFactory
                          .buildListVariableQueuedValuePlacerConfig(policy, sourceVariable))
                  .withMoveThreadCount("NONE"),
              sourceVariable);
      var destinationBuilder = builder.ensureThreadSafe(destination);
      assertThat(
              ((QueuedValuePlacer<?>) destinationBuilder.getEntityPlacer()).getVariableDescriptor())
          .isSameAs(destinationVariable);
      assertThat(destinationBuilder.build().getEnvironmentMode())
          .isEqualTo(EnvironmentMode.FULL_ASSERT);
      if (independentModel || enclosingMoveThreads) {
        assertThat(destinationBuilder).isNotSameAs(builder);
        assertThat(destinationBuilder.getEntityPlacer()).isNotSameAs(builder.getEntityPlacer());
      } else {
        assertThat(destinationBuilder).isSameAs(builder);
      }
      var sourceOwner = sourceSolution.getEntityList().getFirst();
      var ruinedValues = List.copyOf(sourceVariable.getValue(sourceOwner).subList(0, 2));
      var scope = new SolverScope<TestdataMultipleListSolution>();
      scope.setScoreDirector(source);
      ListRuinRecreateMove<TestdataMultipleListSolution> move =
          selectorBased
              ? new SelectorBasedListRuinRecreateMove<>(
                  sourceVariable,
                  builder,
                  scope,
                  ruinedValues,
                  new LinkedHashSet<>(List.of(sourceOwner)),
                  37L)
              : new ListRuinRecreateMove<>(
                  sourceVariable,
                  builder,
                  scope,
                  ruinedValues,
                  new LinkedHashSet<>(List.of(sourceOwner)),
                  37L);
      var rebased = move.rebase(destination);
      assertThat(rebased).isExactlyInstanceOf(move.getClass());
      assertThat(rebased.getPlanningEntities())
          .singleElement()
          .isSameAs(destinationSolution.getEntityList().getFirst());
      var expectedDestinationValues =
          destinationVariable.getValue(destinationSolution.getEntityList().getFirst());
      var rebasedValues = rebased.getPlanningValues().iterator();
      assertThat(rebasedValues.next()).isSameAs(expectedDestinationValues.get(0));
      assertThat(rebasedValues.next()).isSameAs(expectedDestinationValues.get(1));

      // Executing the original builder first populates its mutable filter state with source
      // objects.
      var expected = execute(source, move, variableName);
      for (int repetition = 0; repetition < 3; repetition++) {
        if (enclosingMoveThreads) {
          // Both moves share the original builder; even nested NONE must isolate their selectors.
          try (var executor = Executors.newFixedThreadPool(2)) {
            var started = new CountDownLatch(2);
            Callable<String> sourceTask =
                () -> {
                  started.countDown();
                  assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
                  return execute(source, move, variableName);
                };
            Callable<String> destinationTask =
                () -> {
                  started.countDown();
                  assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
                  return execute(destination, rebased, variableName);
                };
            var sourceResult = executor.submit(sourceTask);
            var destinationResult = executor.submit(destinationTask);
            assertThat(sourceResult.get(10, TimeUnit.SECONDS)).isEqualTo(expected);
            assertThat(destinationResult.get(10, TimeUnit.SECONDS)).isEqualTo(expected);
          }
        } else {
          assertThat(execute(destination, rebased, variableName)).isEqualTo(expected);
          assertThat(execute(source, move, variableName)).isEqualTo(expected);
        }
      }
    }
  }

  private static TestdataMultipleListSolution problem(
      ListVariableDescriptor<TestdataMultipleListSolution> target) {
    var solution = TestdataMultipleListSolution.generateInitializedSolution(4, 4, 2);
    var first = target.getValue(solution.getEntityList().getFirst());
    var last = target.getValue(solution.getEntityList().getLast());
    first.addAll(last);
    last.clear();
    return solution;
  }

  private static String execute(
      InnerScoreDirector<TestdataMultipleListSolution, SimpleScore> scoreDirector,
      Move<TestdataMultipleListSolution> move,
      String variableName) {
    var solution = scoreDirector.getWorkingSolution();
    var initialState = snapshot(solution);
    var initialScore = scoreDirector.calculateScore();
    var firstState = scoreDirector.getListVariableState(variable(scoreDirector, "firstValueList"));
    var secondState =
        scoreDirector.getListVariableState(variable(scoreDirector, "secondValueList"));
    var otherVariable =
        variable(
            scoreDirector,
            variableName.equals("firstValueList") ? "secondValueList" : "firstValueList");
    var untouchedLists =
        solution.getEntityList().stream()
            .map(owner -> List.copyOf(otherVariable.getValue(owner)))
            .toList();
    var repaired = new AtomicReference<String>();
    var repairedScore =
        scoreDirector.executeTemporaryMove(
            move,
            ignored -> {
              assertWorkingState(scoreDirector);
              for (int i = 0; i < solution.getEntityList().size(); i++) {
                assertThat(otherVariable.getValue(solution.getEntityList().get(i)))
                    .containsExactlyElementsOf(untouchedLists.get(i));
              }
              repaired.set(snapshot(solution));
              assertThat(repaired.get()).isNotEqualTo(initialState);
            },
            true);
    assertThat(repairedScore.unassignedCount()).isZero();
    assertThat(snapshot(solution)).isEqualTo(initialState);
    assertWorkingState(scoreDirector);
    assertThat(scoreDirector.calculateScore()).isEqualTo(initialScore);
    assertThat(scoreDirector.getListVariableState(variable(scoreDirector, "firstValueList")))
        .isSameAs(firstState);
    assertThat(scoreDirector.getListVariableState(variable(scoreDirector, "secondValueList")))
        .isSameAs(secondState);
    return repaired.get() + ":" + repairedScore;
  }

  private static void assertWorkingState(
      InnerScoreDirector<TestdataMultipleListSolution, SimpleScore> scoreDirector) {
    var solution = scoreDirector.getWorkingSolution();
    assertState(solution);
    assertThat(scoreDirector.calculateScore().raw()).isEqualTo(recompute(solution));
    for (var variableName : List.of("firstValueList", "secondValueList")) {
      var descriptor = variable(scoreDirector, variableName);
      var state = scoreDirector.getListVariableState(descriptor);
      assertThat(state.getSourceVariableDescriptor()).isSameAs(descriptor);
      assertThat(state.getUnassignedCount()).isZero();
      for (var owner : solution.getEntityList()) {
        var values = descriptor.getValue(owner);
        for (int i = 0; i < values.size(); i++) {
          var value = values.get(i);
          assertThat(scoreDirector.<Object>lookUpWorkingObject(value)).isSameAs(value);
          var position = state.getElementPosition(value).ensureAssigned();
          assertThat(position.<Object>entity()).isSameAs(owner);
          assertThat(position.index()).isEqualTo(i);
        }
      }
    }
  }

  private static ListVariableDescriptor<TestdataMultipleListSolution> variable(
      InnerScoreDirector<TestdataMultipleListSolution, SimpleScore> scoreDirector,
      String variableName) {
    return scoreDirector
        .getSolutionDescriptor()
        .resolveListVariableDescriptor(TestdataMultipleListEntity.class, variableName);
  }
}
