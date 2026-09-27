package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.phase.PhaseCommand;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.alns.AlnsPhaseScope;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicPhaseScope;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.score.definition.HardSoftScoreDefinition;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.preview.api.move.builtin.Moves;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.score.TestdataHardSoftScoreSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@Timeout(15)
class IslandScoreTerminationTest {

  private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC);

  @ParameterizedTest
  @MethodSource("targets")
  void publicSolverTargetStopsAnotherIslandWithoutAdoption(TerminationConfig target) {
    var entered = new CountDownLatch(2);
    var published = new CountDownLatch(1);
    var commandIndex = new AtomicInteger();
    var peerTerminated = new AtomicBoolean();
    var peerKeptItsOwnSolution = new AtomicBoolean();
    PhaseCommand<TestdataHardSoftScoreSolution> command =
        context -> {
          int index = commandIndex.getAndIncrement();
          entered.countDown();
          await(entered);
          var entity = context.getWorkingSolution().getEntityList().getFirst();
          if (index == 0) {
            var variable =
                context
                    .getSolutionMetaModel()
                    .genuineEntity(TestdataEntity.class)
                    .basicVariable("value", TestdataValue.class);
            context.execute(
                Moves.change(
                    variable, entity, context.getWorkingSolution().getValueList().getFirst()));
          } else {
            await(published);
            peerTerminated.set(context.isPhaseTerminated());
            peerKeptItsOwnSolution.set(entity.getValue().getCode().equals("bad"));
          }
        };
    var config =
        solverConfig()
            .withTerminationConfig(target)
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(2)
                    .withCompareGlobalEnabled(false)
                    .withPhaseConfigList(
                        List.of(new CustomPhaseConfig().withCustomPhaseCommands(command))));
    var solver = SolverFactory.<TestdataHardSoftScoreSolution>create(config).buildSolver();
    solver.addEventListener(
        event -> {
          if (event.getNewBestScore().equals(HardSoftScore.ZERO)) {
            published.countDown();
          }
        });

    var result = solver.solve(problem());

    assertThat(result.getScore()).isEqualTo(HardSoftScore.ZERO);
    assertThat(commandIndex).hasValue(2);
    assertThat(peerTerminated).isTrue();
    assertThat(peerKeptItsOwnSolution).isTrue();
  }

  @ParameterizedTest
  @MethodSource("targets")
  void inheritedTargetKeepsAndOrGroupingAndEachIslandsWork(TerminationConfig target) {
    var fixture = new Fixture(new TerminationConfig(), HardSoftScore.of(-10, -10));
    fixture.improve(HardSoftScore.of(-10, -10), 0);
    var definition = fixture.definition(target);
    var and = UniversalTermination.and(definition, new MoveCountTermination<TestdataSolution>(3));
    var nested =
        UniversalTermination.or(and, new ScoreCalculationCountTermination<TestdataSolution>(5));
    var fast = scope();
    var slow = scope();
    fast.addMoveEvaluationCount(3);
    slow.addMoveEvaluationCount(2);
    var fastTermination = fixture.budget.createChildSolverTermination(nested, fast);
    var slowTermination = fixture.budget.createChildSolverTermination(nested, slow);
    assertThat(fastTermination.isSolverTerminated(fast)).isFalse();

    fixture.improve(HardSoftScore.ZERO, 1);

    assertThat(fastTermination.isSolverTerminated(fast)).isTrue();
    assertThat(slowTermination.isSolverTerminated(slow)).isFalse();
    assertThat(slowTermination.calculateSolverTimeGradient(slow)).isEqualTo(2.0 / 3.0);
    slow.addMoveEvaluationCount(1);
    assertThat(slowTermination.isSolverTerminated(slow)).isTrue();
    assertThat(slow.<HardSoftScore>getBestScore().raw()).isEqualTo(HardSoftScore.of(-20, -20));
  }

  @ParameterizedTest
  @MethodSource("targets")
  void initializedPublicationEstablishesPhaseBaselineAndKeepsSolverBaseline(
      TerminationConfig target) {
    var fixture = new Fixture(target, HardSoftScore.of(-100, -100));
    fixture.budget.bestScoreImproved(
        InnerScore.withUnassignedCount(HardSoftScore.ZERO, 1), 1_000, 0);
    var scope = scope();
    var sequence = fixture.budget.createIslandTermination(scope);
    sequence.solvingStarted(scope);
    sequence.phaseStarted(new ConstructionHeuristicPhaseScope<>(scope, 0));
    var inherited = fixture.budget.createChildSolverTermination(fixture.definition(target), scope);
    assertThat(sequence.isSolverTerminated(scope)).isFalse();
    assertThat(sequence.calculateSolverTimeGradient(scope)).isZero();
    assertThat(inherited.isSolverTerminated(scope)).isFalse();
    assertThat(inherited.calculateSolverTimeGradient(scope)).isZero();

    fixture.improve(HardSoftScore.of(-10, -10), 1);
    sequence.phaseStarted(new LocalSearchPhaseScope<>(scope, 1));
    fixture.improve(HardSoftScore.of(-5, -5), 2);

    assertThat(sequence.calculateSolverTimeGradient(scope)).isEqualTo(0.5);
    assertThat(inherited.calculateSolverTimeGradient(scope)).isEqualTo(0.95);
    sequence.phaseStarted(new LocalSearchPhaseScope<>(scope, 2));
    assertThat(sequence.calculateSolverTimeGradient(scope)).isEqualTo(0.5);
  }

  @ParameterizedTest
  @MethodSource("targets")
  void missingSolverBaselineUsesFirstInitializedPublication(TerminationConfig target) {
    var fixture = new Fixture(target, null);
    fixture.budget.bestScoreImproved(
        InnerScore.withUnassignedCount(HardSoftScore.ZERO, 1), 1_000, 0);
    var scope = scope();
    var inherited = fixture.budget.createChildSolverTermination(fixture.definition(target), scope);
    fixture.improve(HardSoftScore.of(-10, -10), 1);
    fixture.improve(HardSoftScore.of(-5, -5), 2);
    assertThat(inherited.calculateSolverTimeGradient(scope)).isEqualTo(0.5);
    assertThat(fixture.budget.progress().phaseFirstInitializedScore())
        .isEqualTo(HardSoftScore.of(-10, -10));
    assertThat(fixture.budget.progress().solverFirstInitializedScore())
        .isEqualTo(HardSoftScore.of(-10, -10));
  }

  @ParameterizedTest
  @MethodSource("targets")
  void nestedCopiesRetainOriginalPopulationAndNeverReadMutableScopes(TerminationConfig target) {
    var outer = new Fixture(new TerminationConfig(), HardSoftScore.of(-10, -10));
    outer.improve(HardSoftScore.of(-10, -10), 0);
    var inner = new Fixture(new TerminationConfig(), HardSoftScore.of(-5, -5));
    inner.improve(HardSoftScore.ZERO, 0);
    @SuppressWarnings("unchecked")
    var unusedScope = (SolverScope<TestdataSolution>) mock(SolverScope.class);
    var original = outer.budget.createChildSolverTermination(outer.definition(target), unusedScope);
    var descendant = inner.budget.createChildSolverTermination(original, unusedScope);

    assertThat(descendant).isSameAs(original);
    assertThat(descendant.isSolverTerminated(unusedScope)).isFalse();
    assertThat(descendant.calculateSolverTimeGradient(unusedScope)).isZero();
    outer.improve(HardSoftScore.ZERO, 1);
    assertThat(descendant.isSolverTerminated(unusedScope)).isTrue();
    assertThat(descendant.calculateSolverTimeGradient(unusedScope)).isEqualTo(1.0);
    verifyNoInteractions(unusedScope);
    assertThat(
            ChildThreadSupportingTermination
                .<TestdataSolution, SolverScope<TestdataSolution>>assertChildThreadSupport(
                    descendant)
                .createChildThreadTermination(unusedScope, ChildThreadType.MOVE_THREAD))
        .isSameAs(original);
  }

  @ParameterizedTest
  @MethodSource("targets")
  void alnsRepairAttemptPollingRetainsSharedTargetBinding(TerminationConfig target) {
    var fixture = new Fixture(new TerminationConfig(), HardSoftScore.of(-10, -10));
    fixture.improve(HardSoftScore.of(-10, -10), 0);
    var scope = scope();
    var inherited = fixture.budget.createChildSolverTermination(fixture.definition(target), scope);
    var phase = new AlnsPhaseScope<>(scope, 0);
    var polling = new AlnsTerminationPolling<>(phase, PhaseTermination.bridge(inherited));
    assertThat(polling.supportedForRepairAttempts()).isTrue();
    assertThat(polling.checkProbe()).isFalse();
    fixture.improve(HardSoftScore.ZERO, 1);
    assertThat(polling.checkProbe()).isTrue();
  }

  @Test
  void nestedInvalidIslandFieldsFailAtFactoryCreationWithTheirPath() {
    var invalid = new IslandModelPhaseConfig().withIslandCount(0);
    var config =
        solverConfig()
            .withPhases(
                new IslandModelPhaseConfig()
                    .withPhaseConfigList(
                        List.of(
                            new PartitionedSearchPhaseConfig()
                                .withPhaseConfigList(List.of(invalid)))));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> SolverFactory.create(config))
        .withMessageContaining(
            "solver.phase[0].islandModel.phase[0].partitionedSearch.phase[0].islandModel.islandCount");
  }

  @Test
  void nestedInvalidTerminationFailsBeforeSolvingWithItsPath() {
    var config =
        solverConfig()
            .withPhases(
                new IslandModelPhaseConfig()
                    .withPhaseConfigList(
                        List.of(
                            new IslandModelPhaseConfig()
                                .withTerminationConfig(
                                    new TerminationConfig().withStepCountLimit(-1)))));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> SolverFactory.create(config).buildSolver())
        .withMessageContaining("phase[0].islandModel.phase[0].termination")
        .withMessageContaining("stepCountLimit (-1)");
  }

  @Test
  void unknownInheritedLeafFailsWithTheFullTreePath() {
    @SuppressWarnings("unchecked")
    var unknown =
        (MockableSolverTermination<TestdataSolution>) mock(MockableSolverTermination.class);
    var definition =
        UniversalTermination.or(
            new BasicPlumbingTermination<TestdataSolution>(false),
            UniversalTermination.and(new MoveCountTermination<TestdataSolution>(3), unknown));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> IslandTerminationBinding.validate(definition, "solver.termination"))
        .withMessageContaining("solver.termination.termination[1].termination[1]")
        .withMessageContaining("does not support child termination");
  }

  private static Stream<TerminationConfig> targets() {
    return Stream.of(
        new TerminationConfig().withBestScoreLimit("0hard/0soft"),
        new TerminationConfig().withBestScoreFeasible(true));
  }

  private static SolverConfig solverConfig() {
    return new SolverConfig()
        .withSolutionClass(TestdataHardSoftScoreSolution.class)
        .withEntityClasses(TestdataEntity.class)
        .withEasyScoreCalculatorClass(ValueScoreCalculator.class);
  }

  private static TestdataHardSoftScoreSolution problem() {
    var solution = new TestdataHardSoftScoreSolution("shared target");
    var good = new TestdataValue("good");
    var bad = new TestdataValue("bad");
    solution.setValueList(List.of(good, bad));
    solution.setEntityList(List.of(new TestdataEntity("entity", bad)));
    return solution;
  }

  public static final class ValueScoreCalculator
      implements EasyScoreCalculator<TestdataHardSoftScoreSolution, HardSoftScore> {
    @Override
    public HardSoftScore calculateScore(TestdataHardSoftScoreSolution solution) {
      return solution.getEntityList().getFirst().getValue().getCode().equals("good")
          ? HardSoftScore.ZERO
          : HardSoftScore.of(-1, 0);
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private static SolverScope<TestdataSolution> scope() {
    var scope = new SolverScope<TestdataSolution>(CLOCK);
    scope.setScoreDirector(mock(InnerScoreDirector.class));
    scope.setInitializedBestScore(HardSoftScore.of(-20, -20));
    return scope;
  }

  private static final class Fixture {
    private final HeuristicConfigPolicy<TestdataSolution> policy;
    private final IslandTerminationBudget<TestdataSolution> budget;

    @SuppressWarnings("unchecked")
    private Fixture(TerminationConfig config, HardSoftScore solverBaseline) {
      policy = mock(HeuristicConfigPolicy.class);
      when(policy.getScoreDefinition()).thenReturn(new HardSoftScoreDefinition());
      budget = new IslandTerminationBudget<>(config, policy, CLOCK, 1_000, solverBaseline);
    }

    private SolverTermination<TestdataSolution> definition(TerminationConfig config) {
      return UniversalTermination.or(
          TerminationFactory.<TestdataSolution>create(config).buildTermination(policy));
    }

    private void improve(HardSoftScore score, long version) {
      budget.bestScoreImproved(InnerScore.fullyAssigned(score), 1_000, version);
    }
  }
}
