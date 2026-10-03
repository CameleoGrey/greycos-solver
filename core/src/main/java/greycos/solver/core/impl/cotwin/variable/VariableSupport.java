package greycos.solver.core.impl.cotwin.variable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.analysis.VariableLoop;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.cascade.CascadingUpdateShadowVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.declarative.ConsistencyTracker;
import greycos.solver.core.impl.cotwin.variable.declarative.DeclarativeShadowVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.declarative.DefaultShadowVariableSession;
import greycos.solver.core.impl.cotwin.variable.declarative.DefaultShadowVariableSessionFactory;
import greycos.solver.core.impl.cotwin.variable.declarative.DefaultTopologicalOrderGraph;
import greycos.solver.core.impl.cotwin.variable.declarative.ShadowVariablesInconsistentVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.declarative.TopologicalOrderGraph;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ShadowVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.inverserelation.InverseRelationShadowVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.nextprev.NextElementShadowVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.nextprev.PreviousElementShadowVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.supply.Demand;
import greycos.solver.core.impl.cotwin.variable.supply.Supply;
import greycos.solver.core.impl.cotwin.variable.supply.SupplyManager;
import greycos.solver.core.impl.cotwin.variable.violation.BasicVariableTracker;
import greycos.solver.core.impl.cotwin.variable.violation.ListVariableTracker;
import greycos.solver.core.impl.cotwin.variable.violation.ShadowVariablesAssert;
import greycos.solver.core.impl.cotwin.variable.violation.TrackerResolver;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * This class is not thread-safe.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@NullMarked
public final class VariableSupport<Solution_> implements TrackerResolver<Solution_>, SupplyManager {

  public static <Solution_> VariableSupport<Solution_> create(
      InnerScoreDirector<Solution_, ?> scoreDirector) {
    return new VariableSupport<>(scoreDirector, DefaultTopologicalOrderGraph::new);
  }

  private static final int SHADOW_VARIABLE_VIOLATION_DISPLAY_LIMIT = 3;
  private final InnerScoreDirector<Solution_, ?> scoreDirector;
  private final Map<Demand<?>, SupplyWithDemandCount> supplyMap = new HashMap<>();

  // The single source of truth for the basic variable' state and trackers, created at the first
  // request per variable.
  // Indexed by [{@link EntityDescriptor#getOrdinal()}][{@link VariableDescriptor#getOrdinal()}].
  private final List<BasicVariableChangeHandler<Solution_>>[][] basicVariableChangeHandlerArray;
  private final @Nullable ListVariableDescriptor<Solution_> listVariableDescriptor;
  private final boolean[][] sameListShadowVariables;
  private boolean handlingListStateChanges;
  // The single source of truth for the list variable state, created at first request.
  private @Nullable ListVariableState<Solution_, ?, ?> listVariableState;
  // The single source of truth for the list variable tracker, created at first request.
  private @Nullable ListVariableTracker<Solution_> listVariableTracker;
  // The current list of variable change handlers
  private final List<ListVariableChangeHandler<Solution_>> listVariableChangeHandlerList;

  private final @Nullable CascadingUpdateQueue cascadingUpdateQueue;
  private final boolean hasCascadingUpdates;
  private final @Nullable Class<?> listElementClass;
  private final @Nullable Class<?> listOwnerClass;
  private boolean updatingCascadingVariables;
  private final List<CascadingUpdateShadowVariableDescriptor<Solution_>>
      cascadingUpdateShadowVarDescriptorList;
  private final IntFunction<TopologicalOrderGraph> shadowVariableGraphCreator;

  // States and trackers may register after the working solution has been initialized.
  private boolean hasBasicVariableChangeHandlers = false;
  private boolean dirty = false;
  private boolean updateSuccessful = true;
  private boolean shadowUpdateFailed;
  @Nullable private DefaultShadowVariableSession<Solution_> shadowVariableSession = null;
  private ConsistencyTracker<Solution_> consistencyTracker = new ConsistencyTracker<>();

  @SuppressWarnings("unchecked")
  VariableSupport(
      InnerScoreDirector<Solution_, ?> scoreDirector,
      IntFunction<TopologicalOrderGraph> shadowVariableGraphCreator) {
    this.scoreDirector = Objects.requireNonNull(scoreDirector);

    var solutionDescriptor = scoreDirector.getSolutionDescriptor();
    var entityDescriptorList = solutionDescriptor.getEntityDescriptors();
    this.listVariableDescriptor = solutionDescriptor.getListVariableDescriptor();
    this.sameListShadowVariables = new boolean[entityDescriptorList.size()][];
    this.basicVariableChangeHandlerArray = new List[entityDescriptorList.size()][];
    for (var entityDescriptor : entityDescriptorList) {
      var declaredVariableDescriptorList = entityDescriptor.getDeclaredVariableDescriptors();
      var array = new List[declaredVariableDescriptorList.size()];
      var sameListShadows = new boolean[declaredVariableDescriptorList.size()];
      sameListShadowVariables[entityDescriptor.getOrdinal()] = sameListShadows;
      for (var variableDescriptor : declaredVariableDescriptorList) {
        array[variableDescriptor.getOrdinal()] =
            new ArrayList<BasicVariableChangeHandler<Solution_>>();
        if (listVariableDescriptor != null
            && (variableDescriptor instanceof IndexShadowVariableDescriptor<?>
                || variableDescriptor instanceof InverseRelationShadowVariableDescriptor<?>
                || variableDescriptor instanceof PreviousElementShadowVariableDescriptor<?>
                || variableDescriptor instanceof NextElementShadowVariableDescriptor<?>)) {
          sameListShadows[variableDescriptor.getOrdinal()] =
              ((ShadowVariableDescriptor<Solution_>) variableDescriptor)
                      .getSourceVariableDescriptor()
                  == listVariableDescriptor;
        }
      }
      basicVariableChangeHandlerArray[entityDescriptor.getOrdinal()] = array;
    }

    // Fields specific to list variable; will be ignored if not necessary.
    this.listVariableChangeHandlerList =
        listVariableDescriptor == null ? Collections.emptyList() : new ArrayList<>();
    this.cascadingUpdateShadowVarDescriptorList =
        listVariableDescriptor != null
            ? solutionDescriptor.getEntityDescriptors().stream()
                .flatMap(e -> e.getDeclaredCascadingUpdateShadowVariableDescriptors().stream())
                .toList()
            : Collections.emptyList();
    this.hasCascadingUpdates = !cascadingUpdateShadowVarDescriptorList.isEmpty();
    this.listElementClass = hasCascadingUpdates ? listVariableDescriptor.getElementType() : null;
    this.listOwnerClass =
        hasCascadingUpdates ? listVariableDescriptor.getEntityDescriptor().getEntityClass() : null;
    this.cascadingUpdateQueue = hasCascadingUpdates ? new CascadingUpdateQueue() : null;
    this.shadowVariableGraphCreator = shadowVariableGraphCreator;
  }

  public void linkShadowVariables() {
    if (listVariableDescriptor != null) {
      getListVariableState(listVariableDescriptor, false);
    }
    scoreDirector.getSolutionDescriptor().getEntityDescriptors().stream()
        .map(EntityDescriptor::getDeclaredShadowVariableDescriptors)
        .flatMap(Collection::stream)
        .forEach(this::linkShadowVariable);
  }

  // All information about elements in all shadow variables is tracked in a centralized place.
  // Therefore, all list-related shadow variables need to be connected to that centralized place.
  // Shadow variables which are not related to a list variable are processed normally.
  // Cascading, declarative, and inconsistent shadow variables are routed elsewhere and need no
  // wiring here.
  private void linkShadowVariable(ShadowVariableDescriptor<Solution_> descriptor) {
    var currentListVariableState = getListVariableState(listVariableDescriptor, false);
    if (descriptor
        instanceof
        InverseRelationShadowVariableDescriptor<Solution_>
            inverseRelationShadowVariableDescriptor) {
      if (inverseRelationShadowVariableDescriptor.getSourceVariableDescriptor()
          instanceof ListVariableDescriptor<?>) {
        if (currentListVariableState != null) {
          processShadowVariableDescriptorWithListVariable(
              inverseRelationShadowVariableDescriptor, currentListVariableState);
        }
      } else {
        var basicVariableState =
            getBasicVariableState(
                Objects.requireNonNull(
                    inverseRelationShadowVariableDescriptor.getSourceVariableDescriptor()),
                false);
        basicVariableState.externalize(inverseRelationShadowVariableDescriptor);
      }
    } else if (currentListVariableState != null) {
      switch (descriptor) {
        // When multiple variable types are used,
        // the shadow variable process needs to account for each variable
        // and process them according to their types.
        case IndexShadowVariableDescriptor<Solution_> d ->
            processShadowVariableDescriptorWithListVariable(d, currentListVariableState);
        case PreviousElementShadowVariableDescriptor<Solution_> d ->
            processShadowVariableDescriptorWithListVariable(d, currentListVariableState);
        case NextElementShadowVariableDescriptor<Solution_> d ->
            processShadowVariableDescriptorWithListVariable(d, currentListVariableState);
        case DeclarativeShadowVariableDescriptor<Solution_> ignored -> {
          // Needs no handling here.
        }
        case ShadowVariablesInconsistentVariableDescriptor<Solution_> ignored -> {
          // Needs no handling here.
        }
        case CascadingUpdateShadowVariableDescriptor<Solution_> ignored -> {
          // Needs no handling here.
        }
        // Fail-safe.
        default ->
            throw new IllegalStateException(
                "Impossible state: unknown shadow variable type (%s).".formatted(descriptor));
      }
    }
  }

  private void processShadowVariableDescriptorWithListVariable(
      ShadowVariableDescriptor<Solution_> shadowVariableDescriptor,
      ListVariableState<Solution_, Object, Object> listVariableState) {
    switch (shadowVariableDescriptor) {
      case IndexShadowVariableDescriptor<Solution_> indexShadowVariableDescriptor ->
          listVariableState.externalize(indexShadowVariableDescriptor);
      case InverseRelationShadowVariableDescriptor<Solution_>
              inverseRelationShadowVariableDescriptor ->
          listVariableState.externalize(inverseRelationShadowVariableDescriptor);
      case PreviousElementShadowVariableDescriptor<Solution_>
              previousElementShadowVariableDescriptor ->
          listVariableState.externalize(previousElementShadowVariableDescriptor);
      case NextElementShadowVariableDescriptor<Solution_> nextElementShadowVariableDescriptor ->
          listVariableState.externalize(nextElementShadowVariableDescriptor);
      default -> // The list variable supply supports no other shadow variables.
          throw new IllegalStateException(
              "Impossible state: list-variable-source shadow variable %s (%s) is not Index, InverseRelation, Previous, or Next."
                  .formatted(
                      shadowVariableDescriptor.getVariableName(),
                      shadowVariableDescriptor.getClass().getSimpleName()));
    }
  }

  @Override
  public Consumer<Object> getStateChangeNotifier() {
    var notifier = scoreDirector.getNeighborhoodNotifier();
    return notifier == null ? ignored -> {} : notifier;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <Supply_ extends Supply> Supply_ demand(Demand<Supply_> demand) {
    var supplyWithDemandCount = supplyMap.get(demand);
    if (supplyWithDemandCount == null) {
      var newSupplyWithDemandCount =
          new SupplyWithDemandCount(demand.createExternalizedSupply(this), 1L);
      supplyMap.put(demand, newSupplyWithDemandCount);
      return (Supply_) newSupplyWithDemandCount.supply;
    } else {
      var supply = supplyWithDemandCount.supply;
      var newSupplyWithDemandCount =
          new SupplyWithDemandCount(supply, supplyWithDemandCount.demandCount + 1L);
      supplyMap.put(demand, newSupplyWithDemandCount);
      return (Supply_) supply;
    }
  }

  @Override
  public <Supply_ extends Supply> boolean cancel(Demand<Supply_> demand) {
    var supplyWithDemandCount = supplyMap.get(demand);
    if (supplyWithDemandCount == null) {
      return false;
    }
    if (supplyWithDemandCount.demandCount == 1L) {
      supplyMap.remove(demand);
    } else {
      supplyMap.put(
          demand,
          new SupplyWithDemandCount(
              supplyWithDemandCount.supply, supplyWithDemandCount.demandCount - 1L));
    }
    return true;
  }

  @Override
  public <Supply_ extends Supply> long getActiveCount(Demand<Supply_> demand) {
    var supplyAndDemandCounter = supplyMap.get(demand);
    if (supplyAndDemandCounter == null) {
      return 0L;
    } else {
      return supplyAndDemandCounter.demandCount;
    }
  }

  public ConsistencyTracker<Solution_> getConsistencyTracker() {
    return consistencyTracker;
  }

  public void setConsistencyTracker(ConsistencyTracker<Solution_> consistencyTracker) {
    this.consistencyTracker = consistencyTracker;
  }

  // ************************************************************************
  // List variable methods
  // ************************************************************************

  public @Nullable <Entity_, Value_>
      ListVariableState<Solution_, Entity_, Value_> getListVariableState(
          @Nullable ListVariableDescriptor<Solution_> targetVariableDescriptor) {
    return getListVariableState(targetVariableDescriptor, true);
  }

  @SuppressWarnings("unchecked")
  private @Nullable <Entity_, Value_>
      ListVariableState<Solution_, Entity_, Value_> getListVariableState(
          @Nullable ListVariableDescriptor<Solution_> targetVariableDescriptor, boolean reset) {
    if (targetVariableDescriptor != listVariableDescriptor) {
      throw new IllegalStateException(
          "The variableDescriptor (%s) is not the same as the solution's variableDescriptor (%s)."
              .formatted(targetVariableDescriptor, listVariableDescriptor));
    }
    if (targetVariableDescriptor == null) {
      return null;
    }
    if (listVariableState == null) { // The list state has not been loaded yet.
      listVariableState =
          new DefaultListVariableState<>(
              targetVariableDescriptor,
              getStateChangeNotifier(),
              hasCascadingUpdates ? this::scheduleListShadowChanges : null);
      registerListVariableHandler(listVariableState, reset);
    }
    return (ListVariableState<Solution_, Entity_, Value_>) listVariableState;
  }

  @Override
  public @Nullable ListVariableTracker<Solution_> getListVariableTracker(
      @Nullable ListVariableDescriptor<Solution_> targetVariableDescriptor) {
    if (targetVariableDescriptor != listVariableDescriptor) {
      throw new IllegalStateException(
          "The variableDescriptor (%s) is not the same as the solution's variableDescriptor (%s)."
              .formatted(targetVariableDescriptor, listVariableDescriptor));
    }
    if (targetVariableDescriptor == null) {
      return null;
    }
    if (listVariableTracker == null) {
      listVariableTracker = new ListVariableTracker<>(listVariableDescriptor);
      registerListVariableHandler(listVariableTracker, true);
    }
    return listVariableTracker;
  }

  private void registerListVariableHandler(
      ListVariableChangeHandler<Solution_> handler, boolean reset) {
    if (reset) {
      resetWorkingSolutionIfSet(() -> handler.resetWorkingSolution(scoreDirector));
    }
    listVariableChangeHandlerList.add(handler);
  }

  // ************************************************************************
  // Basic variable methods
  // ************************************************************************

  public BasicVariableState<Solution_> getBasicVariableState(
      VariableDescriptor<Solution_> variableDescriptor) {
    return getBasicVariableState(variableDescriptor, true);
  }

  private BasicVariableState<Solution_> getBasicVariableState(
      VariableDescriptor<Solution_> variableDescriptor, boolean reset) {
    BasicVariableState<Solution_> state =
        findBasicHandler(
            variableDescriptor,
            handler ->
                handler instanceof BasicVariableState<Solution_> handlerState
                    && handlerState.getSourceVariableDescriptor() == variableDescriptor);
    if (state != null) {
      return state;
    }
    // The state has not been loaded yet; there must only ever be one per variable,
    // as it is the single source of truth for the inverse relation of that variable.
    var basicVariableState = new BasicVariableState<>(variableDescriptor, getStateChangeNotifier());
    registerBasicVariableChangeHandler(basicVariableState, reset);
    return basicVariableState;
  }

  @Override
  public BasicVariableTracker<Solution_> getBasicVariableTracker(
      VariableDescriptor<Solution_> variableDescriptor) {
    BasicVariableTracker<Solution_> tracker =
        findBasicHandler(
            variableDescriptor,
            handler ->
                handler instanceof BasicVariableTracker<Solution_> handleTracker
                    && handleTracker.getSourceVariableDescriptor() == variableDescriptor);
    if (tracker != null) {
      return tracker;
    }
    // The tracker has not been loaded yet; there must only ever be one per variable
    var basicVariableTracker = new BasicVariableTracker<>(variableDescriptor);
    registerBasicVariableChangeHandler(basicVariableTracker, true);
    return basicVariableTracker;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  private <Type_ extends BasicVariableChangeHandler<Solution_>> Type_ findBasicHandler(
      VariableDescriptor<Solution_> variableDescriptor,
      Predicate<BasicVariableChangeHandler<Solution_>> checkFunction) {
    var handlerList = getBasicVariableChangeHandlerList(variableDescriptor);
    if (handlerList.isEmpty()) {
      return null;
    }
    var firstHandler = handlerList.getFirst();
    if (checkFunction.test(firstHandler)) {
      return (Type_) firstHandler;
    }
    if (handlerList.size() == 1) {
      return null;
    }
    var secondHandler = handlerList.get(1);
    if (checkFunction.test(secondHandler)) {
      return (Type_) secondHandler;
    }
    return null;
  }

  private List<BasicVariableChangeHandler<Solution_>> getBasicVariableChangeHandlerList(
      VariableDescriptor<Solution_> variableDescriptor) {
    return basicVariableChangeHandlerArray[variableDescriptor.getEntityDescriptor().getOrdinal()][
        variableDescriptor.getOrdinal()];
  }

  private void registerBasicVariableChangeHandler(
      BasicVariableChangeHandler<Solution_> handler, boolean reset) {
    if (reset) {
      resetWorkingSolutionIfSet(() -> handler.resetWorkingSolution(scoreDirector));
    }
    var variableDescriptor = handler.getSourceVariableDescriptor();
    var handlerList = getBasicVariableChangeHandlerList(variableDescriptor);
    if (handlerList.size() >= 2) {
      throw new IllegalStateException(
          "Impossible state: a basic variable cannot have more than two handlers assigned to it.");
    }
    handlerList.add(handler);
    hasBasicVariableChangeHandlers = true;
  }

  // ************************************************************************
  // Lifecycle methods
  // ************************************************************************

  private void resetWorkingSolutionIfSet(Runnable resetWorkingSolution) {
    // An external ScoreDirector can be created before the working solution is set.
    if (scoreDirector.getWorkingSolution() != null) {
      resetWorkingSolution.run();
    }
  }

  public void resetWorkingSolution() {
    // No callback for the new solution may reach the graph of the previous solution.
    shadowVariableSession = null;
    clearPendingShadowVariableUpdates();
    updateSuccessful = true;
    shadowUpdateFailed = false;
    for (var handler : listVariableChangeHandlerList) {
      handler.resetWorkingSolution(scoreDirector);
    }
    for (var handlerList : basicVariableChangeHandlerArray) {
      for (var handlers : handlerList) {
        for (var handler : handlers) {
          handler.resetWorkingSolution(scoreDirector);
        }
      }
    }

    if (!scoreDirector.getSolutionDescriptor().getDeclarativeShadowVariableDescriptors().isEmpty()
        && !consistencyTracker.isFrozen()) {
      var shadowVariableSessionFactory =
          new DefaultShadowVariableSessionFactory<>(
              scoreDirector.getSolutionDescriptor(), scoreDirector, shadowVariableGraphCreator);
      shadowVariableSession =
          shadowVariableSessionFactory.forSolution(
              consistencyTracker,
              scoreDirector.getWorkingSolution(),
              scoreDirector.ignoreInconsistentSolutions());
    }
  }

  public void workingSolutionMutationObserverChanged() {
    if (listVariableState != null) {
      listVariableState.workingSolutionMutationObserverChanged();
    }
  }

  public void close() {
    clearPendingShadowVariableUpdates();
    shadowVariableSession = null;
    if (cascadingUpdateQueue != null) {
      cascadingUpdateQueue.close();
    }
    // Release observer references even if another variable handler fails while closing.
    workingSolutionMutationObserverChanged();
    for (var handler : listVariableChangeHandlerList) {
      handler.close();
    }
    for (var handlerList : basicVariableChangeHandlerArray) {
      for (var handlers : handlerList) {
        for (var handler : handlers) {
          handler.close();
        }
      }
    }
  }

  public void beforeVariableChanged(
      VariableDescriptor<Solution_> variableDescriptor, Object entity) {
    if (!hasBasicVariableChangeHandlers && shadowVariableSession == null) {
      return;
    }
    var handlerList = getBasicVariableChangeHandlerList(variableDescriptor);
    for (var i = 0; i < handlerList.size(); i++) { // Avoid iterator allocations on the hot path.
      var handler = handlerList.get(i);
      handler.beforeVariableChanged(scoreDirector, entity);
    }
    if (shadowVariableSession != null) {
      shadowVariableSession.beforeVariableChanged(variableDescriptor, entity);
      dirty = true;
    }
  }

  public void afterVariableChanged(
      VariableDescriptor<Solution_> variableDescriptor, Object entity) {
    scheduleCascadingUpdate(variableDescriptor, entity);
    if (!hasBasicVariableChangeHandlers && shadowVariableSession == null) {
      return;
    }
    var handlerList = getBasicVariableChangeHandlerList(variableDescriptor);
    for (var i = 0; i < handlerList.size(); i++) { // Avoid iterator allocations on the hot path.
      var handler = handlerList.get(i);
      handler.afterVariableChanged(scoreDirector, entity);
    }
    if (shadowVariableSession != null) {
      shadowVariableSession.afterVariableChanged(variableDescriptor, entity);
    }
  }

  public void afterElementUnassigned(
      ListVariableDescriptor<Solution_> variableDescriptor, Object element) {
    for (var i = 0; i < listVariableChangeHandlerList.size(); i++) {
      var handler = listVariableChangeHandlerList.get(i);
      handlingListStateChanges = hasCascadingUpdates && handler == listVariableState;
      try {
        handler.afterListElementUnassigned(scoreDirector, element);
      } finally {
        handlingListStateChanges = false;
      }
    }
    if (hasCascadingUpdates) {
      cascadingUpdateQueue.addChangedElement(element);
      dirty = true;
    }
    if (shadowVariableSession != null) {
      // List changes may affect declarative shadow variables even when no basic variable event
      // fires,
      // because the externalized shadow processors skip writes when the recomputed value is
      // unchanged.
      dirty = true;
    }
  }

  public void beforeListVariableChanged(
      ListVariableDescriptor<Solution_> variableDescriptor,
      Object entity,
      int fromIndex,
      int toIndex) {
    for (var i = 0;
        i < listVariableChangeHandlerList.size();
        i++) { // Avoid iterator allocations on the hot path.
      var handler = listVariableChangeHandlerList.get(i);
      handler.beforeListVariableChanged(scoreDirector, entity, fromIndex, toIndex);
    }
    if (shadowVariableSession != null) {
      shadowVariableSession.beforeListVariableChanged(
          variableDescriptor, entity, fromIndex, toIndex);
      dirty = true;
    }
  }

  public void afterListVariableChanged(
      ListVariableDescriptor<Solution_> variableDescriptor,
      Object entity,
      int fromIndex,
      int toIndex) {
    for (var i = 0;
        i < listVariableChangeHandlerList.size();
        i++) { // Avoid iterator allocations on the hot path.
      var handler = listVariableChangeHandlerList.get(i);
      handlingListStateChanges = hasCascadingUpdates && handler == listVariableState;
      try {
        handler.afterListVariableChanged(scoreDirector, entity, fromIndex, toIndex);
      } finally {
        handlingListStateChanges = false;
      }
    }
    if (hasCascadingUpdates) {
      cascadingUpdateQueue.addRange(entity, fromIndex, toIndex);
      dirty = true;
    }
    if (shadowVariableSession != null) {
      // See afterElementUnassigned().
      dirty = true;
      shadowVariableSession.afterListVariableChanged(
          variableDescriptor, entity, fromIndex, toIndex);
    }
  }

  @SuppressWarnings("unchecked")
  public <Score_ extends Score<Score_>> InnerScoreDirector<Solution_, Score_> getScoreDirector() {
    return (InnerScoreDirector<Solution_, Score_>) scoreDirector;
  }

  public boolean hasShadowUpdateFailed() {
    return shadowUpdateFailed;
  }

  public boolean updateShadowVariables() {
    if (!dirty || shadowUpdateFailed) {
      return updateSuccessful;
    }
    try {
      // Cascades run last, including changes caused by declarative shadow variables.
      if (shadowVariableSession != null && !shadowVariableSession.updateVariables()) {
        updateSuccessful = false;
        return false;
      }
      if (hasCascadingUpdates && !cascadingUpdateQueue.isEmpty()) {
        triggerCascadingUpdateShadowVariableUpdate();
      }
      dirty = false;
      updateSuccessful = true;
      return true;
    } catch (RuntimeException | Error failure) {
      if (cascadingUpdateQueue != null) {
        cascadingUpdateQueue.clear();
      }
      // Dropped work after a throwing user callback requires a full refresh before retrying.
      shadowUpdateFailed = true;
      updateSuccessful = false;
      throw failure;
    }
  }

  private void scheduleCascadingUpdate(VariableDescriptor<Solution_> descriptor, Object entity) {
    if (!hasCascadingUpdates
        || updatingCascadingVariables
        || descriptor instanceof CascadingUpdateShadowVariableDescriptor<?>) {
      return;
    }
    if (listElementClass.isInstance(entity)
        && !(handlingListStateChanges
            && sameListShadowVariables[descriptor.getEntityDescriptor().getOrdinal()][
                descriptor.getOrdinal()])) {
      // List-state callbacks are covered by their exact changed span or explicit unassignment.
      // Other callbacks still resolve their position only after all changes, including transfers
      // and unassignments.
      cascadingUpdateQueue.addChangedElement(entity);
      dirty = true;
    }
    if (listOwnerClass.isInstance(entity)) {
      cascadingUpdateQueue.addRange(entity, 0, listVariableDescriptor.getListSize(entity));
      dirty = true;
    }
  }

  private void scheduleListShadowChanges(Object entity, int fromIndex, int toIndex) {
    cascadingUpdateQueue.addRange(entity, fromIndex, toIndex);
    dirty = true;
  }

  public List<VariableLoop> getVariableLoops() {
    if (shadowVariableSession == null) {
      return Collections.emptyList();
    }
    return shadowVariableSession.getVariableLoops();
  }

  /** Runs only affected ranges and suffixes, never unrelated lists. */
  private void triggerCascadingUpdateShadowVariableUpdate() {
    try {
      for (var i = 0; i < cascadingUpdateQueue.changedElementCount(); i++) {
        var element = cascadingUpdateQueue.changedElement(i);
        var entity = listVariableState.getInverseSingleton(element);
        if (entity == null) {
          cascadingUpdateQueue.addUnassignedElement(element);
        } else {
          var index = listVariableState.getIndexOrFail(element);
          cascadingUpdateQueue.addRange(entity, index, index + 1);
        }
      }
      cascadingUpdateQueue.prepareRanges();
      updatingCascadingVariables = true;
      for (var descriptorIndex = 0;
          descriptorIndex < cascadingUpdateShadowVarDescriptorList.size();
          descriptorIndex++) {
        var descriptor = cascadingUpdateShadowVarDescriptorList.get(descriptorIndex);
        for (var i = 0; i < cascadingUpdateQueue.updateCount(); i++) {
          cascadeListVariableValueUpdates(cascadingUpdateQueue.updates(i), descriptor);
        }
        // Every descriptor sees the same final unassigned values.
        for (var i = 0; i < cascadingUpdateQueue.unassignedElementCount(); i++) {
          var element = cascadingUpdateQueue.unassignedElement(i);
          if (descriptor.getEntityDescriptor().getEntityClass().isInstance(element)) {
            descriptor.update(scoreDirector, element);
          }
        }
      }
    } finally {
      updatingCascadingVariables = false;
      cascadingUpdateQueue.clear();
    }
  }

  private void cascadeListVariableValueUpdates(
      CascadingUpdateQueue.ListUpdates updates,
      CascadingUpdateShadowVariableDescriptor<Solution_> descriptor) {
    var values = listVariableDescriptor.getValue(Objects.requireNonNull(updates.entity()));
    var rangeIndex = 0;
    while (rangeIndex < updates.rangeCount()) {
      var fromIndex = updates.fromIndex(rangeIndex);
      var toIndex = updates.toIndex(rangeIndex++);
      for (var index = fromIndex; index < values.size(); index++) {
        // Extend through ranges reached during propagation; jump over untouched gaps after
        // convergence.
        while (rangeIndex < updates.rangeCount() && updates.fromIndex(rangeIndex) <= index) {
          toIndex = Math.max(toIndex, updates.toIndex(rangeIndex++));
        }
        var value = values.get(index);
        if (!descriptor.getEntityDescriptor().getEntityClass().isInstance(value)) {
          continue;
        }
        if (!descriptor.update(scoreDirector, value) && index >= toIndex) {
          break;
        }
      }
    }
  }

  /**
   * @return null if there are no violations
   */
  public @Nullable String createShadowVariablesViolationMessage() {
    var workingSolution = scoreDirector.getWorkingSolution();
    var snapshot =
        ShadowVariablesAssert.takeSnapshot(scoreDirector.getSolutionDescriptor(), workingSolution);

    forceUpdateAllShadowVariables(workingSolution);
    return snapshot.createShadowVariablesViolationMessage(SHADOW_VARIABLE_VIOLATION_DISPLAY_LIMIT);
  }

  /**
   * Updates all shadow variables even when no change is pending.
   *
   * <p>To ensure each listener is triggered, an artificial notification is created for each genuine
   * variable without doing any change on the working solution. If everything works correctly,
   * triggering listeners at this point must not change any shadow variables either.
   *
   * @param workingSolution working solution
   */
  public boolean forceUpdateAllShadowVariables(Solution_ workingSolution) {
    shadowUpdateFailed = false;
    updateSuccessful = true;
    scoreDirector
        .getSolutionDescriptor()
        .visitAllEntities(workingSolution, this::simulateGenuineVariableChange);
    return updateShadowVariables();
  }

  /**
   * Discards pending shadow variable updates without applying them. The goal is to clear all queues
   * and avoid executing custom listener logic.
   */
  public void clearPendingShadowVariableUpdates() {
    if (cascadingUpdateQueue != null) {
      cascadingUpdateQueue.clear();
    }
    dirty = false;
  }

  private void simulateGenuineVariableChange(Object entity) {
    if (hasCascadingUpdates
        && listElementClass.isInstance(entity)
        && listVariableState.getInverseSingleton(entity) == null) {
      // Shadow-only values are included; no list event visits an initially unassigned value.
      cascadingUpdateQueue.addChangedElement(entity);
      dirty = true;
    }
    var entityDescriptor =
        scoreDirector.getSolutionDescriptor().findEntityDescriptorOrFail(entity.getClass());
    if (!entityDescriptor.isGenuine()) {
      return;
    }
    for (var variableDescriptor : entityDescriptor.getGenuineVariableDescriptorList()) {
      if (variableDescriptor.isListVariable()) {
        var descriptor = (ListVariableDescriptor<Solution_>) variableDescriptor;
        var size = descriptor.getValue(entity).size();
        beforeListVariableChanged(descriptor, entity, 0, size);
        afterListVariableChanged(descriptor, entity, 0, size);
      } else {
        beforeVariableChanged(variableDescriptor, entity);
        afterVariableChanged(variableDescriptor, entity);
      }
    }
  }

  public void assertShadowVariablesAreUpToDate() {
    if (!dirty) {
      return;
    }
    throw new IllegalStateException(
        """
        The shadow variables might be stale (%s) so score calculation is unreliable.
        Maybe a %s.before*() method was called without calling %s.updateShadowVariables(), before calling %s.calculateScore().\
        """
            .formatted(
                dirty,
                ScoreDirector.class.getSimpleName(),
                ScoreDirector.class.getSimpleName(),
                ScoreDirector.class.getSimpleName()));
  }

  private record SupplyWithDemandCount(Supply supply, long demandCount) {}
}
