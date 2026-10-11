package greycos.solver.core.config.util;

import java.util.function.BiConsumer;

import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.islandmodel.ResumableSearchCooperationPolicy;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Resolves the single worker pool and completed-step quantum of a parallel hierarchy. */
public final class ResumableSearchConfigResolver {

  private ResumableSearchConfigResolver() {}

  /**
   * Returns null when the hierarchy contains no resumable-search declaration. Otherwise validates
   * the entire hierarchy without modifying any configuration object.
   */
  public static @Nullable Resolved resolve(@NonNull PhaseConfig<?> hierarchyRoot) {
    var resolution = new Resolution();
    String rootPath = elementName(hierarchyRoot);
    visit(hierarchyRoot, rootPath, resolution::collect);
    if (!resolution.enabled) {
      return null;
    }
    visit(hierarchyRoot, rootPath, ResumableSearchConfigResolver::validatePhase);
    if (resolution.workerCount == null || resolution.stepQuantum == null) {
      throw new IllegalArgumentException(
          "The resumable search hierarchy (%s) requires explicit positive workerCount (%s) and stepQuantum (%s)."
              .formatted(rootPath, resolution.workerCount, resolution.stepQuantum));
    }
    return new Resolved(resolution.workerCount, resolution.stepQuantum);
  }

  /** Runtime values shared by all resumable descendants of one parallel hierarchy. */
  public record Resolved(int workerCount, int stepQuantum) {}

  private static final class Resolution {
    private boolean enabled;
    private Integer workerCount;
    private String workerCountPath;
    private Integer stepQuantum;
    private String stepQuantumPath;

    private void collect(PhaseConfig<?> phase, String path) {
      if (!(phase instanceof IslandModelPhaseConfig island)
          || island.getResumableSearchConfig() == null) {
        return;
      }
      enabled = true;
      var config = island.getResumableSearchConfig();
      String configPath = path + "/resumableSearch";
      if (config.getWorkerCount() != null) {
        validatePositive(config.getWorkerCount(), configPath + "/workerCount");
        requireAgreement(
            "workerCount", workerCount, workerCountPath, config.getWorkerCount(), configPath);
        workerCount = config.getWorkerCount();
        workerCountPath = configPath;
      }
      if (config.getStepQuantum() != null) {
        validatePositive(config.getStepQuantum(), configPath + "/stepQuantum");
        requireAgreement(
            "stepQuantum", stepQuantum, stepQuantumPath, config.getStepQuantum(), configPath);
        stepQuantum = config.getStepQuantum();
        stepQuantumPath = configPath;
      }
      validatePositive(config.getStagnationRounds(), configPath + "/stagnationRounds");
      if (config.getCooperationPolicy() == ResumableSearchCooperationPolicy.RESTART_WEAKEST
          && config.getStagnationRounds() == null) {
        throw new IllegalArgumentException(
            "The cooperationPolicy (RESTART_WEAKEST) at (%s) requires positive stagnationRounds (null)."
                .formatted(configPath));
      }
    }
  }

  private static void requireAgreement(
      String property, Integer resolved, String resolvedPath, int declared, String declaredPath) {
    if (resolved != null && resolved != declared) {
      throw new IllegalArgumentException(
          "The %s (%d) at (%s) conflicts with %s (%d) at (%s); all declarations in one resumable search hierarchy must agree."
              .formatted(property, declared, declaredPath, property, resolved, resolvedPath));
    }
  }

  private static void validatePhase(PhaseConfig<?> phase, String path) {
    if (phase instanceof IslandModelPhaseConfig island) {
      validatePositive(island.getIslandCount(), path + "/islandCount");
      validateMoveThreads(island.getMoveThreadCount(), path);
      validateContainerTermination(island.getTerminationConfig(), path + "/termination");
      if ((island.getPhaseConfigList() == null || island.getPhaseConfigList().isEmpty())
          && usesSimulatedAnnealing(island.getLocalSearchType(), island.getAcceptorConfig())) {
        throw new IllegalArgumentException(
            "The simulated annealing configuration at (%s) has no episode-local cooling horizon. Configure an explicit inner localSearch with a positive stepCountLimit, moveCountLimit or scoreCalculationCountLimit termination; enclosing island limits and stepQuantum are shared budgets, not episode cooling horizons."
                .formatted(path));
      }
      if (island.getResumableSearchConfig() != null) {
        validateLegacyOption(island.getMigrationFrequency(), path, "migrationFrequency");
        validateLegacyOption(island.getCompareGlobalEnabled(), path, "compareGlobalEnabled");
        validateLegacyOption(
            island.getReceiveGlobalUpdateFrequency(), path, "receiveGlobalUpdateFrequency");
        validateLegacyOption(island.getCompareGlobalFrequency(), path, "compareGlobalFrequency");
        validateLegacyOption(island.getMigrationTimeout(), path, "migrationTimeout");
      }
    } else if (phase instanceof LocalSearchPhaseConfig localSearch) {
      validateMoveThreads(localSearch.getMoveThreadCount(), path);
      if (usesSimulatedAnnealing(localSearch.getLocalSearchType(), localSearch.getAcceptorConfig())
          && !validateEpisodeCooling(localSearch.getTerminationConfig(), path + "/termination")) {
        throw new IllegalArgumentException(
            "The simulated annealing configuration at (%s) requires an explicit positive episode-local stepCountLimit, moveCountLimit or scoreCalculationCountLimit cooling horizon. Enclosing shared limits and stepQuantum cannot provide it."
                .formatted(path));
      }
    } else if (phase instanceof ConstructionHeuristicPhaseConfig construction) {
      validateMoveThreads(construction.getMoveThreadCount(), path);
    } else if (phase instanceof PartitionedSearchPhaseConfig partition) {
      validateContainerTermination(partition.getTerminationConfig(), path + "/termination");
    } else {
      throw new IllegalArgumentException(
          "The phase (%s) at (%s) is not supported in a resumable search hierarchy. Supported phases are constructionHeuristic, localSearch, islandModel and partitionedSearch."
              .formatted(phase.getClass().getSimpleName(), path));
    }
  }

  private static boolean usesSimulatedAnnealing(
      LocalSearchType localSearchType, LocalSearchAcceptorConfig acceptor) {
    return localSearchType == LocalSearchType.SIMULATED_ANNEALING
        || acceptor != null
            && (acceptor.getSimulatedAnnealingStartingTemperature() != null
                || acceptor.getAcceptorTypeList() != null
                    && acceptor.getAcceptorTypeList().contains(AcceptorType.SIMULATED_ANNEALING));
  }

  /** Empty branches do not contribute a horizon, matching TerminationFactory composition. */
  private static boolean validateEpisodeCooling(TerminationConfig termination, String path) {
    if (termination == null) {
      return false;
    }
    rejectCoolingInput(termination.getSpentLimit(), path, "spentLimit");
    rejectCoolingInput(termination.getMillisecondsSpentLimit(), path, "millisecondsSpentLimit");
    rejectCoolingInput(termination.getSecondsSpentLimit(), path, "secondsSpentLimit");
    rejectCoolingInput(termination.getMinutesSpentLimit(), path, "minutesSpentLimit");
    rejectCoolingInput(termination.getHoursSpentLimit(), path, "hoursSpentLimit");
    rejectCoolingInput(termination.getDaysSpentLimit(), path, "daysSpentLimit");
    rejectCoolingInput(termination.getUnimprovedSpentLimit(), path, "unimprovedSpentLimit");
    rejectCoolingInput(
        termination.getUnimprovedMillisecondsSpentLimit(),
        path,
        "unimprovedMillisecondsSpentLimit");
    rejectCoolingInput(
        termination.getUnimprovedSecondsSpentLimit(), path, "unimprovedSecondsSpentLimit");
    rejectCoolingInput(
        termination.getUnimprovedMinutesSpentLimit(), path, "unimprovedMinutesSpentLimit");
    rejectCoolingInput(
        termination.getUnimprovedHoursSpentLimit(), path, "unimprovedHoursSpentLimit");
    rejectCoolingInput(termination.getUnimprovedDaysSpentLimit(), path, "unimprovedDaysSpentLimit");
    rejectCoolingInput(termination.getUnimprovedStepCountLimit(), path, "unimprovedStepCountLimit");
    rejectCoolingInput(
        termination.getUnimprovedScoreDifferenceThreshold(),
        path,
        "unimprovedScoreDifferenceThreshold");
    rejectCoolingInput(termination.getBestScoreLimit(), path, "bestScoreLimit");
    rejectCoolingInput(termination.getBestScoreFeasible(), path, "bestScoreFeasible");
    rejectCoolingInput(termination.getDiminishedReturnsConfig(), path, "diminishedReturns");
    validatePositive(termination.getStepCountLimit(), path + "/stepCountLimit");
    validatePositive(termination.getMoveCountLimit(), path + "/moveCountLimit");
    validatePositive(
        termination.getScoreCalculationCountLimit(), path + "/scoreCalculationCountLimit");
    boolean hasWorkHorizon =
        termination.getStepCountLimit() != null
            || termination.getMoveCountLimit() != null
            || termination.getScoreCalculationCountLimit() != null;
    if (termination.getTerminationConfigList() != null) {
      for (int i = 0; i < termination.getTerminationConfigList().size(); i++) {
        // Validate every branch even if a preceding branch already supplies a horizon.
        hasWorkHorizon |=
            validateEpisodeCooling(
                termination.getTerminationConfigList().get(i), path + "/termination[" + i + "]");
      }
    }
    return hasWorkHorizon;
  }

  private static void rejectCoolingInput(Object value, String path, String property) {
    if (value != null) {
      throw new IllegalArgumentException(
          "The %s (%s) at (%s) cannot supply a fixed work-only simulated annealing cooling horizon in resumable search. Use only positive stepCountLimit, moveCountLimit or scoreCalculationCountLimit in the episode-local termination tree; put clock, idle and score-target conditions on the enclosing phase."
              .formatted(property, value, path));
    }
  }

  private static void validateContainerTermination(TerminationConfig termination, String path) {
    if (termination == null) {
      return;
    }
    rejectContainerTermination(termination.getDiminishedReturnsConfig(), path, "diminishedReturns");
    rejectContainerTermination(
        termination.getUnimprovedScoreDifferenceThreshold(),
        path,
        "unimprovedScoreDifferenceThreshold");
    if (termination.getTerminationConfigList() != null) {
      for (int i = 0; i < termination.getTerminationConfigList().size(); i++) {
        validateContainerTermination(
            termination.getTerminationConfigList().get(i), path + "/termination[" + i + "]");
      }
    }
  }

  private static void rejectContainerTermination(Object value, String path, String property) {
    if (value != null) {
      throw new IllegalArgumentException(
          "The %s (%s) at (%s) is not supported as a shared resumable search termination. Configure it on an explicit inner localSearch phase instead."
              .formatted(property, value, path));
    }
  }

  private static void validatePositive(Number value, String path) {
    if (value != null && value.longValue() <= 0) {
      throw new IllegalArgumentException(
          "The configuration value (%d) at (%s) must be positive.".formatted(value, path));
    }
  }

  private static void validateMoveThreads(String moveThreadCount, String path) {
    if (moveThreadCount != null && !SolverConfig.MOVE_THREAD_COUNT_NONE.equals(moveThreadCount)) {
      throw new IllegalArgumentException(
          "The moveThreadCount (%s) at (%s) conflicts with the shared resumable search worker pool. Set moveThreadCount to NONE or omit it."
              .formatted(moveThreadCount, path));
    }
  }

  private static void validateLegacyOption(Object value, String path, String property) {
    if (value != null) {
      throw new IllegalArgumentException(
          "The legacy %s (%s) at (%s) cannot be combined with resumableSearch. Configure resumableSearch/cooperationPolicy instead."
              .formatted(property, value, path));
    }
  }

  private static void visit(
      PhaseConfig<?> phase, String path, BiConsumer<PhaseConfig<?>, String> visitor) {
    visitor.accept(phase, path);
    if (phase instanceof IslandModelPhaseConfig island && island.getPhaseConfigList() != null) {
      for (int i = 0; i < island.getPhaseConfigList().size(); i++) {
        var child = island.getPhaseConfigList().get(i);
        visit(child, path + "/phase[" + i + "]/" + elementName(child), visitor);
      }
    } else if (phase instanceof PartitionedSearchPhaseConfig partition
        && partition.getPhaseConfigList() != null) {
      for (int i = 0; i < partition.getPhaseConfigList().size(); i++) {
        var child = partition.getPhaseConfigList().get(i);
        visit(child, path + "/phase[" + i + "]/" + elementName(child), visitor);
      }
    }
  }

  private static String elementName(PhaseConfig<?> phase) {
    if (phase instanceof IslandModelPhaseConfig) {
      return IslandModelPhaseConfig.XML_ELEMENT_NAME;
    } else if (phase instanceof PartitionedSearchPhaseConfig) {
      return PartitionedSearchPhaseConfig.XML_ELEMENT_NAME;
    } else if (phase instanceof ConstructionHeuristicPhaseConfig) {
      return ConstructionHeuristicPhaseConfig.XML_ELEMENT_NAME;
    } else if (phase instanceof LocalSearchPhaseConfig) {
      return LocalSearchPhaseConfig.XML_ELEMENT_NAME;
    }
    return phase.getClass().getSimpleName();
  }
}
