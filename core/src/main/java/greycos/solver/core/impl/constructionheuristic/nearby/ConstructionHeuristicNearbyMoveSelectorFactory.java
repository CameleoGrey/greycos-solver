package greycos.solver.core.impl.constructionheuristic.nearby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.MoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.CartesianProductMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyMoveSelector.CandidateSource;
import greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyMoveSelector.OriginCandidates;
import greycos.solver.core.impl.constructionheuristic.nearby.ConstructionHeuristicNearbyRanking.MeterProfile;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.move.SelectorBasedCompositeMove;
import greycos.solver.core.impl.heuristic.move.SelectorBasedNoChangeMove;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.heuristic.selector.entity.mimic.MimicRecordingEntitySelector;
import greycos.solver.core.impl.heuristic.selector.entity.mimic.MimicReplayingEntitySelector;
import greycos.solver.core.impl.heuristic.selector.move.AbstractMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.MoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.composite.CartesianProductMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.composite.UnionMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.decorator.AbstractCachingMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.decorator.FilteringMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.decorator.SelectedCountLimitMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.decorator.SortingMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMoveSelector;
import greycos.solver.core.impl.heuristic.selector.move.generic.SelectorBasedChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.list.ListChangeMoveSelector;
import greycos.solver.core.impl.heuristic.selector.value.mimic.MimicRecordingValueSelector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.preview.api.move.Move;

/** Installs automatic CH ranking only around supported built-in selectors. */
public final class ConstructionHeuristicNearbyMoveSelectorFactory {

  private ConstructionHeuristicNearbyMoveSelectorFactory() {}

  public static <Solution_> MoveSelector<Solution_> wrap(
      MoveSelector<Solution_> delegate,
      MoveSelectorConfig<?> sourceConfig,
      HeuristicConfigPolicy<Solution_> configPolicy) {
    if (!configPolicy.isConstructionHeuristicNearbyAutoConfigurationEnabled()
        || configPolicy.getConstructionHeuristicNearbyProfiles().isEmpty()
        || hasExplicitRandomizedSelection(sourceConfig)
        || delegate instanceof ConstructionHeuristicNearbyMoveSelector<?>) {
      return delegate;
    }
    var source = buildSource(delegate, sourceConfig, configPolicy);
    return source == null
        ? delegate
        : new ConstructionHeuristicNearbyMoveSelector<>(
            delegate, source, configPolicy.getConstructionHeuristicNearbySelectionSize());
  }

  private static boolean isRandomized(SelectionOrder order) {
    return order == SelectionOrder.RANDOM || order == SelectionOrder.SHUFFLED;
  }

  private static boolean hasExplicitRandomizedSelection(MoveSelectorConfig<?> config) {
    if (config == null) {
      return false;
    }
    if (isRandomized(config.getSelectionOrder())) {
      return true;
    }
    if (config instanceof ChangeMoveSelectorConfig change) {
      var entity = change.getEntitySelectorConfig();
      var value = change.getValueSelectorConfig();
      return entity != null && isRandomized(entity.getSelectionOrder())
          || value != null && isRandomized(value.getSelectionOrder());
    }
    if (config instanceof ListChangeMoveSelectorConfig list) {
      var source = list.getValueSelectorConfig();
      var destination = list.getDestinationSelectorConfig();
      var entity = destination == null ? null : destination.getEntitySelectorConfig();
      var value = destination == null ? null : destination.getValueSelectorConfig();
      return source != null && isRandomized(source.getSelectionOrder())
          || entity != null && isRandomized(entity.getSelectionOrder())
          || value != null && isRandomized(value.getSelectionOrder());
    }
    if (config instanceof UnionMoveSelectorConfig union) {
      var children = union.getMoveSelectorList();
      return children != null
          && children.stream()
              .anyMatch(
                  ConstructionHeuristicNearbyMoveSelectorFactory::hasExplicitRandomizedSelection);
    }
    if (config instanceof CartesianProductMoveSelectorConfig product) {
      var children = product.getMoveSelectorList();
      return children != null
          && children.stream()
              .anyMatch(
                  ConstructionHeuristicNearbyMoveSelectorFactory::hasExplicitRandomizedSelection);
    }
    return false;
  }

  private static <Solution_> CandidateSource<Solution_> buildSource(
      MoveSelector<Solution_> delegate,
      MoveSelectorConfig<?> config,
      HeuristicConfigPolicy<Solution_> policy) {
    if (config != null && isRandomized(config.getSelectionOrder())) {
      return null;
    }
    var base = unwrap(delegate);
    if (base instanceof ChangeMoveSelector<Solution_> changeSelector) {
      if (!(config instanceof ChangeMoveSelectorConfig changeConfig)
          || changeConfig.hasNearbySelectionConfig()
          || delegate.isNeverEnding()) {
        return null;
      }
      var descriptor = changeSelector.getValueSelector().getVariableDescriptor();
      var profiles = profilesFor(descriptor, policy);
      if (profiles.isEmpty()) {
        return null;
      }
      var queuedValue =
          changeConfig.getValueSelectorConfig() != null
              && changeConfig.getValueSelectorConfig().getMimicSelectorRef() != null;
      return new LeafSource<>(
          delegate,
          descriptor,
          profiles,
          queuedValue,
          preserveOrder(changeConfig, queuedValue),
          changeConfig,
          policy);
    }
    if (base instanceof ListChangeMoveSelector<Solution_> listSelector) {
      if (!(config instanceof ListChangeMoveSelectorConfig listConfig)
          || listConfig.hasNearbySelectionConfig()
          || delegate.isNeverEnding()) {
        return null;
      }
      var descriptor = listSelector.getListVariableDescriptor();
      var profiles = profilesFor(descriptor, policy);
      return profiles.isEmpty()
          ? null
          : new LeafSource<>(
              delegate, descriptor, profiles, false, preserveOrder(listConfig), listConfig, policy);
    }
    if (base instanceof UnionMoveSelector<Solution_> unionSelector
        && config instanceof UnionMoveSelectorConfig unionConfig) {
      var children = unionSelector.getChildMoveSelectorList();
      var childConfigs = unionConfig.getMoveSelectorList();
      if (childConfigs == null
          || childConfigs.size() != children.size()
          || delegate.isNeverEnding()) {
        return null;
      }
      var sources = new ArrayList<CandidateSource<Solution_>>();
      var anyNearby = false;
      for (var i = 0; i < children.size(); i++) {
        var source = buildSource(children.get(i), childConfigs.get(i), policy);
        anyNearby |= source != null;
        sources.add(source == null ? plainSource(children.get(i)) : source);
      }
      if (!anyNearby) {
        return null;
      }
      var source = new UnionSource<>(sources, preserveOrder(unionConfig));
      return decorateComposite(delegate, base, source, policy);
    }
    if (base instanceof CartesianProductMoveSelector<Solution_> productSelector
        && config instanceof CartesianProductMoveSelectorConfig productConfig) {
      var children = productSelector.getChildMoveSelectorList();
      var childConfigs = productConfig.getMoveSelectorList();
      if (childConfigs == null
          || childConfigs.size() != children.size()
          || delegate.isNeverEnding()) {
        return null;
      }
      var sources = new ArrayList<CandidateSource<Solution_>>();
      var anyNearby = false;
      for (var i = 0; i < children.size(); i++) {
        var source = buildSource(children.get(i), childConfigs.get(i), policy);
        anyNearby |= source != null;
        // A built-in variable without a compatible profile retains its CH candidate order.
        // Custom and explicitly nearby-configured selectors control the complete product.
        if (source == null) {
          source = unrankedLeafSource(children.get(i), childConfigs.get(i), policy);
          if (source == null) {
            return null;
          }
        }
        sources.add(source);
      }
      if (sources.isEmpty() || !anyNearby) {
        return null;
      }
      CandidateSource<Solution_> source =
          hasSharedCartesianOrigin(productConfig)
              ? new CartesianSource<>(
                  sources,
                  productSelector.isIgnoreEmptyChildIterators(),
                  preserveOrder(productConfig))
              : new DependentCartesianSource<>(
                  sources, productSelector, preserveOrder(productConfig));
      return decorateComposite(delegate, base, source, policy);
    }
    return null;
  }

  private static boolean hasSharedCartesianOrigin(CartesianProductMoveSelectorConfig config) {
    var leaves = new ArrayList<MoveSelectorConfig>();
    config.extractLeafMoveSelectorConfigsIntoList(leaves);
    if (leaves.isEmpty()) {
      return false;
    }
    if (leaves.stream().allMatch(leaf -> leaf instanceof ListChangeMoveSelectorConfig)) {
      var first = (ListChangeMoveSelectorConfig) leaves.getFirst();
      var firstValue = first.getValueSelectorConfig();
      var valueRef = firstValue == null ? null : firstValue.getMimicSelectorRef();
      if (valueRef == null) {
        return false;
      }
      for (var leaf : leaves) {
        var change = (ListChangeMoveSelectorConfig) leaf;
        var value = change.getValueSelectorConfig();
        var destination = change.getDestinationSelectorConfig();
        var entity = destination == null ? null : destination.getEntitySelectorConfig();
        if (value == null
            || !valueRef.equals(value.getMimicSelectorRef())
            || entity != null && (entity.getId() != null || entity.getMimicSelectorRef() != null)) {
          return false;
        }
      }
      return true;
    }
    if (leaves.stream().anyMatch(leaf -> !(leaf instanceof ChangeMoveSelectorConfig))) {
      return false;
    }
    var first = (ChangeMoveSelectorConfig) leaves.getFirst();
    var firstEntity = first.getEntitySelectorConfig();
    var originRef =
        firstEntity == null
            ? null
            : firstEntity.getMimicSelectorRef() == null
                ? firstEntity.getId()
                : firstEntity.getMimicSelectorRef();
    if (originRef != null) {
      for (var i = 1; i < leaves.size(); i++) {
        var entity = ((ChangeMoveSelectorConfig) leaves.get(i)).getEntitySelectorConfig();
        if (entity == null || !originRef.equals(entity.getMimicSelectorRef())) {
          return false;
        }
      }
      return true;
    }
    // Queued-value products can have several independent candidate entity streams, while their
    // source origin remains one fixed queued value. They must not contain entity recorder links.
    var firstValue = first.getValueSelectorConfig();
    var valueRef = firstValue == null ? null : firstValue.getMimicSelectorRef();
    if (valueRef == null) {
      return false;
    }
    for (var leaf : leaves) {
      var change = (ChangeMoveSelectorConfig) leaf;
      var entity = change.getEntitySelectorConfig();
      var value = change.getValueSelectorConfig();
      if (entity != null && (entity.getId() != null || entity.getMimicSelectorRef() != null)
          || value == null
          || !valueRef.equals(value.getMimicSelectorRef())) {
        return false;
      }
    }
    return true;
  }

  private static <Solution_> CandidateSource<Solution_> unrankedLeafSource(
      MoveSelector<Solution_> delegate,
      MoveSelectorConfig<?> config,
      HeuristicConfigPolicy<Solution_> policy) {
    if (config.hasNearbySelectionConfig()
        || delegate.isNeverEnding()
        || isRandomized(config.getSelectionOrder())) {
      return null;
    }
    var base = unwrap(delegate);
    if (base instanceof ChangeMoveSelector<Solution_> changeSelector
        && config instanceof ChangeMoveSelectorConfig changeConfig) {
      var queuedValue =
          changeConfig.getValueSelectorConfig() != null
              && changeConfig.getValueSelectorConfig().getMimicSelectorRef() != null;
      return new LeafSource<>(
          delegate,
          changeSelector.getValueSelector().getVariableDescriptor(),
          List.of(),
          queuedValue,
          preserveOrder(changeConfig, queuedValue),
          changeConfig,
          policy);
    } else if (base instanceof ListChangeMoveSelector<Solution_> listSelector
        && config instanceof ListChangeMoveSelectorConfig) {
      return new LeafSource<>(
          delegate,
          listSelector.getListVariableDescriptor(),
          List.of(),
          false,
          preserveOrder((ListChangeMoveSelectorConfig) config),
          config,
          policy);
    }
    return null;
  }

  private static boolean preserveOrder(ChangeMoveSelectorConfig config, boolean queuedValue) {
    return preserveOrder(config)
        || (queuedValue
            ? config.getEntitySelectorConfig() != null
                && config.getEntitySelectorConfig().getSelectionOrder() != null
            : config.getValueSelectorConfig() != null
                && config.getValueSelectorConfig().getSelectionOrder() != null);
  }

  private static boolean preserveOrder(ListChangeMoveSelectorConfig config) {
    var destination = config.getDestinationSelectorConfig();
    return preserveOrder((MoveSelectorConfig<?>) config)
        || destination != null
            && (destination.getEntitySelectorConfig() != null
                    && destination.getEntitySelectorConfig().getSelectionOrder() != null
                || destination.getValueSelectorConfig() != null
                    && destination.getValueSelectorConfig().getSelectionOrder() != null);
  }

  private static boolean preserveOrder(MoveSelectorConfig<?> config) {
    return config.getSelectionOrder() != null
        || config.getComparatorClass() != null
        || config.getComparatorFactoryClass() != null
        || config.getSorterClass() != null;
  }

  private static <Solution_> List<MeterProfile> profilesFor(
      GenuineVariableDescriptor<Solution_> descriptor, HeuristicConfigPolicy<Solution_> policy) {
    return policy.getConstructionHeuristicNearbyProfiles().getProfiles(descriptor).stream()
        .filter(
            profile ->
                descriptor.isListVariable()
                    ? profile.argumentShape()
                            == ConstructionHeuristicNearbyProfile.ArgumentShape.VALUE_DESTINATION
                        || profile.argumentShape()
                            == ConstructionHeuristicNearbyProfile.ArgumentShape.VALUE_VALUE
                    : profile.argumentShape()
                            == ConstructionHeuristicNearbyProfile.ArgumentShape.ENTITY_VALUE
                        || profile.argumentShape()
                            == ConstructionHeuristicNearbyProfile.ArgumentShape.ENTITY_ENTITY)
        .map(MeterProfile::of)
        .toList();
  }

  private static <Solution_> MoveSelector<Solution_> unwrap(MoveSelector<Solution_> delegate) {
    var current = delegate;
    while (true) {
      if (current instanceof FilteringMoveSelector<Solution_> filtering) {
        current = filtering.getChildMoveSelector();
      } else if (current instanceof SelectedCountLimitMoveSelector<Solution_> limited) {
        current = limited.getChildMoveSelector();
      } else if (current instanceof AbstractCachingMoveSelector<Solution_> caching) {
        current = caching.getChildMoveSelector();
      } else {
        return current;
      }
    }
  }

  private static <Solution_> CandidateSource<Solution_> decorateComposite(
      MoveSelector<Solution_> delegate,
      MoveSelector<Solution_> base,
      CandidateSource<Solution_> rankedSource,
      HeuristicConfigPolicy<Solution_> policy) {
    var filters = new ArrayList<SelectionFilter<Solution_, Move<Solution_>>>();
    var current = delegate;
    var limited = false;
    while (current != base) {
      if (current instanceof FilteringMoveSelector<Solution_> filtering) {
        filters.add(filtering.getFilter());
        current = filtering.getChildMoveSelector();
      } else if (current instanceof SelectedCountLimitMoveSelector<Solution_> selectedLimit) {
        limited = true;
        current = selectedLimit.getChildMoveSelector();
      } else if (current instanceof AbstractCachingMoveSelector<Solution_> caching) {
        current = caching.getChildMoveSelector();
      } else {
        return null;
      }
    }
    if (!moveSorters(delegate).isEmpty()) {
      return new SortedCompositeSource<>(delegate, rankedSource);
    }
    if (limited) {
      // Explicit composite limits define an admitted prefix of the original product. Only that
      // finite prefix is snapshotted; an unrestricted Cartesian product is never materialized.
      return new LimitedCompositeSource<>(delegate, rankedSource);
    }
    return filters.isEmpty()
        ? rankedSource
        : new CandidateSource<>() {
          @Override
          public Iterator<OriginCandidates<Solution_>> iterator(
              ScoreDirector<Solution_> scoreDirector) {
            return mapping(
                rankedSource.iterator(scoreDirector),
                origin -> decorateOrigin(origin, scoreDirector));
          }

          private OriginCandidates<Solution_> decorateOrigin(
              OriginCandidates<Solution_> origin, ScoreDirector<Solution_> scoreDirector) {
            return new OriginCandidates<>(
                filtering(
                    origin.rankedMoves(),
                    move ->
                        filters.stream().allMatch(filter -> filter.accept(scoreDirector, move))),
                filtering(
                    origin.tailMoves(),
                    move ->
                        filters.stream().allMatch(filter -> filter.accept(scoreDirector, move))),
                origin.adaptive(),
                origin.sourceOrder(),
                origin.preserveCandidateOrder(),
                origin.neighborhoodSorter(),
                origin.containsCandidate(),
                origin.sourceRanks(),
                origin.selectionRecorder());
          }

          @Override
          public ProgressiveConstructionIterator<Solution_> placementIterator(
              ScoreDirector<Solution_> scoreDirector,
              int initialSelectionSize,
              boolean inheritedOrder,
              java.util.function.UnaryOperator<OriginCandidates<Solution_>> decorator) {
            return rankedSource.placementIterator(
                scoreDirector,
                initialSelectionSize,
                inheritedOrder,
                origin -> decorator.apply(decorateOrigin(origin, scoreDirector)));
          }

          @Override
          public void resetPlacement() {
            rankedSource.resetPlacement();
          }

          @Override
          public void recordSelection(Move<Solution_> move) {
            rankedSource.recordSelection(move);
          }

          @Override
          public boolean isGloballyOrdered() {
            return rankedSource.isGloballyOrdered();
          }
        };
  }

  private static <Solution_> CandidateSource<Solution_> plainSource(
      MoveSelector<Solution_> delegate) {
    return scoreDirector ->
        Collections.singletonList(
                new OriginCandidates<>(
                    delegate.iterator(), Collections.<Move<Solution_>>emptyIterator(), false))
            .iterator();
  }

  private static final class LeafSource<Solution_> implements CandidateSource<Solution_> {
    private final MoveSelector<Solution_> delegate;
    private final GenuineVariableDescriptor<Solution_> descriptor;
    private final List<MeterProfile> profiles;
    private final boolean queuedValue;
    private final boolean preserveCandidateOrder;
    private final MoveSelectorConfig<?> config;
    private final HeuristicConfigPolicy<Solution_> policy;
    private SortedPopulation<Solution_> sortedPopulation;

    private LeafSource(
        MoveSelector<Solution_> delegate,
        GenuineVariableDescriptor<Solution_> descriptor,
        List<MeterProfile> profiles,
        boolean queuedValue,
        boolean preserveCandidateOrder,
        MoveSelectorConfig<?> config,
        HeuristicConfigPolicy<Solution_> policy) {
      this.delegate = delegate;
      this.descriptor = descriptor;
      this.profiles = profiles;
      this.queuedValue = queuedValue;
      this.preserveCandidateOrder = preserveCandidateOrder;
      this.config = config;
      this.policy = policy;
    }

    @Override
    public Iterator<OriginCandidates<Solution_>> iterator(ScoreDirector<Solution_> scoreDirector) {
      if (!moveSorters(delegate).isEmpty()) {
        return sortedOrigins(scoreDirector);
      }
      if (!queuedValue
          && unwrap(delegate) instanceof ChangeMoveSelector<Solution_> changeSelector) {
        return changeOrigins(changeSelector, scoreDirector);
      }
      return new Iterator<>() {
        private final Iterator<Move<Solution_>> moves = delegate.iterator();
        private Move<Solution_> lookAhead;
        private boolean noChangeSeen;
        private int originOrdinal;

        @Override
        public boolean hasNext() {
          return lookAhead != null || moves.hasNext();
        }

        @Override
        public OriginCandidates<Solution_> next() {
          if (!hasNext()) {
            throw new NoSuchElementException();
          }
          var first = lookAhead == null ? moves.next() : lookAhead;
          lookAhead = null;
          var origin = origin(first);
          var allowed = new ArrayList<Move<Solution_>>();
          append(allowed, first);
          while (moves.hasNext()) {
            var move = moves.next();
            if (!(move instanceof SelectorBasedNoChangeMove<?>) && origin(move) != origin) {
              lookAhead = move;
              break;
            }
            append(allowed, move);
          }
          return withOriginOrdinal(
              withSelectionRecorder(
                  ConstructionHeuristicNearbyRanking.rank(
                      allowed,
                      descriptor,
                      profiles,
                      queuedValue,
                      scoreDirector,
                      preserveCandidateOrder),
                  scoreDirector),
              originOrdinal++);
        }

        private void append(List<Move<Solution_>> allowed, Move<Solution_> move) {
          if (move instanceof SelectorBasedNoChangeMove<?>) {
            if (noChangeSeen) {
              return;
            }
            noChangeSeen = true;
          }
          allowed.add(move);
        }

        private Object origin(Move<Solution_> move) {
          if (move instanceof ChangeMove<?> change) {
            return queuedValue ? change.getToPlanningValue() : change.getEntity();
          }
          return ConstructionHeuristicNearbyRanking.movedValue(move);
        }
      };
    }

    private Iterator<OriginCandidates<Solution_>> changeOrigins(
        ChangeMoveSelector<Solution_> changeSelector, ScoreDirector<Solution_> scoreDirector) {
      var entityIterator = changeSelector.getEntitySelector().iterator();
      // Replaying selectors share their consumed flag between iterator instances. A sorted
      // placement may suspend several origins while another origin restores its recorder.
      // Capture this source's one recorded entity so restoration cannot resurrect its cursor.
      var entities =
          changeSelector.getEntitySelector() instanceof MimicReplayingEntitySelector<?>
              ? singletonRecording(entityIterator)
              : entityIterator;
      var decorators = new ArrayList<MoveSelector<Solution_>>();
      var current = delegate;
      while (current != changeSelector) {
        decorators.add(current);
        if (current instanceof FilteringMoveSelector<Solution_> filtering) {
          current = filtering.getChildMoveSelector();
        } else if (current instanceof SelectedCountLimitMoveSelector<Solution_> limited) {
          current = limited.getChildMoveSelector();
        } else if (current instanceof AbstractCachingMoveSelector<Solution_> caching) {
          current = caching.getChildMoveSelector();
        }
      }
      Collections.reverse(decorators);
      var selectedCounts = new long[decorators.size()];
      return new Iterator<>() {
        private int originOrdinal;

        @Override
        public boolean hasNext() {
          for (var i = 0; i < decorators.size(); i++) {
            if (decorators.get(i) instanceof SelectedCountLimitMoveSelector<?> limited
                && selectedCounts[i] >= limited.getSelectedCountLimit()) {
              return false;
            }
          }
          return entities.hasNext();
        }

        @Override
        public OriginCandidates<Solution_> next() {
          if (!hasNext()) {
            throw new NoSuchElementException();
          }
          var entity = entities.next();
          var ordinal = originOrdinal++;
          Iterator<Move<Solution_>> moves =
              mapping(
                  changeSelector.getValueSelector().iterator(entity),
                  value -> new SelectorBasedChangeMove<>(descriptor, entity, value));
          for (var i = 0; i < decorators.size(); i++) {
            var index = i;
            var decorator = decorators.get(i);
            if (decorator instanceof FilteringMoveSelector<Solution_> filtering) {
              moves = filtering(moves, move -> filtering.getFilter().accept(scoreDirector, move));
            } else if (decorator instanceof SelectedCountLimitMoveSelector<Solution_> limited) {
              var input = moves;
              moves =
                  new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                      return selectedCounts[index] < limited.getSelectedCountLimit()
                          && input.hasNext();
                    }

                    @Override
                    public Move<Solution_> next() {
                      if (!hasNext()) {
                        throw new NoSuchElementException();
                      }
                      selectedCounts[index]++;
                      return input.next();
                    }
                  };
            }
          }
          var allowed = new ArrayList<Move<Solution_>>();
          moves.forEachRemaining(allowed::add);
          var origin =
              withSelectionRecorder(
                  ConstructionHeuristicNearbyRanking.rank(
                      allowed, descriptor, profiles, false, scoreDirector, preserveCandidateOrder),
                  scoreDirector);
          return withOriginOrdinal(origin, ordinal);
        }
      };
    }

    private OriginCandidates<Solution_> withSelectionRecorder(
        OriginCandidates<Solution_> origin, ScoreDirector<Solution_> scoreDirector) {
      return new OriginCandidates<>(
          origin.rankedMoves(),
          origin.tailMoves(),
          origin.adaptive(),
          origin.sourceOrder(),
          origin.preserveCandidateOrder(),
          origin.neighborhoodSorter(),
          origin.containsCandidate(),
          origin.sourceRanks(),
          this::recordSelection);
    }

    @Override
    public void recordSelection(Move<Solution_> move) {
      if (move instanceof SelectorBasedCompositeMove<Solution_> composite) {
        for (var child : composite.getMoves()) {
          recordSelection(child);
        }
        return;
      }
      if (ConstructionHeuristicNearbyRanking.variableDescriptor(move) != descriptor) {
        return;
      }
      if (config instanceof ChangeMoveSelectorConfig change
          && move instanceof ChangeMove<?> selected) {
        var entity = change.getEntitySelectorConfig();
        if (entity != null) {
          recordEntity(entity.getId(), entity.getMimicSelectorRef(), selected.getEntity());
        }
        var value = change.getValueSelectorConfig();
        if (value != null) {
          recordValue(value.getId(), value.getMimicSelectorRef(), selected.getToPlanningValue());
        }
      } else if (config instanceof ListChangeMoveSelectorConfig list) {
        var value = list.getValueSelectorConfig();
        if (value != null) {
          recordValue(
              value.getId(),
              value.getMimicSelectorRef(),
              ConstructionHeuristicNearbyRanking.movedValue(move));
        }
        var destination = list.getDestinationSelectorConfig();
        if (destination != null
            && ConstructionHeuristicNearbyRanking.isMeaningfulAssignment(move)) {
          var entity = destination.getEntitySelectorConfig();
          if (entity != null) {
            recordEntity(
                entity.getId(),
                entity.getMimicSelectorRef(),
                ConstructionHeuristicNearbyRanking.destinationEntity(move));
          }
          var destinationValue = destination.getValueSelectorConfig();
          var index = ConstructionHeuristicNearbyRanking.destinationIndex(move);
          if (destinationValue != null && index > 0) {
            var owner = ConstructionHeuristicNearbyRanking.destinationEntity(move);
            var anchor =
                ((greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor<
                            Solution_>)
                        descriptor)
                    .getElement(owner, index - 1);
            recordValue(destinationValue.getId(), destinationValue.getMimicSelectorRef(), anchor);
          }
        }
      }
    }

    private void recordEntity(String id, String mimicRef, Object selected) {
      // Replayers do not own recordings. Writing their reference back would reset the replay
      // iterator's consumed flag and can make a finite dependent product repeat indefinitely.
      var recorderId = id;
      if (recorderId != null
          && policy.getEntityMimicRecorder(recorderId)
              instanceof MimicRecordingEntitySelector<Solution_> recorder) {
        recorder.recordSelection(selected);
      }
    }

    private void recordValue(String id, String mimicRef, Object selected) {
      // Replayers do not own recordings. Writing their reference back would reset the replay
      // iterator's consumed flag and can make a finite dependent product repeat indefinitely.
      var recorderId = id;
      if (recorderId != null
          && policy.getValueMimicRecorder(recorderId)
              instanceof MimicRecordingValueSelector<Solution_> recorder) {
        recorder.recordSelection(selected);
      }
    }

    @Override
    public boolean isGloballyOrdered() {
      return !moveSorters(delegate).isEmpty();
    }

    @Override
    public void resetPlacement() {
      sortedPopulation = null;
    }

    private Iterator<OriginCandidates<Solution_>> sortedOrigins(
        ScoreDirector<Solution_> scoreDirector) {
      if (sortedPopulation == null) {
        var groups = new ArrayList<List<Move<Solution_>>>();
        var byOrigin = new java.util.IdentityHashMap<Object, List<Move<Solution_>>>();
        var orders =
            new java.util.HashMap<ConstructionHeuristicNearbyRanking.CandidateIdentity, Integer>();
        var moves = delegate.iterator();
        while (moves.hasNext()) {
          var move = moves.next();
          orders.putIfAbsent(ConstructionHeuristicNearbyRanking.identity(move), orders.size());
          var origin = originOf(move, queuedValue);
          var group = byOrigin.get(origin);
          if (group == null) {
            group = new ArrayList<>();
            byOrigin.put(origin, group);
            groups.add(group);
          }
          group.add(move);
        }
        sortedPopulation =
            new SortedPopulation<>(groups.stream().map(List::copyOf).toList(), Map.copyOf(orders));
      }
      var population = sortedPopulation;
      return mapping(
          population.groups().iterator(),
          group -> {
            recordSelection(group.getFirst());
            return withGlobalOrder(
                ConstructionHeuristicNearbyRanking.rank(
                    group, descriptor, profiles, queuedValue, scoreDirector, true),
                population.orders(),
                this::recordSelection);
          });
    }
  }

  private record SortedPopulation<Solution_>(
      List<List<Move<Solution_>>> groups,
      Map<ConstructionHeuristicNearbyRanking.CandidateIdentity, Integer> orders) {}

  private static Object originOf(Move<?> move, boolean queuedValue) {
    if (move instanceof ChangeMove<?> change) {
      return queuedValue ? change.getToPlanningValue() : change.getEntity();
    }
    return ConstructionHeuristicNearbyRanking.movedValue(move);
  }

  private static <Solution_> OriginCandidates<Solution_> withOriginOrdinal(
      OriginCandidates<Solution_> origin, int ordinal) {
    return new OriginCandidates<>(
        origin.rankedMoves(),
        origin.tailMoves(),
        origin.adaptive(),
        origin.sourceOrder(),
        origin.preserveCandidateOrder(),
        origin.neighborhoodSorter(),
        origin.containsCandidate(),
        move -> {
          var ranks = new ArrayList<Integer>();
          ranks.add(ordinal);
          if (origin.sourceRanks() != null) {
            ranks.addAll(origin.sourceRanks().apply(move));
          }
          return List.copyOf(ranks);
        },
        origin.selectionRecorder());
  }

  private static <Solution_> OriginCandidates<Solution_> withGlobalOrder(
      OriginCandidates<Solution_> origin,
      java.util.Map<ConstructionHeuristicNearbyRanking.CandidateIdentity, Integer> orders,
      java.util.function.Consumer<Move<Solution_>> recorder) {
    return withGlobalOrder(origin, orders, recorder, Integer.MAX_VALUE);
  }

  private static <Solution_> OriginCandidates<Solution_> withGlobalOrder(
      OriginCandidates<Solution_> origin,
      java.util.Map<ConstructionHeuristicNearbyRanking.CandidateIdentity, Integer> orders,
      java.util.function.Consumer<Move<Solution_>> recorder,
      int optionalOrdinal) {
    java.util.function.Function<Move<Solution_>, List<Integer>> ranks =
        move ->
            List.of(
                move instanceof SelectorBasedNoChangeMove<?> && optionalOrdinal != Integer.MAX_VALUE
                    ? optionalOrdinal
                    : orders.getOrDefault(
                        ConstructionHeuristicNearbyRanking.identity(move), Integer.MAX_VALUE));
    return new OriginCandidates<>(
        origin.rankedMoves(),
        origin.tailMoves(),
        origin.adaptive(),
        java.util.Comparator.comparingInt(move -> ranks.apply(move).getFirst()),
        true,
        null,
        origin.containsCandidate(),
        ranks,
        recorder);
  }

  /** An explicitly sorted cache already owns the complete, admitted candidate population. */
  private static final class SortedCompositeSource<Solution_>
      implements CandidateSource<Solution_> {
    private final MoveSelector<Solution_> delegate;
    private final CandidateSource<Solution_> rankedSource;
    private SortedPopulation<Solution_> sortedPopulation;

    private SortedCompositeSource(
        MoveSelector<Solution_> delegate, CandidateSource<Solution_> rankedSource) {
      this.delegate = delegate;
      this.rankedSource = rankedSource;
    }

    @Override
    public void resetPlacement() {
      sortedPopulation = null;
      rankedSource.resetPlacement();
    }

    @Override
    public boolean isGloballyOrdered() {
      return true;
    }

    @Override
    public void recordSelection(Move<Solution_> move) {
      rankedSource.recordSelection(move);
    }

    @Override
    public Iterator<OriginCandidates<Solution_>> iterator(ScoreDirector<Solution_> scoreDirector) {
      var leaves = new ArrayList<LeafSource<Solution_>>();
      collectLeaves(rankedSource, leaves);
      var rankings =
          leaves.stream()
              .map(
                  leaf ->
                      new ConstructionHeuristicNearbyRanking.LeafRanking<>(
                          leaf.descriptor,
                          leaf.profiles,
                          leaf.queuedValue,
                          leaf.preserveCandidateOrder))
              .toList();
      if (sortedPopulation == null) {
        var groups = new java.util.LinkedHashMap<OriginIdentity, List<Move<Solution_>>>();
        var orders =
            new java.util.HashMap<ConstructionHeuristicNearbyRanking.CandidateIdentity, Integer>();
        var moves = delegate.iterator();
        while (moves.hasNext()) {
          var move = moves.next();
          orders.putIfAbsent(ConstructionHeuristicNearbyRanking.identity(move), orders.size());
          var origin = compositeOrigin(move, leaves);
          groups.computeIfAbsent(origin, ignored -> new ArrayList<>()).add(move);
        }
        sortedPopulation =
            new SortedPopulation<>(
                groups.values().stream().map(List::copyOf).toList(), Map.copyOf(orders));
      }
      var population = sortedPopulation;
      var orders = population.orders();
      return mapping(
          population.groups().iterator(),
          group -> {
            recordSelection(group.getFirst());
            var membership =
                new java.util.HashSet<ConstructionHeuristicNearbyRanking.CandidateIdentity>();
            for (var move : group) {
              membership.add(ConstructionHeuristicNearbyRanking.identity(move));
              if (!ConstructionHeuristicNearbyRanking.isMeaningfulAssignment(move)) {
                membership.add(
                    ConstructionHeuristicNearbyRanking.identity(
                        SelectorBasedNoChangeMove.getInstance()));
              }
            }
            var optionalOrdinal =
                group.stream()
                    .filter(
                        move -> !ConstructionHeuristicNearbyRanking.isMeaningfulAssignment(move))
                    .mapToInt(move -> orders.get(ConstructionHeuristicNearbyRanking.identity(move)))
                    .min()
                    .orElse(Integer.MAX_VALUE);
            var origin =
                withGlobalOrder(
                    ConstructionHeuristicNearbyRanking.rankAdmittedComposite(
                        group, rankings, scoreDirector),
                    orders,
                    this::recordSelection,
                    optionalOrdinal);
            return new OriginCandidates<>(
                origin.rankedMoves(),
                origin.tailMoves(),
                origin.adaptive(),
                origin.sourceOrder(),
                origin.preserveCandidateOrder(),
                origin.neighborhoodSorter(),
                move -> membership.contains(ConstructionHeuristicNearbyRanking.identity(move)),
                origin.sourceRanks(),
                origin.selectionRecorder());
          });
    }
  }

  private record OriginIdentity(Object descriptor, Object origin) {
    @Override
    public boolean equals(Object other) {
      return other instanceof OriginIdentity identity
          && descriptor == identity.descriptor
          && origin == identity.origin;
    }

    @Override
    public int hashCode() {
      return 31 * System.identityHashCode(descriptor) + System.identityHashCode(origin);
    }
  }

  private static <Solution_> OriginIdentity compositeOrigin(
      Move<Solution_> move, List<LeafSource<Solution_>> leaves) {
    var children = new ArrayList<Move<Solution_>>();
    flattenMoves(move, children);
    for (var leaf : leaves) {
      for (var child : children) {
        if (ConstructionHeuristicNearbyRanking.variableDescriptor(child) == leaf.descriptor) {
          return new OriginIdentity(leaf.descriptor, originOf(child, leaf.queuedValue));
        }
      }
    }
    return new OriginIdentity(null, null);
  }

  private static <Solution_> void flattenMoves(
      Move<Solution_> move, List<Move<Solution_>> children) {
    if (move instanceof SelectorBasedCompositeMove<Solution_> composite) {
      for (var child : composite.getMoves()) {
        flattenMoves(child, children);
      }
    } else {
      children.add(move);
    }
  }

  private static <Solution_> List<SortingMoveSelector<Solution_>> moveSorters(
      MoveSelector<Solution_> delegate) {
    var sorters = new ArrayList<SortingMoveSelector<Solution_>>();
    var current = delegate;
    while (true) {
      if (current instanceof SortingMoveSelector<Solution_> sorting) {
        sorters.add(sorting);
        current = sorting.getChildMoveSelector();
      } else if (current instanceof FilteringMoveSelector<Solution_> filtering) {
        current = filtering.getChildMoveSelector();
      } else if (current instanceof SelectedCountLimitMoveSelector<Solution_> limited) {
        current = limited.getChildMoveSelector();
      } else if (current instanceof AbstractCachingMoveSelector<Solution_> caching) {
        current = caching.getChildMoveSelector();
      } else {
        Collections.reverse(sorters);
        return sorters;
      }
    }
  }

  private static final class UnionSource<Solution_> implements CandidateSource<Solution_> {
    private final List<CandidateSource<Solution_>> children;
    private final boolean preserveCandidateOrder;

    private UnionSource(List<CandidateSource<Solution_>> children, boolean preserveCandidateOrder) {
      this.children = children;
      this.preserveCandidateOrder = preserveCandidateOrder;
    }

    @Override
    public void resetPlacement() {
      children.forEach(CandidateSource::resetPlacement);
    }

    @Override
    public boolean isGloballyOrdered() {
      return children.stream().anyMatch(CandidateSource::isGloballyOrdered);
    }

    @Override
    public ProgressiveConstructionIterator<Solution_> placementIterator(
        ScoreDirector<Solution_> scoreDirector,
        int initialSelectionSize,
        boolean inheritedOrder,
        java.util.function.UnaryOperator<OriginCandidates<Solution_>> decorator) {
      var preserveOrder = inheritedOrder || preserveCandidateOrder;
      return new ConcatenatingConstructionIterator<>(
          children.stream()
              .<java.util.function.Supplier<ProgressiveConstructionIterator<Solution_>>>map(
                  child ->
                      () ->
                          child.placementIterator(
                              scoreDirector, initialSelectionSize, preserveOrder, decorator))
              .iterator());
    }

    @Override
    public void recordSelection(Move<Solution_> move) {
      children.forEach(child -> child.recordSelection(move));
    }

    @Override
    public Iterator<OriginCandidates<Solution_>> iterator(ScoreDirector<Solution_> scoreDirector) {
      return new Iterator<>() {
        private int nextChild;
        private Iterator<OriginCandidates<Solution_>> current = Collections.emptyIterator();

        @Override
        public boolean hasNext() {
          while (!current.hasNext() && nextChild < children.size()) {
            current = children.get(nextChild++).iterator(scoreDirector);
          }
          return current.hasNext();
        }

        @Override
        public OriginCandidates<Solution_> next() {
          if (!hasNext()) {
            throw new NoSuchElementException();
          }
          var origin = current.next();
          var branch = nextChild - 1;
          return withOriginOrdinal(
              new OriginCandidates<>(
                  origin.rankedMoves(),
                  origin.tailMoves(),
                  origin.adaptive(),
                  origin.sourceOrder(),
                  preserveCandidateOrder || origin.preserveCandidateOrder(),
                  origin.neighborhoodSorter(),
                  origin.containsCandidate(),
                  origin.sourceRanks(),
                  origin.selectionRecorder()),
              branch);
        }
      };
    }
  }

  private static final class CartesianSource<Solution_> implements CandidateSource<Solution_> {
    private final List<CandidateSource<Solution_>> children;
    private final boolean ignoreEmpty;
    private final boolean preserveCandidateOrder;

    private CartesianSource(
        List<CandidateSource<Solution_>> children,
        boolean ignoreEmpty,
        boolean preserveCandidateOrder) {
      this.children = children;
      this.ignoreEmpty = ignoreEmpty;
      this.preserveCandidateOrder = preserveCandidateOrder;
    }

    @Override
    public void resetPlacement() {
      children.forEach(CandidateSource::resetPlacement);
    }

    @Override
    public boolean isGloballyOrdered() {
      return children.stream().anyMatch(CandidateSource::isGloballyOrdered);
    }

    @Override
    public void recordSelection(Move<Solution_> move) {
      children.forEach(child -> child.recordSelection(move));
    }

    @Override
    public Iterator<OriginCandidates<Solution_>> iterator(ScoreDirector<Solution_> scoreDirector) {
      var firstOrigins = children.getFirst().iterator(scoreDirector);
      return new Iterator<>() {
        @Override
        public boolean hasNext() {
          return firstOrigins.hasNext();
        }

        @Override
        public OriginCandidates<Solution_> next() {
          if (!hasNext()) {
            throw new NoSuchElementException();
          }
          var firstOrigin = firstOrigins.next();
          var origins = new ArrayList<OriginCandidates<Solution_>>();
          origins.add(firstOrigin);
          // Snapshot every replayer while the recorder still points at this source entity.
          for (var i = 1; i < children.size(); i++) {
            var childOrigins = children.get(i).iterator(scoreDirector);
            var childOriginList = new ArrayList<OriginCandidates<Solution_>>();
            while (childOrigins.hasNext()) {
              childOriginList.add(childOrigins.next());
            }
            origins.add(mergeOrigins(childOriginList));
          }
          return productOrigin(origins);
        }
      };
    }

    private OriginCandidates<Solution_> productOrigin(List<OriginCandidates<Solution_>> origins) {
      var childStreams = new ArrayList<Iterator<Move<Solution_>>>();
      var childOrders = new ArrayList<java.util.Comparator<Move<Solution_>>>();
      var childSorters = new ArrayList<java.util.function.Consumer<List<Move<Solution_>>>>();
      var childMembership = new ArrayList<java.util.function.Predicate<Move<Solution_>>>();
      var childRecorders = new ArrayList<java.util.function.Consumer<Move<Solution_>>>();
      var childRanks = new ArrayList<java.util.function.Function<Move<Solution_>, List<Integer>>>();
      var allOptional = true;
      for (var origin : origins) {
        var tail = new ArrayList<Move<Solution_>>();
        origin.tailMoves().forEachRemaining(tail::add);
        var stream = insertTailAfterFirst(origin.rankedMoves(), tail.iterator());
        if (!stream.hasNext()) {
          if (ignoreEmpty) {
            continue;
          }
          return new OriginCandidates<>(
              Collections.emptyIterator(), Collections.emptyIterator(), true);
        }
        childStreams.add(stream);
        childOrders.add(origin.sourceOrder());
        childSorters.add(origin.neighborhoodSorter());
        childMembership.add(origin.containsCandidate());
        childRecorders.add(origin.selectionRecorder());
        childRanks.add(origin.sourceRanks());
        if (tail.isEmpty()) {
          allOptional = false;
        }
      }
      if (childStreams.isEmpty()) {
        return new OriginCandidates<>(
            Collections.emptyIterator(), Collections.emptyIterator(), true);
      }
      var tuples = new RankSumCartesianIterator<Move<Solution_>>(childStreams);
      var rankedMoves =
          filtering(
              mapping(tuples, SelectorBasedCompositeMove::buildMove),
              ConstructionHeuristicNearbyRanking::isMeaningfulAssignment);
      var tail =
          allOptional
              ? Collections.<Move<Solution_>>singletonList(SelectorBasedNoChangeMove.getInstance())
                  .iterator()
              : Collections.<Move<Solution_>>emptyIterator();
      var hasOptionalTail = allOptional;
      java.util.Comparator<Move<Solution_>> sourceOrder =
          (left, right) -> compareChildren(left, right, childOrders);
      java.util.function.Consumer<List<Move<Solution_>>> neighborhoodSorter =
          childSorters.stream().allMatch(java.util.Objects::isNull)
              ? null
              : moves -> {
                var sortedOrders = new ArrayList<>(childOrders);
                for (var i = 0; i < childSorters.size(); i++) {
                  var sorter = childSorters.get(i);
                  if (sorter == null) {
                    continue;
                  }
                  var uniqueChildren =
                      new java.util.LinkedHashMap<
                          ConstructionHeuristicNearbyRanking.CandidateIdentity, Move<Solution_>>();
                  for (var move : moves) {
                    var child = childMove(move, i, childSorters.size());
                    uniqueChildren.putIfAbsent(
                        ConstructionHeuristicNearbyRanking.identity(child), child);
                  }
                  var selectedChildren = new ArrayList<>(uniqueChildren.values());
                  if (childOrders.get(i) != null) {
                    selectedChildren.sort(childOrders.get(i));
                  }
                  sorter.accept(selectedChildren);
                  var ranks =
                      new java.util.HashMap<
                          ConstructionHeuristicNearbyRanking.CandidateIdentity, Integer>();
                  for (var j = 0; j < selectedChildren.size(); j++) {
                    ranks.put(
                        ConstructionHeuristicNearbyRanking.identity(selectedChildren.get(j)), j);
                  }
                  sortedOrders.set(
                      i,
                      java.util.Comparator.comparingInt(
                          child -> ranks.get(ConstructionHeuristicNearbyRanking.identity(child))));
                }
                moves.sort((left, right) -> compareChildren(left, right, sortedOrders));
              };
      return new OriginCandidates<>(
          rankedMoves,
          tail,
          true,
          sourceOrder,
          preserveCandidateOrder
              || origins.stream().anyMatch(OriginCandidates::preserveCandidateOrder),
          neighborhoodSorter,
          move -> {
            if (move instanceof SelectorBasedNoChangeMove<?>) {
              return hasOptionalTail;
            }
            if (childMembership.size() > 1
                && (!(move instanceof SelectorBasedCompositeMove<?> composite)
                    || composite.getMoves().length != childMembership.size())) {
              return false;
            }
            for (var i = 0; i < childMembership.size(); i++) {
              if (!childMembership.get(i).test(childMove(move, i, childMembership.size()))) {
                return false;
              }
            }
            return true;
          },
          move -> {
            var ranks = new ArrayList<Integer>();
            for (var i = 0; i < childRanks.size(); i++) {
              if (childRanks.get(i) != null) {
                ranks.addAll(childRanks.get(i).apply(childMove(move, i, childRanks.size())));
              }
            }
            return List.copyOf(ranks);
          },
          move -> {
            if (!(move instanceof SelectorBasedNoChangeMove<?>)) {
              for (var i = 0; i < childRecorders.size(); i++) {
                var recorder = childRecorders.get(i);
                if (recorder != null) {
                  recorder.accept(childMove(move, i, childRecorders.size()));
                }
              }
            }
          });
    }
  }

  private static <Solution_> OriginCandidates<Solution_> mergeOrigins(
      List<OriginCandidates<Solution_>> origins) {
    if (origins.isEmpty()) {
      return new OriginCandidates<>(
          Collections.emptyIterator(),
          Collections.emptyIterator(),
          true,
          null,
          false,
          null,
          move -> false);
    }
    if (origins.size() == 1) {
      return origins.getFirst();
    }
    var emitted = new java.util.HashSet<ConstructionHeuristicNearbyRanking.CandidateIdentity>();
    var ranked =
        filtering(
            concatenateAll(origins.stream().map(OriginCandidates::rankedMoves).toList()),
            move -> emitted.add(ConstructionHeuristicNearbyRanking.identity(move)));
    var tail =
        filtering(
            concatenateAll(origins.stream().map(OriginCandidates::tailMoves).toList()),
            move -> emitted.add(ConstructionHeuristicNearbyRanking.identity(move)));
    java.util.function.ToIntFunction<Move<Solution_>> branchIndex =
        move -> {
          for (var i = 0; i < origins.size(); i++) {
            if (origins.get(i).containsCandidate().test(move)) {
              return i;
            }
          }
          return Integer.MAX_VALUE;
        };
    java.util.Comparator<Move<Solution_>> sourceOrder =
        (left, right) -> {
          var leftBranch = branchIndex.applyAsInt(left);
          var rightBranch = branchIndex.applyAsInt(right);
          if (leftBranch != rightBranch) {
            return Integer.compare(leftBranch, rightBranch);
          }
          var order = leftBranch < origins.size() ? origins.get(leftBranch).sourceOrder() : null;
          return order == null ? 0 : order.compare(left, right);
        };
    java.util.function.Consumer<List<Move<Solution_>>> sorter =
        origins.stream().allMatch(origin -> origin.neighborhoodSorter() == null)
            ? null
            : moves -> {
              var groups = new ArrayList<List<Move<Solution_>>>(origins.size());
              for (var ignored : origins) {
                groups.add(new ArrayList<>());
              }
              for (var move : moves) {
                groups.get(branchIndex.applyAsInt(move)).add(move);
              }
              moves.clear();
              for (var i = 0; i < groups.size(); i++) {
                var branchSorter = origins.get(i).neighborhoodSorter();
                if (branchSorter != null) {
                  branchSorter.accept(groups.get(i));
                }
                moves.addAll(groups.get(i));
              }
            };
    return new OriginCandidates<>(
        ranked,
        tail,
        true,
        sourceOrder,
        origins.stream().anyMatch(OriginCandidates::preserveCandidateOrder),
        sorter,
        move -> branchIndex.applyAsInt(move) < origins.size(),
        move -> {
          var branch = branchIndex.applyAsInt(move);
          var ranks = new ArrayList<Integer>();
          ranks.add(branch);
          if (branch < origins.size() && origins.get(branch).sourceRanks() != null) {
            ranks.addAll(origins.get(branch).sourceRanks().apply(move));
          }
          return List.copyOf(ranks);
        },
        move -> {
          var branch = branchIndex.applyAsInt(move);
          if (branch < origins.size() && origins.get(branch).selectionRecorder() != null) {
            origins.get(branch).selectionRecorder().accept(move);
          }
        });
  }

  private static <T> Iterator<T> concatenateAll(List<Iterator<T>> children) {
    return new Iterator<>() {
      private int nextChild;
      private Iterator<T> current = Collections.emptyIterator();

      @Override
      public boolean hasNext() {
        while (!current.hasNext() && nextChild < children.size()) {
          current = children.get(nextChild++);
        }
        return current.hasNext();
      }

      @Override
      public T next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        return current.next();
      }
    };
  }

  private static <Solution_> Move<Solution_> childMove(
      Move<Solution_> move, int index, int childCount) {
    return childCount > 1 && move instanceof SelectorBasedCompositeMove<Solution_> composite
        ? composite.getMoves()[index]
        : move;
  }

  private static <Solution_> int compareChildren(
      Move<Solution_> left,
      Move<Solution_> right,
      List<java.util.Comparator<Move<Solution_>>> orders) {
    for (var i = 0; i < orders.size(); i++) {
      var order = orders.get(i);
      if (order != null) {
        var result =
            order.compare(childMove(left, i, orders.size()), childMove(right, i, orders.size()));
        if (result != 0) {
          return result;
        }
      }
    }
    return 0;
  }

  private static final class LimitedCompositeSource<Solution_>
      implements CandidateSource<Solution_> {
    private final MoveSelector<Solution_> delegate;
    private final CandidateSource<Solution_> rankedSource;

    private LimitedCompositeSource(
        MoveSelector<Solution_> delegate, CandidateSource<Solution_> rankedSource) {
      this.delegate = delegate;
      this.rankedSource = rankedSource;
    }

    @Override
    public void resetPlacement() {
      rankedSource.resetPlacement();
    }

    @Override
    public void recordSelection(Move<Solution_> move) {
      rankedSource.recordSelection(move);
    }

    @Override
    public Iterator<OriginCandidates<Solution_>> iterator(ScoreDirector<Solution_> scoreDirector) {
      var allowed = new ArrayList<Move<Solution_>>();
      delegate.iterator().forEachRemaining(allowed::add);
      var leaves = new ArrayList<LeafSource<Solution_>>();
      collectLeaves(rankedSource, leaves);
      var origin =
          ConstructionHeuristicNearbyRanking.rankAdmittedComposite(
              allowed,
              leaves.stream()
                  .map(
                      leaf ->
                          new ConstructionHeuristicNearbyRanking.LeafRanking<>(
                              leaf.descriptor,
                              leaf.profiles,
                              leaf.queuedValue,
                              leaf.preserveCandidateOrder))
                  .toList(),
              scoreDirector);
      return Collections.singletonList(
              new OriginCandidates<>(
                  origin.rankedMoves(),
                  origin.tailMoves(),
                  origin.adaptive(),
                  origin.sourceOrder(),
                  sourcePreservesOrder(rankedSource),
                  origin.neighborhoodSorter(),
                  origin.containsCandidate(),
                  origin.sourceRanks(),
                  this::recordSelection))
          .iterator();
    }
  }

  /** Keeps independent recorder graphs in their declared Cartesian traversal order. */
  private static final class DependentCartesianSource<Solution_>
      implements CandidateSource<Solution_> {
    private final List<CandidateSource<Solution_>> children;
    private final CartesianProductMoveSelector<Solution_> delegate;
    private final boolean preserveCandidateOrder;

    private DependentCartesianSource(
        List<CandidateSource<Solution_>> children,
        CartesianProductMoveSelector<Solution_> delegate,
        boolean preserveCandidateOrder) {
      this.children = children;
      this.delegate = delegate;
      this.preserveCandidateOrder = preserveCandidateOrder;
    }

    @Override
    public void resetPlacement() {
      children.forEach(CandidateSource::resetPlacement);
    }

    @Override
    public boolean isGloballyOrdered() {
      return children.stream().anyMatch(CandidateSource::isGloballyOrdered);
    }

    @Override
    public void recordSelection(Move<Solution_> move) {
      children.forEach(child -> child.recordSelection(move));
    }

    @Override
    public Iterator<OriginCandidates<Solution_>> iterator(ScoreDirector<Solution_> scoreDirector) {
      var firstOrigins = children.getFirst().iterator(scoreDirector);
      return new Iterator<>() {
        @Override
        public boolean hasNext() {
          return firstOrigins.hasNext();
        }

        @Override
        public OriginCandidates<Solution_> next() {
          if (!hasNext()) {
            throw new NoSuchElementException();
          }
          var firstOrigin = firstOrigins.next();
          var adapters = new ArrayList<MoveSelector<Solution_>>(children.size());
          var optional = new boolean[] {false};
          var optionalChildren = new boolean[children.size()];
          var tupleRanks =
              new ArrayList<List<Integer>>(Collections.nCopies(children.size(), List.of()));
          var tupleRecordings = new Runnable[children.size()];
          var lastTupleRanks =
              new java.util.concurrent.atomic.AtomicReference<List<Integer>>(List.of());
          var lastTupleRecorder =
              new java.util.concurrent.atomic.AtomicReference<Runnable>(() -> {});
          for (var i = 0; i < children.size(); i++) {
            var childIndex = i;
            var source = children.get(i);
            var childDelegate = delegate.getChildMoveSelectorList().get(i);
            adapters.add(
                new AbstractMoveSelector<>() {
                  @Override
                  public boolean isNeverEnding() {
                    return false;
                  }

                  @Override
                  public long getSize() {
                    return childDelegate.getSize();
                  }

                  @Override
                  public boolean supportsPhaseAndSolverCaching() {
                    return false;
                  }

                  @Override
                  public Iterator<Move<Solution_>> iterator() {
                    tupleRanks.set(childIndex, List.of());
                    tupleRecordings[childIndex] = null;
                    var origins =
                        childIndex == 0
                            ? Collections.singletonList(firstOrigin).iterator()
                            : source.iterator(scoreDirector);
                    return new Iterator<>() {
                      private Iterator<Move<Solution_>> current = Collections.emptyIterator();
                      private OriginCandidates<Solution_> currentOrigin;
                      private int originOrdinal = -1;
                      private int emittedOrdinal;

                      @Override
                      public boolean hasNext() {
                        while (!current.hasNext() && origins.hasNext()) {
                          var origin = origins.next();
                          currentOrigin = origin;
                          originOrdinal++;
                          optionalChildren[childIndex] = origin.tailMoves().hasNext();
                          current = insertTailAfterFirst(origin.rankedMoves(), origin.tailMoves());
                        }
                        return current.hasNext();
                      }

                      @Override
                      public Move<Solution_> next() {
                        if (!hasNext()) {
                          throw new NoSuchElementException();
                        }
                        var move = current.next();
                        var ranks = new ArrayList<Integer>();
                        if (currentOrigin.sourceRanks() != null) {
                          ranks.addAll(currentOrigin.sourceRanks().apply(move));
                        } else {
                          ranks.add(originOrdinal);
                          ranks.add(emittedOrdinal);
                        }
                        emittedOrdinal++;
                        tupleRanks.set(childIndex, List.copyOf(ranks));
                        // Candidate snapshots may prefetch a recorder's next source origin. Restore
                        // the actual emitted selection before dependent child iterators are reset.
                        var recorder = currentOrigin.selectionRecorder();
                        Runnable recording =
                            recorder == null
                                ? () -> source.recordSelection(move)
                                : recorder
                                        instanceof
                                        ConstructionHeuristicNearbyMoveSelector
                                                    .SnapshotSelectionRecorder<
                                                Solution_>
                                            snapshot
                                    ? snapshot.snapshot(move)
                                    : () -> recorder.accept(move);
                        tupleRecordings[childIndex] = recording;
                        recording.run();
                        return move;
                      }
                    };
                  }
                });
          }
          var tuples =
              new CartesianProductMoveSelector<>(
                      adapters, delegate.isIgnoreEmptyChildIterators(), false)
                  .iterator();
          var ranked =
              filtering(
                  tuples,
                  move -> {
                    var ranks = new ArrayList<Integer>();
                    tupleRanks.forEach(ranks::addAll);
                    lastTupleRanks.set(List.copyOf(ranks));
                    var recordings =
                        java.util.Arrays.copyOf(tupleRecordings, tupleRecordings.length);
                    lastTupleRecorder.set(
                        () -> {
                          for (var recording : recordings) {
                            if (recording != null) {
                              recording.run();
                            }
                          }
                        });
                    if (ConstructionHeuristicNearbyRanking.isMeaningfulAssignment(move)) {
                      return true;
                    }
                    optional[0] = true;
                    return false;
                  });
          // The all-optional tuple is unique in effect and is evaluated once after assignment
          // search settles. Detection remains lazy; no optional product is materialized.
          Iterator<Move<Solution_>> tail =
              new Iterator<>() {
                private boolean emitted;

                @Override
                public boolean hasNext() {
                  var allOptional = true;
                  for (var childOptional : optionalChildren) {
                    allOptional &= childOptional;
                  }
                  return !emitted && (optional[0] || allOptional);
                }

                @Override
                public Move<Solution_> next() {
                  if (!hasNext()) {
                    throw new NoSuchElementException();
                  }
                  emitted = true;
                  return SelectorBasedNoChangeMove.getInstance();
                }
              };
          return new OriginCandidates<>(
              ranked,
              tail,
              true,
              null,
              preserveCandidateOrder
                  || children.stream()
                      .anyMatch(
                          ConstructionHeuristicNearbyMoveSelectorFactory::sourcePreservesOrder),
              null,
              move -> true,
              move -> lastTupleRanks.get(),
              new ConstructionHeuristicNearbyMoveSelector.SnapshotSelectionRecorder<>() {
                @Override
                public void accept(Move<Solution_> move) {
                  lastTupleRecorder.get().run();
                }

                @Override
                public Runnable snapshot(Move<Solution_> move) {
                  return lastTupleRecorder.get();
                }
              });
        }
      };
    }
  }

  private static boolean sourcePreservesOrder(CandidateSource<?> source) {
    if (source instanceof LeafSource<?> leaf) {
      return leaf.preserveCandidateOrder;
    } else if (source instanceof CartesianSource<?> product) {
      return product.preserveCandidateOrder
          || product.children.stream()
              .anyMatch(ConstructionHeuristicNearbyMoveSelectorFactory::sourcePreservesOrder);
    } else if (source instanceof UnionSource<?> union) {
      return union.preserveCandidateOrder
          || union.children.stream()
              .anyMatch(ConstructionHeuristicNearbyMoveSelectorFactory::sourcePreservesOrder);
    } else if (source instanceof SortedCompositeSource<?> sorted) {
      return true;
    } else if (source instanceof LimitedCompositeSource<?> limited) {
      return sourcePreservesOrder(limited.rankedSource);
    } else if (source instanceof DependentCartesianSource<?> product) {
      return product.preserveCandidateOrder
          || product.children.stream()
              .anyMatch(ConstructionHeuristicNearbyMoveSelectorFactory::sourcePreservesOrder);
    }
    return false;
  }

  private static <Solution_> void collectLeaves(
      CandidateSource<Solution_> source, List<LeafSource<Solution_>> leaves) {
    if (source instanceof LeafSource<Solution_> leaf) {
      leaves.add(leaf);
    } else if (source instanceof CartesianSource<Solution_> product) {
      product.children.forEach(child -> collectLeaves(child, leaves));
    } else if (source instanceof UnionSource<Solution_> union) {
      union.children.forEach(child -> collectLeaves(child, leaves));
    } else if (source instanceof SortedCompositeSource<Solution_> sorted) {
      collectLeaves(sorted.rankedSource, leaves);
    } else if (source instanceof LimitedCompositeSource<Solution_> limited) {
      collectLeaves(limited.rankedSource, leaves);
    } else if (source instanceof DependentCartesianSource<Solution_> product) {
      product.children.forEach(child -> collectLeaves(child, leaves));
    }
  }

  private static <T> Iterator<T> insertTailAfterFirst(Iterator<T> ranked, Iterator<T> tail) {
    return new Iterator<>() {
      private boolean firstReturned;

      @Override
      public boolean hasNext() {
        return ranked.hasNext() || tail.hasNext();
      }

      @Override
      public T next() {
        if (!firstReturned && ranked.hasNext()) {
          firstReturned = true;
          return ranked.next();
        }
        firstReturned = true;
        return tail.hasNext() ? tail.next() : ranked.next();
      }
    };
  }

  private static <T> Iterator<T> singletonRecording(Iterator<T> source) {
    return new Iterator<>() {
      private Iterator<T> snapshot;

      @Override
      public boolean hasNext() {
        if (snapshot == null) {
          snapshot =
              source.hasNext()
                  ? Collections.singletonList(source.next()).iterator()
                  : Collections.emptyIterator();
        }
        return snapshot.hasNext();
      }

      @Override
      public T next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        return snapshot.next();
      }
    };
  }

  private static <T, R> Iterator<R> mapping(
      Iterator<T> input, java.util.function.Function<T, R> mapper) {
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return input.hasNext();
      }

      @Override
      public R next() {
        return mapper.apply(input.next());
      }
    };
  }

  private static <T> Iterator<T> filtering(
      Iterator<T> input, java.util.function.Predicate<T> predicate) {
    return new Iterator<>() {
      private T upcoming;

      @Override
      public boolean hasNext() {
        while (upcoming == null && input.hasNext()) {
          var candidate = input.next();
          if (predicate.test(candidate)) {
            upcoming = candidate;
          }
        }
        return upcoming != null;
      }

      @Override
      public T next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        var candidate = upcoming;
        upcoming = null;
        return candidate;
      }
    };
  }
}
