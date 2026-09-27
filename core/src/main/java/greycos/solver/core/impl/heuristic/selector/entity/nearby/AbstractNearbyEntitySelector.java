package greycos.solver.core.impl.heuristic.selector.entity.nearby;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;
import greycos.solver.core.impl.heuristic.selector.AbstractDemandEnabledSelector;
import greycos.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMatrix;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMatrixDemand;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyRandom;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.heuristic.selector.value.IterableValueSelector;
import greycos.solver.core.impl.neighborhood.stream.FilteringIterator;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.score.director.ValueRangeManager;
import greycos.solver.core.impl.solver.scope.SolverScope;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Abstract base for nearby entity selectors. */
public abstract class AbstractNearbyEntitySelector<Solution_>
    extends AbstractDemandEnabledSelector<Solution_> implements EntitySelector<Solution_> {

  protected final @NonNull EntitySelector<Solution_> childEntitySelector;
  protected final @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter;
  protected final @Nullable NearbyRandom nearbyRandom;
  protected final boolean randomSelection;
  protected final int maxNearbySortSize;
  protected final boolean eagerInitialization;
  private final @NonNull Object originSelectorKey;
  private final @NonNull String demandType;
  private boolean eagerInitialized = false;

  protected @Nullable NearbyDistanceMatrix<Object, Object> distanceMatrix;
  private @Nullable NearbyDistanceMatrixDemand<Object, Object> distanceMatrixDemand;
  private @Nullable SupplyManager distanceMatrixSupplyManager;
  private @Nullable EntitySelector<Solution_> valueRangeEntityOriginSelector;
  private @Nullable IterableValueSelector<Solution_> valueRangeValueOriginSelector;
  private @Nullable ValueRangeManager<Solution_> valueRangeManager;
  private List<BasicVariableDescriptor<Solution_>> entityRangeVariables = List.of();
  private @Nullable SelectionFilter<Solution_, Object> selectionFilter;
  private @Nullable ScoreDirector<Solution_> scoreDirector;

  public final void configureSelectionFilter(SelectionFilter<Solution_, Object> filter) {
    selectionFilter = filter;
  }

  private boolean hasRangeFiltering() {
    return valueRangeEntityOriginSelector != null || valueRangeValueOriginSelector != null;
  }

  private boolean acceptsCandidate(Object candidate) {
    return selectionFilter == null
        || selectionFilter.accept(Objects.requireNonNull(scoreDirector), candidate);
  }

  public final void configureValueRangeFiltering(EntitySelector<Solution_> originSelector) {
    valueRangeEntityOriginSelector = originSelector;
    phaseLifecycleSupport.addEventListener(originSelector);
  }

  public final void configureValueRangeFiltering(IterableValueSelector<Solution_> originSelector) {
    valueRangeValueOriginSelector = originSelector;
    phaseLifecycleSupport.addEventListener(originSelector);
  }

  private boolean hasDynamicFiltering() {
    return selectionFilter != null || hasRangeFiltering();
  }

  protected AbstractNearbyEntitySelector(
      @NonNull EntitySelector<Solution_> childEntitySelector,
      @NonNull Object originSelectorKey,
      @NonNull String demandType,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection) {
    this(
        childEntitySelector,
        originSelectorKey,
        demandType,
        nearbyDistanceMeter,
        nearbyRandom,
        randomSelection,
        Integer.MAX_VALUE,
        false);
  }

  protected AbstractNearbyEntitySelector(
      @NonNull EntitySelector<Solution_> childEntitySelector,
      @NonNull Object originSelectorKey,
      @NonNull String demandType,
      @NonNull NearbyDistanceMeter<?, ?> nearbyDistanceMeter,
      @Nullable NearbyRandom nearbyRandom,
      boolean randomSelection,
      int maxNearbySortSize,
      boolean eagerInitialization) {
    this.childEntitySelector = childEntitySelector;
    this.nearbyDistanceMeter = nearbyDistanceMeter;
    if (randomSelection && nearbyRandom == null) {
      throw new IllegalArgumentException(
          "The selector ("
              + this
              + ") with randomSelection ("
              + randomSelection
              + ") has no nearbyRandom ("
              + nearbyRandom
              + ").");
    }
    this.nearbyRandom = nearbyRandom;
    this.randomSelection = randomSelection;
    if (maxNearbySortSize < 1) {
      throw new IllegalArgumentException(
          "The maxNearbySortSize (%d) must be at least 1.".formatted(maxNearbySortSize));
    }
    this.maxNearbySortSize = maxNearbySortSize;
    this.eagerInitialization = eagerInitialization;
    this.originSelectorKey = originSelectorKey;
    this.demandType = demandType;
    phaseLifecycleSupport.addEventListener(childEntitySelector);
  }

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    if (distanceMatrix != null || distanceMatrixDemand != null) {
      throw new IllegalStateException("The nearby entity selector is already solving.");
    }
    super.solvingStarted(solverScope);
    distanceMatrix = null;
    distanceMatrixDemand = null;
    eagerInitialized = false;
  }

  private void initializeDistanceMatrix(@NonNull SupplyManager supplyManager) {
    @SuppressWarnings("unchecked")
    var castedDistanceMeter = (NearbyDistanceMeter<Object, Object>) nearbyDistanceMeter;
    var demand =
        new NearbyDistanceMatrixDemand<>(
            castedDistanceMeter,
            nearbyRandom,
            hasDynamicFiltering() ? Integer.MAX_VALUE : calculateEffectiveMaxNearbySortSize(),
            false,
            childEntitySelector,
            originSelectorKey,
            demandType,
            this::calculateOriginSizeEstimate,
            this::endingDestinations,
            origin -> calculateDestinationSize());
    distanceMatrix = supplyManager.demand(demand);
    distanceMatrixDemand = demand;
    distanceMatrixSupplyManager = supplyManager;
  }

  @Override
  public void phaseStarted(AbstractPhaseScope<Solution_> phaseScope) {
    try {
      super.phaseStarted(phaseScope);
      scoreDirector = phaseScope.getScoreDirector();
      if (hasRangeFiltering()) {
        valueRangeManager = phaseScope.getScoreDirector().getValueRangeManager();
        if (valueRangeEntityOriginSelector != null) {
          entityRangeVariables =
              getEntityDescriptor().getGenuineBasicVariableDescriptorList().stream()
                  .filter(variable -> !variable.canExtractValueRangeFromSolution())
                  .map(variable -> (BasicVariableDescriptor<Solution_>) variable)
                  .toList();
        }
      }
      if (distanceMatrix == null) {
        initializeDistanceMatrix(phaseScope.getScoreDirector().getSupplyManager());
      }
      if (eagerInitialization && !eagerInitialized) {
        initializeAllOrigins();
        eagerInitialized = true;
      }
    } catch (RuntimeException | Error failure) {
      try {
        releaseDistanceMatrix();
      } catch (RuntimeException | Error cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
  }

  @Override
  public void phaseEnded(AbstractPhaseScope<Solution_> phaseScope) {
    try {
      super.phaseEnded(phaseScope);
    } finally {
      releaseDistanceMatrix();
    }
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    try {
      super.solvingEnded(solverScope);
    } finally {
      releaseDistanceMatrix();
    }
  }

  private void releaseDistanceMatrix() {
    var demand = distanceMatrixDemand;
    var supplyManager = distanceMatrixSupplyManager;
    // The solver may already use another director. Release through the owner and clear first
    // so repeated cleanup remains safe, including when cancellation itself fails.
    distanceMatrixDemand = null;
    distanceMatrixSupplyManager = null;
    distanceMatrix = null;
    valueRangeManager = null;
    scoreDirector = null;
    entityRangeVariables = List.of();
    eagerInitialized = false;
    if (demand != null && !Objects.requireNonNull(supplyManager).cancel(demand)) {
      throw new IllegalStateException("The nearby distance matrix demand is not active.");
    }
  }

  protected abstract @NonNull Iterator<?> endingOriginIterator();

  protected final @NonNull NearbyDistanceMatrix<Object, Object> getDistanceMatrix() {
    if (distanceMatrix == null) {
      throw new IllegalStateException(
          "distanceMatrix is null. Make sure solvingStarted() was called.");
    }
    return distanceMatrix;
  }

  protected final int getNearbySize(@NonNull Object origin) {
    return getDistanceMatrix().getDestinationSize(origin);
  }

  protected final Object getNearbyDestination(@NonNull Object origin, int nearbyIndex) {
    return getDistanceMatrix().getDestination(origin, nearbyIndex);
  }

  protected boolean excludeOrigin() {
    return false;
  }

  private Iterator<Object> endingDestinations(Object origin) {
    var iterator = childEntitySelector.endingIterator();
    if (!excludeOrigin()) {
      return iterator;
    }
    return new Iterator<>() {
      private Object next;
      private boolean ready;

      @Override
      public boolean hasNext() {
        while (!ready && iterator.hasNext()) {
          Object candidate = iterator.next();
          if (candidate != origin) {
            next = candidate;
            ready = true;
          }
        }
        return ready;
      }

      @Override
      public Object next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        ready = false;
        return next;
      }
    };
  }

  protected abstract Iterator<Object> originIterator();

  @Override
  public final Iterator<Object> iterator() {
    return nearbyIterator(randomSelection);
  }

  private Iterator<Object> nearbyIterator(boolean randomSelection) {
    var originIterator = originIterator();
    return new Iterator<>() {
      private Object origin;
      private boolean prepared;
      private boolean originSelected;
      private int nearbySize;
      private int populationSize;
      private int index;
      private Object rangeOrigin;
      private List<Object> eligibleDestinations;

      private void prepare() {
        if (prepared || (!randomSelection && originSelected)) {
          return;
        }
        if (originIterator.hasNext()) {
          Object nextOrigin = originIterator.next();
          if (origin != nextOrigin) {
            origin = nextOrigin;
            nearbySize = getNearbySize(origin);
            populationSize = getDistanceMatrix().getDestinationPopulationSize(origin);
            index = 0;
          }
        }
        if (hasDynamicFiltering() && origin != null) {
          if (hasRangeFiltering()) {
            var rangeOriginIterator =
                valueRangeEntityOriginSelector == null
                    ? Objects.requireNonNull(valueRangeValueOriginSelector).iterator()
                    : valueRangeEntityOriginSelector.iterator();
            if (rangeOriginIterator.hasNext()) {
              rangeOrigin = rangeOriginIterator.next();
            }
          }
          if (randomSelection) {
            eligibleDestinations = new ArrayList<>();
            populationSize = 0;
            int limit = calculateEffectiveMaxNearbySortSize();
            int storedSize = getNearbySize(origin);
            for (int destinationIndex = 0; destinationIndex < storedSize; destinationIndex++) {
              Object candidate = getNearbyDestination(origin, destinationIndex);
              if (isEligible(rangeOrigin, candidate)) {
                populationSize++;
                if (eligibleDestinations.size() < limit) {
                  eligibleDestinations.add(candidate);
                }
                if (eligibleDestinations.size() == limit
                    && !Objects.requireNonNull(nearbyRandom).requiresPopulationSize()) {
                  break;
                }
              }
            }
            nearbySize = eligibleDestinations.size();
          }
        }
        originSelected = true;
        prepared = true;
      }

      @Override
      public boolean hasNext() {
        prepare();
        if (origin == null) {
          return false;
        }
        if (!randomSelection && hasDynamicFiltering()) {
          while (index < nearbySize
              && !isEligible(rangeOrigin, getNearbyDestination(origin, index))) {
            index++;
          }
        }
        return randomSelection ? nearbySize > 0 : index < nearbySize;
      }

      @Override
      public Object next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        if (randomSelection) {
          prepared = false;
          return destination(
              Objects.requireNonNull(nearbyRandom)
                  .nextInt(workingRandom, populationSize, nearbySize));
        }
        return destination(index++);
      }

      private Object destination(int destinationIndex) {
        return eligibleDestinations == null
            ? getNearbyDestination(origin, destinationIndex)
            : eligibleDestinations.get(destinationIndex);
      }
    };
  }

  private boolean isEligible(Object source, Object candidate) {
    return acceptsCandidate(candidate) && (!hasRangeFiltering() || isReachable(source, candidate));
  }

  private boolean isReachable(Object source, Object candidate) {
    if (source == null) {
      return false;
    }
    var manager = Objects.requireNonNull(valueRangeManager);
    if (valueRangeEntityOriginSelector == null) {
      var descriptor = getEntityDescriptor().getListVariableDescriptor();
      return manager
          .getFromEntity(descriptor.getValueRangeDescriptor(), candidate)
          .contains(source);
    }
    if (source == candidate) {
      return false;
    }
    for (var descriptor : entityRangeVariables) {
      if (!manager
              .getFromEntity(descriptor.getValueRangeDescriptor(), source)
              .contains(descriptor.getValue(candidate))
          || !manager
              .getFromEntity(descriptor.getValueRangeDescriptor(), candidate)
              .contains(descriptor.getValue(source))) {
        return false;
      }
    }
    return true;
  }

  private void initializeAllOrigins() {
    var originIterator = endingOriginIterator();
    while (originIterator.hasNext()) {
      getDistanceMatrix().addAllDestinations(originIterator.next());
    }
  }

  private int calculateOriginSizeEstimate() {
    if (originSelectorKey instanceof EntitySelector<?> originEntitySelector) {
      return toIntSize(originEntitySelector.getSize(), "originEntitySelector");
    }
    if (originSelectorKey instanceof IterableValueSelector<?> originValueSelector) {
      return toIntSize(originValueSelector.getSize(), "originValueSelector");
    }
    throw new IllegalStateException(
        "The originSelectorKey (%s) is neither an EntitySelector nor an IterableValueSelector."
            .formatted(originSelectorKey));
  }

  private int calculateDestinationSize() {
    return toIntSize(childEntitySelector.getSize(), "childEntitySelector");
  }

  protected static int toIntSize(long size, String selectorLabel) {
    if (size < 0 || size > Integer.MAX_VALUE) {
      throw new IllegalStateException(
          "The "
              + selectorLabel
              + " has a size ("
              + size
              + ") outside the supported range [0, Integer.MAX_VALUE].");
    }
    return (int) size;
  }

  private int calculateEffectiveMaxNearbySortSize() {
    if (!randomSelection || nearbyRandom == null) {
      return maxNearbySortSize;
    }
    return Math.min(maxNearbySortSize, nearbyRandom.getOverallSizeMaximum());
  }

  @Override
  public EntityDescriptor<Solution_> getEntityDescriptor() {
    return childEntitySelector.getEntityDescriptor();
  }

  @Override
  public long getSize() {
    return childEntitySelector.getSize();
  }

  @Override
  public boolean isNeverEnding() {
    return randomSelection;
  }

  @Override
  public Iterator<Object> endingIterator() {
    var iterator = childEntitySelector.endingIterator();
    return selectionFilter == null
        ? iterator
        : new FilteringIterator<>(iterator, this::acceptsCandidate);
  }

  @Override
  public ListIterator<Object> listIterator() {
    return listIterator(0);
  }

  @Override
  public ListIterator<Object> listIterator(int index) {
    var destinations = new ArrayList<Object>();
    nearbyIterator(false).forEachRemaining(destinations::add);
    return destinations.listIterator(index);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AbstractNearbyEntitySelector<?> that)) {
      return false;
    }
    return Objects.equals(selectionFilter, that.selectionFilter)
        && Objects.equals(childEntitySelector, that.childEntitySelector)
        && Objects.equals(nearbyDistanceMeter, that.nearbyDistanceMeter)
        && Objects.equals(nearbyRandom, that.nearbyRandom)
        && randomSelection == that.randomSelection
        && maxNearbySortSize == that.maxNearbySortSize
        && eagerInitialization == that.eagerInitialization
        && Objects.equals(valueRangeEntityOriginSelector, that.valueRangeEntityOriginSelector)
        && Objects.equals(valueRangeValueOriginSelector, that.valueRangeValueOriginSelector);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        selectionFilter,
        childEntitySelector,
        nearbyDistanceMeter,
        nearbyRandom,
        randomSelection,
        maxNearbySortSize,
        eagerInitialization,
        valueRangeEntityOriginSelector,
        valueRangeValueOriginSelector);
  }
}
