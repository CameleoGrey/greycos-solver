package greycos.solver.core.config.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.islandmodel.ResumableSearchConfig;
import greycos.solver.core.config.islandmodel.ResumableSearchCooperationPolicy;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.termination.TerminationCompositionStyle;
import greycos.solver.core.config.solver.termination.TerminationConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ResumableSearchConfigResolverTest {

  @Test
  void legacyHierarchyRemainsUnvalidated() {
    var legacy =
        new IslandModelPhaseConfig()
            .withMoveThreadCount("AUTO")
            .withMigrationFrequency(7)
            .withPhaseConfigList(List.of(new AlnsPhaseConfig(), new CustomPhaseConfig()));
    assertThat(ResumableSearchConfigResolver.resolve(legacy)).isNull();
  }

  @Test
  void valuesCanBeDeclaredAcrossNestedHierarchyWithoutMutatingConfiguration() {
    var outerConfig = new ResumableSearchConfig().withStepQuantum(7);
    var innerConfig = new ResumableSearchConfig().withWorkerCount(2);
    var outer =
        new IslandModelPhaseConfig()
            .withResumableSearchConfig(outerConfig)
            .withPhaseConfigList(
                List.of(
                    new ConstructionHeuristicPhaseConfig(),
                    new PartitionedSearchPhaseConfig()
                        .withPhaseConfigs(
                            new IslandModelPhaseConfig()
                                .withResumableSearchConfig(innerConfig)
                                .withLocalSearchType(LocalSearchType.GUIDED_LOCAL_SEARCH))));

    assertThat(ResumableSearchConfigResolver.resolve(outer))
        .isEqualTo(new ResumableSearchConfigResolver.Resolved(2, 7));
    assertThat(outerConfig.getWorkerCount()).isNull();
    assertThat(innerConfig.getStepQuantum()).isNull();
    assertThat(outerConfig.getCooperationPolicy()).isNull();
  }

  @Test
  void partitionRootFindsResumableDescendantAndLegacySibling() {
    var root =
        new PartitionedSearchPhaseConfig()
            .withPhaseConfigs(
                new IslandModelPhaseConfig().withMigrationFrequency(3), resumableIsland());
    assertThat(ResumableSearchConfigResolver.resolve(root))
        .isEqualTo(new ResumableSearchConfigResolver.Resolved(2, 8));
  }

  @Test
  void repeatedDeclarationsMustAgreeForBothWorkersAndQuantum() {
    var inner = resumableIsland();
    var outer = resumableIsland().withPhaseConfigList(List.of(inner));
    assertThat(ResumableSearchConfigResolver.resolve(outer))
        .isEqualTo(new ResumableSearchConfigResolver.Resolved(2, 8));

    inner.getResumableSearchConfig().setWorkerCount(4);
    assertThatThrownBy(() -> ResumableSearchConfigResolver.resolve(outer))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContainingAll("workerCount (4)", "workerCount (2)", "phase[0]/islandModel");

    inner.getResumableSearchConfig().setWorkerCount(2);
    inner.getResumableSearchConfig().setStepQuantum(64);
    assertThatThrownBy(() -> ResumableSearchConfigResolver.resolve(outer))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContainingAll("stepQuantum (64)", "stepQuantum (8)", "phase[0]/islandModel");
  }

  @Test
  void workersAndQuantumRequireExplicitValues() {
    var root = new IslandModelPhaseConfig().withResumableSearchConfig(new ResumableSearchConfig());
    assertThatThrownBy(() -> ResumableSearchConfigResolver.resolve(root))
        .hasMessageContainingAll("workerCount (null)", "stepQuantum (null)");
    root.getResumableSearchConfig().setWorkerCount(2);
    assertThatThrownBy(() -> ResumableSearchConfigResolver.resolve(root))
        .hasMessageContainingAll("stepQuantum (null)");
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1})
  void rejectsNonpositiveValues(int value) {
    for (Consumer<ResumableSearchConfig> invalidator :
        List.<Consumer<ResumableSearchConfig>>of(
            config -> config.setWorkerCount(value),
            config -> config.setStepQuantum(value),
            config -> config.setStagnationRounds(value))) {
      var root = resumableIsland();
      invalidator.accept(root.getResumableSearchConfig());
      assertThatThrownBy(() -> ResumableSearchConfigResolver.resolve(root))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContainingAll("must be positive", "islandModel/resumableSearch");
    }
  }

  @Test
  void restartRequiresStagnationThreshold() {
    var root = resumableIsland();
    root.getResumableSearchConfig()
        .setCooperationPolicy(ResumableSearchCooperationPolicy.RESTART_WEAKEST);
    assertThatThrownBy(() -> ResumableSearchConfigResolver.resolve(root))
        .hasMessageContainingAll("RESTART_WEAKEST", "stagnationRounds (null)");
    root.getResumableSearchConfig().setStagnationRounds(3);
    assertThat(ResumableSearchConfigResolver.resolve(root)).isNotNull();
  }

  @Test
  void reportsUnsupportedDescendantWithHierarchyPath() {
    var root =
        resumableIsland()
            .withPhaseConfigList(
                List.of(
                    new PartitionedSearchPhaseConfig().withPhaseConfigs(new AlnsPhaseConfig())));
    assertThatThrownBy(() -> ResumableSearchConfigResolver.resolve(root))
        .hasMessageContainingAll(
            "AlnsPhaseConfig", "islandModel/phase[0]/partitionedSearch/phase[0]");
    root.setPhaseConfigList(List.of(new CustomPhaseConfig()));
    assertThatThrownBy(() -> ResumableSearchConfigResolver.resolve(root))
        .hasMessageContainingAll("CustomPhaseConfig", "islandModel/phase[0]");
  }

  @ParameterizedTest
  @ValueSource(strings = {"AUTO", "1", "4", "0"})
  void rejectsNestedMovePools(String moveThreads) {
    var root =
        resumableIsland()
            .withPhaseConfigList(
                List.of(new LocalSearchPhaseConfig().withMoveThreadCount(moveThreads)));
    assertThatThrownBy(() -> ResumableSearchConfigResolver.resolve(root))
        .hasMessageContainingAll("moveThreadCount (" + moveThreads + ")", "phase[0]/localSearch");
    root.setPhaseConfigList(
        List.of(new ConstructionHeuristicPhaseConfig().withMoveThreadCount(moveThreads)));
    assertThatThrownBy(() -> ResumableSearchConfigResolver.resolve(root))
        .hasMessageContainingAll("phase[0]/constructionHeuristic");
    root.setPhaseConfigList(List.of(new IslandModelPhaseConfig().withMoveThreadCount(moveThreads)));
    assertThatThrownBy(() -> ResumableSearchConfigResolver.resolve(root))
        .hasMessageContainingAll("phase[0]/islandModel");
  }

  @Test
  void explicitSingleThreadedMoveEvaluationIsAllowed() {
    var root =
        resumableIsland()
            .withMoveThreadCount("NONE")
            .withPhaseConfigList(
                List.of(
                    new ConstructionHeuristicPhaseConfig().withMoveThreadCount("NONE"),
                    new LocalSearchPhaseConfig().withMoveThreadCount("NONE")));
    assertThat(ResumableSearchConfigResolver.resolve(root)).isNotNull();
  }

  @Test
  void legacyCooperationOptionsConflictOnlyOnOptedInIsland() {
    for (Consumer<IslandModelPhaseConfig> configure :
        List.<Consumer<IslandModelPhaseConfig>>of(
            config -> config.setMigrationFrequency(7),
            config -> config.setCompareGlobalEnabled(false),
            config -> config.setReceiveGlobalUpdateFrequency(7),
            config -> config.setCompareGlobalFrequency(7),
            config -> config.setMigrationTimeout(10L))) {
      var legacy = new IslandModelPhaseConfig();
      configure.accept(legacy);
      var outer = resumableIsland().withPhaseConfigList(List.of(legacy));
      assertThat(ResumableSearchConfigResolver.resolve(outer)).isNotNull();

      configure.accept(outer);
      assertThatThrownBy(() -> ResumableSearchConfigResolver.resolve(outer))
          .hasMessageContainingAll("legacy", "cannot be combined with resumableSearch");
    }
  }

  @Test
  void simulatedAnnealingNeedsExplicitEpisodeWorkHorizonForEveryConfigurationForm() {
    for (var localSearch : simulatedAnnealingConfigurations()) {
      var root =
          resumableIsland()
              .withTerminationConfig(new TerminationConfig().withStepCountLimit(256))
              .withPhaseConfigList(List.of(localSearch));
      assertThatThrownBy(() -> ResumableSearchConfigResolver.resolve(root))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContainingAll("episode-local", "islandModel/phase[0]/localSearch");

      localSearch.setTerminationConfig(new TerminationConfig().withStepCountLimit(100));
      assertThat(ResumableSearchConfigResolver.resolve(root)).isNotNull();
    }
  }

  @Test
  void generatedSimulatedAnnealingRequiresExplicitInnerLocalSearch() {
    for (var localSearch : simulatedAnnealingConfigurations()) {
      var root =
          resumableIsland().withTerminationConfig(new TerminationConfig().withStepCountLimit(256));
      root.setLocalSearchType(localSearch.getLocalSearchType());
      root.setAcceptorConfig(localSearch.getAcceptorConfig());
      assertThatThrownBy(() -> ResumableSearchConfigResolver.resolve(root))
          .hasMessageContainingAll("islandModel", "explicit inner localSearch", "episode-local");
      root.setPhaseConfigList(List.of());
      assertThatThrownBy(() -> ResumableSearchConfigResolver.resolve(root))
          .hasMessageContaining("explicit inner localSearch");
    }
  }

  @Test
  void simulatedAnnealingAcceptsNestedWorkOnlyAndOrTerminationTrees() {
    for (var composition : TerminationCompositionStyle.values()) {
      var termination =
          new TerminationConfig()
              .withTerminationCompositionStyle(composition)
              .withTerminationConfigList(
                  List.of(
                      new TerminationConfig().withMoveCountLimit(1000L),
                      new TerminationConfig()
                          .withTerminationCompositionStyle(TerminationCompositionStyle.AND)
                          .withStepCountLimit(100)
                          .withTerminationConfigList(
                              List.of(
                                  new TerminationConfig().withScoreCalculationCountLimit(2000L)))));
      var localSearch =
          new LocalSearchPhaseConfig()
              .withLocalSearchType(LocalSearchType.SIMULATED_ANNEALING)
              .withTerminationConfig(termination);
      var root = resumableIsland().withPhaseConfigList(List.of(localSearch));

      assertThat(ResumableSearchConfigResolver.resolve(root)).isNotNull();
      assertThat(termination.getStepCountLimit()).isNull();
      assertThat(termination.getTerminationConfigList().getFirst().getMoveCountLimit())
          .isEqualTo(1000L);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0})
  void simulatedAnnealingRejectsNonpositiveEpisodeWorkLimits(int limit) {
    for (var termination :
        List.of(
            new TerminationConfig().withStepCountLimit(limit),
            new TerminationConfig().withMoveCountLimit((long) limit),
            new TerminationConfig().withScoreCalculationCountLimit((long) limit))) {
      var root =
          resumableIsland()
              .withPhaseConfigList(
                  List.of(
                      new LocalSearchPhaseConfig()
                          .withLocalSearchType(LocalSearchType.SIMULATED_ANNEALING)
                          .withTerminationConfig(termination)));
      assertThatThrownBy(() -> ResumableSearchConfigResolver.resolve(root))
          .hasMessageContainingAll(
              "must be positive", "islandModel/phase[0]/localSearch/termination");
    }
  }

  @Test
  void simulatedAnnealingRejectsClockIdleAndScoreTargetCoolingEvenAlongsideWorkHorizon() {
    var unsupported =
        List.<Consumer<TerminationConfig>>of(
            config -> config.setSpentLimit(Duration.ofSeconds(1)),
            config -> config.setMillisecondsSpentLimit(1L),
            config -> config.setSecondsSpentLimit(1L),
            config -> config.setMinutesSpentLimit(1L),
            config -> config.setHoursSpentLimit(1L),
            config -> config.setDaysSpentLimit(1L),
            config -> config.setUnimprovedSpentLimit(Duration.ofSeconds(1)),
            config -> config.setUnimprovedMillisecondsSpentLimit(1L),
            config -> config.setUnimprovedSecondsSpentLimit(1L),
            config -> config.setUnimprovedMinutesSpentLimit(1L),
            config -> config.setUnimprovedHoursSpentLimit(1L),
            config -> config.setUnimprovedDaysSpentLimit(1L),
            config -> config.setUnimprovedStepCountLimit(1),
            config -> config.setUnimprovedScoreDifferenceThreshold("1"),
            config -> config.setBestScoreLimit("100"),
            config -> config.setBestScoreFeasible(true),
            TerminationConfig::withDiminishedReturns);
    for (var configure : unsupported) {
      var child = new TerminationConfig();
      configure.accept(child);
      var termination =
          new TerminationConfig().withStepCountLimit(100).withTerminationConfigList(List.of(child));
      var root =
          resumableIsland()
              .withPhaseConfigList(
                  List.of(
                      new LocalSearchPhaseConfig()
                          .withLocalSearchType(LocalSearchType.SIMULATED_ANNEALING)
                          .withTerminationConfig(termination)));
      assertThatThrownBy(() -> ResumableSearchConfigResolver.resolve(root))
          .hasMessageContainingAll(
              "fixed work-only", "islandModel/phase[0]/localSearch/termination/termination[0]");
    }
  }

  @Test
  void nonAnnealingLeafTerminationRemainsUnrestricted() {
    var localSearch =
        new LocalSearchPhaseConfig()
            .withTerminationConfig(
                new TerminationConfig()
                    .withDiminishedReturns()
                    .withUnimprovedSecondsSpentLimit(1L)
                    .withUnimprovedScoreDifferenceThreshold("1"));
    var root = resumableIsland().withPhaseConfigList(List.of(localSearch));
    assertThat(ResumableSearchConfigResolver.resolve(root)).isNotNull();
  }

  @Test
  void unsupportedSharedTerminationReportsNestedContainerPath() {
    for (var unsupported :
        List.of(
            new TerminationConfig().withDiminishedReturns(),
            new TerminationConfig().withUnimprovedScoreDifferenceThreshold("1"))) {
      var partition =
          new PartitionedSearchPhaseConfig()
              .withTerminationConfig(
                  new TerminationConfig().withTerminationConfigList(List.of(unsupported)))
              .withPhaseConfigs(new LocalSearchPhaseConfig());
      var root = resumableIsland().withPhaseConfigList(List.of(partition));
      assertThatThrownBy(() -> ResumableSearchConfigResolver.resolve(root))
          .hasMessageContainingAll(
              "shared resumable search termination",
              "islandModel/phase[0]/partitionedSearch/termination/termination[0]");

      root.setPhaseConfigList(null);
      root.setTerminationConfig(unsupported);
      assertThatThrownBy(() -> ResumableSearchConfigResolver.resolve(root))
          .hasMessageContainingAll(
              "shared resumable search termination", "islandModel/termination");
    }
  }

  private static List<LocalSearchPhaseConfig> simulatedAnnealingConfigurations() {
    return List.of(
        new LocalSearchPhaseConfig().withLocalSearchType(LocalSearchType.SIMULATED_ANNEALING),
        new LocalSearchPhaseConfig()
            .withAcceptorConfig(
                new LocalSearchAcceptorConfig()
                    .withAcceptorTypeList(List.of(AcceptorType.SIMULATED_ANNEALING))),
        new LocalSearchPhaseConfig()
            .withAcceptorConfig(
                new LocalSearchAcceptorConfig()
                    .withAcceptorTypeList(
                        List.of(AcceptorType.ENTITY_TABU, AcceptorType.SIMULATED_ANNEALING))),
        new LocalSearchPhaseConfig()
            .withAcceptorConfig(
                new LocalSearchAcceptorConfig().withSimulatedAnnealingStartingTemperature("10")));
  }

  private static IslandModelPhaseConfig resumableIsland() {
    return new IslandModelPhaseConfig()
        .withResumableSearchConfig(
            new ResumableSearchConfig().withWorkerCount(2).withStepQuantum(8));
  }
}
