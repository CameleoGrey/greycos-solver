package greycos.solver.core.impl.constructionheuristic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicType;
import greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicForagerConfig;
import greycos.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicPickEarlyType;
import greycos.solver.core.config.constructionheuristic.placer.PooledEntityPlacerConfig;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.RuinRecreateMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.constructionheuristic.decider.forager.DefaultConstructionHeuristicForager;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicMoveScope;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.common.TestdataSortableValue;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListEasyScoreCalculator;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListEntity;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListSolution;
import greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListValue;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarEntity;
import greycos.solver.core.testcotwin.multivar.TestdataMultiVarSolution;
import greycos.solver.core.testcotwin.pinned.unassignedvar.TestdataPinnedAllowsUnassignedEntity;
import greycos.solver.core.testcotwin.pinned.unassignedvar.TestdataPinnedAllowsUnassignedSolution;
import greycos.solver.core.testcotwin.score.TestdataHardSoftScoreSolution;
import greycos.solver.core.testcotwin.sort.comparator.TestdataComparatorSortableEntity;
import greycos.solver.core.testcotwin.sort.comparator.TestdataComparatorSortableSolution;
import greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedEntity;
import greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedSolution;
import greycos.solver.core.testcotwin.valuerange.sort.comparator.TestdataComparatorSortableEntityProvidingEntity;
import greycos.solver.core.testcotwin.valuerange.sort.comparator.TestdataComparatorSortableEntityProvidingSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SerialConstructionRegressionTest {
  @Test
  void queuedValueNullableOnlyNullMustFinishNaturally() {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataAllowsUnassignedSolution.class)
            .withEntityClasses(TestdataAllowsUnassignedEntity.class)
            .withEasyScoreCalculatorClass(PenalizeAssignment.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount("NONE")
            .withPhases(
                new ConstructionHeuristicPhaseConfig()
                    .withConstructionHeuristicType(
                        ConstructionHeuristicType.ALLOCATE_TO_VALUE_FROM_QUEUE)
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(8)));
    var solver =
        (DefaultSolver<TestdataAllowsUnassignedSolution>)
            SolverFactory.<TestdataAllowsUnassignedSolution>create(config).buildSolver();
    var steps = new ArrayList<String>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataAllowsUnassignedSolution> scope) {
            steps.add(scope.toString());
          }
        });
    var input = new TestdataAllowsUnassignedSolution();
    input.setEntityList(List.of(new TestdataAllowsUnassignedEntity("e1")));
    input.setValueList(List.of());
    var output = solver.solve(input);
    assertThat(steps).hasSize(1);
    assertThat(output.getScore()).isEqualTo(SimpleScore.ZERO);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void listOptionalValuesMustEachBeConsideredOnce(int unassignedCount) {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataAllowsUnassignedValuesListSolution.class)
            .withEntityClasses(
                TestdataAllowsUnassignedValuesListEntity.class,
                TestdataAllowsUnassignedValuesListValue.class)
            .withEasyScoreCalculatorClass(
                TestdataAllowsUnassignedValuesListEasyScoreCalculator.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount("NONE")
            .withPhases(new ConstructionHeuristicPhaseConfig());
    var solver =
        (DefaultSolver<TestdataAllowsUnassignedValuesListSolution>)
            SolverFactory.<TestdataAllowsUnassignedValuesListSolution>create(config).buildSolver();
    var steps = new ArrayList<String>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(
              AbstractStepScope<TestdataAllowsUnassignedValuesListSolution> scope) {
            steps.add(scope.toString());
          }
        });
    var input = new TestdataAllowsUnassignedValuesListSolution();
    var assigned = new TestdataAllowsUnassignedValuesListValue("assigned");
    input.setEntityList(List.of(new TestdataAllowsUnassignedValuesListEntity("e1", assigned)));
    var values = new ArrayList<TestdataAllowsUnassignedValuesListValue>();
    values.add(assigned);
    for (var i = 0; i < unassignedCount; i++) {
      values.add(new TestdataAllowsUnassignedValuesListValue("unassigned-" + i));
    }
    input.setValueList(values);
    SolutionManager.updateShadowVariables(input);
    var output = solver.solve(input);
    assertThat(steps).hasSize(unassignedCount);
    assertThat(output.getEntityList().getFirst().getValueList())
        .containsExactly(output.getValueList().getFirst());
    assertThat(output.getScore())
        .isEqualTo(
            new TestdataAllowsUnassignedValuesListEasyScoreCalculator().calculateScore(output));
  }

  @Test
  void automaticNearbyMustPreserveExplicitShuffledMoveOrder() {
    var without = shuffledSolve(false);
    var with = shuffledSolve(true);
    assertThat(with).isEqualTo(without);
  }

  private String shuffledSolve(boolean nearby) {
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withNearbySelectionAutoConfigurationEnabled(nearby)
            .withNearbySelectionSize(100)
            .withEntityPlacerConfig(
                new PooledEntityPlacerConfig()
                    .withMoveSelectorConfig(
                        new ChangeMoveSelectorConfig()
                            .withSelectionOrder(SelectionOrder.SHUFFLED)
                            .withCacheType(SelectionCacheType.PHASE)))
            .withForagerConfig(
                new ConstructionHeuristicForagerConfig()
                    .withPickEarlyType(
                        ConstructionHeuristicPickEarlyType.FIRST_NON_DETERIORATING_SCORE))
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(1));
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(ConstantSimple.class)
            .withNearbyDistanceMeterClass(ConstantDistance.class)
            .withRandomSeed(0L)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount("NONE")
            .withPhases(phase);
    var result =
        SolverFactory.<TestdataSolution>create(config)
            .buildSolver()
            .solve(TestdataSolution.generateUninitializedSolution(6, 1));
    return result.getEntityList().getFirst().getValue().getCode();
  }

  @Test
  void hardDifferenceMustNotOverflowWhenChoosingFirstNonDeterioratingMove() {
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withForagerConfig(
                new ConstructionHeuristicForagerConfig()
                    .withPickEarlyType(
                        ConstructionHeuristicPickEarlyType
                            .FIRST_FEASIBLE_SCORE_OR_NON_DETERIORATING_HARD));
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataHardSoftScoreSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(ExtremeHard.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount("NONE")
            .withPhases(phase);
    var input = TestdataHardSoftScoreSolution.generateSolution(2, 1);
    input.getEntityList().getFirst().setValue(null);
    var output =
        SolverFactory.<TestdataHardSoftScoreSolution>create(config).buildSolver().solve(input);
    assertThat(output.getEntityList().getFirst().getValue().getCode()).endsWith("1");
    assertThat(output.getScore()).isEqualTo(HardSoftScore.ofHard(1));
  }

  @Test
  void queuedValueRepeatsPassesWithAssignmentProgress() {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataAllowsUnassignedSolution.class)
            .withEntityClasses(TestdataAllowsUnassignedEntity.class)
            .withEasyScoreCalculatorClass(RewardAssignment.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount("NONE")
            .withPhases(
                new ConstructionHeuristicPhaseConfig()
                    .withConstructionHeuristicType(
                        ConstructionHeuristicType.ALLOCATE_TO_VALUE_FROM_QUEUE));
    var solver = SolverFactory.<TestdataAllowsUnassignedSolution>create(config).buildSolver();
    for (int iteration = 0; iteration < 2; iteration++) {
      var input = new TestdataAllowsUnassignedSolution();
      input.setValueList(List.of(new TestdataValue("shared")));
      input.setEntityList(
          List.of(
              new TestdataAllowsUnassignedEntity("a"),
              new TestdataAllowsUnassignedEntity("b"),
              new TestdataAllowsUnassignedEntity("c")));
      var result = solver.solve(input);
      assertThat(result.getEntityList())
          .allSatisfy(
              entity -> assertThat(entity.getValue()).isSameAs(result.getValueList().getFirst()));
      assertThat(result.getScore()).isEqualTo(SimpleScore.of(3));
      assertThat(result.getScore()).isEqualTo(new RewardAssignment().calculateScore(result));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void invalidRuinVariableIsRejectedEvenOnSingleVariableEntity(boolean multiVariable) {
    var config =
        new SolverConfig()
            .withSolutionClass(
                multiVariable ? TestdataMultiVarSolution.class : TestdataSolution.class)
            .withEntityClasses(multiVariable ? TestdataMultiVarEntity.class : TestdataEntity.class)
            .withEasyScoreCalculatorClass(
                multiVariable ? RewardOptional.class : ConstantSimple.class)
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withMoveSelectorConfig(
                        new RuinRecreateMoveSelectorConfig().withVariableName("missing")));
    assertThatThrownBy(() -> SolverFactory.create(config).buildSolver())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("no variable named missing");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void shuffledFullSequenceAndLimitsRemainAuthoritative(int shape) {
    assertThat(shuffledSequence(true, shape))
        .containsExactlyElementsOf(shuffledSequence(false, shape));
  }

  private List<String> shuffledSequence(boolean nearby, int shape) {
    MoveSelectorConfig<?> moves =
        new ChangeMoveSelectorConfig()
            .withSelectionOrder(SelectionOrder.SHUFFLED)
            .withCacheType(SelectionCacheType.PHASE)
            .withSelectedCountLimit(3L);
    if (shape == 1) {
      moves = new UnionMoveSelectorConfig(List.of(moves));
    } else if (shape == 2) {
      moves =
          new UnionMoveSelectorConfig(List.of(new ChangeMoveSelectorConfig()))
              .withSelectionOrder(SelectionOrder.SHUFFLED)
              .withCacheType(SelectionCacheType.PHASE)
              .withSelectedCountLimit(3L);
    }
    var phase =
        new ConstructionHeuristicPhaseConfig()
            .withNearbySelectionAutoConfigurationEnabled(nearby)
            .withNearbySelectionSize(1)
            .withEntityPlacerConfig(new PooledEntityPlacerConfig().withMoveSelectorConfig(moves))
            .withForagerConfig(
                new ConstructionHeuristicForagerConfig().withForagerClass(RecordingForager.class))
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(1));
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withEasyScoreCalculatorClass(ConstantSimple.class)
            .withNearbyDistanceMeterClass(ConstantDistance.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount("NONE")
            .withRandomSeed(0L)
            .withPhases(phase);
    RECORDINGS.set(new ArrayList<>());
    try {
      SolverFactory.<TestdataSolution>create(config)
          .buildSolver()
          .solve(TestdataSolution.generateUninitializedSolution(6, 1));
      assertThat(RECORDINGS.get()).hasSize(3);
      return List.copyOf(RECORDINGS.get());
    } finally {
      RECORDINGS.remove();
    }
  }

  private static final ThreadLocal<List<String>> RECORDINGS = new ThreadLocal<>();

  public static final class RecordingForager
      extends DefaultConstructionHeuristicForager<TestdataSolution> {
    public RecordingForager() {
      super(ConstructionHeuristicPickEarlyType.NEVER);
    }

    @Override
    public void addMove(ConstructionHeuristicMoveScope<TestdataSolution> scope) {
      RECORDINGS
          .get()
          .add(((TestdataValue) ((ChangeMove<?>) scope.getMove()).getToPlanningValue()).getCode());
      super.addMove(scope);
    }
  }

  @Test
  void randomPoolFinishesWithoutWaitingForSpentTermination(@TempDir Path directory)
      throws Exception {
    var output = directory.resolve("random-pool.log");
    var process =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx256m",
                "-cp",
                System.getProperty("java.class.path"),
                PooledCompletionProbe.class.getName())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start();
    try {
      assertThat(process.waitFor(20, TimeUnit.SECONDS))
          .as("bounded random-pool completion")
          .isTrue();
      assertThat(process.exitValue()).withFailMessage(Files.readString(output)).isZero();
      assertThat(Files.readString(output)).contains("POOL_COMPLETED");
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        process.waitFor();
      }
    }
  }

  public static class PooledCompletionProbe {
    public static void main(String[] args) {
      var config =
          new SolverConfig()
              .withSolutionClass(TestdataSolution.class)
              .withEntityClasses(TestdataEntity.class)
              .withEasyScoreCalculatorClass(ConstantSimple.class)
              .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
              .withMoveThreadCount("NONE")
              .withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(5)))
              .withPhases(
                  new ConstructionHeuristicPhaseConfig()
                      .withEntityPlacerConfig(
                          new PooledEntityPlacerConfig()
                              .withMoveSelectorConfig(
                                  new ChangeMoveSelectorConfig()
                                      .withSelectionOrder(SelectionOrder.RANDOM)))
                      .withForagerConfig(
                          new ConstructionHeuristicForagerConfig()
                              .withPickEarlyType(
                                  ConstructionHeuristicPickEarlyType
                                      .FIRST_NON_DETERIORATING_SCORE)));
      var solver =
          (DefaultSolver<TestdataSolution>)
              SolverFactory.<TestdataSolution>create(config).buildSolver();
      var phase =
          (DefaultConstructionHeuristicPhase<TestdataSolution>) solver.getPhaseList().getFirst();
      var result = solver.solve(TestdataSolution.generateUninitializedSolution(1, 1));
      if (result.getEntityList().getFirst().getValue() == null
          || phase.getTerminationStatus().early()) {
        throw new AssertionError("Random pool did not complete naturally.");
      }
      // A pinned nullable null must not keep the reinitialization proof inconclusive.
      var pinnedConfig =
          config
              .copyConfig()
              .withSolutionClass(TestdataPinnedAllowsUnassignedSolution.class)
              .withEntityClasses(TestdataPinnedAllowsUnassignedEntity.class)
              .withEasyScoreCalculatorClass(PinnedConstantSimple.class)
              .withTerminationConfig(new TerminationConfig());
      var pinnedInput = TestdataPinnedAllowsUnassignedSolution.generateSolution(1, 2);
      pinnedInput.getEntityList().getFirst().setValue(null);
      pinnedInput.getEntityList().getFirst().setPinned(true);
      var pinnedSolver =
          (DefaultSolver<TestdataPinnedAllowsUnassignedSolution>)
              SolverFactory.<TestdataPinnedAllowsUnassignedSolution>create(pinnedConfig)
                  .buildSolver();
      var pinnedResult = pinnedSolver.solve(pinnedInput);
      if (pinnedResult.getEntityList().getFirst().getValue() != null
          || pinnedResult.getEntityList().get(1).getValue() == null) {
        throw new AssertionError("Pinned null or initialized movable entity changed.");
      }
      var pinnedPhase =
          (DefaultConstructionHeuristicPhase<TestdataPinnedAllowsUnassignedSolution>)
              pinnedSolver.getPhaseList().getFirst();
      if (pinnedPhase.getTerminationStatus().early()) {
        throw new AssertionError("Pinned-null random pool did not complete naturally.");
      }
      System.out.println("POOL_COMPLETED");
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void ruinRecreatePreservesDefaultStrengthOrderingForEitherRangeLocation(boolean entityRange) {
    var config =
        new SolverConfig()
            .withSolutionClass(
                entityRange
                    ? TestdataComparatorSortableEntityProvidingSolution.class
                    : TestdataComparatorSortableSolution.class)
            .withEntityClasses(
                entityRange
                    ? TestdataComparatorSortableEntityProvidingEntity.class
                    : TestdataComparatorSortableEntity.class)
            .withEasyScoreCalculatorClass(PreferAnyWeakerValue.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount("NONE")
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withMoveSelectorConfig(
                        new RuinRecreateMoveSelectorConfig()
                            .withVariableName("value")
                            .withMinimumRuinedCount(1)
                            .withMaximumRuinedCount(1))
                    .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1))
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(1)));
    if (entityRange) {
      var input = TestdataComparatorSortableEntityProvidingSolution.generateSolution(3, 1, false);
      var entity = input.getEntityList().getFirst();
      entity.setValueRange(new ArrayList<>(entity.getValueRange().reversed()));
      entity.setValue(entity.getValueRange().getFirst());
      var result =
          SolverFactory.<TestdataComparatorSortableEntityProvidingSolution>create(config)
              .buildSolver()
              .solve(input);
      assertThat(result.getEntityList().getFirst().getValue().getComparatorValue()).isZero();
      assertThat(result.getScore()).isEqualTo(new PreferAnyWeakerValue().calculateScore(result));
    } else {
      var input = TestdataComparatorSortableSolution.generateSolution(3, 1, false);
      input.setValueList(new ArrayList<>(input.getValueList().reversed()));
      input.getEntityList().getFirst().setValue(input.getValueList().getFirst());
      var result =
          SolverFactory.<TestdataComparatorSortableSolution>create(config)
              .buildSolver()
              .solve(input);
      assertThat(result.getEntityList().getFirst().getValue().getComparatorValue()).isZero();
      assertThat(result.getScore()).isEqualTo(new PreferAnyWeakerValue().calculateScore(result));
    }
  }

  public static class PreferAnyWeakerValue implements EasyScoreCalculator<Object, HardSoftScore> {
    @Override
    public HardSoftScore calculateScore(Object solution) {
      TestdataSortableValue value =
          solution instanceof TestdataComparatorSortableSolution global
              ? global.getEntityList().getFirst().getValue()
              : ((TestdataComparatorSortableEntityProvidingSolution) solution)
                  .getEntityList()
                  .getFirst()
                  .getValue();
      return HardSoftScore.ofSoft(value != null && value.getComparatorValue() == 2 ? -1 : 0);
    }
  }

  public static class PinnedConstantSimple
      implements EasyScoreCalculator<TestdataPinnedAllowsUnassignedSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataPinnedAllowsUnassignedSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  public static class RewardAssignment
      implements EasyScoreCalculator<TestdataAllowsUnassignedSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataAllowsUnassignedSolution solution) {
      return SimpleScore.of(
          solution.getEntityList().stream().filter(entity -> entity.getValue() != null).count());
    }
  }

  public static class ConstantSimple implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return SimpleScore.ZERO;
    }
  }

  public static class ConstantDistance
      implements NearbyDistanceMeter<TestdataEntity, TestdataValue> {
    @Override
    public double getNearbyDistance(TestdataEntity entity, TestdataValue value) {
      return 0;
    }
  }

  public static class ExtremeHard
      implements EasyScoreCalculator<TestdataHardSoftScoreSolution, HardSoftScore> {
    @Override
    public HardSoftScore calculateScore(TestdataHardSoftScoreSolution solution) {
      var value = solution.getEntityList().getFirst().getValue();
      return HardSoftScore.ofHard(
          value != null && value.getCode().endsWith("0") ? Long.MIN_VALUE : 1L);
    }
  }

  @Test
  void ruinRecreateMustNotMutateAnotherNullableVariableWhileEvaluatingPrimary() {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataMultiVarSolution.class)
            .withEntityClasses(TestdataMultiVarEntity.class)
            .withEasyScoreCalculatorClass(RewardOptional.class)
            .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
            .withMoveThreadCount("NONE")
            .withPhases(
                new LocalSearchPhaseConfig()
                    .withMoveSelectorConfig(
                        new RuinRecreateMoveSelectorConfig()
                            .withVariableName("primaryValue")
                            .withMinimumRuinedCount(1)
                            .withMaximumRuinedCount(1))
                    .withForagerConfig(new LocalSearchForagerConfig().withAcceptedCountLimit(1))
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(1)));
    var input = TestdataMultiVarSolution.generateSolution(1, 2, 1);
    input.getMultiVarEntityList().getFirst().setTertiaryValueAllowedUnassigned(null);
    var result = SolverFactory.<TestdataMultiVarSolution>create(config).buildSolver().solve(input);
    assertThat(result.getMultiVarEntityList().getFirst().getTertiaryValueAllowedUnassigned())
        .isNull();
    assertThat(result.getScore()).isEqualTo(new RewardOptional().calculateScore(result));
    assertThat(result.getMultiVarEntityList().getFirst().getPrimaryValue())
        .isSameAs(result.getValueList().get(1));
    assertThat(result.getMultiVarEntityList().getFirst().getSecondaryValue())
        .isSameAs(result.getValueList().get(1));
  }

  public static class RewardOptional
      implements EasyScoreCalculator<TestdataMultiVarSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataMultiVarSolution solution) {
      var entity = solution.getMultiVarEntityList().getFirst();
      return SimpleScore.of(
          (entity.getTertiaryValueAllowedUnassigned() == null ? 0 : 10)
              + (entity.getPrimaryValue() == solution.getValueList().get(1) ? 1 : 0));
    }
  }

  public static class PenalizeAssignment
      implements EasyScoreCalculator<TestdataAllowsUnassignedSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataAllowsUnassignedSolution solution) {
      return SimpleScore.of(
          -(int) solution.getEntityList().stream().filter(e -> e.getValue() != null).count());
    }
  }
}
