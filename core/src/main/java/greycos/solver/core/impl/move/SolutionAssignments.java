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
import java.util.Set;

import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.impl.cotwin.solution.cloner.DeepCloningUtils;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

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
    return capture(solutionDescriptor, sourceSolution, false);
  }

  /**
   * Captures every genuine binding, including pinned bindings. The snapshot retains entity/value
   * references, but neither live assignment lists nor shadow state. Rebase before crossing working
   * solution graphs; discard snapshots when the problem changes.
   */
  public static <Solution_> SolutionAssignments<Solution_> captureComplete(
      SolutionDescriptor<Solution_> solutionDescriptor, Solution_ sourceSolution) {
    return capture(solutionDescriptor, sourceSolution, true);
  }

  private static <Solution_> SolutionAssignments<Solution_> capture(
      SolutionDescriptor<Solution_> solutionDescriptor,
      Solution_ sourceSolution,
      boolean includePinned) {
    Map<GenuineVariableDescriptor<Solution_>, List<BasicChangeRecord<?>>> basicChangeMap =
        new LinkedHashMap<>();
    Map<ListVariableDescriptor<Solution_>, List<ListChangeRecord<?>>> listChangeMap =
        new LinkedHashMap<>();

    if (includePinned) {
      // Keep descriptors with no owners too: a mandatory list range can still contain values.
      for (var entityDescriptor : solutionDescriptor.getEntityDescriptors()) {
        for (var variableDescriptor : entityDescriptor.getGenuineVariableDescriptorList()) {
          if (variableDescriptor
              instanceof ListVariableDescriptor<Solution_> listVariableDescriptor) {
            listChangeMap.computeIfAbsent(listVariableDescriptor, key -> new ArrayList<>());
          } else {
            basicChangeMap.computeIfAbsent(variableDescriptor, key -> new ArrayList<>());
          }
        }
      }
    }

    solutionDescriptor.visitAllEntities(
        sourceSolution,
        entity -> {
          var entityDescriptor = solutionDescriptor.findEntityDescriptorOrFail(entity.getClass());
          if (!includePinned && !entityDescriptor.isMovable(sourceSolution, entity)) {
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

  /** Compares assignments on the same working graph; entity collection ordering is immaterial. */
  public boolean sameAssignments(SolutionAssignments<Solution_> other) {
    if (!basicChangeMap.keySet().equals(other.basicChangeMap.keySet())
        || !listChangeMap.keySet().equals(other.listChangeMap.keySet())) {
      return false;
    }
    for (var entry : basicChangeMap.entrySet()) {
      var otherRecords = new IdentityHashMap<Object, Object>();
      for (var record : other.basicChangeMap.get(entry.getKey())) {
        otherRecords.put(record.entity(), record.value());
      }
      if (entry.getValue().size() != otherRecords.size()) {
        return false;
      }
      for (var record : entry.getValue()) {
        if (!otherRecords.containsKey(record.entity())
            || !sameValue(record.value(), otherRecords.get(record.entity()))) {
          return false;
        }
      }
    }
    for (var entry : listChangeMap.entrySet()) {
      var otherRecords = new IdentityHashMap<Object, List<Object>>();
      for (var record : other.listChangeMap.get(entry.getKey())) {
        otherRecords.put(record.entity(), record.values());
      }
      if (entry.getValue().size() != otherRecords.size()) {
        return false;
      }
      for (var record : entry.getValue()) {
        var otherValues = otherRecords.get(record.entity());
        if (otherValues == null || !sameList(record.values(), otherValues)) {
          return false;
        }
      }
    }
    return true;
  }

  public boolean matchesCurrent(VariableDescriptorAwareScoreDirector<Solution_> scoreDirector) {
    return sameAssignments(
        captureComplete(scoreDirector.getSolutionDescriptor(), scoreDirector.getWorkingSolution()));
  }

  /**
   * Validates a complete target against the current problem before issuing any change notification.
   * Unlike {@link #apply}, this rejects stale or incomplete targets and changes to pinned entities.
   */
  public void validateComplete(VariableDescriptorAwareScoreDirector<Solution_> scoreDirector) {
    var current =
        captureComplete(scoreDirector.getSolutionDescriptor(), scoreDirector.getWorkingSolution());
    if (!basicChangeMap.keySet().equals(current.basicChangeMap.keySet())
        || !listChangeMap.keySet().equals(current.listChangeMap.keySet())) {
      throw new IllegalStateException(
          "The assignment snapshot does not cover the working solution's genuine variables.");
    }
    var rangeMembers = new IdentityHashMap<ValueRange<Object>, Set<Object>>();
    for (var entry : basicChangeMap.entrySet()) {
      var variable = (BasicVariableDescriptor<Solution_>) entry.getKey();
      var currentEntities = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
      current.basicChangeMap.get(variable).forEach(record -> currentEntities.add(record.entity()));
      for (var record : entry.getValue()) {
        requireWorkingEntity(currentEntities, record.entity(), variable);
        if (!isMovable(scoreDirector, record.entity())
            && !sameValue(variable.getValue(record.entity()), record.value())) {
          throw new IllegalStateException(
              "Pinned assignment differs for "
                  + variable
                  + " on entity ("
                  + record.entity()
                  + ").");
        }
        if (record.value() == null) {
          if (!variable.allowsUnassigned()) {
            throw new IllegalStateException(
                "Mandatory variable ("
                    + variable
                    + ") is unassigned on entity ("
                    + record.entity()
                    + ").");
          }
        } else {
          var range =
              scoreDirector
                  .getValueRangeManager()
                  .<Object, Object>getFromEntity(
                      variable.getValueRangeDescriptor(), record.entity());
          if (!containsAssignment(range, record.value(), rangeMembers)) {
            throw new IllegalStateException(
                "Target value ("
                    + record.value()
                    + ") is outside the working value range of "
                    + variable
                    + " on entity ("
                    + record.entity()
                    + ").");
          }
        }
      }
      requireCompleteEntities(currentEntities, variable);
    }
    for (var entry : listChangeMap.entrySet()) {
      var variable = entry.getKey();
      var currentEntities = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
      current.listChangeMap.get(variable).forEach(record -> currentEntities.add(record.entity()));
      var assigned = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
      // List state and notifications use canonical range identities, including immutable values.
      var range =
          scoreDirector
              .getValueRangeManager()
              .<Object>getFromSolution(variable.getValueRangeDescriptor());
      var members = identityMembers(range, rangeMembers);
      for (var record : entry.getValue()) {
        requireWorkingEntity(currentEntities, record.entity(), variable);
        var currentValues = variable.getValue(record.entity());
        if (!isMovable(scoreDirector, record.entity())
            && !sameList(currentValues, record.values())) {
          throw new IllegalStateException(
              "Pinned assignment differs for "
                  + variable
                  + " on entity ("
                  + record.entity()
                  + ").");
        }
        validatePinnedPrefix(variable, record.entity(), currentValues, record.values());
        var destinationMembers =
            variable.canExtractValueRangeFromSolution()
                ? members
                : identityMembers(
                    scoreDirector
                        .getValueRangeManager()
                        .<Object, Object>getFromEntity(
                            variable.getValueRangeDescriptor(), record.entity()),
                    rangeMembers);
        for (var value : record.values()) {
          if (!destinationMembers.contains(value)) {
            throw new IllegalStateException(
                "Target value ("
                    + value
                    + ") is outside the working value range of "
                    + variable
                    + ".");
          }
          if (!assigned.add(value)) {
            throw new IllegalStateException(
                "Target list value (" + value + ") occurs more than once for " + variable + ".");
          }
        }
      }
      requireCompleteEntities(currentEntities, variable);
      if (!variable.allowsUnassignedValues() && assigned.size() != members.size()) {
        throw new IllegalStateException(
            "Mandatory list variable ("
                + variable
                + ") leaves "
                + (members.size() - assigned.size())
                + " value(s) unassigned.");
      }
    }
  }

  private static <Solution_> void requireWorkingEntity(
      Set<Object> remaining, Object entity, GenuineVariableDescriptor<Solution_> variable) {
    if (!remaining.remove(entity)) {
      throw new IllegalStateException(
          "The snapshot entity ("
              + entity
              + ") for "
              + variable
              + " is duplicated or is not a working entity. Rebase the snapshot onto the current"
              + " working solution.");
    }
  }

  private static <Solution_> void requireCompleteEntities(
      Set<Object> remaining, GenuineVariableDescriptor<Solution_> variable) {
    if (!remaining.isEmpty()) {
      throw new IllegalStateException(
          "The assignment snapshot is missing working entities ("
              + remaining
              + ") for "
              + variable
              + ".");
    }
  }

  private static boolean containsAssignment(
      ValueRange<Object> range,
      Object value,
      IdentityHashMap<ValueRange<Object>, Set<Object>> rangeMembers) {
    return DeepCloningUtils.isImmutable(value.getClass())
        ? range.contains(value)
        : identityMembers(range, rangeMembers).contains(value);
  }

  private static Set<Object> identityMembers(
      ValueRange<Object> range, IdentityHashMap<ValueRange<Object>, Set<Object>> rangeMembers) {
    return rangeMembers.computeIfAbsent(
        range,
        key -> {
          var members = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
          key.createOriginalIterator().forEachRemaining(members::add);
          return members;
        });
  }

  private static <Solution_> boolean isMovable(
      VariableDescriptorAwareScoreDirector<Solution_> director, Object entity) {
    return director
        .getSolutionDescriptor()
        .findEntityDescriptorOrFail(entity.getClass())
        .isMovable(director.getWorkingSolution(), entity);
  }

  private static boolean sameValue(@Nullable Object left, @Nullable Object right) {
    return left == right
        || (left != null && DeepCloningUtils.isImmutable(left.getClass()) && left.equals(right));
  }

  private static boolean sameList(List<Object> left, List<Object> right) {
    if (left.size() != right.size()) {
      return false;
    }
    for (int i = 0; i < left.size(); i++) {
      if (left.get(i) != right.get(i)) {
        return false;
      }
    }
    return true;
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
    for (Map.Entry<GenuineVariableDescriptor<Solution_>, List<BasicChangeRecord<?>>> entry :
        basicChangeMap.entrySet()) {
      GenuineVariableDescriptor<Solution_> variableDescriptor = entry.getKey();
      for (BasicChangeRecord<?> changeRecord : entry.getValue()) {
        if (!isMovable(scoreDirector, changeRecord.entity())
            || sameValue(
                variableDescriptor.getValue(changeRecord.entity()), changeRecord.value())) {
          continue;
        }
        scoreDirector.changeVariableFacade(
            variableDescriptor, changeRecord.entity(), changeRecord.value());
      }
    }
  }

  private List<PreparedListChanges<Solution_>> prepareListChanges(
      VariableDescriptorAwareScoreDirector<Solution_> scoreDirector) {
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
        if (!isMovable(scoreDirector, entity)) {
          continue;
        }
        List<Object> targetList = changeRecord.values();
        List<Object> currentList = variableDescriptor.getValue(entity);
        if (sameList(currentList, targetList)) {
          continue;
        }
        int fromIndex = validatePinnedPrefix(variableDescriptor, entity, currentList, targetList);
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

  private static <Solution_> int validatePinnedPrefix(
      ListVariableDescriptor<Solution_> variableDescriptor,
      Object entity,
      List<Object> currentList,
      List<Object> targetList) {
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
      if (!sameList(currentPinned, targetPinned)) {
        throw new IllegalStateException(
            "Pinned list segment differs for "
                + variableDescriptor.getSimpleEntityAndVariableName()
                + " on entity ("
                + entity
                + ").");
      }
    }
    return fromIndex;
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
      destinationBasicChangeMap.put(
          rebaseDescriptor(entry.getKey(), destinationScoreDirector), destinationChangeRecords);
    }

    for (Map.Entry<ListVariableDescriptor<Solution_>, List<ListChangeRecord<?>>> entry :
        listChangeMap.entrySet()) {
      var destinationDescriptor =
          (ListVariableDescriptor<Solution_>)
              rebaseDescriptor(entry.getKey(), destinationScoreDirector);
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
                .getFromSolution(destinationDescriptor.getValueRangeDescriptor());
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
                        immutableValues.containsKey(value)
                            ? immutableValues.get(value)
                            : destinationScoreDirector.lookUpWorkingObject(value))
                .toList();
        destinationChangeRecords.add(new ListChangeRecord<>(destinationEntity, destinationValues));
      }
      destinationListChangeMap.put(destinationDescriptor, destinationChangeRecords);
    }

    return new SolutionAssignments<>(destinationBasicChangeMap, destinationListChangeMap);
  }

  private static <Solution_> GenuineVariableDescriptor<Solution_> rebaseDescriptor(
      GenuineVariableDescriptor<Solution_> descriptor,
      ScoreDirector<Solution_> destinationScoreDirector) {
    if (destinationScoreDirector
        instanceof VariableDescriptorAwareScoreDirector<Solution_> variableAwareDirector) {
      return Objects.requireNonNull(
          variableAwareDirector
              .getSolutionDescriptor()
              .findEntityDescriptorOrFail(descriptor.getEntityDescriptor().getEntityClass())
              .getGenuineVariableDescriptor(descriptor.getVariableName()));
    }
    return descriptor;
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
    private final @Nullable Object value;

    private BasicChangeRecord(E entity, @Nullable Object value) {
      this.entity = Objects.requireNonNull(entity);
      this.value = value;
    }

    public E entity() {
      return entity;
    }

    public @Nullable Object value() {
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
