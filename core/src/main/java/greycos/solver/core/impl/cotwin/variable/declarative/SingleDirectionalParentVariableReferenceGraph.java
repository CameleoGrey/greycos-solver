package greycos.solver.core.impl.cotwin.variable.declarative;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;

import greycos.solver.core.api.score.analysis.VariableLoop;
import greycos.solver.core.impl.cotwin.variable.ListVariableState;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.util.LinkedIdentityHashSet;
import greycos.solver.core.impl.util.ShrinkingIdentityHashMap;
import greycos.solver.core.preview.api.cotwin.metamodel.VariableMetaModel;

import org.jspecify.annotations.Nullable;

public final class SingleDirectionalParentVariableReferenceGraph<Solution_>
    implements VariableReferenceGraph {

  private final Set<VariableMetaModel<?, ?, ?>> monitoredSourceVariableSet;
  private final VariableUpdaterInfo<Solution_>[] sortedVariableUpdaterInfos;
  // Positions are only consistent when updateChanged is called, not during a move.
  private final ListVariableState<Solution_, Object, Object> listVariableState;
  private final ListVariableDescriptor<Solution_> listVariableDescriptor;

  /** True if the parent is the previous element, so successors follow in increasing index order. */
  private final boolean forward;

  private final ChangedVariableNotifier<Solution_> changedVariableNotifier;
  private final Set<Object> changedEntities;
  private final SortedChangeBatch batch;
  // Created only for sequential lists without an externalized successor; never retained across
  // updates.
  private @Nullable ShrinkingIdentityHashMap<Object, ListIterator<Object>> sequentialIterators;
  private final Class<?> monitoredEntityClass;
  private final boolean canTerminateEarly;
  private final MonitoredVariableChangeHook monitoredVariableChangeHook =
      new MonitoredVariableChangeHook();
  private boolean isUpdating;

  @SuppressWarnings("unchecked")
  public SingleDirectionalParentVariableReferenceGraph(
      ConsistencyTracker<Solution_> consistencyTracker,
      List<DeclarativeShadowVariableDescriptor<Solution_>>
          sortedDeclarativeShadowVariableDescriptors,
      ListVariableState<Solution_, Object, Object> listVariableState,
      boolean forward,
      ChangedVariableNotifier<Solution_> changedVariableNotifier,
      boolean canTerminateEarly,
      Object[] entities) {
    monitoredEntityClass =
        sortedDeclarativeShadowVariableDescriptors
            .getFirst()
            .getEntityDescriptor()
            .getEntityClass();
    sortedVariableUpdaterInfos =
        new VariableUpdaterInfo[sortedDeclarativeShadowVariableDescriptors.size()];
    monitoredSourceVariableSet = new HashSet<>();
    changedEntities = new LinkedIdentityHashSet<>();
    isUpdating = false;

    this.canTerminateEarly = canTerminateEarly;
    this.listVariableState = listVariableState;
    this.listVariableDescriptor = listVariableState.getSourceVariableDescriptor();
    this.forward = forward;
    this.batch = new SortedChangeBatch(forward);
    this.changedVariableNotifier = changedVariableNotifier;
    var comparator =
        Comparator.comparingInt((Object entity) -> listVariableState.getIndexOrElse(entity, 0));
    var shadowEntities =
        Arrays.stream(entities)
            .filter(monitoredEntityClass::isInstance)
            .sorted(forward ? comparator : comparator.reversed())
            .toArray();
    var entityConsistencyState =
        consistencyTracker.getDeclarativeEntityConsistencyState(
            sortedDeclarativeShadowVariableDescriptors.getFirst().getEntityDescriptor());

    var updaterIndex = 0;
    for (var variableDescriptor : sortedDeclarativeShadowVariableDescriptors) {
      var variableMetaModel = variableDescriptor.getVariableMetaModel();
      var variableUpdaterInfo =
          new VariableUpdaterInfo<>(
              variableMetaModel,
              updaterIndex,
              variableDescriptor,
              entityConsistencyState,
              variableDescriptor.getMemberAccessor(),
              variableDescriptor.getCalculator());
      sortedVariableUpdaterInfos[updaterIndex++] = variableUpdaterInfo;

      for (var source : variableDescriptor.getSources()) {
        for (var sourceReference : source.variableSourceReferences()) {
          monitoredSourceVariableSet.add(sourceReference.variableMetaModel());
        }
      }
    }

    changedEntities.addAll(List.of(shadowEntities));
    for (var shadowEntity : shadowEntities) {
      entityConsistencyState.setEntityIsInconsistent(changedVariableNotifier, shadowEntity, false);
    }

    updateChanged();
  }

  @Override
  public boolean updateChanged() {
    isUpdating = true;
    try {
      batch.load(changedEntities, listVariableState);
      for (var i = 0; i < batch.size(); i++) {
        var changedEntity = batch.entity(i);
        var owner = batch.owner(i);
        if (owner == null) { // Unassigned element; it has no successor.
          propagate(changedEntity, null, 0);
          continue;
        }
        var lastProcessedIndex = batch.lastProcessedIndexToAdvance(i);
        if (lastProcessedIndex != null) {
          lastProcessedIndex.setValue(propagate(changedEntity, owner, batch.index(i)));
        }
      }
      return true;
    } finally {
      isUpdating = false;
      changedEntities.clear();
      batch.clear();
      if (sequentialIterators != null) {
        sequentialIterators.clear();
      }
    }
  }

  /**
   * Update entities and its successor until one of them does not change. Random-access lists use
   * indexes; sequential lists use externalized neighbors or a cursor shared by this owner's seeds.
   *
   * @param entity The first entity to process.
   * @param owner null if the entity is unassigned
   * @param index the index of the entity in the owner's list
   * @return The index of the last processed entity: the first unchanged one, or the last one in
   *     walk direction if all of them changed.
   */
  private int propagate(Object entity, @Nullable Object owner, int index) {
    var elementList =
        owner == null ? Collections.emptyList() : listVariableDescriptor.getValue(owner);
    var indexed = elementList instanceof RandomAccess;
    var externalizedSuccessor =
        !indexed
            && (forward
                ? listVariableState.isNextElementExternalized()
                : listVariableState.isPreviousElementExternalized());
    ListIterator<Object> iterator = null;
    var current = entity;
    var previousIndex = index;
    while (current != null) {
      var anyChanged = false;
      for (var updater : sortedVariableUpdaterInfos) {
        anyChanged |= updater.updateIfChanged(current, changedVariableNotifier);
      }
      // We cannot look ahead by 1 in the cannot terminate early case;
      // If a successor was unchanged and its parent unchanged,
      // it can still be the case that the successor's successor was changed
      if (!canTerminateEarly || anyChanged) {
        previousIndex = index;
        index = forward ? index + 1 : index - 1;
        if (index < 0 || index >= elementList.size()) {
          current = null;
        } else if (indexed) {
          current = elementList.get(index);
        } else if (externalizedSuccessor) {
          current =
              forward
                  ? listVariableState.getNextElement(current)
                  : listVariableState.getPreviousElement(current);
        } else {
          if (iterator == null) {
            iterator =
                sequentialIterator(Objects.requireNonNull(owner), elementList, previousIndex);
          }
          current = forward ? iterator.next() : iterator.previous();
        }
      } else {
        return index;
      }
    }
    return previousIndex;
  }

  private ListIterator<Object> sequentialIterator(Object owner, List<Object> elements, int index) {
    if (sequentialIterators == null) {
      sequentialIterators = new ShrinkingIdentityHashMap<>();
    }
    var boundary = forward ? index + 1 : index;
    var iterator = sequentialIterators.get(owner);
    if (iterator == null) {
      iterator = elements.listIterator(boundary);
      sequentialIterators.put(owner, iterator);
    } else if (forward) {
      // SortedChangeBatch guarantees that later seeds of this owner are beyond the previous walk.
      // Reusing the cursor avoids a linear listIterator(index) seek for every sparse seed.
      while (iterator.nextIndex() < boundary) {
        iterator.next();
      }
    } else {
      while (iterator.nextIndex() > boundary) {
        iterator.previous();
      }
    }
    return iterator;
  }

  @Override
  public boolean hasPendingChanges() {
    return !changedEntities.isEmpty();
  }

  @Override
  public @Nullable VariableChangeHook resolveHookFor(VariableMetaModel<?, ?, ?> variableReference) {
    return monitoredSourceVariableSet.contains(variableReference)
        ? monitoredVariableChangeHook
        : null;
  }

  private final class MonitoredVariableChangeHook implements VariableChangeHook {

    @Override
    public void beforeVariableChanged(Object entity) {
      // Do nothing
    }

    @Override
    public void afterVariableChanged(Object entity) {
      if (!isUpdating && monitoredEntityClass.isInstance(entity)) {
        changedEntities.add(entity);
      }
    }
  }

  @Override
  public List<VariableLoop> getVariableLoops() {
    return Collections.emptyList();
  }
}
