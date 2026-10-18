package greycos.solver.core.impl.exhaustivesearch;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import greycos.solver.core.config.exhaustivesearch.ExhaustiveSearchPhaseConfig;
import greycos.solver.core.config.heuristic.selector.SelectorConfig;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.DestinationSelectorConfig;
import greycos.solver.core.config.heuristic.selector.list.SubListSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.exhaustivesearch.decider.JointVariableExhaustiveSearchDecider;
import greycos.solver.core.impl.exhaustivesearch.decider.JointVariableExhaustiveSearchDecider.DecisionFilter;
import greycos.solver.core.impl.exhaustivesearch.node.bounder.TrendBasedScoreBounder;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.composite.CartesianProductMoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.composite.UnionMoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMoveSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ListChangeMoveSelectorFactory;
import greycos.solver.core.impl.solver.recaller.BestSolutionRecaller;
import greycos.solver.core.impl.solver.termination.PhaseTermination;

/**
 * Builds the joint search using existing selector configuration and ordinary list insertion moves.
 */
final class JointExhaustiveSearchDeciderFactory<Solution_> {

  private final ExhaustiveSearchPhaseConfig config;
  private final HeuristicConfigPolicy<Solution_> policy;
  private final List<GenuineVariableDescriptor<Solution_>> variables;
  private final DecisionFilter<Solution_> decisionFilter = new DecisionFilter<>();

  JointExhaustiveSearchDeciderFactory(
      ExhaustiveSearchPhaseConfig config, HeuristicConfigPolicy<Solution_> policy) {
    this.config = config;
    // Ordinary list destinations must exclude unassigned values while construction is in progress.
    this.policy = policy.cloneBuilder().withUnassignedValuesAllowed(true).build();
    var distinct = new LinkedHashSet<GenuineVariableDescriptor<Solution_>>();
    policy
        .getSolutionDescriptor()
        .getGenuineEntityDescriptors()
        .forEach(entity -> distinct.addAll(entity.getGenuineVariableDescriptorList()));
    variables = List.copyOf(distinct);
  }

  JointVariableExhaustiveSearchDecider<Solution_, ?> build(
      BestSolutionRecaller<Solution_> recaller,
      PhaseTermination<Solution_> termination,
      boolean bounded) {
    validateFiniteSelection(config.getEntitySelectorConfig(), "entitySelector");
    MoveSelector<Solution_> configuredMoves = null;
    if (config.getMoveSelectorConfig() != null) {
      Set<GenuineVariableDescriptor<Solution_>> covered = new LinkedHashSet<>();
      var normalized = normalize(config.getMoveSelectorConfig(), covered);
      if (!covered.containsAll(variables)) {
        var missing = variables.stream().filter(variable -> !covered.contains(variable)).toList();
        throw new IllegalArgumentException(
            ("The exhaustiveSearch moveSelectorConfig (%s) does not cover planning variables (%s). "
                    + "Use automatic selectors or a union of explicitly targeted change/listChange selectors.")
                .formatted(config.getMoveSelectorConfig(), missing));
      }
      configuredMoves = buildMoves(normalized);
    }
    var bindings =
        new ArrayList<JointVariableExhaustiveSearchDecider.VariableSelector<Solution_>>();
    for (var variable : variables) {
      var entityConfig = entityConfig(variable.getEntityDescriptor());
      var entities =
          EntitySelectorFactory.<Solution_>create(entityConfig)
              .buildEntitySelector(policy, SelectionCacheType.PHASE, SelectionOrder.ORIGINAL);
      var moves =
          configuredMoves == null
              ? buildMoves(defaultMoves(variable, entityConfig))
              : configuredMoves;
      var sorter =
          ValueSelectorConfig.hasSorter(policy.getValueSorterManner(), variable)
              ? ValueSelectorConfig.determineSorter(policy.getValueSorterManner(), variable)
              : null;
      bindings.add(
          new JointVariableExhaustiveSearchDecider.VariableSelector<>(
              variable, entities, moves, sorter));
    }
    var bounder =
        bounded
            ? new TrendBasedScoreBounder<>(
                policy.getScoreDefinition(), policy.getInitializingScoreTrend())
            : null;
    var decider =
        new JointVariableExhaustiveSearchDecider<>(
            policy.getLogIndentation(),
            recaller,
            termination,
            bindings,
            decisionFilter,
            bounded,
            bounder);
    decider.enableAssertions(policy.getEnvironmentMode());
    return decider;
  }

  private EntitySelectorConfig entityConfig(EntityDescriptor<Solution_> entity) {
    var selector =
        config.getEntitySelectorConfig() == null
            ? new EntitySelectorConfig()
            : config.getEntitySelectorConfig().copyConfig();
    if (selector.getEntityClass() != null && selector.getEntityClass() != entity.getEntityClass()) {
      throw new IllegalArgumentException(
          ("The exhaustiveSearch entitySelector (%s) does not cover entity class (%s). "
                  + "Omit the phase entitySelector and target the existing move selectors separately.")
              .formatted(selector, entity.getEntityClass().getName()));
    }
    selector.setEntityClass(entity.getEntityClass());
    if (config.getEntitySelectorConfig() == null
        && EntitySelectorConfig.hasSorter(policy.getEntitySorterManner(), entity)) {
      selector
          .withCacheType(SelectionCacheType.PHASE)
          .withSelectionOrder(SelectionOrder.SORTED)
          .withSorterManner(policy.getEntitySorterManner());
    }
    return selector;
  }

  private ValueSelectorConfig valueConfig(GenuineVariableDescriptor<Solution_> variable) {
    var value = new ValueSelectorConfig(variable.getVariableName());
    if (ValueSelectorConfig.hasSorter(policy.getValueSorterManner(), variable)) {
      value
          .withCacheType(
              variable.canExtractValueRangeFromSolution()
                  ? SelectionCacheType.PHASE
                  : SelectionCacheType.STEP)
          .withSelectionOrder(SelectionOrder.SORTED)
          .withSorterManner(policy.getValueSorterManner());
    }
    return value;
  }

  private EntitySelectorConfig normalizeEntityConfig(
      EntitySelectorConfig selector, GenuineVariableDescriptor<Solution_> variable) {
    if (selector == null) {
      return entityConfig(variable.getEntityDescriptor());
    }
    var copy = selector.copyConfig();
    if (copy.getEntityClass() == null) {
      copy.setEntityClass(variable.getEntityDescriptor().getEntityClass());
    }
    return copy;
  }

  private MoveSelectorConfig<?> defaultMoves(
      GenuineVariableDescriptor<Solution_> variable, EntitySelectorConfig entity) {
    if (variable instanceof ListVariableDescriptor<Solution_>) {
      return new ListChangeMoveSelectorConfig()
          .withValueSelectorConfig(valueConfig(variable))
          .withDestinationSelectorConfig(
              new DestinationSelectorConfig()
                  .withEntitySelectorConfig(entity.copyConfig())
                  .withValueSelectorConfig(new ValueSelectorConfig(variable.getVariableName())));
    }
    return new ChangeMoveSelectorConfig()
        .withEntitySelectorConfig(entity.copyConfig())
        .withValueSelectorConfig(valueConfig(variable));
  }

  private MoveSelector<Solution_> buildMoves(MoveSelectorConfig<?> moveConfig) {
    validateFiniteSelection(moveConfig, "moveSelector");
    // The legacy ES list selector appends only. A fixed element order instead requires every
    // insertion position, as provided by the ordinary list selector.
    var moves =
        moveFactory(moveConfig)
            .buildMoveSelector(
                policy, SelectionCacheType.JUST_IN_TIME, SelectionOrder.ORIGINAL, false);
    return requireFiniteMoves(moves, moveConfig);
  }

  private MoveSelector<Solution_> requireFiniteMoves(
      MoveSelector<Solution_> moves, MoveSelectorConfig<?> moveConfig) {
    if (moves.isNeverEnding()) {
      throw new IllegalArgumentException(
          ("The joint exhaustiveSearch moveSelectorConfig (%s) has a never-ending selector (%s) for variables (%s). "
                  + "Use finite ORIGINAL, SORTED or SHUFFLED selection at every level; selectedCountLimit cannot make an endless inner selection exhaustive.")
              .formatted(moveConfig, moves, variables));
    }
    return moves;
  }

  private void validateFiniteSelection(SelectorConfig<?> selector, String path) {
    if (selector == null) {
      return;
    }
    switch (selector) {
      case MoveSelectorConfig<?> move -> {
        validateFiniteSelectionOrder(
            move, path, move.getSelectionOrder(), move.getSelectedCountLimit());
        if (move instanceof UnionMoveSelectorConfig union) {
          for (int i = 0; i < union.getMoveSelectorList().size(); i++) {
            validateFiniteSelection(union.getMoveSelectorList().get(i), path + ".union[" + i + "]");
          }
        } else if (move instanceof CartesianProductMoveSelectorConfig product) {
          for (int i = 0; i < product.getMoveSelectorList().size(); i++) {
            validateFiniteSelection(
                product.getMoveSelectorList().get(i), path + ".cartesianProduct[" + i + "]");
          }
        } else if (move instanceof ChangeMoveSelectorConfig change) {
          validateFiniteSelection(change.getEntitySelectorConfig(), path + ".entitySelector");
          validateFiniteSelection(change.getValueSelectorConfig(), path + ".valueSelector");
        } else if (move instanceof ListChangeMoveSelectorConfig change) {
          validateFiniteSelection(change.getValueSelectorConfig(), path + ".valueSelector");
          validateFiniteSelection(
              change.getDestinationSelectorConfig(), path + ".destinationSelector");
        }
      }
      case EntitySelectorConfig entity -> {
        validateFiniteSelectionOrder(
            entity, path, entity.getSelectionOrder(), entity.getSelectedCountLimit());
        validateFiniteSelection(entity.getNearbySelectionConfig(), path + ".nearbySelection");
      }
      case ValueSelectorConfig value -> {
        validateFiniteSelectionOrder(
            value, path, value.getSelectionOrder(), value.getSelectedCountLimit());
        validateFiniteSelection(value.getNearbySelectionConfig(), path + ".nearbySelection");
      }
      case DestinationSelectorConfig destination -> {
        validateFiniteSelection(destination.getEntitySelectorConfig(), path + ".entitySelector");
        validateFiniteSelection(destination.getValueSelectorConfig(), path + ".valueSelector");
        validateFiniteSelection(destination.getNearbySelectionConfig(), path + ".nearbySelection");
      }
      case NearbySelectionConfig nearby -> {
        validateFiniteSelection(
            nearby.getOriginEntitySelectorConfig(), path + ".originEntitySelector");
        validateFiniteSelection(
            nearby.getOriginValueSelectorConfig(), path + ".originValueSelector");
        validateFiniteSelection(
            nearby.getOriginSubListSelectorConfig(), path + ".originSubListSelector");
      }
      case SubListSelectorConfig subList -> {
        validateFiniteSelection(subList.getValueSelectorConfig(), path + ".valueSelector");
        validateFiniteSelection(subList.getNearbySelectionConfig(), path + ".nearbySelection");
      }
      default -> {
        // Unsupported move types are rejected during normalization.
      }
    }
  }

  private void validateFiniteSelectionOrder(
      SelectorConfig<?> selector, String path, SelectionOrder order, Long selectedCountLimit) {
    if (order == SelectionOrder.RANDOM || order == SelectionOrder.PROBABILISTIC) {
      throw new IllegalArgumentException(
          ("The joint exhaustiveSearch selector (%s) at (%s) has selectionOrder (%s) and selectedCountLimit (%s) for variables (%s). "
                  + "Use finite ORIGINAL, SORTED or SHUFFLED selection at every level; selectedCountLimit cannot make an endless inner selection exhaustive.")
              .formatted(selector, path, order, selectedCountLimit, variables));
    }
  }

  private AbstractMoveSelectorFactory<Solution_, ?> moveFactory(MoveSelectorConfig<?> moveConfig) {
    if (moveConfig instanceof UnionMoveSelectorConfig union) {
      return new UnionMoveSelectorFactory<>(union) {
        @Override
        protected List<MoveSelector<Solution_>> buildInnerMoveSelectors(
            List<MoveSelectorConfig> children,
            HeuristicConfigPolicy<Solution_> policy,
            SelectionCacheType cache,
            boolean random) {
          return buildChildren(children, policy, cache, random);
        }
      };
    }
    if (moveConfig instanceof CartesianProductMoveSelectorConfig product) {
      return new CartesianProductMoveSelectorFactory<>(product) {
        @Override
        protected List<MoveSelector<Solution_>> buildInnerMoveSelectors(
            List<MoveSelectorConfig> children,
            HeuristicConfigPolicy<Solution_> policy,
            SelectionCacheType cache,
            boolean random) {
          return buildChildren(children, policy, cache, random);
        }
      };
    }
    if (moveConfig instanceof ListChangeMoveSelectorConfig change) {
      var descriptor =
          resolve(
              change.getDestinationSelectorConfig().getEntitySelectorConfig().getEntityClass(),
              change.getValueSelectorConfig().getVariableName(),
              true,
              change);
      return new ListChangeMoveSelectorFactory<>(change) {
        @Override
        protected MoveSelector<Solution_> buildBaseMoveSelector(
            HeuristicConfigPolicy<Solution_> policy, SelectionCacheType cache, boolean random) {
          return new JointCoordinateMoveSelector<>(
              super.buildBaseMoveSelector(policy, cache, random), descriptor, decisionFilter);
        }
      };
    }
    if (moveConfig instanceof ChangeMoveSelectorConfig change) {
      var descriptor =
          resolve(
              change.getEntitySelectorConfig().getEntityClass(),
              change.getValueSelectorConfig().getVariableName(),
              false,
              change);
      return new ChangeMoveSelectorFactory<>(change) {
        @Override
        protected MoveSelector<Solution_> buildBaseMoveSelector(
            HeuristicConfigPolicy<Solution_> policy, SelectionCacheType cache, boolean random) {
          // Validate before selected-count or cache decorators can hide an endless child.
          return requireFiniteMoves(super.buildBaseMoveSelector(policy, cache, random), change);
        }

        @Override
        public MoveSelector<Solution_> buildMoveSelector(
            HeuristicConfigPolicy<Solution_> policy,
            SelectionCacheType minimumCacheType,
            SelectionOrder inheritedSelectionOrder,
            boolean skipNonDoableMoves) {
          // Basic assignments may cache their population for a phase or solve. Keep the live
          // decision frontier outside that cache, otherwise it is either rejected as uncacheable
          // or frozen before the joint tree has started enumerating coordinates.
          return new JointCoordinateMoveSelector<>(
              super.buildMoveSelector(
                  policy, minimumCacheType, inheritedSelectionOrder, skipNonDoableMoves),
              descriptor,
              decisionFilter);
        }
      };
    }
    throw new IllegalArgumentException(
        "Unsupported joint exhaustive search selector: " + moveConfig);
  }

  @SuppressWarnings("rawtypes")
  private List<MoveSelector<Solution_>> buildChildren(
      List<MoveSelectorConfig> children,
      HeuristicConfigPolicy<Solution_> policy,
      SelectionCacheType cache,
      boolean random) {
    return children.stream()
        .map(
            child ->
                moveFactory(child)
                    .buildMoveSelector(
                        policy, cache, SelectionOrder.fromRandomSelectionBoolean(random), false))
        .toList();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private MoveSelectorConfig<?> normalize(
      MoveSelectorConfig<?> selector, Set<GenuineVariableDescriptor<Solution_>> covered) {
    if (selector instanceof CartesianProductMoveSelectorConfig product) {
      if (Boolean.FALSE.equals(product.getIgnoreEmptyChildIterators())) {
        throw new IllegalArgumentException(
            ("The joint exhaustiveSearch Cartesian product selector (%s) has ignoreEmptyChildIterators (false) for variables (%s). "
                    + "Completed decision coordinates have empty selectors. Set ignoreEmptyChildIterators to true or omit it to retain all joint completions.")
                .formatted(product, variables));
      }
      if (product.getMoveSelectorList() == null || product.getMoveSelectorList().isEmpty()) {
        throw new IllegalArgumentException(
            "The exhaustiveSearch Cartesian product selector must not be empty.");
      }
      var copy = product.copyConfig();
      var children = new ArrayList<MoveSelectorConfig>();
      for (var child : product.getMoveSelectorList()) {
        children.add(normalize(child, covered));
      }
      copy.setMoveSelectorList(children);
      return copy;
    }
    if (selector instanceof UnionMoveSelectorConfig union) {
      if (union.getMoveSelectorList() == null || union.getMoveSelectorList().isEmpty()) {
        throw new IllegalArgumentException(
            "The exhaustiveSearch union selector must not be empty.");
      }
      var copy = union.copyConfig();
      var children = new ArrayList<MoveSelectorConfig>();
      for (var child : union.getMoveSelectorList()) {
        children.add(normalize(child, covered));
      }
      copy.setMoveSelectorList(children);
      return copy;
    }
    if (selector instanceof ChangeMoveSelectorConfig change) {
      var entity = change.getEntitySelectorConfig();
      var value = change.getValueSelectorConfig();
      var variable =
          resolve(
              entity == null ? null : entity.getEntityClass(),
              value == null ? null : value.getVariableName(),
              false,
              selector);
      covered.add(variable);
      var copy = change.copyConfig();
      copy.setEntitySelectorConfig(normalizeEntityConfig(entity, variable));
      copy.setValueSelectorConfig(
          (value == null ? valueConfig(variable) : value.copyConfig())
              .withVariableName(variable.getVariableName()));
      return copy;
    }
    if (selector instanceof ListChangeMoveSelectorConfig change) {
      var destination = change.getDestinationSelectorConfig();
      var entity = destination == null ? null : destination.getEntitySelectorConfig();
      var sourceValue = change.getValueSelectorConfig();
      var destinationValue = destination == null ? null : destination.getValueSelectorConfig();
      String name = sourceValue == null ? null : sourceValue.getVariableName();
      String destinationName = destinationValue == null ? null : destinationValue.getVariableName();
      if (name != null && destinationName != null && !name.equals(destinationName)) {
        throw new IllegalArgumentException(
            "The exhaustiveSearch list selector (%s) targets different source (%s) and destination (%s) variables."
                .formatted(selector, name, destinationName));
      }
      var variable =
          resolve(
              entity == null ? null : entity.getEntityClass(),
              name == null ? destinationName : name,
              true,
              selector);
      covered.add(variable);
      var copy = change.copyConfig();
      copy.setValueSelectorConfig(
          (sourceValue == null ? valueConfig(variable) : sourceValue.copyConfig())
              .withVariableName(variable.getVariableName()));
      var target = destination == null ? new DestinationSelectorConfig() : destination.copyConfig();
      target.setEntitySelectorConfig(normalizeEntityConfig(entity, variable));
      target.setValueSelectorConfig(
          (destinationValue == null ? new ValueSelectorConfig() : destinationValue.copyConfig())
              .withVariableName(variable.getVariableName()));
      copy.setDestinationSelectorConfig(target);
      return copy;
    }
    throw new IllegalArgumentException(
        ("The exhaustiveSearch moveSelectorConfig (%s) does not identify a single assignment coordinate. "
                + "Use automatic selectors or a union of explicitly targeted change/listChange selectors for variables (%s).")
            .formatted(selector, variables));
  }

  private GenuineVariableDescriptor<Solution_> resolve(
      Class<?> entityClass, String variableName, boolean list, MoveSelectorConfig<?> selector) {
    if (entityClass == null && config.getEntitySelectorConfig() != null) {
      entityClass = config.getEntitySelectorConfig().getEntityClass();
    }
    var matches = new ArrayList<GenuineVariableDescriptor<Solution_>>();
    for (var variable : variables) {
      if (variable.isListVariable() == list
          && (variableName == null || variable.getVariableName().equals(variableName))
          && (entityClass == null
              || policy
                      .getSolutionDescriptor()
                      .findEntityDescriptorOrFail(entityClass)
                      .getGenuineVariableDescriptor(variable.getVariableName())
                  == variable)) {
        matches.add(variable);
      }
    }
    if (matches.size() != 1) {
      throw new IllegalArgumentException(
          ("The exhaustiveSearch selector (%s) must target one variable; entityClass (%s), variableName (%s), candidates (%s). "
                  + "Set the entityClass and variableName on its existing entity/value selectors.")
              .formatted(selector, entityClass, variableName, matches));
    }
    return matches.getFirst();
  }
}
