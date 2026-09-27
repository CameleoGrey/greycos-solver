package greycos.solver.core.impl.move;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedCollection;

import greycos.solver.core.impl.cotwin.solution.cloner.DeepCloningUtils;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;

import org.jspecify.annotations.NullMarked;

/**
 * Immutable snapshot of genuine assignments, shared by island synchronization and partition
 * merging. Rebased snapshots preserve destination entities and apply changes through the recording
 * director.
 */
@NullMarked
public final class SolutionAssignments<Solution_> {

  private final Map<GenuineVariableDescriptor<Solution_>, List<BasicChangeRecord<?>>>
      basicChangeMap;
  private final Map<ListVariableDescriptor<Solution_>, List<ListChangeRecord<?>>> listChangeMap;

  private SolutionAssignments(
      Map<GenuineVariableDescriptor<Solution_>, List<BasicChangeRecord<?>>> basicChangeMap,
      Map<ListVariableDescriptor<Solution_>, List<ListChangeRecord<?>>> listChangeMap) {
    basicChangeMap.replaceAll((descriptor, records) -> List.copyOf(records));
    listChangeMap.replaceAll((descriptor, records) -> List.copyOf(records));
    this.basicChangeMap = Collections.unmodifiableMap(basicChangeMap);
    this.listChangeMap = Collections.unmodifiableMap(listChangeMap);
  }

  public static <Solution_> SolutionAssignments<Solution_> capture(
      SolutionDescriptor<Solution_> solutionDescriptor, Solution_ sourceSolution) {
    Map<GenuineVariableDescriptor<Solution_>, List<BasicChangeRecord<?>>> basicChangeMap =
        new LinkedHashMap<>();
    Map<ListVariableDescriptor<Solution_>, List<ListChangeRecord<?>>> listChangeMap =
        new LinkedHashMap<>();

    solutionDescriptor.visitAllEntities(
        sourceSolution,
        entity -> {
          var entityDescriptor = solutionDescriptor.findEntityDescriptorOrFail(entity.getClass());
          if (!entityDescriptor.isMovable(sourceSolution, entity)) {
            return;
          }
          for (GenuineVariableDescriptor<Solution_> variableDescriptor :
              entityDescriptor.getGenuineVariableDescriptorList()) {
            if (variableDescriptor
                instanceof ListVariableDescriptor<Solution_> listVariableDescriptor) {
              List<Object> values = new ArrayList<>(listVariableDescriptor.getValue(entity));
              listChangeMap
                  .computeIfAbsent(listVariableDescriptor, k -> new ArrayList<>())
                  .add(new ListChangeRecord<>(entity, values));
            } else {
              Object value = variableDescriptor.getValue(entity);
              basicChangeMap
                  .computeIfAbsent(variableDescriptor, k -> new ArrayList<>())
                  .add(new BasicChangeRecord<>(entity, value));
            }
          }
        });

    return new SolutionAssignments<>(basicChangeMap, listChangeMap);
  }

  public void apply(VariableDescriptorAwareScoreDirector<Solution_> castScoreDirector) {
    // Validate the entire list update before changing even the basic variables. A later pinned
    // prefix mismatch must not leave an earlier entity partially synchronized.
    var preparedListChanges = prepareListChanges(castScoreDirector);
    applyBasicChanges(castScoreDirector);
    applyListChanges(castScoreDirector, preparedListChanges);
  }

  public Map<GenuineVariableDescriptor<Solution_>, List<BasicChangeRecord<?>>> getBasicChanges() {
    return basicChangeMap;
  }

  public Map<ListVariableDescriptor<Solution_>, List<ListChangeRecord<?>>> getListChanges() {
    return listChangeMap;
  }

  private void applyBasicChanges(VariableDescriptorAwareScoreDirector<Solution_> scoreDirector) {
    var workingSolution = scoreDirector.getWorkingSolution();
    for (Map.Entry<GenuineVariableDescriptor<Solution_>, List<BasicChangeRecord<?>>> entry :
        basicChangeMap.entrySet()) {
      GenuineVariableDescriptor<Solution_> variableDescriptor = entry.getKey();
      for (BasicChangeRecord<?> changeRecord : entry.getValue()) {
        if (!variableDescriptor
            .getEntityDescriptor()
            .isMovable(workingSolution, changeRecord.entity())) {
          continue;
        }
        scoreDirector.changeVariableFacade(
            variableDescriptor, changeRecord.entity(), changeRecord.value());
      }
    }
  }

  private List<PreparedListChanges<Solution_>> prepareListChanges(
      VariableDescriptorAwareScoreDirector<Solution_> scoreDirector) {
    var workingSolution = scoreDirector.getWorkingSolution();
    var preparedListChanges = new ArrayList<PreparedListChanges<Solution_>>();
    for (Map.Entry<ListVariableDescriptor<Solution_>, List<ListChangeRecord<?>>> entry :
        listChangeMap.entrySet()) {
      ListVariableDescriptor<Solution_> variableDescriptor = entry.getKey();
      var changes = new ArrayList<PreparedListChange>();
      var oldValues = new ArrayList<Object>();
      var targetValues = new ArrayList<Object>();
      var oldValueSet = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
      var targetValueSet = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
      for (ListChangeRecord<?> changeRecord : entry.getValue()) {
        Object entity = changeRecord.entity();
        if (!variableDescriptor.getEntityDescriptor().isMovable(workingSolution, entity)) {
          continue;
        }
        List<Object> targetList = changeRecord.values();
        List<Object> currentList = variableDescriptor.getValue(entity);
        if (currentList.equals(targetList)) {
          continue;
        }
        int fromIndex = variableDescriptor.getFirstUnpinnedIndex(entity);
        if (fromIndex < 0 || fromIndex > currentList.size()) {
          throw new IllegalStateException(
              "Pinned index (" + fromIndex + ") exceeds list size (" + currentList.size() + ").");
        }
        if (fromIndex > targetList.size()) {
          throw new IllegalStateException(
              "Target list size ("
                  + targetList.size()
                  + ") is smaller than pinned index ("
                  + fromIndex
                  + ").");
        }
        if (fromIndex > 0) {
          List<Object> currentPinned = currentList.subList(0, fromIndex);
          List<Object> targetPinned = targetList.subList(0, fromIndex);
          if (!currentPinned.equals(targetPinned)) {
            throw new IllegalStateException(
                "Pinned list segment differs for "
                    + variableDescriptor.getSimpleEntityAndVariableName()
                    + " on entity ("
                    + entity
                    + ").");
          }
        }
        changes.add(
            new PreparedListChange(entity, currentList, targetList, fromIndex, currentList.size()));
        for (var value : currentList.subList(fromIndex, currentList.size())) {
          if (oldValueSet.add(value)) {
            oldValues.add(value);
          }
        }
        for (var value : targetList.subList(fromIndex, targetList.size())) {
          if (targetValueSet.add(value)) {
            targetValues.add(value);
          }
        }
      }
      if (!changes.isEmpty()) {
        // Compare assignment membership across all entities, not per list. Values transferred
        // between lists remain assigned and must not receive unassignment notifications.
        oldValues.removeIf(targetValueSet::contains);
        targetValues.removeIf(oldValueSet::contains);
        preparedListChanges.add(
            new PreparedListChanges<>(variableDescriptor, changes, targetValues, oldValues));
      }
    }
    return preparedListChanges;
  }

  private void applyListChanges(
      VariableDescriptorAwareScoreDirector<Solution_> scoreDirector,
      List<PreparedListChanges<Solution_>> preparedListChanges) {
    for (var prepared : preparedListChanges) {
      var variableDescriptor = prepared.variableDescriptor();
      for (var value : prepared.assignedValues()) {
        scoreDirector.beforeListVariableElementAssigned(variableDescriptor, value);
      }
      for (var value : prepared.unassignedValues()) {
        scoreDirector.beforeListVariableElementUnassigned(variableDescriptor, value);
      }
      for (var change : prepared.changes()) {
        scoreDirector.beforeListVariableChanged(
            variableDescriptor, change.entity(), change.fromIndex(), change.oldSize());
      }
      for (var change : prepared.changes()) {
        change.currentList().subList(change.fromIndex(), change.oldSize()).clear();
        change
            .currentList()
            .addAll(change.targetList().subList(change.fromIndex(), change.targetList().size()));
      }
      for (var change : prepared.changes()) {
        scoreDirector.afterListVariableChanged(
            variableDescriptor, change.entity(), change.fromIndex(), change.currentList().size());
      }
      // Keep assignment notifications outside the list-change brackets, as in the built-in
      // replacement move. The recorder then restores both list contents and assignment state.
      for (var value : prepared.unassignedValues()) {
        scoreDirector.afterListVariableElementUnassigned(variableDescriptor, value);
      }
      for (var value : prepared.assignedValues()) {
        scoreDirector.afterListVariableElementAssigned(variableDescriptor, value);
      }
    }
  }

  private record PreparedListChanges<Solution_>(
      ListVariableDescriptor<Solution_> variableDescriptor,
      List<PreparedListChange> changes,
      List<Object> assignedValues,
      List<Object> unassignedValues) {}

  private record PreparedListChange(
      Object entity,
      List<Object> currentList,
      List<Object> targetList,
      int fromIndex,
      int oldSize) {}

  public SolutionAssignments<Solution_> rebase(ScoreDirector<Solution_> destinationScoreDirector) {
    Map<GenuineVariableDescriptor<Solution_>, List<BasicChangeRecord<?>>>
        destinationBasicChangeMap = new LinkedHashMap<>();
    Map<ListVariableDescriptor<Solution_>, List<ListChangeRecord<?>>> destinationListChangeMap =
        new LinkedHashMap<>();

    for (Map.Entry<GenuineVariableDescriptor<Solution_>, List<BasicChangeRecord<?>>> entry :
        basicChangeMap.entrySet()) {
      List<BasicChangeRecord<?>> destinationChangeRecords = new ArrayList<>();
      for (BasicChangeRecord<?> record : entry.getValue()) {
        Object destinationEntity = destinationScoreDirector.lookUpWorkingObject(record.entity());
        Object destinationValue =
            record.value() == null
                ? null
                : destinationScoreDirector.lookUpWorkingObject(record.value());
        destinationChangeRecords.add(new BasicChangeRecord<>(destinationEntity, destinationValue));
      }
      destinationBasicChangeMap.put(entry.getKey(), destinationChangeRecords);
    }

    for (Map.Entry<ListVariableDescriptor<Solution_>, List<ListChangeRecord<?>>> entry :
        listChangeMap.entrySet()) {
      List<ListChangeRecord<?>> destinationChangeRecords = new ArrayList<>();
      var immutableValues = new HashMap<Object, Object>();
      boolean hasImmutableValues =
          entry.getValue().stream()
              .flatMap(record -> record.values().stream())
              .anyMatch(value -> DeepCloningUtils.isImmutable(value.getClass()));
      if (hasImmutableValues) {
        var range =
            ((VariableDescriptorAwareScoreDirector<Solution_>) destinationScoreDirector)
                .getValueRangeManager()
                .getFromSolution(entry.getKey().getValueRangeDescriptor());
        range
            .createOriginalIterator()
            .forEachRemaining(
                value -> {
                  if (DeepCloningUtils.isImmutable(value.getClass())) {
                    immutableValues.put(value, value);
                  }
                });
      }
      for (ListChangeRecord<?> record : entry.getValue()) {
        Object destinationEntity = destinationScoreDirector.lookUpWorkingObject(record.entity());
        List<Object> destinationValues =
            record.values().stream()
                .map(
                    value ->
                        immutableValues.getOrDefault(
                            value, destinationScoreDirector.lookUpWorkingObject(value)))
                .toList();
        destinationChangeRecords.add(new ListChangeRecord<>(destinationEntity, destinationValues));
      }
      destinationListChangeMap.put(entry.getKey(), destinationChangeRecords);
    }

    return new SolutionAssignments<>(destinationBasicChangeMap, destinationListChangeMap);
  }

  public SequencedCollection<Object> getPlanningEntities() {
    if (basicChangeMap.isEmpty() && listChangeMap.isEmpty()) {
      return Collections.emptyList();
    }
    var entities = new ArrayList<Object>();
    var seen = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    for (List<BasicChangeRecord<?>> changeRecords : basicChangeMap.values()) {
      for (BasicChangeRecord<?> record : changeRecords) {
        if (seen.add(record.entity())) {
          entities.add(record.entity());
        }
      }
    }
    for (List<ListChangeRecord<?>> changeRecords : listChangeMap.values()) {
      for (ListChangeRecord<?> record : changeRecords) {
        if (seen.add(record.entity())) {
          entities.add(record.entity());
        }
      }
    }
    return entities;
  }

  @Override
  public String toString() {
    return "SolutionAssignments{basicChanges="
        + basicChangeMap.size()
        + ", listChanges="
        + listChangeMap.size()
        + "}";
  }

  public static final class BasicChangeRecord<E> {
    private final E entity;
    private final Object value;

    private BasicChangeRecord(E entity, Object value) {
      this.entity = entity;
      this.value = value;
    }

    public E entity() {
      return entity;
    }

    public Object value() {
      return value;
    }
  }

  public static final class ListChangeRecord<E> {
    private final E entity;
    private final List<Object> values;

    private ListChangeRecord(E entity, List<Object> values) {
      this.entity = Objects.requireNonNull(entity);
      this.values = List.copyOf(values);
    }

    public E entity() {
      return entity;
    }

    public List<Object> values() {
      return values;
    }
  }
}
