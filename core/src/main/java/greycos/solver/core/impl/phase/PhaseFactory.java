package greycos.solver.core.impl.phase;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.exhaustivesearch.ExhaustiveSearchPhaseConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.partitionedsearch.PartitionedSearchPhaseConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.alns.DefaultAlnsPhaseFactory;
import greycos.solver.core.impl.constructionheuristic.DefaultConstructionHeuristicPhaseFactory;
import greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyProfileResolver;
import greycos.solver.core.impl.exhaustivesearch.DefaultExhaustiveSearchPhaseFactory;
import greycos.solver.core.impl.geneticalgorithm.DefaultGeneticAlgorithmPhaseFactory;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.islandmodel.DefaultIslandModelPhaseFactory;
import greycos.solver.core.impl.localsearch.DefaultLocalSearchPhaseFactory;
import greycos.solver.core.impl.partitionedsearch.DefaultPartitionedSearchPhaseFactory;
import greycos.solver.core.impl.phase.custom.DefaultCustomPhaseFactory;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.termination.SolverTermination;

public interface PhaseFactory<Solution_> {

  static <Solution_> PhaseFactory<Solution_> create(PhaseConfig<?> phaseConfig) {
    if (phaseConfig instanceof AlnsPhaseConfig alnsPhaseConfig) {
      return new DefaultAlnsPhaseFactory<>(alnsPhaseConfig);
    } else if (phaseConfig instanceof GeneticAlgorithmPhaseConfig geneticAlgorithmPhaseConfig) {
      return new DefaultGeneticAlgorithmPhaseFactory<>(geneticAlgorithmPhaseConfig);
    } else if (IslandModelPhaseConfig.class.isAssignableFrom(phaseConfig.getClass())) {
      return new DefaultIslandModelPhaseFactory<>((IslandModelPhaseConfig) phaseConfig);
    } else if (LocalSearchPhaseConfig.class.isAssignableFrom(phaseConfig.getClass())) {
      return new DefaultLocalSearchPhaseFactory<>((LocalSearchPhaseConfig) phaseConfig);
    } else if (ConstructionHeuristicPhaseConfig.class.isAssignableFrom(phaseConfig.getClass())) {
      return new DefaultConstructionHeuristicPhaseFactory<>(
          (ConstructionHeuristicPhaseConfig) phaseConfig);
    } else if (PartitionedSearchPhaseConfig.class.isAssignableFrom(phaseConfig.getClass())) {
      return new DefaultPartitionedSearchPhaseFactory<>((PartitionedSearchPhaseConfig) phaseConfig);
    } else if (CustomPhaseConfig.class.isAssignableFrom(phaseConfig.getClass())) {
      return new DefaultCustomPhaseFactory<>((CustomPhaseConfig) phaseConfig);
    } else if (ExhaustiveSearchPhaseConfig.class.isAssignableFrom(phaseConfig.getClass())) {
      return new DefaultExhaustiveSearchPhaseFactory<>((ExhaustiveSearchPhaseConfig) phaseConfig);
    } else {
      throw new IllegalArgumentException(
          String.format(
              "Unknown %s type: (%s).",
              PhaseConfig.class.getSimpleName(), phaseConfig.getClass().getName()));
    }
  }

  static <Solution_> List<Phase<Solution_>> buildPhases(
      List<PhaseConfig> phaseConfigList,
      HeuristicConfigPolicy<Solution_> configPolicy,
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      SolverTermination<Solution_> termination) {
    List<Phase<Solution_>> phaseList = new ArrayList<>(phaseConfigList.size());
    boolean isPhaseSelected = false;
    for (int phaseIndex = 0; phaseIndex < phaseConfigList.size(); phaseIndex++) {
      var phaseConfig = phaseConfigList.get(phaseIndex);
      if (phaseIndex > 0) {
        PhaseConfig previousPhaseConfig = phaseConfigList.get(phaseIndex - 1);
        if (!canTerminate(previousPhaseConfig)) {
          throw new IllegalStateException(
              "Solver configuration contains an unreachable phase. "
                  + "Phase #"
                  + phaseIndex
                  + " ("
                  + phaseConfig
                  + ") follows a phase "
                  + "without a configured termination ("
                  + previousPhaseConfig
                  + ").");
        }
      }
      var isConstructionPhase =
          ConstructionHeuristicPhaseConfig.class.isAssignableFrom(phaseConfig.getClass());
      var updatedConfigPolicy =
          configPolicy
              .cloneBuilder()
              .withEntitySorterManner(configPolicy.getEntitySorterManner())
              .withValueSorterManner(configPolicy.getValueSorterManner())
              .withReinitializeVariableFilterEnabled(
                  configPolicy.isReinitializeVariableFilterEnabled())
              .withUnassignedValuesAllowed(configPolicy.isUnassignedValuesAllowed())
              .withConstructionHeuristicNearbyProfiles(
                  ConstructionHeuristicNearbyProfileResolver.resolveForPhase(
                      phaseConfigList, phaseIndex, configPolicy))
              .build();
      // The initialization phase can only be applied to construction heuristics or custom phases
      var isConstructionOrCustomPhase =
          isConstructionPhase || CustomPhaseConfig.class.isAssignableFrom(phaseConfig.getClass());
      // Initialization must finish before any phase that improves an initialized solution.
      var isNextPhaseLocalSearch =
          phaseIndex + 1 < phaseConfigList.size()
              && requiresInitializedSolution(phaseConfigList.get(phaseIndex + 1));
      PhaseFactory<Solution_> phaseFactory = PhaseFactory.create(phaseConfig);
      var phase =
          phaseFactory.buildPhase(
              phaseIndex,
              !isPhaseSelected && isConstructionOrCustomPhase && isNextPhaseLocalSearch,
              updatedConfigPolicy,
              bestSolutionRecaller,
              termination);
      // Ensure only one initialization phase is set
      if (!isPhaseSelected && isConstructionOrCustomPhase && isNextPhaseLocalSearch) {
        isPhaseSelected = true;
      }
      phaseList.add(phase);
    }
    return phaseList;
  }

  static boolean canTerminate(PhaseConfig phaseConfig) {
    if (phaseConfig instanceof ConstructionHeuristicPhaseConfig
        || phaseConfig instanceof ExhaustiveSearchPhaseConfig
        || phaseConfig instanceof CustomPhaseConfig
        || phaseConfig instanceof IslandModelPhaseConfig) { // Termination guaranteed.
      return true;
    }
    TerminationConfig terminationConfig = phaseConfig.getTerminationConfig();
    if (terminationConfig != null && terminationConfig.isConfigured()) {
      return true;
    }
    if (phaseConfig instanceof PartitionedSearchPhaseConfig partitionedSearchPhaseConfig) {
      var childPhaseConfigList = partitionedSearchPhaseConfig.getPhaseConfigList();
      // An unspecified or empty list defaults to construction heuristic and unterminated local
      // search.
      return childPhaseConfigList != null
          && !childPhaseConfigList.isEmpty()
          && childPhaseConfigList.stream().allMatch(PhaseFactory::canTerminate);
    }
    return false;
  }

  static boolean requiresInitializedSolution(PhaseConfig<?> phaseConfig) {
    return phaseConfig instanceof LocalSearchPhaseConfig
        || phaseConfig instanceof AlnsPhaseConfig
        || phaseConfig instanceof GeneticAlgorithmPhaseConfig
        || phaseConfig instanceof IslandModelPhaseConfig;
  }

  Phase<Solution_> buildPhase(
      int phaseIndex,
      boolean lastInitializingPhase,
      HeuristicConfigPolicy<Solution_> solverConfigPolicy,
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      SolverTermination<Solution_> solverTermination);
}
