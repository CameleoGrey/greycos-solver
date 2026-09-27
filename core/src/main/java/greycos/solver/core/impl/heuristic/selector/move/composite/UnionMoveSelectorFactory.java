package greycos.solver.core.impl.heuristic.selector.move.composite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.NearbyAutoConfigurationEnabled;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionProbabilityWeightFactory;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelectorFactory;

public class UnionMoveSelectorFactory<Solution_>
    extends AbstractCompositeMoveSelectorFactory<Solution_, UnionMoveSelectorConfig> {

  public UnionMoveSelectorFactory(UnionMoveSelectorConfig moveSelectorConfig) {
    super(moveSelectorConfig);
  }

  @Override
  protected MoveSelector<Solution_> buildBaseMoveSelector(
      HeuristicConfigPolicy<Solution_> configPolicy,
      SelectionCacheType minimumCacheType,
      boolean randomSelection) {
    var moveSelectorConfigList = new LinkedList<MoveSelectorConfig>();
    List<MoveSelector<Solution_>> moveSelectorList;
    if (configPolicy.getNearbyDistanceMeterClass() != null) {
      moveSelectorList = new ArrayList<>();
      var nearbyMoveSelectorConfigList = new ArrayList<MoveSelectorConfig>();
      var isMixedModel = configPolicy.getSolutionDescriptor().hasBothBasicAndListVariables();
      for (var originalSelectorConfig : config.getMoveSelectorList()) {
        // A generic change or swap can unfold into a union. Apply nearby after expansion,
        // while retaining construction order so earlier mimic recorders are available.
        var selectorConfig =
            MoveSelectorFactory.<Solution_>create(
                    (MoveSelectorConfig<?>) originalSelectorConfig.copyConfig())
                .unfoldMoveSelectorConfig(configPolicy);
        if (selectorConfig instanceof NearbyAutoConfigurationEnabled nearbySelectorConfig) {
          if (selectorConfig.hasNearbySelectionConfig()) {
            throw new IllegalArgumentException(
                """
                The selector configuration (%s) already includes the Nearby Selection setting, making it incompatible with the top-level property nearbyDistanceMeterClass (%s).
                Remove the Nearby setting from the selector configuration or remove the top-level nearbyDistanceMeterClass.\
                """
                    .formatted(nearbySelectorConfig, configPolicy.getNearbyDistanceMeterClass()));
          }
          // We delay the autoconfiguration to the deepest UnionMoveSelectorConfig node in the tree
          // to avoid duplicating configuration
          // when there are nested unionMoveSelector configurations
          var isUnionMoveSelectorConfig = selectorConfig instanceof UnionMoveSelectorConfig;
          // When using a mixed model, we do not enable nearby for basic variables,
          // as it applies only to list variables.
          var isNearbyDisabled =
              isMixedModel && !nearbySelectorConfig.canEnableNearbyInMixedModels();
          if (isUnionMoveSelectorConfig
              && !(originalSelectorConfig instanceof UnionMoveSelectorConfig)
              && originalSelectorConfig
                  instanceof NearbyAutoConfigurationEnabled<?> originalNearbyConfig
              && (!isMixedModel || originalNearbyConfig.canEnableNearbyInMixedModels())
              && config.getSelectorProbabilityWeightFactoryClass() == null) {
            // The generated union contains both ordinary and nearby leaves. Preserve the
            // weight of the two sibling selectors that an already-concrete move receives.
            selectorConfig.setFixedProbabilityWeight(
                2.0
                    * Objects.requireNonNullElse(
                        originalSelectorConfig.getFixedProbabilityWeight(), 1.0));
          }
          if (!isUnionMoveSelectorConfig && !isNearbyDisabled) {
            // Add a new configuration with Nearby Selection enabled.
            nearbyMoveSelectorConfigList.add(
                nearbySelectorConfig.enableNearbySelection(
                    configPolicy.getNearbyDistanceMeterClass(),
                    configPolicy.getRandom().factoryUsage()));
          }
        }
        moveSelectorConfigList.add(selectorConfig);
        moveSelectorList.addAll(
            buildInnerMoveSelectors(
                List.of(selectorConfig), configPolicy, minimumCacheType, randomSelection));
      }
      moveSelectorConfigList.addAll(nearbyMoveSelectorConfigList);
      moveSelectorList.addAll(
          buildInnerMoveSelectors(
              nearbyMoveSelectorConfigList, configPolicy, minimumCacheType, randomSelection));
    } else {
      moveSelectorConfigList.addAll(config.getMoveSelectorList());
      moveSelectorList =
          buildInnerMoveSelectors(
              moveSelectorConfigList, configPolicy, minimumCacheType, randomSelection);
    }

    SelectionProbabilityWeightFactory<Solution_, MoveSelector<Solution_>>
        selectorProbabilityWeightFactory;
    var selectorProbabilityWeightFactoryClass = config.getSelectorProbabilityWeightFactoryClass();
    if (selectorProbabilityWeightFactoryClass != null) {
      if (!randomSelection) {
        throw new IllegalArgumentException(
            "The moveSelectorConfig (%s) with selectorProbabilityWeightFactoryClass (%s) has non-random randomSelection (%s)."
                .formatted(configPolicy, selectorProbabilityWeightFactoryClass, randomSelection));
      }
      selectorProbabilityWeightFactory =
          ConfigUtils.newInstance(
              config,
              "selectorProbabilityWeightFactoryClass",
              selectorProbabilityWeightFactoryClass);
    } else if (randomSelection) {
      Map<MoveSelector<Solution_>, Double> fixedProbabilityWeightMap =
          new HashMap<>(moveSelectorConfigList.size());
      for (int i = 0; i < moveSelectorConfigList.size(); i++) {
        MoveSelectorConfig<?> innerMoveSelectorConfig = moveSelectorConfigList.get(i);
        MoveSelector<Solution_> moveSelector = moveSelectorList.get(i);
        Double fixedProbabilityWeight = innerMoveSelectorConfig.getFixedProbabilityWeight();
        if (fixedProbabilityWeight != null) {
          fixedProbabilityWeightMap.put(moveSelector, fixedProbabilityWeight);
        }
      }
      if (fixedProbabilityWeightMap
          .isEmpty()) { // Will end up using UniformRandomUnionMoveIterator.
        selectorProbabilityWeightFactory = null;
      } else { // Will end up using BiasedRandomUnionMoveIterator.
        selectorProbabilityWeightFactory =
            new FixedSelectorProbabilityWeightFactory<>(fixedProbabilityWeightMap);
      }
    } else {
      selectorProbabilityWeightFactory = null;
    }
    return new UnionMoveSelector<>(
        moveSelectorList, randomSelection, selectorProbabilityWeightFactory);
  }
}
