package greycos.solver.core.impl.partitionedsearch.scope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.impl.cotwin.lookup.LookUpManager;
import greycos.solver.core.impl.cotwin.solution.cloner.DeepCloningUtils;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ValueRangeManager;

/** Validates the independent mutable domains of a partitioned search phase. */
public final class PartitionOwnership<Solution_> {

  private final Map<Object, Integer> entityOwners = new IdentityHashMap<>();
  private final Map<ListVariableDescriptor<Solution_>, Map<Object, Integer>> valueOwners =
      new LinkedHashMap<>();
  private final Map<GenuineVariableDescriptor<Solution_>, Map<Object, AssignmentDomain>>
      basicRanges = new LinkedHashMap<>();
  private final Map<ListVariableDescriptor<Solution_>, Map<Object, Object>> immutableListValues =
      new LinkedHashMap<>();
  private final Map<ListVariableDescriptor<Solution_>, Map<Object, AssignmentDomain>>
      listEntityRanges = new LinkedHashMap<>();
  private final int partCount;

  private PartitionOwnership(int partCount) {
    this.partCount = partCount;
  }

  /**
   * Validate before starting workers. Partition inputs may refer to parent objects: each child
   * solver clones its input before changing it. Pinned copies may provide fixed boundary context.
   */
  public static <Solution_> PartitionOwnership<Solution_> validate(
      InnerScoreDirector<Solution_, ?> parent, List<Solution_> parts) {
    Objects.requireNonNull(
        parts, "The solution partitioner must return a non-null partition list.");
    var ownership = new PartitionOwnership<Solution_>(parts.size());
    var descriptor = parent.getSolutionDescriptor();
    var parentEntities = identitySet();
    descriptor.visitAllEntities(parent.getWorkingSolution(), parentEntities::add);
    var listVariables = new LinkedHashSet<ListVariableDescriptor<Solution_>>();
    for (var entityDescriptor : descriptor.getEntityDescriptors()) {
      for (var variable : entityDescriptor.getGenuineVariableDescriptorList()) {
        if (variable instanceof ListVariableDescriptor<Solution_> listVariable) {
          listVariables.add(listVariable);
          ownership.valueOwners.putIfAbsent(listVariable, new IdentityHashMap<>());
        }
      }
    }
    var parentRanges = new LinkedHashMap<ListVariableDescriptor<Solution_>, Set<Object>>();
    for (var variable : listVariables) {
      var values = identitySet();
      parent
          .getValueRangeManager()
          .getFromSolution(variable.getValueRangeDescriptor())
          .createOriginalIterator()
          .forEachRemaining(values::add);
      parentRanges.put(variable, values);
      var immutableValues = new HashMap<Object, Object>();
      for (var value : values) {
        if (DeepCloningUtils.isImmutable(value.getClass())) {
          immutableValues.put(value, value);
        }
      }
      ownership.immutableListValues.put(variable, immutableValues);
    }
    for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
      var part = parts.get(partIndex);
      if (part == null) {
        throw invalid(partIndex, "the partition solution is null");
      }
      ownership.validatePartition(
          parent, descriptor, parentEntities, parentRanges, part, partIndex);
    }
    for (var entity : parentEntities) {
      var entityDescriptor = descriptor.findEntityDescriptorOrFail(entity.getClass());
      if (entityDescriptor.isMovable(parent.getWorkingSolution(), entity)
          && !ownership.entityOwners.containsKey(entity)) {
        throw invalid(-1, "movable entity (" + entity + ") is missing from all partitions");
      }
    }
    for (var entry : parentRanges.entrySet()) {
      var variable = entry.getKey();
      var state = parent.getListVariableState(variable);
      var owners = ownership.valueOwners.get(variable);
      for (var value : entry.getValue()) {
        if (state.isPinned(value)) {
          continue;
        }
        var owner = owners.get(value);
        if (owner == null) {
          throw invalid(
              -1,
              "list value ("
                  + value
                  + ") of variable ("
                  + variable
                  + ") is missing from all assignable partition ranges");
        }
        var holder = state.getInverseSingleton(value);
        if (holder != null && !owner.equals(ownership.entityOwners.get(holder))) {
          throw invalid(
              owner,
              "assigned list value ("
                  + value
                  + ") of variable ("
                  + variable
                  + ") belongs to another partition than its current entity ("
                  + holder
                  + ")");
        }
        var valueEntityDescriptor = descriptor.findEntityDescriptor(value.getClass());
        if (valueEntityDescriptor != null
            && valueEntityDescriptor.isMovable(parent.getWorkingSolution(), value)
            && !owner.equals(ownership.entityOwners.get(value))) {
          throw invalid(
              owner, "list value (" + value + ") has genuine variables owned by another partition");
        }
      }
    }
    return ownership;
  }

  private void validatePartition(
      InnerScoreDirector<Solution_, ?> parent,
      SolutionDescriptor<Solution_> descriptor,
      Set<Object> parentEntities,
      Map<ListVariableDescriptor<Solution_>, Set<Object>> parentRanges,
      Solution_ part,
      int partIndex) {
    var ranges = ValueRangeManager.of(descriptor, part);
    var partLookup = new LookUpManager(descriptor.getLookUpStrategyResolver());
    descriptor.visitAll(part, partLookup::addWorkingObject);
    var localEntities = new ArrayList<Object>();
    descriptor.visitAllEntities(part, localEntities::add);
    var canonicalEntities = identitySet();
    var pinnedValues = new LinkedHashMap<ListVariableDescriptor<Solution_>, Set<Object>>();
    var listedValues = new LinkedHashMap<ListVariableDescriptor<Solution_>, Set<Object>>();
    var movableListVariables = new LinkedHashSet<ListVariableDescriptor<Solution_>>();
    for (var entity : localEntities) {
      var destination = lookup(parent, entity, partIndex);
      if (!parentEntities.contains(destination) || !canonicalEntities.add(destination)) {
        throw invalid(partIndex, "entity (" + entity + ") is foreign or occurs more than once");
      }
      var entityDescriptor = descriptor.findEntityDescriptorOrFail(entity.getClass());
      boolean movable = entityDescriptor.isMovable(part, entity);
      if (movable) {
        if (!entityDescriptor.isMovable(parent.getWorkingSolution(), destination)) {
          throw invalid(partIndex, "pinned parent entity (" + destination + ") became movable");
        }
        addOwner(entityOwners, destination, partIndex, "movable entity");
      }
      for (var variable : entityDescriptor.getGenuineVariableDescriptorList()) {
        if (variable instanceof ListVariableDescriptor<Solution_> listVariable) {
          if (movable) {
            movableListVariables.add(listVariable);
            var parentRange =
                parent
                    .getValueRangeManager()
                    .getFromEntity(variable.getValueRangeDescriptor(), destination);
            var range = ranges.getFromEntity(variable.getValueRangeDescriptor(), entity);
            listEntityRanges
                .computeIfAbsent(listVariable, ignored -> new IdentityHashMap<>())
                .put(destination, new AssignmentDomain(range, parentRange, partLookup));
          }
          validateInitialList(
              parent,
              partIndex,
              entity,
              destination,
              movable,
              listVariable,
              pinnedValues.computeIfAbsent(listVariable, ignored -> identitySet()),
              listedValues.computeIfAbsent(listVariable, ignored -> identitySet()));
        } else if (movable) {
          var range = ranges.getFromEntity(variable.getValueRangeDescriptor(), entity);
          var parentRange =
              parent
                  .getValueRangeManager()
                  .getFromEntity(variable.getValueRangeDescriptor(), destination);
          var domain = new AssignmentDomain(range, parentRange, partLookup);
          var initialValue = variable.getValue(entity);
          if (initialValue != null) {
            if (!range.contains(initialValue)) {
              throw invalid(
                  partIndex,
                  "assigned value ("
                      + initialValue
                      + ") of variable ("
                      + variable
                      + ") on entity ("
                      + entity
                      + ") is absent from its partition range");
            }
            if (!parentRange.contains(lookup(parent, initialValue, partIndex))) {
              throw invalid(
                  partIndex,
                  "assigned value ("
                      + initialValue
                      + ") of variable ("
                      + variable
                      + ") on entity ("
                      + entity
                      + ") is outside its parent range");
            }
          }
          basicRanges
              .computeIfAbsent(variable, ignored -> new IdentityHashMap<>())
              .put(destination, domain);
          if (range.isEmpty()
              && !((BasicVariableDescriptor<Solution_>) variable).allowsUnassigned()) {
            throw invalid(
                partIndex,
                "required variable ("
                    + variable
                    + ") of entity ("
                    + entity
                    + ") has an empty value range");
          }
        } else if (entityDescriptor.isGenuine()) {
          var value = variable.getValue(entity);
          var rebasedValue = value == null ? null : lookup(parent, value, partIndex);
          if (!Objects.equals(variable.getValue(destination), rebasedValue)) {
            throw invalid(
                partIndex,
                "pinned variable ("
                    + variable
                    + ") of entity ("
                    + entity
                    + ") differs from its parent value");
          }
        }
      }
    }
    for (var entry : parentRanges.entrySet()) {
      var variable = entry.getKey();
      var localRange = identitySet();
      var iterator =
          ranges.getFromSolution(variable.getValueRangeDescriptor()).createOriginalIterator();
      while (iterator.hasNext()) {
        var value = lookupListValue(parent, variable, iterator.next(), partIndex);
        if (!entry.getValue().contains(value) || !localRange.add(value)) {
          throw invalid(
              partIndex,
              "list range of variable ("
                  + variable
                  + ") contains foreign or repeated value ("
                  + value
                  + ")");
        }
        if (pinnedValues.getOrDefault(variable, Collections.emptySet()).contains(value)) {
          continue;
        }
        if (parent.getListVariableState(variable).isPinned(value)) {
          throw invalid(
              partIndex,
              "pinned list value ("
                  + value
                  + ") of variable ("
                  + variable
                  + ") is offered without its pinned entity context");
        }
        if (!variable.allowsUnassignedValues() && !movableListVariables.contains(variable)) {
          throw invalid(
              partIndex,
              "required list value ("
                  + value
                  + ") of variable ("
                  + variable
                  + ") has no movable destination entity");
        }
        addOwner(
            valueOwners.get(variable), value, partIndex, "assignable list value of " + variable);
      }
      for (var value : listedValues.getOrDefault(variable, Collections.emptySet())) {
        if (!localRange.contains(value)) {
          throw invalid(
              partIndex,
              "assigned list value ("
                  + value
                  + ") is absent from the range of variable ("
                  + variable
                  + ")");
        }
      }
    }
  }

  private void validateInitialList(
      InnerScoreDirector<Solution_, ?> parent,
      int partIndex,
      Object entity,
      Object destination,
      boolean movable,
      ListVariableDescriptor<Solution_> variable,
      Set<Object> pinnedValues,
      Set<Object> listedValues) {
    var source = variable.getValue(entity);
    var current = variable.getValue(destination);
    int pinnedSize = movable ? variable.getFirstUnpinnedIndex(entity) : source.size();
    if (pinnedSize < 0
        || pinnedSize > source.size()
        || (movable && pinnedSize != variable.getFirstUnpinnedIndex(destination))
        || pinnedSize > current.size()
        || (!movable && source.size() != current.size())) {
      throw invalid(
          partIndex,
          "pinned list bounds differ for variable (" + variable + ") of entity (" + entity + ")");
    }
    for (int i = 0; i < source.size(); i++) {
      var value = lookupListValue(parent, variable, source.get(i), partIndex);
      if (movable && !listEntityRanges.get(variable).get(destination).contains(value)) {
        throw invalid(
            partIndex,
            "assigned list value ("
                + value
                + ") of variable ("
                + variable
                + ") is outside the range of entity ("
                + entity
                + ")");
      }
      if (!listedValues.add(value)) {
        throw invalid(
            partIndex, "duplicate list value (" + value + ") for variable (" + variable + ")");
      }
      if (i < pinnedSize) {
        if (current.get(i) != value) {
          throw invalid(
              partIndex,
              "pinned list segment differs for variable ("
                  + variable
                  + ") of entity ("
                  + entity
                  + ")");
        }
        pinnedValues.add(value);
      }
    }
  }

  /** Validate a rebased publication on the parent thread, before any assignments change. */
  public void validateMove(
      PartitionChangeMove<Solution_> move, InnerScoreDirector<Solution_, ?> parent) {
    int partIndex = move.getPartIndex();
    if (partIndex < 0 || partIndex >= partCount) {
      throw invalid(partIndex, "publication has an unknown partition index");
    }
    for (var entry : move.getAssignments().getBasicChanges().entrySet()) {
      var variable = (BasicVariableDescriptor<Solution_>) entry.getKey();
      for (var change : entry.getValue()) {
        validateEntityOwner(change.entity(), partIndex);
        var allowed =
            basicRanges.getOrDefault(variable, Collections.emptyMap()).get(change.entity());
        // A construction heuristic may publish its partially initialized best when interrupted.
        if (change.value() != null && (allowed == null || !allowed.contains(change.value()))) {
          throw invalid(
              partIndex,
              "publication assigns value ("
                  + change.value()
                  + ") outside the range of variable ("
                  + variable
                  + ") on entity ("
                  + change.entity()
                  + ")");
        }
      }
    }
    for (var entry : move.getAssignments().getListChanges().entrySet()) {
      var variable = entry.getKey();
      var owners = valueOwners.get(variable);
      var state = parent.getListVariableState(variable);
      var targetedValues = identitySet();
      var targetedEntities = identitySet();
      for (var change : entry.getValue()) {
        validateEntityOwner(change.entity(), partIndex);
        targetedEntities.add(change.entity());
      }
      for (var change : entry.getValue()) {
        var current = variable.getValue(change.entity());
        int fromIndex = variable.getFirstUnpinnedIndex(change.entity());
        if (fromIndex < 0 || fromIndex > current.size() || fromIndex > change.values().size()) {
          throw invalid(
              partIndex,
              "invalid pinned bounds for variable ("
                  + variable
                  + ") of entity ("
                  + change.entity()
                  + ")");
        }
        for (int i = 0; i < change.values().size(); i++) {
          var value = change.values().get(i);
          var allowed =
              listEntityRanges.getOrDefault(variable, Collections.emptyMap()).get(change.entity());
          if (allowed == null || !allowed.contains(value)) {
            throw invalid(
                partIndex,
                "publication assigns list value ("
                    + value
                    + ") outside the range of variable ("
                    + variable
                    + ") on entity ("
                    + change.entity()
                    + ")");
          }
          if (!targetedValues.add(value)) {
            throw invalid(
                partIndex,
                "publication contains duplicate list value ("
                    + value
                    + ") for variable ("
                    + variable
                    + ")");
          }
          if (i < fromIndex) {
            if (current.get(i) != value) {
              throw invalid(
                  partIndex, "pinned list segment differs for entity (" + change.entity() + ")");
            }
          } else if (owners == null
              || !Objects.equals(owners.get(value), partIndex)
              || state.isPinned(value)) {
            throw invalid(
                partIndex,
                "publication assigns foreign or pinned list value ("
                    + value
                    + ") for variable ("
                    + variable
                    + ")");
          }
          var holder = state.getInverseSingleton(value);
          if (holder != null && !targetedEntities.contains(holder)) {
            throw invalid(
                partIndex,
                "publication takes list value ("
                    + value
                    + ") from unchanged entity ("
                    + holder
                    + ")");
          }
        }
      }
    }
  }

  private record AssignmentDomain(
      ValueRange<Object> partitionRange,
      ValueRange<Object> parentRange,
      LookUpManager partitionLookup) {
    boolean contains(Object value) {
      if (!parentRange.contains(value)) {
        return false;
      }
      var partitionValue = partitionLookup.lookUpWorkingObjectOrReturnNull(value);
      return partitionValue != null && partitionRange.contains(partitionValue);
    }
  }

  private void validateEntityOwner(Object entity, int partIndex) {
    if (!Objects.equals(entityOwners.get(entity), partIndex)) {
      throw invalid(
          partIndex, "publication changes entity (" + entity + ") owned by another partition");
    }
  }

  private Object lookupListValue(
      InnerScoreDirector<Solution_, ?> parent,
      ListVariableDescriptor<Solution_> variable,
      Object value,
      int partIndex) {
    var immutable = immutableListValues.get(variable).get(value);
    return immutable == null ? lookup(parent, value, partIndex) : immutable;
  }

  private static Set<Object> identitySet() {
    return Collections.newSetFromMap(new IdentityHashMap<>());
  }

  private static void addOwner(
      Map<Object, Integer> owners, Object object, int partIndex, String kind) {
    var previous = owners.putIfAbsent(object, partIndex);
    if (previous != null) {
      throw invalid(
          partIndex, kind + " (" + object + ") is also owned by partition (" + previous + ")");
    }
  }

  private static Object lookup(InnerScoreDirector<?, ?> parent, Object object, int partIndex) {
    try {
      return Objects.requireNonNull(parent.lookUpWorkingObject(object));
    } catch (RuntimeException failure) {
      throw new IllegalArgumentException(
          "Partition ("
              + partIndex
              + ") contains an unknown entity or value ("
              + object
              + "). Preserve the parent's planning IDs and include all referenced facts.",
          failure);
    }
  }

  private static IllegalArgumentException invalid(int partIndex, String detail) {
    return new IllegalArgumentException(
        "Invalid partition ("
            + partIndex
            + "): "
            + detail
            + ". Give every movable entity and assignable list value one owner; keep assigned list values with their entity and preserve pinned context.");
  }
}
