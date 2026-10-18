package greycos.solver.greybay.impl.study;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import greycos.solver.core.config.alns.AlnsMoveThreadingMode;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchStepLoggingMode;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.greybay.api.ConfigurationTemplate;
import greycos.solver.greybay.api.RuntimeReconstructionProvider;
import greycos.solver.greybay.config.GreybayStudyDefinition;
import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.impl.config.FrozenSolverConfig;

final class StudyProtocol {
  private StudyProtocol() {}

  static SolverConfig apply(
      GreybayStudyDefinition<?, ?> definition,
      SolverConfig candidate,
      ConfigurationTemplate template,
      ParameterAssignment assignment,
      long seed,
      Duration horizon) {
    String frozenIdentity =
        invariantIdentity(
            definition.baseSolverConfig(), definition.runtimeReconstructionProvider());
    validateCandidate(definition, candidate, frozenIdentity);
    Map<Integer, Duration> supplied;
    try {
      supplied =
          template == null
              ? Map.of()
              : template.approvedSearchPhaseLimits(candidate, horizon, assignment);
    } catch (StudyProtocolException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new CandidateMaterializationException(failure);
    }
    // The callback receives a mutable configuration and may replace its phase list.
    // Validate its final state before interpreting caps or applying protocol-owned settings.
    if (template != null) validateCandidate(definition, candidate, frozenIdentity);
    var phases = candidate.getPhaseConfigList();
    Map<Integer, Duration> approved = Map.copyOf(supplied);
    long allocated = 0;
    int finalSearch = -1;
    for (int i = 0; i < phases.size(); i++) {
      if (!(isInitialization(phases.get(i)))) finalSearch = i;
    }
    for (var entry : approved.entrySet()) {
      int index = entry.getKey();
      Duration cap = entry.getValue();
      if (index < 0
          || index >= phases.size()
          || index == finalSearch
          || isInitialization(phases.get(index))
          || cap.isNegative()
          || cap.isZero()
          || cap.toNanos() % 1_000_000 != 0) {
        throw new StudyProtocolException(
            "Invalid approved pipeline cap at phase (" + index + "): " + cap);
      }
      allocated = Math.addExact(allocated, cap.toMillis());
    }
    if (allocated >= horizon.toMillis()) {
      throw new StudyProtocolException(
          "Nonfinal search phase caps must sum to less than the global horizon.");
    }
    for (int i = 0; i < phases.size(); i++) {
      var phase = phases.get(i);
      if (!definition.resourcePolicy().allowInternalWorkers()
          && (phase instanceof IslandModelPhaseConfig
              || phase instanceof PartitionedSearchPhaseConfig)) {
        throw new StudyProtocolException(
            "Internal-worker phases require an explicit fixed resource envelope.");
      }
      if (isInitialization(phase)) continue;
      if (phase.getTerminationConfig() != null) {
        throw new StudyProtocolException(
            "Search phase ("
                + i
                + ") supplied a hidden termination. Declare only protocol-owned pipeline caps.");
      }
      Duration cap = approved.get(i);
      if (cap != null) phase.setTerminationConfig(new TerminationConfig().withSpentLimit(cap));
    }
    candidate.setEnvironmentMode(EnvironmentMode.REPRODUCIBLE);
    candidate.setRandomSeed(seed);
    candidate.setMoveThreadCount(definition.resourcePolicy().moveThreadCount());
    candidate.setTerminationConfig(new TerminationConfig().withSpentLimit(horizon));
    return candidate;
  }

  private static void validateCandidate(
      GreybayStudyDefinition<?, ?> definition, SolverConfig candidate, String frozenIdentity) {
    if (!frozenIdentity.equals(
        invariantIdentity(candidate, definition.runtimeReconstructionProvider()))) {
      throw new StudyProtocolException(
          "A template changed frozen solution/entity classes, scoring, business weights, "
              + "initialization, preview policy, logging/monitoring or fixed configuration resources.");
    }
    if (Boolean.TRUE.equals(candidate.getDaemon())) {
      throw new StudyProtocolException(
          "Daemon solver mode is incompatible with finite study evaluations.");
    }
    validateResources(candidate.getPhaseConfigList(), definition);
    var phases = candidate.getPhaseConfigList();
    if (phases == null || phases.isEmpty()) {
      throw new StudyProtocolException(
          "Greybay requires an explicit phase list to freeze the initialization policy.");
    }
  }

  private static boolean isInitialization(PhaseConfig phase) {
    return phase instanceof ConstructionHeuristicPhaseConfig || phase instanceof CustomPhaseConfig;
  }

  private static void validateResources(
      List<? extends PhaseConfig> phases, GreybayStudyDefinition<?, ?> definition) {
    if (phases == null) return;
    for (var phase : phases) {
      if (!isInitialization(phase) && phase.getTerminationConfig() != null) {
        throw new StudyProtocolException(
            "Search phase supplied a hidden termination; only protocol pipeline caps are allowed.");
      }
      if (phase.getEnvironmentMode() != null
          && phase.getEnvironmentMode() != EnvironmentMode.REPRODUCIBLE) {
        throw new StudyProtocolException("Phase environment must inherit or equal REPRODUCIBLE.");
      }
      String count = null;
      if (phase instanceof ConstructionHeuristicPhaseConfig construction) {
        count = construction.getMoveThreadCount();
      } else if (phase instanceof LocalSearchPhaseConfig local) {
        count = local.getMoveThreadCount();
        var expectedLogging =
            definition
                .resourcePolicy()
                .environmentIdentity()
                .getOrDefault("localSearchStepLoggingMode", "ALL");
        if (!Objects.requireNonNullElse(local.getStepLoggingMode(), LocalSearchStepLoggingMode.ALL)
            .name()
            .equals(expectedLogging)) {
          throw new StudyProtocolException(
              "Local search step logging must match the fixed instrumentation policy ("
                  + expectedLogging
                  + ").");
        }
      } else if (phase instanceof AlnsPhaseConfig alns) {
        count = alns.getMoveThreadCount();
        String mode =
            Objects.requireNonNullElse(alns.getMoveThreadingMode(), AlnsMoveThreadingMode.PROBES)
                .name();
        String expectedMode =
            definition
                .resourcePolicy()
                .environmentIdentity()
                .getOrDefault("alnsMoveThreadingMode", "PROBES");
        if (!mode.equals(expectedMode))
          throw new StudyProtocolException(
              "ALNS move threading mode must match the fixed resource policy.");
        if (alns.getRepairAttemptCount() != null) {
          String expectedAttempts =
              definition.resourcePolicy().environmentIdentity().get("alnsRepairAttemptCount");
          if (!alns.getRepairAttemptCount().toString().equals(expectedAttempts)) {
            throw new StudyProtocolException(
                "ALNS repair attempts must match the explicitly fixed resource policy.");
          }
        }
      } else if (phase instanceof IslandModelPhaseConfig island) {
        count = island.getMoveThreadCount();
        validateResources(island.getPhaseConfigList(), definition);
      } else if (phase instanceof PartitionedSearchPhaseConfig partition) {
        if (partition.getRunnablePartThreadLimit() == null
            || "AUTO".equals(partition.getRunnablePartThreadLimit())) {
          throw new StudyProtocolException(
              "Partitioned search requires an explicit fixed runnable-part limit.");
        }
        validateResources(partition.getPhaseConfigList(), definition);
      }
      if (count != null && !count.equals(definition.resourcePolicy().moveThreadCount())) {
        throw new StudyProtocolException(
            "Phase moveThreadCount ("
                + count
                + ") differs from fixed resources ("
                + definition.resourcePolicy().moveThreadCount()
                + ").");
      }
    }
  }

  private static String invariantIdentity(
      SolverConfig config, RuntimeReconstructionProvider provider) {
    var invariant = config.copyConfig();
    List<PhaseConfig> initialization = new ArrayList<>();
    if (config.getPhaseConfigList() != null) {
      boolean searchSeen = false;
      for (var phase : config.getPhaseConfigList()) {
        if (isInitialization(phase)) {
          if (searchSeen)
            throw new StudyProtocolException("Construction phases must precede search phases.");
          initialization.add((PhaseConfig) phase.copyConfig());
        } else {
          searchSeen = true;
          // The fixed worker topology is part of the phase configuration, never a sampled resource.
          if (phase instanceof IslandModelPhaseConfig
              || phase instanceof PartitionedSearchPhaseConfig) {
            initialization.add((PhaseConfig) phase.copyConfig());
          }
        }
      }
    }
    invariant.setPhaseConfigList(initialization);
    invariant.setTerminationConfig(null);
    invariant.setRandomSeed(null);
    invariant.setEnvironmentMode(EnvironmentMode.REPRODUCIBLE);
    return FrozenSolverConfig.canonicalIdentity(
        invariant, provider == null ? null : provider.identity());
  }
}
