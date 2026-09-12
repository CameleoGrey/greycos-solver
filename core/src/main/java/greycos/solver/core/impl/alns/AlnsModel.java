package greycos.solver.core.impl.alns;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.api.solver.alns.AlnsAssignment;
import greycos.solver.core.api.solver.alns.AlnsBasicVariable;
import greycos.solver.core.api.solver.alns.AlnsListVariable;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.api.solver.alns.AlnsVariable;
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.preview.api.cotwin.metamodel.PositionInList;
import greycos.solver.core.preview.api.cotwin.metamodel.UnassignedElement;

/** Resolves stable ALNS handles and enforces entity ranges and pinning across mixed models. */
final class AlnsModel<Solution_> {
  private final InnerScoreDirector<Solution_, ?> scoreDirector;
  private final Map<AlnsVariable<Solution_>, GenuineVariableDescriptor<Solution_>> variables =
      new LinkedHashMap<>();
  private final Map<GenuineVariableDescriptor<Solution_>, List<Object>> entities =
      new IdentityHashMap<>();
  private final Map<GenuineVariableDescriptor<Solution_>, Set<Object>> entitySets =
      new IdentityHashMap<>();
  private final Map<GenuineVariableDescriptor<Solution_>, Set<Object>> listValues =
      new IdentityHashMap<>();
  private final Map<GenuineVariableDescriptor<Solution_>, List<Object>> orderedListValues =
      new IdentityHashMap<>();
  private final Runnable checkpoint;

  @SuppressWarnings({"rawtypes", "unchecked"})
  AlnsModel(InnerScoreDirector<Solution_, ?> scoreDirector, Runnable checkpoint) {
    this.scoreDirector = scoreDirector;
    this.checkpoint = checkpoint;
    for (var entityDescriptor :
        scoreDirector.getSolutionDescriptor().getGenuineEntityDescriptors()) {
      for (var descriptor : entityDescriptor.getGenuineVariableDescriptorList()) {
        if (entities.containsKey(descriptor))
          continue; // Inherited bindings share their descriptor.
        var declaringEntity = descriptor.getEntityDescriptor();
        AlnsVariable<Solution_> handle;
        if (descriptor instanceof ListVariableDescriptor<Solution_> list) {
          handle =
              new AlnsListVariable(
                  declaringEntity.getEntityClass(),
                  descriptor.getVariableName(),
                  list.getElementType(),
                  list.allowsUnassignedValues());
          scoreDirector.getListVariableStateSupply(list);
        } else {
          var basic = (BasicVariableDescriptor<Solution_>) descriptor;
          handle =
              new AlnsBasicVariable(
                  declaringEntity.getEntityClass(),
                  descriptor.getVariableName(),
                  basic.getVariablePropertyType(),
                  basic.allowsUnassigned());
        }
        variables.put(handle, descriptor);
        var entityList = declaringEntity.extractEntities(scoreDirector.getWorkingSolution());
        entities.put(descriptor, entityList);
        Set<Object> entitySet = Collections.newSetFromMap(new IdentityHashMap<>());
        entitySet.addAll(entityList);
        entitySets.put(descriptor, entitySet);
        if (descriptor instanceof ListVariableDescriptor) {
          Set<Object> values = Collections.newSetFromMap(new IdentityHashMap<>());
          var orderedValues = new ArrayList<Object>();
          if (descriptor.getValueRangeDescriptor().canExtractValueRangeFromSolution()) {
            collectValues(range(descriptor, null), values, orderedValues);
          } else {
            for (var entity : entityList) {
              collectValues(range(descriptor, entity), values, orderedValues);
            }
          }
          listValues.put(descriptor, values);
          orderedListValues.put(descriptor, List.copyOf(orderedValues));
        }
      }
    }
  }

  List<AlnsVariable<Solution_>> variables() {
    return List.copyOf(variables.keySet());
  }

  GenuineVariableDescriptor<Solution_> descriptor(AlnsTarget<Solution_> target) {
    var descriptor = variables.get(target.variable());
    if (descriptor == null)
      throw new IllegalArgumentException("Unknown ALNS variable: " + target.variable());
    if (!target.isList() && !entitySets.get(descriptor).contains(target.entity())) {
      throw new IllegalArgumentException(
          "ALNS target belongs to a different working solution: " + target);
    }
    if (target.isList() && !listValues.get(descriptor).contains(target.value())) {
      throw new IllegalArgumentException(
          "ALNS list target belongs to a different working solution or value range: " + target);
    }
    return descriptor;
  }

  AlnsAssignment<Solution_> current(AlnsTarget<Solution_> target) {
    var descriptor = descriptor(target);
    if (descriptor instanceof ListVariableDescriptor<Solution_> list) {
      var position =
          scoreDirector.getListVariableStateSupply(list).getElementPosition(target.value());
      return position instanceof PositionInList assigned
          ? new AlnsAssignment<>(target, assigned.entity(), target.value(), assigned.index())
          : new AlnsAssignment<>(target, null, target.value(), -1);
    }
    return new AlnsAssignment<>(target, target.entity(), descriptor.getValue(target.entity()), -1);
  }

  List<AlnsTarget<Solution_>> targets(boolean unassigned) {
    var result = new ArrayList<AlnsTarget<Solution_>>();
    for (var entry : variables.entrySet()) {
      checkpoint.run();
      var handle = entry.getKey();
      var descriptor = entry.getValue();
      if (descriptor instanceof ListVariableDescriptor<Solution_> list) {
        if (unassigned) {
          var stateSupply = scoreDirector.getListVariableStateSupply(list);
          if (stateSupply.getUnassignedCount() == 0) continue;
          if (descriptor.getValueRangeDescriptor().canExtractValueRangeFromSolution()) {
            boolean hasMovableDestination = false;
            for (var entity : entities.get(descriptor)) {
              checkpoint.run();
              if (movable(descriptor, entity)) {
                hasMovableDestination = true;
                break;
              }
            }
            if (!hasMovableDestination) continue;
            for (var value : orderedListValues.get(descriptor)) {
              checkpoint.run();
              if (stateSupply.getElementPosition(value) instanceof UnassignedElement) {
                result.add(new AlnsTarget<>(handle, null, value));
              }
            }
            continue;
          }
          Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
          for (var entity : entities.get(descriptor)) {
            if (!movable(descriptor, entity)) continue;
            var iterator = range(descriptor, entity).createOriginalIterator();
            while (iterator.hasNext()) {
              checkpoint.run();
              Object value = iterator.next();
              if (value != null
                  && seen.add(value)
                  && stateSupply.getElementPosition(value) instanceof UnassignedElement) {
                result.add(new AlnsTarget<>(handle, null, value));
              }
            }
          }
        } else {
          for (var entity : entities.get(descriptor)) {
            checkpoint.run();
            if (!movable(descriptor, entity)) continue;
            var values = list.getValue(entity);
            for (int i = firstUnpinnedIndex(entity); i < values.size(); i++) {
              checkpoint.run();
              result.add(new AlnsTarget<>(handle, entity, values.get(i)));
            }
          }
        }
      } else {
        for (var entity : entities.get(descriptor)) {
          checkpoint.run();
          Object value = descriptor.getValue(entity);
          if (movable(descriptor, entity) && (value == null) == unassigned) {
            result.add(new AlnsTarget<>(handle, entity, value));
          }
        }
      }
    }
    return List.copyOf(result);
  }

  List<AlnsAssignment<Solution_>> assignments(AlnsTarget<Solution_> target) {
    var descriptor = descriptor(target);
    var current = current(target);
    assertMovable(target, current);
    var result = new ArrayList<AlnsAssignment<Solution_>>();
    if (descriptor instanceof ListVariableDescriptor<Solution_> list) {
      for (var entity : entities.get(descriptor)) {
        checkpoint.run();
        if (!movable(descriptor, entity) || !range(descriptor, entity).contains(target.value()))
          continue;
        int sizeAfterRemoval = list.getListSize(entity) - (current.entity() == entity ? 1 : 0);
        for (int index = firstUnpinnedIndex(entity); index <= sizeAfterRemoval; index++) {
          checkpoint.run();
          result.add(new AlnsAssignment<>(target, entity, target.value(), index));
        }
      }
      if (list.allowsUnassignedValues())
        result.add(new AlnsAssignment<>(target, null, target.value(), -1));
    } else {
      var iterator = range(descriptor, target.entity()).createOriginalIterator();
      boolean sawNull = false;
      while (iterator.hasNext()) {
        checkpoint.run();
        Object value = iterator.next();
        if (value == null) {
          if (!target.variable().allowsUnassigned() || sawNull) continue;
          sawNull = true;
        }
        result.add(new AlnsAssignment<>(target, target.entity(), value, -1));
      }
      if (target.variable().allowsUnassigned() && !sawNull) {
        result.add(new AlnsAssignment<>(target, target.entity(), null, -1));
      }
    }
    return List.copyOf(result);
  }

  void validateAssignment(AlnsAssignment<Solution_> assignment) {
    var target = assignment.target();
    var descriptor = descriptor(target);
    var current = current(target);
    assertMovable(target, current);
    if (target.isList() && assignment.value() != target.value()) {
      throw new IllegalArgumentException("ALNS list assignment changes the target identity.");
    }
    if (!target.isList() && (assignment.entity() != target.entity() || assignment.index() != -1)) {
      throw new IllegalArgumentException("ALNS basic assignment changes the target identity.");
    }
    if (assignment.isUnassigned()) {
      if (!target.variable().allowsUnassigned())
        throw new IllegalArgumentException(
            "Mandatory ALNS target cannot remain unassigned: " + target);
      if (assignment.index() != -1)
        throw new IllegalArgumentException("Unassigned index must be -1.");
      return;
    }
    if (!entitySets.get(descriptor).contains(assignment.entity())
        || !movable(descriptor, assignment.entity())) {
      throw new IllegalArgumentException(
          "Foreign or pinned ALNS destination: " + assignment.entity());
    }
    if (descriptor instanceof ListVariableDescriptor<Solution_> list) {
      if (assignment.value() != target.value()
          || !range(descriptor, assignment.entity()).contains(target.value())) {
        throw new IllegalArgumentException("ALNS list value is outside the destination range.");
      }
      int sizeAfterRemoval =
          list.getListSize(assignment.entity()) - (current.entity() == assignment.entity() ? 1 : 0);
      if (assignment.index() < firstUnpinnedIndex(assignment.entity())
          || assignment.index() > sizeAfterRemoval) {
        throw new IllegalArgumentException("ALNS insertion index is pinned or outside the list.");
      }
    } else if (assignment.entity() != target.entity()
        || assignment.index() != -1
        || !range(descriptor, target.entity()).contains(assignment.value())) {
      throw new IllegalArgumentException(
          "ALNS basic assignment does not belong to its entity range.");
    }
  }

  void assertMovable(AlnsTarget<Solution_> target, AlnsAssignment<Solution_> current) {
    var descriptor = descriptor(target);
    if (descriptor instanceof ListVariableDescriptor<Solution_> list) {
      if (!current.isUnassigned()
          && (!movable(descriptor, current.entity())
              || current.index() < firstUnpinnedIndex(current.entity()))) {
        throw new IllegalArgumentException("Cannot change a pinned ALNS list target: " + target);
      }
    } else if (!movable(descriptor, target.entity())) {
      throw new IllegalArgumentException("Cannot change a pinned ALNS basic target: " + target);
    }
  }

  private boolean movable(GenuineVariableDescriptor<Solution_> descriptor, Object entity) {
    return scoreDirector
        .getSolutionDescriptor()
        .findEntityDescriptorOrFail(entity.getClass())
        .isMovable(scoreDirector.getWorkingSolution(), entity);
  }

  private int firstUnpinnedIndex(Object entity) {
    var reader =
        scoreDirector
            .getSolutionDescriptor()
            .findEntityDescriptorOrFail(entity.getClass())
            .getEffectivePlanningPinToIndexReader();
    return reader == null ? 0 : reader.applyAsInt(entity);
  }

  private ValueRange<Object> range(GenuineVariableDescriptor<Solution_> descriptor, Object entity) {
    var rangeDescriptor = descriptor.getValueRangeDescriptor();
    return rangeDescriptor.canExtractValueRangeFromSolution()
        ? scoreDirector.getValueRangeManager().getFromSolution(rangeDescriptor)
        : scoreDirector.getValueRangeManager().getFromEntity(rangeDescriptor, entity);
  }

  private void collectValues(
      ValueRange<Object> range, Set<Object> values, List<Object> orderedValues) {
    var iterator = range.createOriginalIterator();
    while (iterator.hasNext()) {
      checkpoint.run();
      Object value = iterator.next();
      if (value != null && values.add(value)) orderedValues.add(value);
    }
  }
}
