package greycos.solver.core.impl.constructionheuristic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.constructionheuristic.placer.QueuedEntityPlacerConfig;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.addvar.TestdataAddVarBaseEntity;
import greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.addvar.TestdataAddVarChildEntity;
import greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.addvar.TestdataAddVarSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Execution(ExecutionMode.SAME_THREAD)
class PolymorphicNearbyConstructionHeuristicTest {

  private static final AtomicLong METER_CALLS = new AtomicLong();

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void interfaceMeterRanksSubclassValuesAndOptOutPreservesExhaustiveConstruction(
      String moveThreadCount) {
    for (var enabled : List.of(true, false)) {
      var config =
          basicConfig(moveThreadCount)
              .withPhases(
                  new ConstructionHeuristicPhaseConfig()
                      .withNearbySelectionAutoConfigurationEnabled(enabled));
      var problem = basicProblem();
      var outcome = solve(config, problem);

      assertThat(outcome.selectedMoves()).isEqualTo(enabled ? 40 : 100);
      assertThat(outcome.meterCalls()).isEqualTo(enabled ? 100 : 0);
      assertThat(outcome.solution().getEntityList().getFirst().getValue()).isNotNull();
      assertThat(outcome.solution().getScore()).isEqualTo(basicScore(outcome.solution()));
      assertThat(outcome.solution().getScore()).isEqualTo(SimpleScore.ZERO);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void unsupportedRuntimeValuesRemainReachableWithoutBeingPassedToTheInterfaceMeter(
      String moveThreadCount) {
    var problem = basicProblem();
    var values = new ArrayList<TestdataValue>();
    IntStream.range(1, 100).mapToObj(RankedValue::new).forEach(values::add);
    var unsupported = new TestdataValue("untyped");
    values.add(unsupported);
    problem.setValueList(values);

    var outcome =
        solve(
            basicConfig(moveThreadCount).withPhases(new ConstructionHeuristicPhaseConfig()),
            problem);

    assertThat(outcome.selectedMoves()).isEqualTo(40);
    assertThat(outcome.meterCalls()).isEqualTo(99);
    assertThat(outcome.solution().getEntityList().getFirst().getValue()).isSameAs(unsupported);
    assertThat(outcome.solution().getScore()).isEqualTo(basicScore(outcome.solution()));
    assertThat(outcome.solution().getScore()).isEqualTo(SimpleScore.ZERO);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NONE", "2"})
  void parentEntitySelectorAndChildDowncastSupplyTheConstructionProfile(String moveThreadCount) {
    var local =
        upcomingSearch(
            new ChangeMoveSelectorConfig()
                .withEntitySelectorConfig(
                    new EntitySelectorConfig(TestdataAddVarBaseEntity.class).withId("origin"))
                .withValueSelectorConfig(
                    new ValueSelectorConfig("value2")
                        .withDowncastEntityClass(TestdataAddVarChildEntity.class)
                        .withNearbySelectionConfig(inheritanceNearby())));
    for (var enabled : List.of(true, false)) {
      var construction =
          inheritanceConstruction().withNearbySelectionAutoConfigurationEnabled(enabled);
      var outcome =
          solve(
              inheritanceConfig(moveThreadCount).withPhases(construction, local),
              TestdataAddVarSolution.generateSolution(32, 1, false));

      assertThat(outcome.selectedMoves()).isEqualTo(enabled ? 40 : 1024);
      assertThat(outcome.meterCalls()).isEqualTo(enabled ? 32 : 0);
      var entity = outcome.solution().getEntityList().getFirst();
      assertThat(entity.getValue()).isNotNull();
      assertThat(entity.getValue2()).isNotNull();
      assertThat(outcome.solution().getScore()).isEqualTo(inheritanceScore(outcome.solution()));
      assertThat(outcome.solution().getScore()).isEqualTo(SimpleScore.ZERO);
    }
  }

  @Test
  void unknownDowncastStillFailsAtStartup() {
    assertInvalidDowncast(
        UnregisteredChild.class, IllegalArgumentException.class, "not a known planning entity");
  }

  @Test
  void unrelatedDowncastStillFailsAtStartup() {
    assertInvalidDowncast(TestdataEntity.class, IllegalStateException.class, "not a subclass");
  }

  private static void assertInvalidDowncast(
      Class<?> downcast, Class<? extends Throwable> errorType, String message) {
    var local =
        upcomingSearch(
            new ChangeMoveSelectorConfig()
                .withEntitySelectorConfig(
                    new EntitySelectorConfig(TestdataAddVarBaseEntity.class).withId("origin"))
                .withValueSelectorConfig(
                    new ValueSelectorConfig("value")
                        .withDowncastEntityClass(downcast)
                        .withNearbySelectionConfig(inheritanceNearby())));
    var config = inheritanceConfig("NONE").withPhases(inheritanceConstruction(), local);

    assertThatThrownBy(() -> SolverFactory.create(config).buildSolver())
        .isInstanceOf(errorType)
        .hasMessageContaining("downcastEntityClass")
        .hasMessageContaining(message);
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing", "name", "context"})
  void invalidValueMimicStillFailsAtStartup(String invalidCase) {
    var recorder =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig(TestdataAddVarBaseEntity.class))
            .withValueSelectorConfig(new ValueSelectorConfig("value").withId("recordedValue"));
    var replayEntity =
        invalidCase.equals("context") ? UnrelatedEntity.class : TestdataAddVarChildEntity.class;
    var replayValue = invalidCase.equals("name") ? "value2" : "value";
    var replayReference = invalidCase.equals("missing") ? "missingValue" : "recordedValue";
    var replay =
        new ChangeMoveSelectorConfig()
            .withEntitySelectorConfig(new EntitySelectorConfig(replayEntity))
            .withValueSelectorConfig(
                new ValueSelectorConfig(replayValue).withMimicSelectorRef(replayReference));
    var config =
        inheritanceConfig("NONE")
            .withEntityClasses(
                TestdataAddVarBaseEntity.class,
                TestdataAddVarChildEntity.class,
                UnrelatedEntity.class)
            .withPhases(
                inheritanceConstruction(),
                upcomingSearch(new UnionMoveSelectorConfig().withMoveSelectors(recorder, replay)));

    assertThatThrownBy(() -> SolverFactory.create(config).buildSolver())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mimicSelectorRef");
  }

  @Test
  void missingEntityMimicStillFailsAtStartup() {
    var local =
        upcomingSearch(
            new ChangeMoveSelectorConfig()
                .withEntitySelectorConfig(
                    new EntitySelectorConfig().withMimicSelectorRef("missingEntity"))
                .withValueSelectorConfig(new ValueSelectorConfig("value")));
    var config = inheritanceConfig("NONE").withPhases(inheritanceConstruction(), local);

    // The existing unfolding path dereferences the missing recorder before mimic validation.
    assertThatThrownBy(() -> SolverFactory.create(config).buildSolver())
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("EntityMimicRecorder");
  }

  private static SolverConfig basicConfig(String moveThreadCount) {
    return commonConfig(moveThreadCount)
        .withSolutionClass(TestdataSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withEasyScoreCalculatorClass(BasicCalculator.class)
        .withNearbyDistanceMeterClass(InterfaceMeter.class);
  }

  private static TestdataSolution basicProblem() {
    var problem = new TestdataSolution("polymorphic nearby");
    problem.setValueList(
        IntStream.range(0, 100).mapToObj(rank -> (TestdataValue) new RankedValue(rank)).toList());
    problem.setEntityList(List.of(new TestdataEntity("entity", null)));
    return problem;
  }

  private static SimpleScore basicScore(TestdataSolution solution) {
    return SimpleScore.of(
        -solution.getEntityList().stream()
            .map(TestdataEntity::getValue)
            .mapToInt(value -> value instanceof Ranked ranked ? ranked.rank() : 0)
            .sum());
  }

  private static SolverConfig inheritanceConfig(String moveThreadCount) {
    return commonConfig(moveThreadCount)
        .withSolutionClass(TestdataAddVarSolution.class)
        .withEntityClasses(TestdataAddVarBaseEntity.class, TestdataAddVarChildEntity.class)
        .withEasyScoreCalculatorClass(InheritanceCalculator.class);
  }

  private static SolverConfig commonConfig(String moveThreadCount) {
    return new SolverConfig()
        .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
        .withMoveThreadCount(moveThreadCount)
        .withRandomSeed(0L);
  }

  private static ConstructionHeuristicPhaseConfig inheritanceConstruction() {
    var changes = new ArrayList<MoveSelectorConfig>();
    for (var variable : List.of("value", "value2")) {
      changes.add(
          new ChangeMoveSelectorConfig()
              .withEntitySelectorConfig(
                  new EntitySelectorConfig().withMimicSelectorRef("construction"))
              .withValueSelectorConfig(new ValueSelectorConfig(variable)));
    }
    return new ConstructionHeuristicPhaseConfig()
        .withEntityPlacerConfig(
            new QueuedEntityPlacerConfig()
                .withEntitySelectorConfig(
                    new EntitySelectorConfig(TestdataAddVarChildEntity.class)
                        .withId("construction"))
                .withMoveSelectorConfigs(new CartesianProductMoveSelectorConfig(changes)));
  }

  private static NearbySelectionConfig inheritanceNearby() {
    return new NearbySelectionConfig()
        .withOriginEntitySelectorConfig(new EntitySelectorConfig().withMimicSelectorRef("origin"))
        .withNearbyDistanceMeterClass(InheritanceMeter.class);
  }

  private static LocalSearchPhaseConfig upcomingSearch(MoveSelectorConfig<?> move) {
    return new LocalSearchPhaseConfig()
        .withMoveSelectorConfig(move)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(0));
  }

  private static SimpleScore inheritanceScore(TestdataAddVarSolution solution) {
    return SimpleScore.of(
        -solution.getEntityList().stream()
            .mapToInt(entity -> stringRank(entity.getValue()) + stringRank(entity.getValue2()))
            .sum());
  }

  private static int stringRank(String value) {
    return value == null ? 0 : Integer.parseInt(value.substring(value.lastIndexOf(' ') + 1));
  }

  private static <Solution_> Outcome<Solution_> solve(SolverConfig config, Solution_ problem) {
    METER_CALLS.set(0);
    var selectedMoves = new AtomicLong();
    var solver = (DefaultSolver<Solution_>) SolverFactory.<Solution_>create(config).buildSolver();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<Solution_> stepScope) {
            if (stepScope instanceof ConstructionHeuristicStepScope<Solution_> constructionStep) {
              selectedMoves.addAndGet(constructionStep.getSelectedMoveCount());
            }
          }
        });
    var solution = solver.solve(problem);
    return new Outcome<>(solution, selectedMoves.get(), METER_CALLS.get());
  }

  private record Outcome<Solution_>(Solution_ solution, long selectedMoves, long meterCalls) {}

  public interface Ranked {
    int rank();
  }

  public static class RankedValue extends TestdataValue implements Ranked {
    private final int rank;

    public RankedValue(int rank) {
      super("value " + rank);
      this.rank = rank;
    }

    @Override
    public int rank() {
      return rank;
    }
  }

  public static class InterfaceMeter implements NearbyDistanceMeter<TestdataEntity, Ranked> {
    @Override
    public double getNearbyDistance(TestdataEntity origin, Ranked destination) {
      METER_CALLS.incrementAndGet();
      return destination.rank();
    }
  }

  public static class InheritanceMeter
      implements NearbyDistanceMeter<TestdataAddVarBaseEntity, String> {
    @Override
    public double getNearbyDistance(TestdataAddVarBaseEntity origin, String destination) {
      METER_CALLS.incrementAndGet();
      return stringRank(destination);
    }
  }

  public static class BasicCalculator
      implements EasyScoreCalculator<TestdataSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataSolution solution) {
      return basicScore(solution);
    }
  }

  public static class InheritanceCalculator
      implements EasyScoreCalculator<TestdataAddVarSolution, SimpleScore> {
    @Override
    public SimpleScore calculateScore(TestdataAddVarSolution solution) {
      return inheritanceScore(solution);
    }
  }

  @PlanningEntity
  public static class UnregisteredChild extends TestdataAddVarBaseEntity {}

  @PlanningEntity
  public static class UnrelatedEntity {
    @PlanningVariable(valueRangeProviderRefs = "valueRange")
    public String value;
  }
}
