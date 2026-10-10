package greycos.solver.core.impl.solver.termination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.solver.termination.TerminationCompositionStyle;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchPhaseScope;
import greycos.solver.core.impl.partitionedsearch.scope.PartitionedSearchPhaseScope;
import greycos.solver.core.impl.score.definition.SimpleScoreDefinition;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings({"rawtypes", "unchecked"})
class IteratedLocalSearchTerminationSupportTest {

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void compositionRetainsUnavailableAndCancellationOperands(boolean and, boolean reverse) {
    var limit = new TimeMillisSpentTermination<TestdataSolution>(100L);
    for (var unavailable : unavailable()) {
      var children = reverse ? List.of(unavailable, limit) : List.of(limit, unavailable);
      var tree = composite(and, children);
      assertThat(IteratedLocalSearchTerminationSupport.hasApplicableLimit(tree)).isEqualTo(!and);
      assertThat(
              IteratedLocalSearchTerminationSupport.hasApplicableLimit(
                  PhaseTermination.bridge(tree)))
          .isEqualTo(!and);
      var wrapped = composite(and, List.of(limit, UniversalTermination.or(unavailable)));
      assertThat(IteratedLocalSearchTerminationSupport.hasApplicableLimit(wrapped)).isEqualTo(!and);
    }
    assertThat(
            IteratedLocalSearchTerminationSupport.hasApplicableLimit(
                composite(and, List.of(limit, new StepCountTermination<>(2)))))
        .isTrue();
  }

  @Test
  void plumbingAndEmptyExpressionsNeverSupplyALimit() {
    for (var unavailable : unavailable()) {
      assertThat(IteratedLocalSearchTerminationSupport.hasApplicableLimit(unavailable)).isFalse();
      assertThat(
              IteratedLocalSearchTerminationSupport.hasApplicableLimit(
                  PhaseTermination.bridge(UniversalTermination.or(unavailable))))
          .isFalse();
    }
  }

  @Test
  void solverBridgeUsesSolverEndpointsButRetainsApplicablePhaseOnlyLeaves() {
    var universal = unavailable(true);
    assertThat(IteratedLocalSearchTerminationSupport.hasApplicableLimit(universal)).isFalse();
    assertThat(
            IteratedLocalSearchTerminationSupport.hasApplicableLimit(
                PhaseTermination.bridge((SolverTermination<TestdataSolution>) universal)))
        .isTrue();
    assertThat(
            IteratedLocalSearchTerminationSupport.hasApplicableLimit(
                new PhaseToSolverTerminationBridge<>(universal)))
        .isTrue();

    var phaseOnly = new StepCountTermination<TestdataSolution>(2);
    assertThat(
            IteratedLocalSearchTerminationSupport.hasApplicableLimit(
                PhaseTermination.bridge(UniversalTermination.or(phaseOnly))))
        .isTrue();
    assertThat(
            IteratedLocalSearchTerminationSupport.hasApplicableLimit(
                new PhaseToSolverTerminationBridge<>(UniversalTermination.or(phaseOnly))))
        .isFalse();
    verify(universal, never()).isPhaseTerminated(any());
    verify(universal, never()).isSolverTerminated(any());
  }

  @ParameterizedTest
  @CsvSource({
    "false, false, false", "false, false, true", "false, true, false", "false, true, true",
    "true, false, false", "true, false, true", "true, true, false", "true, true, true"
  })
  void partitionMetadataPreservesCompositionOriginsAndNesting(
      boolean and, boolean solverOrigin, boolean nested) {
    var limit = new TimeMillisSpentTermination<TestdataSolution>(100L);
    for (var unavailable : unavailable()) {
      var tree = composite(and, List.of(limit, unavailable));
      var child = partition(tree, solverOrigin, nested);
      assertThat(IteratedLocalSearchTerminationSupport.hasApplicableLimit(child)).isEqualTo(!and);
      assertThat(
              IteratedLocalSearchTerminationSupport.hasApplicableLimit(
                  PhaseTermination.bridge(child)))
          .isEqualTo(!and);
    }
    for (var limitOnly :
        List.<PhaseTermination<TestdataSolution>>of(
            new StepCountTermination<>(2), new MoveCountTermination<>(3),
            new ScoreCalculationCountTermination<>(4), new UnimprovedStepCountTermination<>(2))) {
      var child = partition(limitOnly, solverOrigin, nested);
      assertThat(
              IteratedLocalSearchTerminationSupport.hasApplicableLimit(
                  PhaseTermination.bridge(child)))
          .isTrue();
    }
  }

  @Test
  void partitionUsesBoundChildApplicabilityWithoutPollingIt() {
    var definition = unavailable(false);
    var copy = unavailable(false);
    when(((ChildThreadSupportingTermination) definition).createChildThreadTermination(any(), any()))
        .thenReturn(copy);
    var child = partition(definition, false, true);
    clearInvocations(copy);
    assertThat(IteratedLocalSearchTerminationSupport.hasApplicableLimit(child)).isFalse();
    verify(copy).isApplicableTo(IteratedLocalSearchPhaseScope.class);
    verify(copy, never()).isPhaseTerminated(any());
    verify(copy, never()).isSolverTerminated(any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void partitionKeepsSolverOriginForDualEndpointLeaves(boolean nested) {
    var universal = unavailable(true);
    assertThat(
            IteratedLocalSearchTerminationSupport.hasApplicableLimit(
                partition(universal, false, nested)))
        .isFalse();
    assertThat(
            IteratedLocalSearchTerminationSupport.hasApplicableLimit(
                partition(universal, true, nested)))
        .isTrue();
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void islandAndPartitionWrappersDoNotCreateMissingLimits(boolean configured, boolean partitioned) {
    var config = new TerminationConfig();
    if (configured) config.withSpentLimit(Duration.ofSeconds(1));
    var island = island(config);
    Termination<TestdataSolution> termination =
        partitioned ? partition(island, true, true) : island;
    assertThat(IteratedLocalSearchTerminationSupport.hasApplicableLimit(termination))
        .isEqualTo(configured);
    assertThat(
            IteratedLocalSearchTerminationSupport.hasApplicableLimit(
                PhaseTermination.bridge(
                    UniversalTermination.or(new ChildThreadPlumbingTermination<>(), termination))))
        .isEqualTo(configured);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void islandSearchOnlyAndWorkLimitsRemainApplicableInsidePartitions(boolean and) {
    var config =
        new TerminationConfig()
            .withTerminationCompositionStyle(
                and ? TerminationCompositionStyle.AND : TerminationCompositionStyle.OR)
            .withTerminationConfigList(
                List.of(
                    new TerminationConfig().withUnimprovedStepCountLimit(2),
                    new TerminationConfig().withMoveCountLimit(3L)));
    assertThat(
            IteratedLocalSearchTerminationSupport.hasApplicableLimit(
                partition(island(config), true, true)))
        .isTrue();
  }

  private static List<Termination<TestdataSolution>> unavailable() {
    return List.of(
        new BasicPlumbingTermination<>(false),
        new ChildThreadPlumbingTermination<>(),
        new AndCompositeTermination<>(List.of()),
        new OrCompositeTermination<>(List.of()),
        unavailable(false));
  }

  private static MockablePhaseTermination<TestdataSolution> unavailable(boolean solverCapable) {
    var settings =
        solverCapable
            ? withSettings()
                .extraInterfaces(
                    ChildThreadSupportingTermination.class, MockableSolverTermination.class)
            : withSettings().extraInterfaces(ChildThreadSupportingTermination.class);
    var termination =
        (MockablePhaseTermination<TestdataSolution>) mock(MockablePhaseTermination.class, settings);
    when(((ChildThreadSupportingTermination) termination)
            .createChildThreadTermination(any(), any()))
        .thenReturn(termination);
    return termination;
  }

  private static UniversalTermination<TestdataSolution> composite(
      boolean and, List<? extends Termination<TestdataSolution>> children) {
    return and
        ? new AndCompositeTermination<>(List.copyOf(children))
        : new OrCompositeTermination<>(List.copyOf(children));
  }

  private static UniversalTermination<TestdataSolution> partition(
      PhaseTermination<TestdataSolution> definition, boolean solverOrigin, boolean nested) {
    var parent = scope();
    var parentPhase = new PartitionedSearchPhaseScope<>(parent, 0);
    parentPhase.startingNow();
    var budget =
        new PartitionTerminationBudget<>(
            solverOrigin
                ? PhaseTermination.bridge(UniversalTermination.or(definition))
                : definition,
            parentPhase);
    var child = budget.createChildTermination(scope());
    if (nested) {
      var nestedBudget =
          new PartitionTerminationBudget<>(PhaseTermination.bridge(child), parentPhase);
      child = nestedBudget.createChildTermination(scope());
    }
    return child;
  }

  private static IslandSequenceTermination<TestdataSolution> island(TerminationConfig config) {
    var policy = (HeuristicConfigPolicy<TestdataSolution>) mock(HeuristicConfigPolicy.class);
    when(policy.getScoreDefinition()).thenReturn(new SimpleScoreDefinition());
    return new IslandTerminationBudget<>(config, policy, Clock.systemUTC(), 0L)
        .createIslandTermination(scope());
  }

  private static SolverScope<TestdataSolution> scope() {
    var scope = new SolverScope<TestdataSolution>();
    scope.setScoreDirector(mock(InnerScoreDirector.class));
    scope.setInitializedBestScore(SimpleScore.ZERO);
    scope.startingNow();
    return scope;
  }
}
