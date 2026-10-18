package greycos.solver.greybay.impl.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicType;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchStepLoggingMode;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.api.ConfigurationTemplate;
import greycos.solver.greybay.api.ProblemCapabilities;
import greycos.solver.greybay.api.objective.BalancedQualityObjective;
import greycos.solver.greybay.config.GreybayStudyDefinition;
import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.config.ProblemPanel;
import greycos.solver.greybay.config.RandomSamplerConfig;
import greycos.solver.greybay.config.ResourcePolicy;
import greycos.solver.greybay.config.SearchSpace;
import greycos.solver.greybay.examples.GreybayWorkflowExample;
import greycos.solver.greybay.examples.RoutingExample;
import greycos.solver.greybay.examples.SchedulingExample;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class StudyProtocolTest {
  private static final Duration HORIZON = Duration.ofSeconds(1);

  @ParameterizedTest
  @CsvSource(
      value = {"NONE, null", "NONE, NONE", "2, null", "2, 2"},
      nullValues = "null")
  void constructionWorkersMayInheritOrMatchFixedResources(String workers, String phaseWorkers) {
    var base = SchedulingExample.baseSolverConfig();
    ((ConstructionHeuristicPhaseConfig) base.getPhaseConfigList().getFirst())
        .setMoveThreadCount(phaseWorkers);
    var definition = definition(base, resources(workers), GreybayWorkflowExample.template());

    var effective = apply(definition, null);

    assertThat(effective.getMoveThreadCount()).isEqualTo(workers);
    assertThat(
            ((ConstructionHeuristicPhaseConfig) effective.getPhaseConfigList().getFirst())
                .getMoveThreadCount())
        .isEqualTo(phaseWorkers);
  }

  @ParameterizedTest
  @CsvSource({"NONE, 8", "NONE, AUTO", "2, 8", "2, NONE", "2, AUTO"})
  void constructionWorkersMustMatchFixedResources(String workers, String phaseWorkers) {
    var base = SchedulingExample.baseSolverConfig();
    ((ConstructionHeuristicPhaseConfig) base.getPhaseConfigList().getFirst())
        .setMoveThreadCount(phaseWorkers);
    var definition = definition(base, resources(workers), GreybayWorkflowExample.template());

    assertThatThrownBy(() -> apply(definition, null))
        .isInstanceOf(StudyProtocolException.class)
        .hasMessageContaining("moveThreadCount (" + phaseWorkers + ")")
        .hasMessageContaining("fixed resources (" + workers + ")");
  }

  @ParameterizedTest
  @CsvSource(
      value = {"island, null", "island, 2", "partition, null", "partition, 2"},
      nullValues = "null")
  void nestedConstructionWorkersMayInheritOrMatch(String container, String phaseWorkers) {
    var definition =
        definition(
            nestedBase(container, phaseWorkers), resources("2"), GreybayWorkflowExample.template());

    assertThat(apply(definition, null).getMoveThreadCount()).isEqualTo("2");
  }

  @ParameterizedTest
  @CsvSource({"island, 8", "island, AUTO", "partition, 8", "partition, AUTO"})
  void nestedConstructionWorkersMustMatchFixedResources(String container, String phaseWorkers) {
    var definition =
        definition(
            nestedBase(container, phaseWorkers), resources("2"), GreybayWorkflowExample.template());

    assertThatThrownBy(() -> apply(definition, null))
        .isInstanceOf(StudyProtocolException.class)
        .hasMessageContaining("moveThreadCount (" + phaseWorkers + ")")
        .hasMessageContaining("fixed resources (2)");
  }

  @Test
  void approvedLimitCallbackFailureRetainsItsCauseAndMessage() {
    var cause = new IllegalArgumentException("Candidate fraction produces a zero-millisecond cap.");
    var template =
        caps(
            () -> {
              throw cause;
            });
    var definition = definition(pipelineBase(), ResourcePolicy.singleThreaded(), template);

    assertThatThrownBy(() -> apply(definition, template))
        .isExactlyInstanceOf(CandidateMaterializationException.class)
        .hasMessage(cause.getMessage())
        .hasCauseReference(cause);
  }

  @Test
  void protocolFailureFromApprovedLimitCallbackRemainsFatal() {
    var cause = new StudyProtocolException("The callback violated the frozen protocol.");
    var template =
        caps(
            () -> {
              throw cause;
            });
    var definition = definition(pipelineBase(), ResourcePolicy.singleThreaded(), template);

    assertThatThrownBy(() -> apply(definition, template)).isSameAs(cause);
  }

  @ParameterizedTest
  @MethodSource("invalidApprovedLimits")
  void returnedLimitValidationRemainsAProtocolFailure(Map<Integer, Duration> limits) {
    var template = caps(() -> limits);
    var definition = definition(pipelineBase(), ResourcePolicy.singleThreaded(), template);

    assertThatThrownBy(() -> apply(definition, template))
        .isExactlyInstanceOf(StudyProtocolException.class);
  }

  static Stream<Map<Integer, Duration>> invalidApprovedLimits() {
    return Stream.of(
        Map.of(-1, Duration.ofMillis(100)),
        Map.of(0, Duration.ofMillis(100)),
        Map.of(2, Duration.ofMillis(100)),
        Map.of(3, Duration.ofMillis(100)),
        Map.of(1, Duration.ZERO),
        Map.of(1, Duration.ofMillis(-1)),
        Map.of(1, Duration.ofNanos(1)),
        Map.of(1, HORIZON));
  }

  @Test
  void nullApprovedLimitsAreNotACandidateFailure() {
    var template = caps(() -> null);
    var definition = definition(pipelineBase(), ResourcePolicy.singleThreaded(), template);

    assertThatThrownBy(() -> apply(definition, template))
        .isExactlyInstanceOf(NullPointerException.class);
  }

  @Test
  void validApprovedLimitsAreAppliedAfterTheCallbackReturns() {
    var template = caps(() -> Map.of(1, Duration.ofMillis(300)));
    var definition = definition(pipelineBase(), ResourcePolicy.singleThreaded(), template);

    var effective = apply(definition, template);

    assertThat(effective.getTerminationConfig().getSpentLimit()).isEqualTo(HORIZON);
    assertThat(effective.getPhaseConfigList().get(1).getTerminationConfig().getSpentLimit())
        .isEqualTo(Duration.ofMillis(300));
    assertThat(effective.getPhaseConfigList().get(2).getTerminationConfig()).isNull();
  }

  @ParameterizedTest
  @MethodSource("invalidCallbackMutations")
  void approvedLimitCallbackCannotChangeFrozenProtocol(Consumer<SolverConfig> mutation) {
    var template =
        caps(
            config -> {
              mutation.accept(config);
              return Map.of();
            });
    var definition = definition(pipelineBase(), ResourcePolicy.singleThreaded(), template);

    assertThatThrownBy(() -> apply(definition, template))
        .isExactlyInstanceOf(StudyProtocolException.class);
  }

  static Stream<Consumer<SolverConfig>> invalidCallbackMutations() {
    return Stream.of(
        config -> config.withEasyScoreCalculatorClass(RoutingExample.RouteScoreCalculator.class),
        config ->
            ((ConstructionHeuristicPhaseConfig) config.getPhaseConfigList().getFirst())
                .setConstructionHeuristicType(ConstructionHeuristicType.FIRST_FIT),
        config -> config.setDaemon(true),
        config -> config.setMoveThreadCount("8"),
        config ->
            ((LocalSearchPhaseConfig) config.getPhaseConfigList().get(1)).setMoveThreadCount("8"),
        config ->
            config.getPhaseConfigList().get(1).setEnvironmentMode(EnvironmentMode.FULL_ASSERT),
        config ->
            ((LocalSearchPhaseConfig) config.getPhaseConfigList().get(1))
                .setStepLoggingMode(LocalSearchStepLoggingMode.BEST_SCORE_IMPROVED),
        config ->
            config.withPhases(new LocalSearchPhaseConfig(), new ConstructionHeuristicPhaseConfig()),
        config -> config.setPhaseConfigList(List.of()),
        config -> config.setPhaseConfigList(null),
        config ->
            config.withPhases(
                config.getPhaseConfigList().getFirst(),
                new LocalSearchPhaseConfig()
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(1))));
  }

  @Test
  void approvedLimitsUseThePhaseListReplacedByTheCallback() {
    var template =
        caps(
            config -> {
              config.withPhases(
                  config.getPhaseConfigList().getFirst(),
                  new LocalSearchPhaseConfig(),
                  new LocalSearchPhaseConfig(),
                  new LocalSearchPhaseConfig());
              return Map.of(2, Duration.ofMillis(300));
            });
    var definition = definition(pipelineBase(), ResourcePolicy.singleThreaded(), template);

    var effective = apply(definition, template);

    assertThat(effective.getPhaseConfigList()).hasSize(4);
    assertThat(effective.getPhaseConfigList().get(1).getTerminationConfig()).isNull();
    assertThat(effective.getPhaseConfigList().get(2).getTerminationConfig().getSpentLimit())
        .isEqualTo(Duration.ofMillis(300));
    assertThat(effective.getPhaseConfigList().get(3).getTerminationConfig()).isNull();
    assertThat(effective.getTerminationConfig().getSpentLimit()).isEqualTo(HORIZON);
  }

  private static SolverConfig apply(
      GreybayStudyDefinition<?, ?> definition, ConfigurationTemplate template) {
    return StudyProtocol.apply(
        definition,
        definition.baseSolverConfig(),
        template,
        template == null ? null : template.defaultAssignment(),
        11L,
        HORIZON);
  }

  private static ResourcePolicy resources(String workers) {
    return new ResourcePolicy(workers, !workers.equals("NONE"), Map.of());
  }

  private static SolverConfig nestedBase(String container, String workers) {
    var construction = new ConstructionHeuristicPhaseConfig().withMoveThreadCount(workers);
    var local = new LocalSearchPhaseConfig();
    var base = SchedulingExample.baseSolverConfig();
    if (container.equals("island")) {
      return base.withPhases(
          new IslandModelPhaseConfig().withPhaseConfigList(List.of(construction, local)));
    }
    return base.withPhases(
        new PartitionedSearchPhaseConfig()
            .withRunnablePartThreadLimit("2")
            .withPhaseConfigs(construction, local));
  }

  private static SolverConfig pipelineBase() {
    return SchedulingExample.baseSolverConfig()
        .withPhases(
            new ConstructionHeuristicPhaseConfig(),
            new LocalSearchPhaseConfig(),
            new LocalSearchPhaseConfig());
  }

  private static GreybayStudyDefinition<SchedulingExample.Schedule, HardSoftScore> definition(
      SolverConfig base, ResourcePolicy resources, ConfigurationTemplate template) {
    return GreybayStudyDefinition.<SchedulingExample.Schedule, HardSoftScore>builder()
        .baseSolverConfig(base)
        .problemAdapter(SchedulingExample.problemAdapter())
        .trainingPanel(new ProblemPanel(List.of("train-small"), List.of(11L)))
        .heldOutPanel(new ProblemPanel(List.of("heldout-small"), List.of(101L)))
        .templates(List.of(template))
        .objective(new BalancedQualityObjective<>())
        .sampler(new RandomSamplerConfig())
        .optimizerSeed(1L)
        .solveDuration(HORIZON)
        .warmupDuration(Duration.ZERO)
        .resourcePolicy(resources)
        .maxTrials(1)
        .activeTimeLimit(Duration.ofMinutes(1))
        .build();
  }

  private static ConfigurationTemplate caps(Supplier<Map<Integer, Duration>> callback) {
    return caps(config -> callback.get());
  }

  private static ConfigurationTemplate caps(
      Function<SolverConfig, Map<Integer, Duration>> callback) {
    return new ConfigurationTemplate() {
      @Override
      public ComponentIdentity identity() {
        return new ComponentIdentity("pipeline-caps", "1");
      }

      @Override
      public SearchSpace searchSpace() {
        return new SearchSpace(List.of());
      }

      @Override
      public ParameterAssignment defaultAssignment() {
        return ParameterAssignment.of(Map.of());
      }

      @Override
      public void validate(ProblemCapabilities capabilities) {}

      @Override
      public SolverConfig materialize(SolverConfig baseCopy, ParameterAssignment assignment) {
        return baseCopy;
      }

      @Override
      public Map<Integer, Duration> approvedSearchPhaseLimits(
          SolverConfig materialized, Duration horizon, ParameterAssignment assignment) {
        return callback.apply(materialized);
      }
    };
  }
}
