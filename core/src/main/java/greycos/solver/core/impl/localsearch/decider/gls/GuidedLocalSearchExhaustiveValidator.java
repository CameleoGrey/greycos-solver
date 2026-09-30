package greycos.solver.core.impl.localsearch.decider.gls;

import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;

/** Conservatively verifies complete iteration of the explicitly configured neighborhood. */
public final class GuidedLocalSearchExhaustiveValidator {

  private GuidedLocalSearchExhaustiveValidator() {}

  public static void validate(MoveSelectorConfig<?> config) {
    if (config == null) {
      throw invalid("an explicit move selector is required");
    }
    requireOriginal(config.getSelectionOrder());
    if (config.getSelectedCountLimit() != null
        || config.getProbabilityWeightFactoryClass() != null
        || config.getFixedProbabilityWeight() != null) {
      throw invalid("sampling or selectedCountLimit on " + config);
    }
    if (config.getClass() == UnionMoveSelectorConfig.class) {
      var union = (UnionMoveSelectorConfig) config;
      if (union.getSelectorProbabilityWeightFactoryClass() != null) {
        throw invalid("a selector probability factory on " + config);
      }
      var children = union.getMoveSelectorList();
      if (children == null || children.isEmpty()) {
        throw invalid("the union must explicitly contain finite move selectors");
      }
      children.forEach(GuidedLocalSearchExhaustiveValidator::validate);
    } else if (config.getClass() == ChangeMoveSelectorConfig.class) {
      var change = (ChangeMoveSelectorConfig) config;
      validateEntity(change.getEntitySelectorConfig());
      validateValue(change.getValueSelectorConfig());
    } else if (config.getClass() == SwapMoveSelectorConfig.class) {
      var swap = (SwapMoveSelectorConfig) config;
      validateEntity(swap.getEntitySelectorConfig());
      validateEntity(swap.getSecondaryEntitySelectorConfig());
    } else if (config.getClass() == ListChangeMoveSelectorConfig.class) {
      var change = (ListChangeMoveSelectorConfig) config;
      validateValue(change.getValueSelectorConfig());
      validateDestination(change.getDestinationSelectorConfig());
    } else if (config.getClass() == ListSwapMoveSelectorConfig.class) {
      var swap = (ListSwapMoveSelectorConfig) config;
      validateValue(swap.getValueSelectorConfig());
      validateValue(swap.getSecondaryValueSelectorConfig());
    } else {
      throw invalid("unsupported selector " + config.getClass().getSimpleName());
    }
  }

  private static void validateEntity(EntitySelectorConfig config) {
    if (config == null) {
      return;
    }
    requireOriginal(config.getSelectionOrder());
    if (config.getSelectedCountLimit() != null
        || config.getNearbySelectionConfig() != null
        || config.getMimicSelectorRef() != null
        || config.getProbabilityWeightFactoryClass() != null) {
      throw invalid("sampling, nearby selection, mimic selection or truncation on " + config);
    }
  }

  private static void validateValue(ValueSelectorConfig config) {
    if (config == null) {
      return;
    }
    requireOriginal(config.getSelectionOrder());
    if (config.getSelectedCountLimit() != null
        || config.getNearbySelectionConfig() != null
        || config.getMimicSelectorRef() != null
        || config.getProbabilityWeightFactoryClass() != null) {
      throw invalid("sampling, nearby selection, mimic selection or truncation on " + config);
    }
  }

  private static void validateDestination(DestinationSelectorConfig config) {
    if (config == null) {
      return;
    }
    if (config.getNearbySelectionConfig() != null) {
      throw invalid("nearby destination selection");
    }
    validateEntity(config.getEntitySelectorConfig());
    validateValue(config.getValueSelectorConfig());
  }

  private static void requireOriginal(SelectionOrder order) {
    if (order != null && order != SelectionOrder.ORIGINAL) {
      throw invalid("selectionOrder (" + order + ")");
    }
  }

  private static IllegalArgumentException invalid(String detail) {
    return new IllegalArgumentException(
        "Guided Local Search EXHAUSTIVE requires complete ORIGINAL change, swap, list-change or list-swap selectors, optionally in a union: "
            + detail
            + ". Maybe use SAMPLED mode for other selectors.");
  }
}
