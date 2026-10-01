package greycos.solver.core.impl.localsearch.decider.gls;

import java.util.HashSet;
import java.util.Set;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.SubListSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.SubListSwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.kopt.KOptListMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;

/** Validates the entire selector tree before directed origin selection is enabled. */
public final class GuidedLocalSearchDirectedSelectionValidator {

  private GuidedLocalSearchDirectedSelectionValidator() {}

  public static void validate(MoveSelectorConfig<?> config) {
    validate(config, "moveSelector", SelectionOrder.RANDOM);
  }

  private static void validate(
      MoveSelectorConfig<?> config, String path, SelectionOrder inheritedOrder) {
    if (config == null) {
      throw invalid(path, "the effective move selector configuration is missing");
    }
    if (config.getCacheType() != null && config.getCacheType() != SelectionCacheType.JUST_IN_TIME) {
      throw invalid(path + ".cacheType", "cached moves cannot observe current origin priorities");
    }
    if (config.getSelectionOrder() != null
        && config.getSelectionOrder() != SelectionOrder.RANDOM
        && config.getSelectionOrder() != SelectionOrder.ORIGINAL) {
      throw invalid(
          path + ".selectionOrder", "only JUST_IN_TIME ORIGINAL or RANDOM moves are supported");
    }
    var resolvedOrder = SelectionOrder.resolve(config.getSelectionOrder(), inheritedOrder);
    if (config.getClass() == UnionMoveSelectorConfig.class) {
      var children = ((UnionMoveSelectorConfig) config).getMoveSelectorList();
      if (children == null || children.isEmpty()) {
        throw invalid(path, "the union must explicitly contain supported selectors");
      }
      for (int i = 0; i < children.size(); i++) {
        validate(children.get(i), path + ".union[" + i + "]", resolvedOrder);
      }
      return;
    }
    Set<String> localRecorders = new HashSet<>();
    if (config.getClass() == ChangeMoveSelectorConfig.class) {
      var move = (ChangeMoveSelectorConfig) config;
      entity(move.getEntitySelectorConfig(), path + ".entitySelector", Set.of(), true);
      add(
          localRecorders,
          "entity",
          move.getEntitySelectorConfig() == null ? null : move.getEntitySelectorConfig().getId());
      value(move.getValueSelectorConfig(), path + ".valueSelector", localRecorders, false);
    } else if (config.getClass() == SwapMoveSelectorConfig.class) {
      var move = (SwapMoveSelectorConfig) config;
      if (resolvedOrder == SelectionOrder.ORIGINAL
          && move.getSecondaryEntitySelectorConfig() != null
          && move.getSecondaryEntitySelectorConfig().getMimicSelectorRef() != null) {
        throw invalid(
            path + ".secondaryEntitySelector.mimicSelectorRef",
            "ORIGINAL swap cannot iterate a mimic replay with a ListIterator; use RANDOM swap selection");
      }
      entity(move.getEntitySelectorConfig(), path + ".entitySelector", Set.of(), true);
      add(
          localRecorders,
          "entity",
          move.getEntitySelectorConfig() == null ? null : move.getEntitySelectorConfig().getId());
      entity(
          move.getSecondaryEntitySelectorConfig(),
          path + ".secondaryEntitySelector",
          localRecorders,
          false);
    } else if (config.getClass() == ListChangeMoveSelectorConfig.class) {
      var move = (ListChangeMoveSelectorConfig) config;
      value(move.getValueSelectorConfig(), path + ".valueSelector", Set.of(), true);
      add(
          localRecorders,
          "value",
          move.getValueSelectorConfig() == null ? null : move.getValueSelectorConfig().getId());
      destination(
          move.getDestinationSelectorConfig(), path + ".destinationSelector", localRecorders);
    } else if (config.getClass() == ListSwapMoveSelectorConfig.class) {
      var move = (ListSwapMoveSelectorConfig) config;
      value(move.getValueSelectorConfig(), path + ".valueSelector", Set.of(), true);
      add(
          localRecorders,
          "value",
          move.getValueSelectorConfig() == null ? null : move.getValueSelectorConfig().getId());
      value(
          move.getSecondaryValueSelectorConfig(),
          path + ".secondaryValueSelector",
          localRecorders,
          false);
    } else if (config.getClass() == SubListChangeMoveSelectorConfig.class) {
      var move = (SubListChangeMoveSelectorConfig) config;
      subList(move.getSubListSelectorConfig(), path + ".subListSelector", Set.of(), true);
      add(
          localRecorders,
          "subList",
          move.getSubListSelectorConfig() == null ? null : move.getSubListSelectorConfig().getId());
      destination(
          move.getDestinationSelectorConfig(), path + ".destinationSelector", localRecorders);
    } else if (config.getClass() == SubListSwapMoveSelectorConfig.class) {
      var move = (SubListSwapMoveSelectorConfig) config;
      subList(move.getSubListSelectorConfig(), path + ".subListSelector", Set.of(), true);
      add(
          localRecorders,
          "subList",
          move.getSubListSelectorConfig() == null ? null : move.getSubListSelectorConfig().getId());
      subList(
          move.getSecondarySubListSelectorConfig(),
          path + ".secondarySubListSelector",
          localRecorders,
          false);
    } else if (config.getClass() == KOptListMoveSelectorConfig.class) {
      var move = (KOptListMoveSelectorConfig) config;
      value(move.getOriginSelectorConfig(), path + ".originSelector", Set.of(), true);
      add(
          localRecorders,
          "value",
          move.getOriginSelectorConfig() == null ? null : move.getOriginSelectorConfig().getId());
      value(move.getValueSelectorConfig(), path + ".valueSelector", localRecorders, false);
    } else {
      throw invalid(path, "unsupported selector " + config.getClass().getSimpleName());
    }
  }

  private static void entity(
      EntitySelectorConfig config, String path, Set<String> recorders, boolean origin) {
    if (config == null) {
      return;
    }
    order(config.getSelectionOrder(), path);
    mimic(config.getMimicSelectorRef(), "entity", path, recorders, origin);
    nearby(config.getNearbySelectionConfig(), path + ".nearbySelection", recorders);
  }

  private static void value(
      ValueSelectorConfig config, String path, Set<String> recorders, boolean origin) {
    if (config == null) {
      return;
    }
    order(config.getSelectionOrder(), path);
    mimic(config.getMimicSelectorRef(), "value", path, recorders, origin);
    nearby(config.getNearbySelectionConfig(), path + ".nearbySelection", recorders);
  }

  private static void subList(
      SubListSelectorConfig config, String path, Set<String> recorders, boolean origin) {
    if (config == null) {
      return;
    }
    mimic(config.getMimicSelectorRef(), "subList", path, recorders, origin);
    value(config.getValueSelectorConfig(), path + ".valueSelector", recorders, false);
    nearby(config.getNearbySelectionConfig(), path + ".nearbySelection", recorders);
    if (config.getValueSelectorConfig() != null
        && config.getValueSelectorConfig().getId() != null) {
      throw invalid(
          path + ".valueSelector.id",
          "recording an internal sublist probe would replay a probe instead of the winning sublist");
    }
  }

  private static void destination(
      DestinationSelectorConfig config, String path, Set<String> recorders) {
    if (config == null) {
      return;
    }
    entity(config.getEntitySelectorConfig(), path + ".entitySelector", recorders, false);
    value(config.getValueSelectorConfig(), path + ".valueSelector", recorders, false);
    nearby(config.getNearbySelectionConfig(), path + ".nearbySelection", recorders);
  }

  private static void nearby(NearbySelectionConfig config, String path, Set<String> recorders) {
    if (config == null) {
      return;
    }
    entity(
        config.getOriginEntitySelectorConfig(), path + ".originEntitySelector", recorders, false);
    value(config.getOriginValueSelectorConfig(), path + ".originValueSelector", recorders, false);
    subList(
        config.getOriginSubListSelectorConfig(), path + ".originSubListSelector", recorders, false);
  }

  private static void mimic(
      String reference, String type, String path, Set<String> recorders, boolean origin) {
    if (reference != null && (origin || !recorders.contains(type + ":" + reference))) {
      throw invalid(
          path + ".mimicSelectorRef",
          "external mimic reference (" + reference + ") cannot be directed independently");
    }
  }

  private static void order(SelectionOrder order, String path) {
    if (order == SelectionOrder.PROBABILISTIC) {
      throw invalid(
          path + ".selectionOrder",
          "PROBABILISTIC source iteration has no supported finite-source contract");
    }
  }

  private static void add(Set<String> recorders, String type, String id) {
    if (id != null) {
      recorders.add(type + ":" + id);
    }
  }

  private static IllegalArgumentException invalid(String path, String detail) {
    return new IllegalArgumentException(
        "Guided Local Search directedOriginSelection does not support "
            + path
            + ": "
            + detail
            + ". Maybe use JUST_IN_TIME change, swap, list-change, list-swap, sublist-change, "
            + "sublist-swap or k-opt selectors, optionally in a union, or disable directedOriginSelection.");
  }
}
