package greycos.solver.core.impl.localsearch;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.NearbyAutoConfigurationEnabled;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.kopt.KOptListMoveSelectorConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchFeatureComposition;
import greycos.solver.core.config.localsearch.GuidedLocalSearchGuidanceMode;
import greycos.solver.core.config.localsearch.GuidedLocalSearchSearchMode;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.localsearch.decider.acceptor.AcceptorType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchPickEarlyType;
import greycos.solver.core.config.solver.PreviewFeature;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.composite.UnionMoveSelectorFactory;
import greycos.solver.core.impl.localsearch.decider.LocalSearchDecider;
import greycos.solver.core.impl.localsearch.decider.LocalSearchPhaseDecider;
import greycos.solver.core.impl.localsearch.decider.MultiThreadedLocalSearchDecider;
import greycos.solver.core.impl.localsearch.decider.acceptor.Acceptor;
import greycos.solver.core.impl.localsearch.decider.acceptor.AcceptorFactory;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForager;
import greycos.solver.core.impl.localsearch.decider.forager.LocalSearchForagerFactory;
import greycos.solver.core.impl.localsearch.decider.gls.GuidedLocalSearchDecider;
import greycos.solver.core.impl.localsearch.decider.gls.GuidedLocalSearchDirectedSelectionValidator;
import greycos.solver.core.impl.localsearch.decider.gls.GuidedLocalSearchExhaustiveValidator;
import greycos.solver.core.impl.localsearch.decider.gls.GuidedLocalSearchNumber;
import greycos.solver.core.impl.localsearch.decider.gls.GuidedLocalSearchScoreComparator;
import greycos.solver.core.impl.localsearch.decider.gls.GuidedLocalSearchSelectionContext;
import greycos.solver.core.impl.neighborhood.MixedMoveSelector;
import greycos.solver.core.impl.neighborhood.MoveRepository;
import greycos.solver.core.impl.neighborhood.MoveSelectorBasedMoveRepository;
import greycos.solver.core.impl.neighborhood.NeighborhoodsBasedMoveRepository;
import greycos.solver.core.impl.neighborhood.NeighborhoodsMoveSelector;
import greycos.solver.core.impl.neighborhood.stream.DefaultMoveStreamFactory;
import greycos.solver.core.impl.neighborhood.stream.DefaultNeighborhood;
import greycos.solver.core.impl.neighborhood.stream.DefaultNeighborhoodBuilder;
import greycos.solver.core.impl.phase.AbstractPhaseFactory;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.impl.solver.termination.SolverTermination;
import greycos.solver.core.impl.solver.thread.ChildThreadType;
import greycos.solver.core.preview.api.neighborhood.NeighborhoodProvider;

import org.jspecify.annotations.NonNull;

public class DefaultLocalSearchPhaseFactory<Solution_>
    extends AbstractPhaseFactory<Solution_, LocalSearchPhaseConfig> {

  public DefaultLocalSearchPhaseFactory(LocalSearchPhaseConfig phaseConfig) {
    super(phaseConfig);
  }

  @Override
  public LocalSearchPhase<Solution_> buildPhase(
      int phaseIndex,
      boolean lastInitializingPhase,
      HeuristicConfigPolicy<Solution_> solverConfigPolicy,
      BestSolutionRecaller<Solution_> bestSolutionRecaller,
      SolverTermination<Solution_> solverTermination) {
    var environmentMode = resolveEnvironmentMode(solverConfigPolicy);
    var moveThreadCount =
        resolveMoveThreadCount(
            phaseConfig.getMoveThreadCount(), solverConfigPolicy.getMoveThreadCount(), true);
    var phaseConfigPolicy =
        solverConfigPolicy
            .cloneBuilder()
            .withEnvironmentMode(environmentMode)
            .withMoveThreadCount(moveThreadCount)
            .withMultistageMoveSelectionEnabled(true)
            .withNonDoableCandidateRetentionEnabled(
                phaseConfig.getLocalSearchType() == LocalSearchType.GUIDED_LOCAL_SEARCH)
            .withGuidedLocalSearchSelectionContext(
                phaseConfig.getLocalSearchType() == LocalSearchType.GUIDED_LOCAL_SEARCH
                        && phaseConfig.getGuidedLocalSearchConfig() != null
                        && Boolean.TRUE.equals(
                            phaseConfig.getGuidedLocalSearchConfig().getDirectedOriginSelection())
                    ? new GuidedLocalSearchSelectionContext<>()
                    : null)
            .build();
    var phaseTermination = buildPhaseTermination(phaseConfigPolicy, solverTermination);
    var decider = buildDecider(phaseConfigPolicy, phaseTermination);
    return new DefaultLocalSearchPhase.Builder<>(
            phaseIndex,
            environmentMode,
            solverConfigPolicy.getLogIndentation(),
            phaseTermination,
            decider)
        .enableAssertions()
        .build();
  }

  private LocalSearchPhaseDecider<Solution_> buildDecider(
      HeuristicConfigPolicy<Solution_> phaseConfigPolicy,
      PhaseTermination<Solution_> phaseTermination) {
    validateGuidedLocalSearch(phaseConfigPolicy);
    var neighborhoodProviderClass = phaseConfig.<Solution_>getNeighborhoodProviderClass();
    var neighborhoodsEnabled = neighborhoodProviderClass != null;
    if (neighborhoodsEnabled) {
      phaseConfigPolicy.ensurePreviewFeature(PreviewFeature.NEIGHBORHOODS);
    }
    var moveSelectorConfig = phaseConfig.getMoveSelectorConfig();
    var moveSelectorsEnabled = moveSelectorConfig != null;
    if (moveSelectorsEnabled) {
      if (!neighborhoodsEnabled) {
        return buildMoveSelectorBasedDecider(phaseConfigPolicy, phaseTermination);
      } else {
        return buildMixedDecider(phaseConfigPolicy, phaseTermination, neighborhoodProviderClass);
      }
    } else if (neighborhoodsEnabled) {
      return buildNeighborhoodsBasedDecider(
          phaseConfigPolicy, phaseTermination, neighborhoodProviderClass);
    } else { // The default branch; for now, it is move selectors.
      return buildMoveSelectorBasedDecider(phaseConfigPolicy, phaseTermination);
    }
  }

  private LocalSearchPhaseDecider<Solution_> buildMoveSelectorBasedDecider(
      HeuristicConfigPolicy<Solution_> configPolicy, PhaseTermination<Solution_> termination) {
    var moveRepository = new MoveSelectorBasedMoveRepository<>(buildMoveSelector(configPolicy));
    return buildDecider(moveRepository, configPolicy, termination);
  }

  private LocalSearchPhaseDecider<Solution_> buildNeighborhoodsBasedDecider(
      HeuristicConfigPolicy<Solution_> configPolicy,
      PhaseTermination<Solution_> termination,
      Class<? extends NeighborhoodProvider<Solution_>> neighborhoodProviderClass) {
    return buildDecider(
        buildNeighborhoodsBasedMoveRepository(configPolicy, neighborhoodProviderClass),
        configPolicy,
        termination);
  }

  private NeighborhoodsBasedMoveRepository<Solution_> buildNeighborhoodsBasedMoveRepository(
      HeuristicConfigPolicy<Solution_> configPolicy,
      Class<? extends NeighborhoodProvider<Solution_>> neighborhoodProviderClass) {
    if (phaseConfig.getLocalSearchType() == LocalSearchType.VARIABLE_NEIGHBORHOOD_DESCENT) {
      throw new IllegalArgumentException(
          "The localSearchType (%s) does not support the Neighborhoods API. Maybe use a different localSearchType."
              .formatted(phaseConfig.getLocalSearchType()));
    }
    var solutionDescriptor = configPolicy.getSolutionDescriptor();
    var solutionMetaModel = solutionDescriptor.getMetaModel();
    if (solutionMetaModel.genuineEntities().size() > 1) {
      throw new UnsupportedOperationException(
          "Neighborhoods API currently only supports solutions with a single entity class, not"
              + " multiple.");
    }
    var entityMetaModel = solutionMetaModel.genuineEntities().get(0);
    if (entityMetaModel.genuineVariables().size() > 1) {
      throw new UnsupportedOperationException(
          "Neighborhoods API currently only supports solutions with a single variable class, not"
              + " multiple.");
    }

    if (!NeighborhoodProvider.class.isAssignableFrom(neighborhoodProviderClass)) {
      throw new IllegalArgumentException(
          "The neighborhoodProviderClass (%s) does not implement %s."
              .formatted(neighborhoodProviderClass, NeighborhoodProvider.class.getSimpleName()));
    }
    var neighborhoodProvider =
        ConfigUtils.newInstance(
            LocalSearchPhaseConfig.class::getSimpleName,
            "neighborhoodProviderClass",
            neighborhoodProviderClass);
    var neighborhoodBuilder = new DefaultNeighborhoodBuilder<>(solutionMetaModel);
    var moveStreamFactory =
        new DefaultMoveStreamFactory<>(solutionDescriptor, configPolicy.getEnvironmentMode());
    var moveDefinitionList =
        ((DefaultNeighborhood<Solution_>)
                neighborhoodProvider.defineNeighborhood(neighborhoodBuilder))
            .getMoveProviderList();
    return new NeighborhoodsBasedMoveRepository<>(moveStreamFactory, moveDefinitionList);
  }

  private LocalSearchPhaseDecider<Solution_> buildMixedDecider(
      HeuristicConfigPolicy<Solution_> configPolicy,
      PhaseTermination<Solution_> termination,
      Class<? extends NeighborhoodProvider<Solution_>> neighborhoodProviderClass) {
    var neighborhoodsMoveSelector =
        new NeighborhoodsMoveSelector<>(
            buildNeighborhoodsBasedMoveRepository(configPolicy, neighborhoodProviderClass));
    var legacyMoveSelector = buildMoveSelector(configPolicy);
    var moveSelector = new MixedMoveSelector<>(legacyMoveSelector, neighborhoodsMoveSelector);
    var moveRepository = new MoveSelectorBasedMoveRepository<>(moveSelector);
    return buildDecider(moveRepository, configPolicy, termination);
  }

  private LocalSearchPhaseDecider<Solution_> buildDecider(
      MoveRepository<Solution_> moveRepository,
      HeuristicConfigPolicy<Solution_> configPolicy,
      PhaseTermination<Solution_> termination) {
    if (phaseConfig.getLocalSearchType() == LocalSearchType.GUIDED_LOCAL_SEARCH) {
      return buildGuidedLocalSearchDecider(moveRepository, configPolicy, termination);
    }
    var acceptor =
        buildAcceptor(
            configPolicy, moveRepository instanceof NeighborhoodsBasedMoveRepository<Solution_>);
    var forager = buildForager();
    if (moveRepository.isNeverEnding() && !forager.supportsNeverEndingMoveSelector()) {
      throw new IllegalStateException(
          """
          The move repository (%s) is neverEnding (%s), but the forager (%s) does not support it.
          Maybe configure the <forager> with an <acceptedCountLimit>.\
          """
              .formatted(moveRepository, moveRepository.isNeverEnding(), forager));
    }
    var moveThreadCount = configPolicy.getMoveThreadCount();
    var environmentMode = configPolicy.getEnvironmentMode();
    var decider =
        moveThreadCount == null
            ? new LocalSearchDecider<>(
                configPolicy.getLogIndentation(), termination, moveRepository, acceptor, forager)
            : new MultiThreadedLocalSearchDecider<>(
                configPolicy.getLogIndentation(),
                termination,
                moveRepository,
                acceptor,
                forager,
                configPolicy.buildThreadFactory(ChildThreadType.MOVE_THREAD),
                moveThreadCount,
                moveThreadCount
                    * (configPolicy.getMoveThreadBufferSize() != null
                        ? configPolicy.getMoveThreadBufferSize()
                        : 10));
    decider.enableAssertions(environmentMode);
    return decider;
  }

  protected Acceptor<Solution_> buildAcceptor(
      HeuristicConfigPolicy<Solution_> configPolicy, boolean neighborhoodsEnabled) {
    var acceptorConfig = phaseConfig.getAcceptorConfig();
    var localSearchType = phaseConfig.getLocalSearchType();
    if (acceptorConfig != null) {
      if (localSearchType != null) {
        throw new IllegalArgumentException(
            "The localSearchType (%s) must not be configured if the acceptorConfig (%s) is explicitly configured."
                .formatted(localSearchType, acceptorConfig));
      }
      return buildAcceptor(acceptorConfig, configPolicy);
    } else {
      var updatedLocalSearchType =
          Objects.requireNonNullElse(localSearchType, LocalSearchType.LATE_ACCEPTANCE);
      acceptorConfig = new LocalSearchAcceptorConfig();
      if (neighborhoodsEnabled
          && updatedLocalSearchType == LocalSearchType.VARIABLE_NEIGHBORHOOD_DESCENT) {
        // Maybe works, but never tested.
        throw new UnsupportedOperationException(
            "Variable Neighborhood descent is not yet supported with the Neighborhoods API.");
      }
      var acceptorType = getAcceptorType(neighborhoodsEnabled, updatedLocalSearchType);
      acceptorConfig.setAcceptorTypeList(Collections.singletonList(acceptorType));
      return buildAcceptor(acceptorConfig, configPolicy);
    }
  }

  private static @NonNull AcceptorType getAcceptorType(
      boolean neighborhoodsEnabled, LocalSearchType localSearchType) {
    var acceptorType =
        switch (localSearchType) {
          case HILL_CLIMBING, VARIABLE_NEIGHBORHOOD_DESCENT -> AcceptorType.HILL_CLIMBING;
          case TABU_SEARCH -> AcceptorType.ENTITY_TABU;
          case SIMULATED_ANNEALING -> AcceptorType.SIMULATED_ANNEALING;
          case LATE_ACCEPTANCE -> AcceptorType.LATE_ACCEPTANCE;
          case DIVERSIFIED_LATE_ACCEPTANCE -> AcceptorType.DIVERSIFIED_LATE_ACCEPTANCE;
          case GREAT_DELUGE -> AcceptorType.GREAT_DELUGE;
          case GUIDED_LOCAL_SEARCH ->
              throw new IllegalStateException(
                  "Guided Local Search uses its own decision controller.");
        };
    if (neighborhoodsEnabled && acceptorType.isTabu()) {
      throw new UnsupportedOperationException(
          "Tabu search is not yet supported with the Neighborhoods API.");
    }
    return acceptorType;
  }

  private Acceptor<Solution_> buildAcceptor(
      LocalSearchAcceptorConfig acceptorConfig, HeuristicConfigPolicy<Solution_> configPolicy) {
    return AcceptorFactory.<Solution_>create(acceptorConfig).buildAcceptor(configPolicy);
  }

  protected LocalSearchForager<Solution_> buildForager() {
    LocalSearchForagerConfig foragerConfig_;
    if (phaseConfig.getForagerConfig() != null) {
      if (phaseConfig.getLocalSearchType() != null) {
        throw new IllegalArgumentException(
            "The localSearchType (%s) must not be configured if the foragerConfig (%s) is explicitly configured."
                .formatted(phaseConfig.getLocalSearchType(), phaseConfig.getForagerConfig()));
      }
      foragerConfig_ = phaseConfig.getForagerConfig();
    } else {
      var localSearchType_ =
          Objects.requireNonNullElse(
              phaseConfig.getLocalSearchType(), LocalSearchType.LATE_ACCEPTANCE);
      foragerConfig_ = new LocalSearchForagerConfig();
      switch (localSearchType_) {
        case HILL_CLIMBING:
          foragerConfig_.setAcceptedCountLimit(1);
          break;
        case TABU_SEARCH:
          // Slow stepping algorithm
          foragerConfig_.setAcceptedCountLimit(1000);
          break;
        case SIMULATED_ANNEALING, LATE_ACCEPTANCE, DIVERSIFIED_LATE_ACCEPTANCE, GREAT_DELUGE:
          // Fast stepping algorithm
          foragerConfig_.setAcceptedCountLimit(1);
          break;
        case VARIABLE_NEIGHBORHOOD_DESCENT:
          foragerConfig_.setPickEarlyType(LocalSearchPickEarlyType.FIRST_LAST_STEP_SCORE_IMPROVING);
          break;
        default:
          throw new IllegalStateException(
              "The localSearchType (%s) is not implemented.".formatted(localSearchType_));
      }
    }
    return LocalSearchForagerFactory.<Solution_>create(foragerConfig_).buildForager();
  }

  @SuppressWarnings("rawtypes")
  private MoveSelector<Solution_> buildMoveSelector(HeuristicConfigPolicy<Solution_> configPolicy) {
    MoveSelector<Solution_> moveSelector;
    var defaultCacheType = SelectionCacheType.JUST_IN_TIME;
    var defaultSelectionOrder = pickSelectionOrder();
    // GLS bounds attempted candidates, including non-doable ones, in its own decision loop.
    var skipNonDoableMoves =
        phaseConfig.getLocalSearchType() != LocalSearchType.GUIDED_LOCAL_SEARCH;

    var moveSelectorConfig = phaseConfig.getMoveSelectorConfig();
    if (moveSelectorConfig == null) {
      moveSelector =
          new UnionMoveSelectorFactory<Solution_>(determineDefaultMoveSelectorConfig(configPolicy))
              .buildMoveSelector(
                  configPolicy, defaultCacheType, defaultSelectionOrder, skipNonDoableMoves);
    } else {
      AbstractMoveSelectorFactory<Solution_, ?> moveSelectorFactory =
          MoveSelectorFactory.create(moveSelectorConfig);

      if (configPolicy.getNearbyDistanceMeterClass() != null
          && NearbyAutoConfigurationEnabled.class.isAssignableFrom(moveSelectorConfig.getClass())
          && !UnionMoveSelectorConfig.class.isAssignableFrom(moveSelectorConfig.getClass())) {
        // The move selector config is not a composite selector, but it accepts Nearby
        // autoconfiguration.
        // We create a new UnionMoveSelectorConfig with the existing selector to enable Nearby
        // autoconfiguration.
        var moveSelectorCopy = (MoveSelectorConfig) moveSelectorConfig.copyConfig();
        var updatedConfig = new UnionMoveSelectorConfig().withMoveSelectors(moveSelectorCopy);
        moveSelectorFactory = MoveSelectorFactory.create(updatedConfig);
      }
      moveSelector =
          moveSelectorFactory.buildMoveSelector(
              configPolicy, defaultCacheType, defaultSelectionOrder, skipNonDoableMoves);
    }
    return moveSelector;
  }

  private SelectionOrder pickSelectionOrder() {
    return phaseConfig.getLocalSearchType() == LocalSearchType.VARIABLE_NEIGHBORHOOD_DESCENT
            || (phaseConfig.getLocalSearchType() == LocalSearchType.GUIDED_LOCAL_SEARCH
                && phaseConfig.getGuidedLocalSearchConfig() != null
                && phaseConfig.getGuidedLocalSearchConfig().getSearchMode()
                    == GuidedLocalSearchSearchMode.EXHAUSTIVE)
        ? SelectionOrder.ORIGINAL
        : SelectionOrder.RANDOM;
  }

  private void validateGuidedLocalSearch(HeuristicConfigPolicy<Solution_> configPolicy) {
    var gls = phaseConfig.getGuidedLocalSearchConfig();
    if (phaseConfig.getLocalSearchType() != LocalSearchType.GUIDED_LOCAL_SEARCH) {
      if (gls != null) {
        throw new IllegalArgumentException(
            "The guidedLocalSearch configuration requires localSearchType GUIDED_LOCAL_SEARCH.");
      }
      return;
    }
    if (gls == null) gls = new GuidedLocalSearchConfig();
    var guidanceMode = resolveGuidanceMode(gls);
    var composition = resolveFeatureComposition(gls);
    if (composition != GuidedLocalSearchFeatureComposition.AUTOMATIC
        && gls.getFeatureProviderClass() == null) {
      throw new IllegalArgumentException(
          "Guided Local Search featureComposition ("
              + composition
              + ") requires guidedLocalSearch.featureProviderClass.");
    }
    if (composition == GuidedLocalSearchFeatureComposition.AUTOMATIC
        && gls.getFeatureProviderClass() != null) {
      throw new IllegalArgumentException(
          "Guided Local Search featureComposition AUTOMATIC cannot specify featureProviderClass. Select COMBINED to use both feature families.");
    }
    if (composition == GuidedLocalSearchFeatureComposition.CUSTOM
        && (Boolean.TRUE.equals(gls.getAutomaticListOwnershipEnabled())
            || Boolean.TRUE.equals(gls.getDirectedOriginSelection())
            || gls.getLevelScaleList() != null)) {
      throw new IllegalArgumentException(
          "Guided Local Search featureComposition CUSTOM does not support automaticListOwnershipEnabled, directedOriginSelection or levelScale. Select AUTOMATIC or COMBINED.");
    }
    if (guidanceMode == GuidedLocalSearchGuidanceMode.ALL_LEVELS
        && gls.getTargetScoreLevelIndex() != null) {
      throw new IllegalArgumentException(
          "Guided Local Search ALL_LEVELS cannot specify targetScoreLevelIndex. Remove the target or select FIXED_TARGET.");
    }
    if (phaseConfig.getAcceptorConfig() != null || phaseConfig.getForagerConfig() != null) {
      throw new IllegalArgumentException(
          "Guided Local Search has its own acceptance and first-improvement decisions; remove acceptor and forager configuration.");
    }
    if (gls.getPenaltyFactor() != null && gls.getPenaltyFactor().signum() <= 0) {
      throw new IllegalArgumentException(
          "The guidedLocalSearch penaltyFactor (" + gls.getPenaltyFactor() + ") must be positive.");
    }
    int levelCount = configPolicy.getScoreDefinition().getLevelsSize();
    GuidedLocalSearchScoreComparator.requireSupportedScoreType(
        configPolicy.getScoreDefinition().getScoreClass());
    for (Number level : configPolicy.getScoreDefinition().getZeroScore().toLevelNumbers()) {
      GuidedLocalSearchNumber.of(level);
    }
    int target = Objects.requireNonNullElse(gls.getTargetScoreLevelIndex(), levelCount - 1);
    if (target < 0 || target >= levelCount) {
      throw new IllegalArgumentException(
          "The guidedLocalSearch targetScoreLevelIndex ("
              + target
              + ") must be between 0 and "
              + (levelCount - 1)
              + ".");
    }
    if (gls.getFocusStepLimit() != null && gls.getFocusStepLimit() <= 0
        || gls.getFocusPenaltyUpdateLimit() != null && gls.getFocusPenaltyUpdateLimit() <= 0) {
      throw new IllegalArgumentException(
          "Guided Local Search focusStepLimit and focusPenaltyUpdateLimit must be positive.");
    }
    if (gls.getMaxPenaltyUpdatesPerStep() != null && gls.getMaxPenaltyUpdatesPerStep() <= 0
        || gls.getExcursionStepLimit() != null && gls.getExcursionStepLimit() <= 0
        || gls.getExcursionRepairStepLimit() != null && gls.getExcursionRepairStepLimit() <= 0) {
      throw new IllegalArgumentException(
          "Guided Local Search maxPenaltyUpdatesPerStep (%s), excursionStepLimit (%s) and excursionRepairStepLimit (%s) must be positive when specified."
              .formatted(
                  gls.getMaxPenaltyUpdatesPerStep(),
                  gls.getExcursionStepLimit(),
                  gls.getExcursionRepairStepLimit()));
    }
    if (gls.getLevelScaleList() != null) {
      var configuredLevels = new java.util.HashSet<Integer>();
      for (var scale : gls.getLevelScaleList()) {
        if (scale == null
            || scale.getScoreLevelIndex() == null
            || scale.getScoreLevelIndex() < 0
            || scale.getScoreLevelIndex() >= levelCount
            || scale.getScale() == null
            || scale.getScale().signum() <= 0) {
          throw new IllegalArgumentException(
              "Guided Local Search levelScale ("
                  + scale
                  + ") requires scoreLevelIndex between 0 and "
                  + (levelCount - 1)
                  + " and a positive scale.");
        }
        if (!configuredLevels.add(scale.getScoreLevelIndex())) {
          throw new IllegalArgumentException(
              "Duplicate Guided Local Search levelScale for scoreLevelIndex ("
                  + scale.getScoreLevelIndex()
                  + ").");
        }
      }
    }
    if (gls.getSampleSize() != null && gls.getSampleSize() < 1) {
      throw new IllegalArgumentException("The guidedLocalSearch sampleSize must be positive.");
    }
    if (gls.getMaxUnproductiveRounds() != null && gls.getMaxUnproductiveRounds() < 1) {
      throw new IllegalArgumentException(
          "The guidedLocalSearch maxUnproductiveRounds must be positive.");
    }
    if (gls.getSearchMode() == GuidedLocalSearchSearchMode.EXHAUSTIVE) {
      if (gls.getSampleSize() != null) {
        throw new IllegalArgumentException(
            "The guidedLocalSearch sampleSize must not be set in EXHAUSTIVE mode.");
      }
      if (phaseConfig.getNeighborhoodProviderClass() != null
          || configPolicy.getNearbyDistanceMeterClass() != null) {
        throw new IllegalArgumentException(
            "Guided Local Search EXHAUSTIVE does not support Neighborhoods or automatic nearby selection. Maybe use SAMPLED mode.");
      }
      GuidedLocalSearchExhaustiveValidator.validate(phaseConfig.getMoveSelectorConfig());
    }
    if (Boolean.TRUE.equals(gls.getDirectedOriginSelection())) {
      if (gls.getSearchMode() == GuidedLocalSearchSearchMode.EXHAUSTIVE
          || phaseConfig.getNeighborhoodProviderClass() != null) {
        throw new IllegalArgumentException(
            "Guided Local Search directedOriginSelection requires SAMPLED mode and a supported move selector tree.");
      }
      GuidedLocalSearchDirectedSelectionValidator.validate(
          phaseConfig.getMoveSelectorConfig() == null
              ? determineDefaultMoveSelectorConfig(configPolicy)
              : phaseConfig.getMoveSelectorConfig());
    }
  }

  private LocalSearchPhaseDecider<Solution_> buildGuidedLocalSearchDecider(
      MoveRepository<Solution_> repository,
      HeuristicConfigPolicy<Solution_> configPolicy,
      PhaseTermination<Solution_> termination) {
    var gls =
        Objects.requireNonNullElseGet(
            phaseConfig.getGuidedLocalSearchConfig(), GuidedLocalSearchConfig::new);
    var mode = Objects.requireNonNullElse(gls.getSearchMode(), GuidedLocalSearchSearchMode.SAMPLED);
    if (mode == GuidedLocalSearchSearchMode.EXHAUSTIVE && repository.isNeverEnding()) {
      throw new IllegalArgumentException(
          "Guided Local Search EXHAUSTIVE requires a finite move repository.");
    }
    var threadCount = Objects.requireNonNullElse(configPolicy.getMoveThreadCount(), 0);
    if (gls.getFeatureProviderClass() != null
        && !GuidedLocalSearchFeatureProvider.class.isAssignableFrom(
            gls.getFeatureProviderClass())) {
      throw new IllegalArgumentException(
          "The guidedLocalSearch featureProviderClass must implement GuidedLocalSearchFeatureProvider.");
    }
    var provider =
        gls.getFeatureProviderClass() == null
            ? null
            : ConfigUtils.newInstance(gls, "featureProviderClass", gls.getFeatureProviderClass());
    var decider =
        new GuidedLocalSearchDecider<Solution_>(
            configPolicy.getLogIndentation(),
            termination,
            repository,
            provider,
            resolveGuidanceMode(gls),
            resolveFeatureComposition(gls),
            Boolean.TRUE.equals(gls.getAutomaticListOwnershipEnabled()),
            Objects.requireNonNullElse(gls.getPenaltyFactor(), new BigDecimal("0.1")),
            Objects.requireNonNullElse(
                gls.getTargetScoreLevelIndex(),
                configPolicy.getScoreDefinition().getLevelsSize() - 1),
            gls.getLevelScaleList() == null ? List.of() : gls.getLevelScaleList(),
            Objects.requireNonNullElse(gls.getFocusStepLimit(), 64),
            Objects.requireNonNullElse(gls.getFocusPenaltyUpdateLimit(), 8),
            Objects.requireNonNullElse(gls.getMaxPenaltyUpdatesPerStep(), 64),
            Objects.requireNonNullElse(gls.getExcursionStepLimit(), 8),
            Objects.requireNonNullElse(gls.getExcursionRepairStepLimit(), 64),
            mode,
            Objects.requireNonNullElse(gls.getSampleSize(), 1000),
            Objects.requireNonNullElse(gls.getMaxUnproductiveRounds(), 3),
            Boolean.TRUE.equals(gls.getResetPenaltiesOnNewBest()),
            configPolicy.getGuidedLocalSearchSelectionContext(),
            threadCount == 0 ? null : configPolicy.buildThreadFactory(ChildThreadType.MOVE_THREAD),
            threadCount,
            threadCount * Objects.requireNonNullElse(configPolicy.getMoveThreadBufferSize(), 10));
    decider.enableAssertions(configPolicy.getEnvironmentMode());
    return decider;
  }

  private static GuidedLocalSearchGuidanceMode resolveGuidanceMode(GuidedLocalSearchConfig config) {
    return Objects.requireNonNullElse(
        config.getGuidanceMode(),
        config.getTargetScoreLevelIndex() == null
            ? GuidedLocalSearchGuidanceMode.ALL_LEVELS
            : GuidedLocalSearchGuidanceMode.FIXED_TARGET);
  }

  private static GuidedLocalSearchFeatureComposition resolveFeatureComposition(
      GuidedLocalSearchConfig config) {
    if (config.getFeatureComposition() != null) return config.getFeatureComposition();
    if (resolveGuidanceMode(config) == GuidedLocalSearchGuidanceMode.FIXED_TARGET)
      return GuidedLocalSearchFeatureComposition.CUSTOM;
    return config.getFeatureProviderClass() == null
        ? GuidedLocalSearchFeatureComposition.AUTOMATIC
        : GuidedLocalSearchFeatureComposition.COMBINED;
  }

  private UnionMoveSelectorConfig determineDefaultMoveSelectorConfig(
      HeuristicConfigPolicy<Solution_> configPolicy) {
    var solutionDescriptor = configPolicy.getSolutionDescriptor();
    if (solutionDescriptor.hasBothBasicAndListVariables()) {
      var moveSelectorList = new ArrayList<MoveSelectorConfig>();
      // Specific basic variable moves
      moveSelectorList.addAll(
          List.of(new ChangeMoveSelectorConfig(), new SwapMoveSelectorConfig()));
      // Specific list variable moves
      moveSelectorList.addAll(
          List.of(
              new ListChangeMoveSelectorConfig(),
              new ListSwapMoveSelectorConfig(),
              new KOptListMoveSelectorConfig()));
      return new UnionMoveSelectorConfig().withMoveSelectorList(moveSelectorList);
    } else if (solutionDescriptor.hasListVariable()) {
      // We only have the one list variable.
      return new UnionMoveSelectorConfig()
          .withMoveSelectors(
              new ListChangeMoveSelectorConfig(),
              new ListSwapMoveSelectorConfig(),
              new KOptListMoveSelectorConfig());
    } else {
      // We only have basic variables.
      var basicVariableDescriptorList = solutionDescriptor.getBasicVariableDescriptorList();
      return new UnionMoveSelectorConfig()
          .withMoveSelectors(new ChangeMoveSelectorConfig(), new SwapMoveSelectorConfig());
    }
  }
}
