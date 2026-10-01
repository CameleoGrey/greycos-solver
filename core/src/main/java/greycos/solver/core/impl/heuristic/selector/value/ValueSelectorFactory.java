package greycos.solver.core.impl.heuristic.selector.value;

import static greycos.solver.core.config.heuristic.selector.common.SelectionOrder.SORTED;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.decorator.SelectionSorterOrder;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.AbstractSelectorFactory;
import greycos.solver.core.impl.heuristic.selector.common.decorator.ComparatorFactorySelectionSorter;
import greycos.solver.core.impl.heuristic.selector.common.decorator.ComparatorSelectionSorter;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionProbabilityWeightFactory;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionSorter;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandomFactory;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbySelectionSource;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbySelectionTuning;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelectorFactory;
import greycos.solver.core.impl.heuristic.selector.value.decorator.AssignedListValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.decorator.CachingValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.decorator.DowncastingValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.decorator.FilteringValueRangeSelector;
import greycos.solver.core.impl.heuristic.selector.value.decorator.FilteringValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.decorator.GuidedLocalSearchValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.decorator.IterableFromEntityPropertyValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.decorator.ProbabilityValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.decorator.ReinitializeVariableValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.decorator.SelectedCountLimitValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.decorator.ShufflingValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.decorator.UnassignedListValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.mimic.MimicRecordingValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.mimic.MimicReplayingValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.mimic.ValueMimicRecorder;
import greycos.solver.core.impl.heuristic.selector.value.nearby.AbstractNearbyValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.nearby.NearEntityNearbyValueSelector;
import greycos.solver.core.impl.heuristic.selector.value.nearby.NearValueNearbyValueSelector;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.ClassInstanceCache;

public class ValueSelectorFactory<Solution_>
    extends AbstractSelectorFactory<Solution_, ValueSelectorConfig> {

  public static <Solution_> ValueSelectorFactory<Solution_> create(
      ValueSelectorConfig valueSelectorConfig) {
    return new ValueSelectorFactory<>(valueSelectorConfig);
  }

  public ValueSelectorFactory(ValueSelectorConfig valueSelectorConfig) {
    super(valueSelectorConfig);
  }

  public GenuineVariableDescriptor<Solution_> extractVariableDescriptor(
      HeuristicConfigPolicy<Solution_> configPolicy, EntityDescriptor<Solution_> entityDescriptor) {
    var variableName = config.getVariableName();
    var mimicSelectorRef = config.getMimicSelectorRef();
    if (mimicSelectorRef != null) {
      return resolveMimicVariableDescriptor(configPolicy, entityDescriptor);
    } else if (variableName != null) {
      return getVariableDescriptorForName(
          downcastEntityDescriptor(configPolicy, entityDescriptor), variableName);
    } else {
      return null;
    }
  }

  public ValueSelector<Solution_> buildValueSelector(
      HeuristicConfigPolicy<Solution_> configPolicy,
      EntityDescriptor<Solution_> entityDescriptor,
      SelectionCacheType minimumCacheType,
      SelectionOrder inheritedSelectionOrder) {
    return buildValueSelector(
        configPolicy,
        entityDescriptor,
        minimumCacheType,
        inheritedSelectionOrder,
        configPolicy.isReinitializeVariableFilterEnabled(),
        ListValueFilteringType.NONE);
  }

  public ValueSelector<Solution_> buildValueSelector(
      HeuristicConfigPolicy<Solution_> configPolicy,
      EntityDescriptor<Solution_> entityDescriptor,
      SelectionCacheType minimumCacheType,
      SelectionOrder inheritedSelectionOrder,
      boolean applyReinitializeVariableFiltering,
      ListValueFilteringType listValueFilteringType) {
    return buildValueSelector(
        configPolicy,
        entityDescriptor,
        minimumCacheType,
        inheritedSelectionOrder,
        applyReinitializeVariableFiltering,
        listValueFilteringType,
        null,
        false);
  }

  /**
   * @param configPolicy never null
   * @param entityDescriptor never null
   * @param minimumCacheType never null, If caching is used (different from {@link
   *     SelectionCacheType#JUST_IN_TIME}), then it should be at least this {@link
   *     SelectionCacheType} because an ancestor already uses such caching and less would be
   *     pointless.
   * @param inheritedSelectionOrder never null
   * @param applyReinitializeVariableFiltering the reinitialization flag
   * @param listValueFilteringType the list filtering type
   * @param entityValueRangeRecorderId the recorder id to be used to create a replaying selector
   *     when enabling entity value range
   * @param assertBothSides a flag used by the entity value range filtering select to enable
   *     different types of validations
   * @return never null
   */
  public ValueSelector<Solution_> buildValueSelector(
      HeuristicConfigPolicy<Solution_> configPolicy,
      EntityDescriptor<Solution_> entityDescriptor,
      SelectionCacheType minimumCacheType,
      SelectionOrder inheritedSelectionOrder,
      boolean applyReinitializeVariableFiltering,
      ListValueFilteringType listValueFilteringType,
      String entityValueRangeRecorderId,
      boolean assertBothSides) {
    return buildValueSelectorSource(
            configPolicy,
            entityDescriptor,
            minimumCacheType,
            inheritedSelectionOrder,
            applyReinitializeVariableFiltering,
            listValueFilteringType,
            entityValueRangeRecorderId,
            assertBothSides,
            false)
        .selector();
  }

  public ValueSelector<Solution_> buildOriginValueSelector(
      HeuristicConfigPolicy<Solution_> configPolicy,
      EntityDescriptor<Solution_> entityDescriptor,
      SelectionCacheType minimumCacheType,
      SelectionOrder inheritedSelectionOrder,
      ListValueFilteringType listValueFilteringType) {
    return buildValueSelectorSource(
            configPolicy,
            entityDescriptor,
            minimumCacheType,
            inheritedSelectionOrder,
            false,
            listValueFilteringType,
            null,
            false,
            true)
        .selector();
  }

  public NearbySelectionSource<Solution_, IterableValueSelector<Solution_>>
      buildValueSelectorForNearby(
          HeuristicConfigPolicy<Solution_> configPolicy,
          EntityDescriptor<Solution_> entityDescriptor,
          SelectionCacheType minimumCacheType,
          SelectionOrder inheritedSelectionOrder) {
    var source =
        buildValueSelectorSource(
            configPolicy,
            entityDescriptor,
            minimumCacheType,
            inheritedSelectionOrder,
            false,
            ListValueFilteringType.NONE,
            null,
            false,
            false);
    if (!(source.selector() instanceof IterableValueSelector<Solution_> selector)
        || !(source.populationSelector()
            instanceof IterableValueSelector<Solution_> populationSelector)) {
      throw new IllegalArgumentException(
          "The valueSelectorConfig (%s) for nearby selection needs to be based on an IterableValueSelector (%s)."
              .formatted(config, source.selector()));
    }
    return new NearbySelectionSource<>(
        selector, populationSelector, source.liveFilter(), source.membershipSupplier());
  }

  private NearbySelectionSource<Solution_, ValueSelector<Solution_>> buildValueSelectorSource(
      HeuristicConfigPolicy<Solution_> configPolicy,
      EntityDescriptor<Solution_> entityDescriptor,
      SelectionCacheType minimumCacheType,
      SelectionOrder inheritedSelectionOrder,
      boolean applyReinitializeVariableFiltering,
      ListValueFilteringType listValueFilteringType,
      String entityValueRangeRecorderId,
      boolean assertBothSides,
      boolean originSelection) {
    if (config.getMimicSelectorRef() != null) {
      var variableDescriptor = resolveMimicVariableDescriptor(configPolicy, entityDescriptor);
      var valueSelector = buildMimicReplaying(configPolicy);
      var recordedSource = getMimicRecorder(configPolicy).getNearbySelectionSource();
      var populationSelector =
          recordedSource == null ? valueSelector : recordedSource.populationSelector();
      valueSelector =
          applyReinitializeVariableFiltering(
              applyReinitializeVariableFiltering, variableDescriptor, valueSelector);
      valueSelector = applyDowncasting(valueSelector);
      return new NearbySelectionSource<>(
          valueSelector,
          populationSelector,
          recordedSource == null ? null : recordedSource.liveFilter(),
          recordedSource == null ? null : recordedSource.membershipSupplier());
    }
    var variableDescriptor =
        deduceGenuineVariableDescriptor(
            downcastEntityDescriptor(configPolicy, entityDescriptor), config.getVariableName());
    var resolvedCacheType = SelectionCacheType.resolve(config.getCacheType(), minimumCacheType);
    var resolvedSelectionOrder =
        SelectionOrder.resolve(config.getSelectionOrder(), inheritedSelectionOrder);

    var nearbySelectionConfig = config.getNearbySelectionConfig();
    if (nearbySelectionConfig != null) {
      nearbySelectionConfig.validateNearby(resolvedCacheType, resolvedSelectionOrder);
    }
    validateCacheTypeVersusSelectionOrder(
        resolvedCacheType, resolvedSelectionOrder, entityValueRangeRecorderId != null);
    validateSorting(resolvedSelectionOrder);
    validateProbability(resolvedSelectionOrder);
    validateSelectedLimit(minimumCacheType);

    // baseValueSelector and lower should be SelectionOrder.ORIGINAL if they are going to get cached
    // completely
    var randomSelection =
        determineBaseRandomSelection(variableDescriptor, resolvedCacheType, resolvedSelectionOrder);
    var instanceCache = configPolicy.getClassInstanceCache();
    var sorter = determineSorter(variableDescriptor, resolvedSelectionOrder, instanceCache);
    var valueSelector =
        buildBaseValueSelector(
            variableDescriptor,
            sorter,
            SelectionCacheType.max(minimumCacheType, resolvedCacheType),
            randomSelection);
    var populationSelector = valueSelector;
    SelectionFilter<Solution_, Object> liveFilter =
        config.getFilterClass() == null
            ? null
            : instanceCache.newInstance(config, "filterClass", config.getFilterClass());
    var selectionContext = configPolicy.getGuidedLocalSearchSelectionContext();
    boolean directedOrigin = originSelection && selectionContext != null;
    if (directedOrigin
        && variableDescriptor instanceof ListVariableDescriptor<Solution_> listVariableDescriptor) {
      SelectionFilter<Solution_, Object> legalOriginFilter =
          (scoreDirector, value) -> {
            var state =
                ((InnerScoreDirector<Solution_, ?>) scoreDirector)
                    .getListVariableState(listVariableDescriptor);
            return !state.isPinned(value)
                && (listValueFilteringType != ListValueFilteringType.ACCEPT_ASSIGNED
                    || state.isAssigned(value));
          };
      liveFilter =
          liveFilter == null
              ? legalOriginFilter
              : SelectionFilter.compose(liveFilter, legalOriginFilter);
    }
    if (nearbySelectionConfig != null) {
      valueSelector =
          applyNearbySelection(
              configPolicy,
              entityDescriptor,
              minimumCacheType,
              resolvedSelectionOrder,
              valueSelector,
              entityValueRangeRecorderId,
              assertBothSides,
              listValueFilteringType,
              liveFilter);
    } else {
      /*
       * The nearby selector will implement its own logic to filter out unreachable elements.
       * Therefore, we only apply entity value range filtering if the nearby feature is not enabled;
       * otherwise, we would end up applying the filtering logic twice.
       */
      valueSelector =
          applyValueRangeFiltering(
              configPolicy,
              valueSelector,
              entityDescriptor,
              minimumCacheType,
              inheritedSelectionOrder,
              randomSelection,
              entityValueRangeRecorderId,
              assertBothSides);
    }
    if (nearbySelectionConfig == null && liveFilter != null) {
      valueSelector = FilteringValueSelector.of(valueSelector, liveFilter);
    }
    valueSelector =
        applyProbability(resolvedCacheType, resolvedSelectionOrder, valueSelector, instanceCache);
    valueSelector = applyShuffling(resolvedCacheType, resolvedSelectionOrder, valueSelector);
    valueSelector = applyCaching(resolvedCacheType, resolvedSelectionOrder, valueSelector);
    valueSelector = applySelectedLimit(valueSelector);
    valueSelector =
        applyListValueFiltering(
            configPolicy, listValueFilteringType, variableDescriptor, valueSelector);
    Supplier<Set<Object>> membershipSupplier = null;
    if (nearbySelectionConfig == null && config.getSelectedCountLimit() != null) {
      var membershipSelector = valueSelector;
      membershipSupplier =
          () -> {
            Set<Object> membership = Collections.newSetFromMap(new IdentityHashMap<>());
            membershipSelector.endingIterator(null).forEachRemaining(membership::add);
            return membership;
          };
    }
    if (directedOrigin) {
      if (resolvedCacheType.isCached() && liveFilter != null) {
        valueSelector = FilteringValueSelector.of(valueSelector, liveFilter);
      }
      if (!(valueSelector instanceof IterableValueSelector<Solution_> iterableValueSelector)
          || !variableDescriptor.isListVariable()) {
        throw new IllegalArgumentException(
            "Guided Local Search directedOriginSelection requires an iterable list origin ("
                + config
                + "). Maybe use a list value range or disable directedOriginSelection.");
      }
      valueSelector =
          new GuidedLocalSearchValueSelector<>(
              iterableValueSelector,
              selectionContext,
              resolvedSelectionOrder == SelectionOrder.RANDOM
                  && config.getSelectedCountLimit() == null
                  && valueSelector.isNeverEnding());
    }
    valueSelector =
        applyMimicRecording(
            configPolicy, valueSelector, populationSelector, liveFilter, membershipSupplier);
    valueSelector =
        applyReinitializeVariableFiltering(
            applyReinitializeVariableFiltering, variableDescriptor, valueSelector);
    valueSelector = applyDowncasting(valueSelector);
    return new NearbySelectionSource<>(
        valueSelector, populationSelector, liveFilter, membershipSupplier);
  }

  private ValueMimicRecorder<Solution_> getMimicRecorder(
      HeuristicConfigPolicy<Solution_> configPolicy) {
    var valueMimicRecorder = configPolicy.getValueMimicRecorder(config.getMimicSelectorRef());
    if (valueMimicRecorder == null) {
      throw new IllegalArgumentException(
          "The valueSelectorConfig (%s) has a mimicSelectorRef (%s) for which no valueSelector with that id exists (in its solver phase)."
              .formatted(config, config.getMimicSelectorRef()));
    }
    return valueMimicRecorder;
  }

  private GenuineVariableDescriptor<Solution_> resolveMimicVariableDescriptor(
      HeuristicConfigPolicy<Solution_> configPolicy, EntityDescriptor<Solution_> entityDescriptor) {
    var recordedDescriptor = getMimicRecorder(configPolicy).getVariableDescriptor();
    var contextualDescriptor = downcastEntityDescriptor(configPolicy, entityDescriptor);
    var variableName = config.getVariableName();
    if (variableName != null && !variableName.equals(recordedDescriptor.getVariableName())) {
      throw new IllegalArgumentException(
          "The valueSelectorConfig (%s) with mimicSelectorRef (%s) uses variableName (%s), but the recording selector uses variableName (%s)."
              .formatted(
                  config,
                  config.getMimicSelectorRef(),
                  variableName,
                  recordedDescriptor.getVariableName()));
    }
    // Inherited variables retain the descriptor of their declaring entity class.
    if (contextualDescriptor.getGenuineVariableDescriptor(recordedDescriptor.getVariableName())
        != recordedDescriptor) {
      throw new IllegalArgumentException(
          "The valueSelectorConfig (%s) with mimicSelectorRef (%s) uses entityClass (%s), but the recording selector uses variableName (%s) from entityClass (%s)."
              .formatted(
                  config,
                  config.getMimicSelectorRef(),
                  contextualDescriptor.getEntityClass().getName(),
                  recordedDescriptor.getVariableName(),
                  recordedDescriptor.getEntityDescriptor().getEntityClass().getName()));
    }
    return recordedDescriptor;
  }

  protected ValueSelector<Solution_> buildMimicReplaying(
      HeuristicConfigPolicy<Solution_> configPolicy) {
    if (config.getId() != null
        || config.getCacheType() != null
        || config.getSelectionOrder() != null
        || config.getNearbySelectionConfig() != null
        || config.getFilterClass() != null
        || config.getSorterManner() != null
        || config.getComparatorClass() != null
        || config.getComparatorFactoryClass() != null
        || config.getSorterOrder() != null
        || config.getSorterClass() != null
        || config.getProbabilityWeightFactoryClass() != null
        || config.getSelectedCountLimit() != null) {
      throw new IllegalArgumentException(
          "The valueSelectorConfig (%s) with mimicSelectorRef (%s) has another property that is not null."
              .formatted(config, config.getMimicSelectorRef()));
    }
    return new MimicReplayingValueSelector<>(getMimicRecorder(configPolicy));
  }

  protected EntityDescriptor<Solution_> downcastEntityDescriptor(
      HeuristicConfigPolicy<Solution_> configPolicy, EntityDescriptor<Solution_> entityDescriptor) {
    var downcastEntityClass = config.getDowncastEntityClass();
    if (downcastEntityClass != null) {
      var parentEntityClass = entityDescriptor.getEntityClass();
      if (!parentEntityClass.isAssignableFrom(downcastEntityClass)) {
        throw new IllegalStateException(
            "The downcastEntityClass (%s) is not a subclass of the parentEntityClass (%s) configured by the %s."
                .formatted(
                    downcastEntityClass, parentEntityClass, EntitySelector.class.getSimpleName()));
      }
      var solutionDescriptor = configPolicy.getSolutionDescriptor();
      entityDescriptor = solutionDescriptor.getEntityDescriptorStrict(downcastEntityClass);
      if (entityDescriptor == null) {
        throw new IllegalArgumentException(
            """
                        The selectorConfig (%s) has an downcastEntityClass (%s) that is not a known planning entity.
                        Check your solver configuration. If that class (%s) is not in the entityClassSet (%s), \
                        check your @%s implementation's annotated methods too."""
                .formatted(
                    config,
                    downcastEntityClass,
                    downcastEntityClass.getSimpleName(),
                    solutionDescriptor.getEntityClassSet(),
                    PlanningSolution.class.getSimpleName()));
      }
    }
    return entityDescriptor;
  }

  protected boolean determineBaseRandomSelection(
      GenuineVariableDescriptor<Solution_> variableDescriptor,
      SelectionCacheType resolvedCacheType,
      SelectionOrder resolvedSelectionOrder) {
    return switch (resolvedSelectionOrder) {
      case ORIGINAL, SORTED, SHUFFLED, PROBABILISTIC ->
          // baseValueSelector and lower should be ORIGINAL if they are going to get cached
          // completely
          false;
      case RANDOM ->
          // Predict if caching will occur
          resolvedCacheType.isNotCached() || !hasFiltering(variableDescriptor);
      default ->
          throw new IllegalStateException(
              "The selectionOrder (" + resolvedSelectionOrder + ") is not implemented.");
    };
  }

  private SelectionSorter<Solution_, Object> determineSorter(
      GenuineVariableDescriptor<Solution_> variableDescriptor,
      SelectionOrder resolvedSelectionOrder,
      ClassInstanceCache instanceCache) {
    if (resolvedSelectionOrder != SORTED) {
      return null;
    }
    SelectionSorter<Solution_, Object> sorter;
    var sorterManner = config.getSorterManner();
    var comparatorClass = config.getComparatorClass();
    var comparatorFactoryClass = config.getComparatorFactoryClass();
    if (sorterManner != null) {
      if (!ValueSelectorConfig.hasSorter(sorterManner, variableDescriptor)) {
        return null;
      }
      sorter = ValueSelectorConfig.determineSorter(sorterManner, variableDescriptor);
    } else if (comparatorClass != null) {
      Comparator<Object> sorterComparator =
          instanceCache.newInstance(config, "comparatorClass", comparatorClass);
      sorter =
          new ComparatorSelectionSorter<>(
              sorterComparator, SelectionSorterOrder.resolve(config.getSorterOrder()));
    } else if (comparatorFactoryClass != null) {
      var comparatorFactory =
          instanceCache.newInstance(config, "comparatorFactoryClass", comparatorFactoryClass);
      sorter =
          new ComparatorFactorySelectionSorter<>(
              comparatorFactory, SelectionSorterOrder.resolve(config.getSorterOrder()));
    } else if (config.getSorterClass() != null) {
      sorter = instanceCache.newInstance(config, "sorterClass", config.getSorterClass());
    } else {
      throw new IllegalArgumentException(
          """
                    The valueSelectorConfig (%s) with resolvedSelectionOrder (%s) needs \
                    a sorterManner (%s) or a comparatorClass (%s) or a comparatorFactoryClass (%s) \
                    or a sorterClass (%s)."""
              .formatted(
                  config,
                  resolvedSelectionOrder,
                  sorterManner,
                  comparatorClass,
                  comparatorFactoryClass,
                  config.getSorterClass()));
    }
    return sorter;
  }

  private ValueSelector<Solution_> buildBaseValueSelector(
      GenuineVariableDescriptor<Solution_> variableDescriptor,
      SelectionSorter<Solution_, Object> sorter,
      SelectionCacheType minimumCacheType,
      boolean randomSelection) {
    var valueRangeDescriptor = variableDescriptor.getValueRangeDescriptor();
    // TODO minimumCacheType SOLVER is only a problem if the valueRange includes entities or custom
    // weird cloning
    if (minimumCacheType == SelectionCacheType.SOLVER) {
      // TODO Solver cached entities are not compatible with ConstraintStreams and
      // IncrementalScoreDirector
      // because between phases the entities get cloned
      throw new IllegalArgumentException(
          "The minimumCacheType ("
              + minimumCacheType
              + ") is not yet supported. Please use "
              + SelectionCacheType.PHASE
              + " instead.");
    }
    if (valueRangeDescriptor.canExtractValueRangeFromSolution()) {
      return new IterableFromSolutionPropertyValueSelector<>(
          valueRangeDescriptor, sorter, minimumCacheType, randomSelection);
    } else {
      // TODO Do not allow PHASE cache on FromEntityPropertyValueSelector, except if the
      // moveSelector is PHASE cached too.
      var fromEntityPropertySelector =
          new FromEntityPropertyValueSelector<>(valueRangeDescriptor, sorter, randomSelection);
      return new IterableFromEntityPropertyValueSelector<>(
          fromEntityPropertySelector, minimumCacheType, randomSelection);
    }
  }

  private boolean hasFiltering(GenuineVariableDescriptor<Solution_> variableDescriptor) {
    return config.getFilterClass() != null;
  }

  protected ValueSelector<Solution_> applyFiltering(
      ValueSelector<Solution_> valueSelector, ClassInstanceCache instanceCache) {
    var variableDescriptor = valueSelector.getVariableDescriptor();
    if (hasFiltering(variableDescriptor)) {
      List<SelectionFilter<Solution_, Object>> filterList =
          new ArrayList<>(config.getFilterClass() == null ? 1 : 2);
      if (config.getFilterClass() != null) {
        filterList.add(instanceCache.newInstance(config, "filterClass", config.getFilterClass()));
      }
      valueSelector = FilteringValueSelector.of(valueSelector, SelectionFilter.compose(filterList));
    }
    return valueSelector;
  }

  protected void validateSorting(SelectionOrder resolvedSelectionOrder) {
    var sorterManner = config.getSorterManner();
    var comparatorClass = config.getComparatorClass();
    var comparatorFactoryClass = config.getComparatorFactoryClass();
    var sorterOrder = config.getSorterOrder();
    var sorterClass = config.getSorterClass();
    if ((sorterManner != null
            || comparatorClass != null
            || comparatorFactoryClass != null
            || sorterOrder != null
            || sorterClass != null)
        && resolvedSelectionOrder != SORTED) {
      throw new IllegalArgumentException(
          """
                    The valueSelectorConfig (%s) with sorterManner (%s) \
                    and comparatorClass (%s) and comparatorFactoryClass (%s) and sorterOrder (%s) and sorterClass (%s) \
                    has a resolvedSelectionOrder (%s) that is not %s."""
              .formatted(
                  config,
                  sorterManner,
                  comparatorClass,
                  comparatorFactoryClass,
                  sorterOrder,
                  sorterClass,
                  resolvedSelectionOrder,
                  SORTED));
    }
    assertNotSorterMannerAnd(config, "comparatorClass", ValueSelectorConfig::getComparatorClass);
    assertNotSorterClassAnd(config, "comparatorClass", ValueSelectorConfig::getComparatorClass);
    assertNotSorterMannerAnd(
        config, "comparatorFactoryClass", ValueSelectorConfig::getComparatorFactoryClass);
    assertNotSorterClassAnd(
        config, "comparatorFactoryClass", ValueSelectorConfig::getComparatorFactoryClass);
    assertNotSorterMannerAnd(config, "sorterClass", ValueSelectorConfig::getSorterClass);
    assertNotSorterMannerAnd(config, "sorterOrder", ValueSelectorConfig::getSorterOrder);
    assertNotSorterClassAnd(config, "sorterOrder", ValueSelectorConfig::getSorterOrder);
    if (comparatorClass != null && comparatorFactoryClass != null) {
      throw new IllegalArgumentException(
          "The valueSelectorConfig (%s) has both a comparatorClass (%s) and a comparatorFactoryClass (%s)."
              .formatted(config, comparatorClass, comparatorFactoryClass));
    }
  }

  private static void assertNotSorterMannerAnd(
      ValueSelectorConfig config,
      String propertyName,
      Function<ValueSelectorConfig, Object> propertyAccessor) {
    var sorterManner = config.getSorterManner();
    var property = propertyAccessor.apply(config);
    if (sorterManner != null && property != null) {
      throw new IllegalArgumentException(
          "The entitySelectorConfig (%s) has both a sorterManner (%s) and a %s (%s)."
              .formatted(config, sorterManner, propertyName, property));
    }
  }

  private static void assertNotSorterClassAnd(
      ValueSelectorConfig config,
      String propertyName,
      Function<ValueSelectorConfig, Object> propertyAccessor) {
    var sorterClass = config.getSorterClass();
    var property = propertyAccessor.apply(config);
    if (sorterClass != null && property != null) {
      throw new IllegalArgumentException(
          "The entitySelectorConfig (%s) with sorterClass (%s) has a non-null %s (%s)."
              .formatted(config, sorterClass, propertyName, property));
    }
  }

  protected void validateProbability(SelectionOrder resolvedSelectionOrder) {
    if (config.getProbabilityWeightFactoryClass() != null
        && resolvedSelectionOrder != SelectionOrder.PROBABILISTIC) {
      throw new IllegalArgumentException(
          "The valueSelectorConfig ("
              + config
              + ") with probabilityWeightFactoryClass ("
              + config.getProbabilityWeightFactoryClass()
              + ") has a resolvedSelectionOrder ("
              + resolvedSelectionOrder
              + ") that is not "
              + SelectionOrder.PROBABILISTIC
              + ".");
    }
  }

  protected ValueSelector<Solution_> applyProbability(
      SelectionCacheType resolvedCacheType,
      SelectionOrder resolvedSelectionOrder,
      ValueSelector<Solution_> valueSelector,
      ClassInstanceCache instanceCache) {
    if (resolvedSelectionOrder == SelectionOrder.PROBABILISTIC) {
      if (config.getProbabilityWeightFactoryClass() == null) {
        throw new IllegalArgumentException(
            "The valueSelectorConfig ("
                + config
                + ") with resolvedSelectionOrder ("
                + resolvedSelectionOrder
                + ") needs a probabilityWeightFactoryClass ("
                + config.getProbabilityWeightFactoryClass()
                + ").");
      }
      SelectionProbabilityWeightFactory<Solution_, Object> probabilityWeightFactory =
          instanceCache.newInstance(
              config, "probabilityWeightFactoryClass", config.getProbabilityWeightFactoryClass());
      if (!(valueSelector instanceof IterableValueSelector)) {
        throw new IllegalArgumentException(
            "The valueSelectorConfig ("
                + config
                + ") with resolvedCacheType ("
                + resolvedCacheType
                + ") and resolvedSelectionOrder ("
                + resolvedSelectionOrder
                + ") needs to be based on an "
                + IterableValueSelector.class.getSimpleName()
                + " ("
                + valueSelector
                + ")."
                + " Check your @"
                + ValueRangeProvider.class.getSimpleName()
                + " annotations.");
      }
      valueSelector =
          new ProbabilityValueSelector<>(
              (IterableValueSelector<Solution_>) valueSelector,
              resolvedCacheType,
              probabilityWeightFactory);
    }
    return valueSelector;
  }

  private ValueSelector<Solution_> applyShuffling(
      SelectionCacheType resolvedCacheType,
      SelectionOrder resolvedSelectionOrder,
      ValueSelector<Solution_> valueSelector) {
    if (resolvedSelectionOrder == SelectionOrder.SHUFFLED) {
      if (!(valueSelector instanceof IterableValueSelector)) {
        throw new IllegalArgumentException(
            "The valueSelectorConfig ("
                + config
                + ") with resolvedCacheType ("
                + resolvedCacheType
                + ") and resolvedSelectionOrder ("
                + resolvedSelectionOrder
                + ") needs to be based on an "
                + IterableValueSelector.class.getSimpleName()
                + " ("
                + valueSelector
                + ")."
                + " Check your @"
                + ValueRangeProvider.class.getSimpleName()
                + " annotations.");
      }
      valueSelector =
          new ShufflingValueSelector<>(
              (IterableValueSelector<Solution_>) valueSelector, resolvedCacheType);
    }
    return valueSelector;
  }

  private ValueSelector<Solution_> applyCaching(
      SelectionCacheType resolvedCacheType,
      SelectionOrder resolvedSelectionOrder,
      ValueSelector<Solution_> valueSelector) {
    if (resolvedCacheType.isCached()
        && resolvedCacheType.compareTo(valueSelector.getCacheType()) > 0) {
      if (!(valueSelector instanceof IterableValueSelector)) {
        throw new IllegalArgumentException(
            "The valueSelectorConfig ("
                + config
                + ") with resolvedCacheType ("
                + resolvedCacheType
                + ") and resolvedSelectionOrder ("
                + resolvedSelectionOrder
                + ") needs to be based on an "
                + IterableValueSelector.class.getSimpleName()
                + " ("
                + valueSelector
                + ")."
                + " Check your @"
                + ValueRangeProvider.class.getSimpleName()
                + " annotations.");
      }
      valueSelector =
          new CachingValueSelector<>(
              (IterableValueSelector<Solution_>) valueSelector,
              resolvedCacheType,
              resolvedSelectionOrder.toRandomSelectionBoolean());
    }
    return valueSelector;
  }

  private void validateSelectedLimit(SelectionCacheType minimumCacheType) {
    if (config.getSelectedCountLimit() != null
        && minimumCacheType.compareTo(SelectionCacheType.JUST_IN_TIME) > 0) {
      throw new IllegalArgumentException(
          "The valueSelectorConfig ("
              + config
              + ") with selectedCountLimit ("
              + config.getSelectedCountLimit()
              + ") has a minimumCacheType ("
              + minimumCacheType
              + ") that is higher than "
              + SelectionCacheType.JUST_IN_TIME
              + ".");
    }
  }

  private ValueSelector<Solution_> applySelectedLimit(ValueSelector<Solution_> valueSelector) {
    if (config.getSelectedCountLimit() != null) {
      valueSelector =
          new SelectedCountLimitValueSelector<>(valueSelector, config.getSelectedCountLimit());
    }
    return valueSelector;
  }

  private ValueSelector<Solution_> applyNearbySelection(
      HeuristicConfigPolicy<Solution_> configPolicy,
      EntityDescriptor<Solution_> entityDescriptor,
      SelectionCacheType minimumCacheType,
      SelectionOrder resolvedSelectionOrder,
      ValueSelector<Solution_> valueSelector,
      String entityValueRangeRecorderId,
      boolean assertBothSides,
      ListValueFilteringType listValueFilteringType,
      SelectionFilter<Solution_, Object> liveFilter) {
    var nearbySelectionConfig = config.getNearbySelectionConfig();
    var randomSelection = resolvedSelectionOrder.toRandomSelectionBoolean();
    var instanceCache = configPolicy.getClassInstanceCache();

    var nearbyDistanceMeter =
        instanceCache.newInstance(
            config,
            "nearbyDistanceMeterClass",
            nearbySelectionConfig.getNearbyDistanceMeterClass());
    var nearbyRandom =
        NearbyRandomFactory.create(nearbySelectionConfig).buildNearbyRandom(randomSelection);
    int maxNearbySortSize =
        randomSelection
            ? NearbySelectionTuning.calculateMaxNearbySortSize(nearbySelectionConfig)
            : Integer.MAX_VALUE;
    boolean eagerInitialization =
        NearbySelectionTuning.isEagerInitialization(nearbySelectionConfig);

    AbstractNearbyValueSelector<Solution_, ?> nearbySelector;
    if (nearbySelectionConfig.getOriginEntitySelectorConfig() != null) {
      var originEntitySelector =
          EntitySelectorFactory.<Solution_>create(
                  nearbySelectionConfig.getOriginEntitySelectorConfig())
              .buildEntitySelector(configPolicy, minimumCacheType, resolvedSelectionOrder);
      if (!(valueSelector instanceof IterableValueSelector)) {
        throw new IllegalArgumentException(
            "The valueSelectorConfig ("
                + config
                + ") needs to be based on an IterableValueSelector ("
                + valueSelector
                + ").");
      }
      nearbySelector =
          new NearEntityNearbyValueSelector<>(
              (IterableValueSelector<Solution_>) valueSelector,
              originEntitySelector,
              nearbyDistanceMeter,
              nearbyRandom,
              randomSelection,
              maxNearbySortSize,
              eagerInitialization);
    } else if (nearbySelectionConfig.getOriginValueSelectorConfig() != null) {
      var originValueSelector =
          ValueSelectorFactory.<Solution_>create(
                  nearbySelectionConfig.getOriginValueSelectorConfig())
              .buildValueSelector(
                  configPolicy, entityDescriptor, minimumCacheType, resolvedSelectionOrder);
      if (!(valueSelector instanceof IterableValueSelector)) {
        throw new IllegalArgumentException(
            "The valueSelectorConfig ("
                + config
                + ") needs to be based on an IterableValueSelector ("
                + valueSelector
                + ").");
      }
      if (!(originValueSelector instanceof IterableValueSelector)) {
        throw new IllegalArgumentException(
            "The originValueSelectorConfig ("
                + nearbySelectionConfig.getOriginValueSelectorConfig()
                + ") needs to be based on an IterableValueSelector ("
                + originValueSelector
                + ").");
      }
      nearbySelector =
          new NearValueNearbyValueSelector<>(
              (IterableValueSelector<Solution_>) valueSelector,
              (IterableValueSelector<Solution_>) originValueSelector,
              nearbyDistanceMeter,
              nearbyRandom,
              randomSelection,
              maxNearbySortSize,
              eagerInitialization);
    } else {
      throw new IllegalArgumentException(
          "The valueSelectorConfig ("
              + config
              + ")'s nearbySelectionConfig ("
              + nearbySelectionConfig
              + ") requires an originEntitySelector or an originValueSelector.");
    }
    if (entityValueRangeRecorderId != null) {
      var rangeOriginConfig =
          new ValueSelectorConfig().withMimicSelectorRef(entityValueRangeRecorderId);
      if (entityDescriptor.hasBothListAndBasicVariables()) {
        rangeOriginConfig.setVariableName(valueSelector.getVariableDescriptor().getVariableName());
      }
      var rangeOrigin =
          (IterableValueSelector<Solution_>)
              ValueSelectorFactory.<Solution_>create(rangeOriginConfig)
                  .buildValueSelector(
                      configPolicy, entityDescriptor, minimumCacheType, resolvedSelectionOrder);
      nearbySelector.configureValueRangeFiltering(rangeOrigin, assertBothSides);
    }
    nearbySelector.configureListValueFiltering(
        listValueFilteringType, configPolicy.isUnassignedValuesAllowed());
    nearbySelector.configureSelectionFilter(liveFilter);
    return nearbySelector;
  }

  private ValueSelector<Solution_> applyMimicRecording(
      HeuristicConfigPolicy<Solution_> configPolicy,
      ValueSelector<Solution_> valueSelector,
      ValueSelector<Solution_> populationSelector,
      SelectionFilter<Solution_, Object> liveFilter,
      Supplier<Set<Object>> membershipSupplier) {
    var id = config.getId();
    if (id != null) {
      if (id.isEmpty()) {
        throw new IllegalArgumentException(
            "The valueSelectorConfig (%s) has an empty id (%s).".formatted(config, id));
      }
      if (!(valueSelector instanceof IterableValueSelector)) {
        throw new IllegalArgumentException(
            """
                        The valueSelectorConfig (%s) with id (%s) needs to be based on an %s (%s).
                        Check your @%s annotations."""
                .formatted(
                    config,
                    id,
                    IterableValueSelector.class.getSimpleName(),
                    valueSelector,
                    ValueRangeProvider.class.getSimpleName()));
      }
      var mimicRecordingValueSelector =
          new MimicRecordingValueSelector<>(
              new NearbySelectionSource<>(
                  (IterableValueSelector<Solution_>) valueSelector,
                  (IterableValueSelector<Solution_>) populationSelector,
                  liveFilter,
                  membershipSupplier));
      configPolicy.addValueMimicRecorder(id, mimicRecordingValueSelector);
      valueSelector = mimicRecordingValueSelector;
    }
    return valueSelector;
  }

  ValueSelector<Solution_> applyListValueFiltering(
      HeuristicConfigPolicy<?> configPolicy,
      ListValueFilteringType listValueFilteringType,
      GenuineVariableDescriptor<Solution_> variableDescriptor,
      ValueSelector<Solution_> valueSelector) {
    if (variableDescriptor.isListVariable()
        && configPolicy.isUnassignedValuesAllowed()
        && listValueFilteringType != ListValueFilteringType.NONE) {
      if (!(valueSelector instanceof IterableValueSelector)) {
        throw new IllegalArgumentException(
            "The valueSelectorConfig ("
                + config
                + ") with id ("
                + config.getId()
                + ") needs to be based on an "
                + IterableValueSelector.class.getSimpleName()
                + " ("
                + valueSelector
                + ")."
                + " Check your @"
                + ValueRangeProvider.class.getSimpleName()
                + " annotations.");
      }
      valueSelector =
          listValueFilteringType == ListValueFilteringType.ACCEPT_ASSIGNED
              ? new AssignedListValueSelector<>(((IterableValueSelector<Solution_>) valueSelector))
              : new UnassignedListValueSelector<>(
                  ((IterableValueSelector<Solution_>) valueSelector));
    }
    return valueSelector;
  }

  private ValueSelector<Solution_> applyReinitializeVariableFiltering(
      boolean applyReinitializeVariableFiltering,
      GenuineVariableDescriptor<Solution_> variableDescriptor,
      ValueSelector<Solution_> valueSelector) {
    if (applyReinitializeVariableFiltering && !variableDescriptor.isListVariable()) {
      valueSelector = new ReinitializeVariableValueSelector<>(valueSelector);
    }
    return valueSelector;
  }

  private ValueSelector<Solution_> applyDowncasting(ValueSelector<Solution_> valueSelector) {
    if (config.getDowncastEntityClass() != null) {
      valueSelector =
          new DowncastingValueSelector<>(valueSelector, config.getDowncastEntityClass());
    }
    return valueSelector;
  }

  public static <Solution_> ValueSelector<Solution_> applyValueRangeFiltering(
      HeuristicConfigPolicy<Solution_> configPolicy,
      ValueSelector<Solution_> valueSelector,
      EntityDescriptor<Solution_> entityDescriptor,
      SelectionCacheType minimumCacheType,
      SelectionOrder selectionOrder,
      boolean randomSelection,
      String entityValueRangeRecorderId,
      boolean assertBothSides) {
    if (entityValueRangeRecorderId == null) {
      return valueSelector;
    }
    var valueSelectorConfig =
        new ValueSelectorConfig().withMimicSelectorRef(entityValueRangeRecorderId);
    if (entityDescriptor.hasBothListAndBasicVariables()) {
      valueSelectorConfig.setVariableName(valueSelector.getVariableDescriptor().getVariableName());
    }
    var replayingValueSelector =
        (IterableValueSelector<Solution_>)
            ValueSelectorFactory.<Solution_>create(valueSelectorConfig)
                .buildValueSelector(
                    configPolicy, entityDescriptor, minimumCacheType, selectionOrder);
    return new FilteringValueRangeSelector<>(
        (IterableValueSelector<Solution_>) valueSelector,
        replayingValueSelector,
        randomSelection,
        assertBothSides);
  }

  public enum ListValueFilteringType {
    NONE,
    ACCEPT_ASSIGNED,
    ACCEPT_UNASSIGNED,
  }
}
