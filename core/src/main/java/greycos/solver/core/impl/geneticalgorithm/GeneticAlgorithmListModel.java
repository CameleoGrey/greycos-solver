package greycos.solver.core.impl.geneticalgorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;

import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;

import org.jspecify.annotations.NullMarked;

/**
 * Phase-local list ownership, ranges and canonical element identities. Genomes contain only the
 * indexes into this model; they never contain mutable list containers or shadow state.
 */
@NullMarked
public final class GeneticAlgorithmListModel<Solution_> {

  private final ListVariableDescriptor<Solution_> variableDescriptor;
  private final List<Object> owners;
  private final Object[] values;
  private final IdentityHashMap<Object, Integer> valueIds = new IdentityHashMap<>();
  private final List<ValueRange<Object>> ranges;
  private final boolean[] ownerMovable;
  private final int[] firstUnpinnedIndices;
  private final int[][] initialLists;
  private final int[] movableValueIds;

  public GeneticAlgorithmListModel(InnerScoreDirector<Solution_, ?> director) {
    var descriptor = director.getSolutionDescriptor().getListVariableDescriptor();
    if (descriptor == null) {
      throw new IllegalArgumentException(
          "The genetic algorithm list model requires a list variable.");
    }
    variableDescriptor = descriptor;
    var rangeManager = director.getValueRangeManager();
    ValueRange<Object> universe =
        rangeManager.getFromSolution(variableDescriptor.getValueRangeDescriptor());
    var size = universe.getSize();
    if (size < 0 || size > Integer.MAX_VALUE) {
      throw new IllegalArgumentException(
          "The genetic algorithm list value range (%s) has size (%d), which must fit in an int."
              .formatted(name(), size));
    }
    values = new Object[(int) size];
    var iterator = universe.createOriginalIterator();
    for (var id = 0; id < values.length; id++) {
      if (!iterator.hasNext()) {
        throw new IllegalArgumentException(
            "The genetic algorithm list value range (%s) contains fewer values than its size (%d)."
                .formatted(name(), size));
      }
      var value = iterator.next();
      if (value == null
          || !variableDescriptor.acceptsValueType(value.getClass())
          || valueIds.put(value, id) != null) {
        throw new IllegalArgumentException(
            "The genetic algorithm list value range (%s) contains a null, incompatible or repeated canonical value (%s)."
                .formatted(name(), value));
      }
      values[id] = value;
    }
    if (iterator.hasNext()) {
      throw new IllegalArgumentException(
          "The genetic algorithm list value range (%s) contains more values than its size (%d)."
              .formatted(name(), size));
    }
    var capturedOwners = new ArrayList<Object>();
    var seenOwners = new IdentityHashMap<Object, Boolean>();
    variableDescriptor
        .getEntityDescriptor()
        .visitAllEntities(
            director.getWorkingSolution(),
            owner -> {
              if (seenOwners.put(owner, Boolean.TRUE) != null) {
                throw new IllegalArgumentException(
                    "The genetic algorithm list variable (%s) has a repeated owner (%s)."
                        .formatted(name(), owner));
              }
              capturedOwners.add(owner);
            });
    owners = List.copyOf(capturedOwners);
    ranges = new ArrayList<>(owners.size());
    ownerMovable = new boolean[owners.size()];
    firstUnpinnedIndices = new int[owners.size()];
    for (var ownerId = 0; ownerId < owners.size(); ownerId++) {
      var owner = owners.get(ownerId);
      ranges.add(rangeManager.getFromEntity(variableDescriptor.getValueRangeDescriptor(), owner));
      var entityDescriptor =
          director.getSolutionDescriptor().findEntityDescriptorOrFail(owner.getClass());
      ownerMovable[ownerId] = entityDescriptor.isMovable(director.getWorkingSolution(), owner);
      var listSize = variableDescriptor.getListSize(owner);
      // A whole-entity pin takes precedence over a pin-to-index, including its validation.
      var firstUnpinned =
          ownerMovable[ownerId] ? variableDescriptor.getFirstUnpinnedIndex(owner) : listSize;
      if (firstUnpinned < 0 || firstUnpinned > listSize) {
        throw new IllegalArgumentException(
            "The genetic algorithm list variable (%s) on owner (%s) has pinned index (%d) outside its list size (%d)."
                .formatted(name(), owner, firstUnpinned, listSize));
      }
      firstUnpinnedIndices[ownerId] = firstUnpinned;
    }
    initialLists = captureLists();
    if (!isValid(initialLists)) {
      throw new IllegalArgumentException(
          "The genetic algorithm requires valid initial list assignments for (%s): values must be unique, in their owner's range and assigned when unassignment is disabled."
              .formatted(name()));
    }
    var pinned = new boolean[values.length];
    for (var ownerId = 0; ownerId < owners.size(); ownerId++) {
      for (var index = 0; index < firstUnpinnedIndices[ownerId]; index++) {
        pinned[initialLists[ownerId][index]] = true;
      }
    }
    var movableIds = new int[values.length];
    var movableCount = 0;
    for (var id = 0; id < values.length; id++) {
      if (!pinned[id]) {
        movableIds[movableCount++] = id;
      }
    }
    movableValueIds = Arrays.copyOf(movableIds, movableCount);
  }

  public int ownerCount() {
    return owners.size();
  }

  public int valueCount() {
    return values.length;
  }

  public boolean ownerMovable(int owner) {
    return ownerMovable[owner];
  }

  public int firstUnpinnedIndex(int owner) {
    return firstUnpinnedIndices[owner];
  }

  public boolean accepts(int owner, int valueId) {
    return valueId >= 0 && valueId < values.length && ranges.get(owner).contains(values[valueId]);
  }

  public boolean allowsUnassignedValues() {
    return variableDescriptor.allowsUnassignedValues();
  }

  public int[] movableValueIds() {
    return movableValueIds.clone();
  }

  public int[][] initialLists() {
    var copy = new int[initialLists.length][];
    for (var i = 0; i < copy.length; i++) {
      copy[i] = initialLists[i].clone();
    }
    return copy;
  }

  public String name() {
    return variableDescriptor.getSimpleEntityAndVariableName();
  }

  public Object value(int id) {
    return values[id];
  }

  public Object owner(int id) {
    return owners.get(id);
  }

  public int[][] captureLists() {
    var lists = new int[owners.size()][];
    for (var ownerId = 0; ownerId < owners.size(); ownerId++) {
      var owner = owners.get(ownerId);
      var current = variableDescriptor.getValue(owner);
      var ids = new int[current.size()];
      for (var index = 0; index < current.size(); index++) {
        var value = current.get(index);
        var id = valueIds.get(value);
        if (id == null) {
          throw new IllegalArgumentException(
              "The genetic algorithm list variable (%s) on owner (%s) contains value (%s) outside its canonical value range. Use the same value instances in the lists and value range."
                  .formatted(name(), owner, value));
        }
        ids[index] = id;
      }
      lists[ownerId] = ids;
    }
    return lists;
  }

  public boolean isValid(int[][] lists) {
    if (lists.length != owners.size()) {
      return false;
    }
    var assigned = new boolean[values.length];
    var assignedCount = 0;
    for (var ownerId = 0; ownerId < owners.size(); ownerId++) {
      var list = lists[ownerId];
      if (list == null
          || list.length < firstUnpinnedIndices[ownerId]
          || !ownerMovable[ownerId] && !Arrays.equals(list, initialLists[ownerId])) {
        return false;
      }
      for (var index = 0; index < list.length; index++) {
        var id = list[index];
        if (!accepts(ownerId, id)
            || assigned[id]
            || index < firstUnpinnedIndices[ownerId] && id != initialLists[ownerId][index]) {
          return false;
        }
        assigned[id] = true;
        assignedCount++;
      }
    }
    return allowsUnassignedValues() || assignedCount == values.length;
  }

  ListVariableDescriptor<Solution_> variableDescriptor() {
    return variableDescriptor;
  }
}
