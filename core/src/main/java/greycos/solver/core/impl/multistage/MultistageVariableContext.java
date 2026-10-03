package greycos.solver.core.impl.multistage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.impl.cotwin.solution.cloner.DeepCloningUtils;
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.preview.api.cotwin.metamodel.PositionInList;

/** Candidate-local access to one variable within its declared entity scope. */
final class MultistageVariableContext<Solution_> {
  final MultistageTransaction<Solution_, ?> transaction;
  final GenuineVariableDescriptor<Solution_> variable;
  final List<Object> entities;
  private final MultistageDomain<Solution_> domain;
  private final InnerScoreDirector<Solution_, ?> director;

  MultistageVariableContext(
      MultistageTransaction<Solution_, ?> transaction,
      GenuineVariableDescriptor<Solution_> variable,
      MultistageDomain<Solution_> domain) {
    this.transaction = transaction;
    this.variable = variable;
    this.domain = domain;
    director = transaction.director;
    entities = domain.entities;
  }

  void requireEntity(Object entity) {
    transaction.checkActive();
    if (!domain.entityMembership.containsKey(entity)) {
      throw new IllegalArgumentException(
          "Entity (" + entity + ") does not belong to multistage variable (" + variable + ").");
    }
  }

  boolean movable(Object entity) {
    requireEntity(entity);
    // A variable may be declared on a base entity while pinning is declared on its subtype.
    return director
        .getSolutionDescriptor()
        .findEntityDescriptorOrFail(entity.getClass())
        .isMovable(director.getWorkingSolution(), entity);
  }

  int firstUnpinnedIndex(Object entity) {
    var reader =
        director
            .getSolutionDescriptor()
            .findEntityDescriptorOrFail(entity.getClass())
            .getEffectivePlanningPinToIndexReader();
    return reader == null ? 0 : reader.applyAsInt(entity);
  }

  void requireMovable(Object entity) {
    if (!movable(entity))
      throw new IllegalArgumentException(
          "Multistage operation cannot change pinned entity (" + entity + ").");
  }

  ValueRange<Object> range(Object entity) {
    var descriptor = variable.getValueRangeDescriptor();
    return descriptor.canExtractValueRangeFromSolution()
        ? director.getValueRangeManager().getFromSolution(descriptor)
        : director.getValueRangeManager().getFromEntity(descriptor, entity);
  }

  void requireValue(Object entity, Object value, boolean allowUnassigned) {
    transaction.checkActive();
    if (value == null && allowUnassigned) return;
    if (value != null
        && !DeepCloningUtils.isImmutable(value.getClass())
        && !domain.containsValue(value, variable, director, transaction::checkpoint)) {
      throw new IllegalArgumentException(
          "Value (" + value + ") is not owned by this multistage working solution.");
    }
    if (!range(entity).contains(value))
      throw new IllegalArgumentException(
          "Value (" + value + ") is outside the range of multistage entity (" + entity + ").");
  }

  ListVariableDescriptor<Solution_> listVariable() {
    return (ListVariableDescriptor<Solution_>) variable;
  }

  PositionInList position(Object value) {
    transaction.checkActive();
    Objects.requireNonNull(value, "A list value must not be null.");
    var position = director.getListVariableState(listVariable()).getElementPosition(value);
    if (position instanceof PositionInList assigned) {
      requireEntity(assigned.entity());
      return assigned;
    }
    return null;
  }

  void requireKnownListValue(Object value) {
    transaction.checkActive();
    Objects.requireNonNull(value, "A list value must not be null.");
    if (domain.containsValue(value, variable, director, transaction::checkpoint)) return;
    throw new IllegalArgumentException(
        "List value (" + value + ") is outside this working solution's value ranges.");
  }

  void requireRange(Object entity, int from, int to) {
    requireMovable(entity);
    int size = listVariable().getListSize(entity);
    if (from < firstUnpinnedIndex(entity) || to < from || to > size) {
      throw new IllegalArgumentException(
          "Invalid or pinned multistage list range ["
              + from
              + ", "
              + to
              + ") on entity ("
              + entity
              + ") with size ("
              + size
              + ").");
    }
  }

  private void requireDestination(Object entity, int index, int sizeAfterRemoval) {
    requireMovable(entity);
    if (index < firstUnpinnedIndex(entity) || index > sizeAfterRemoval) {
      throw new IllegalArgumentException(
          "Invalid or pinned multistage insertion index ("
              + index
              + ") on entity ("
              + entity
              + ") with resulting size ("
              + sizeAfterRemoval
              + ").");
    }
  }

  List<ResolvedMutation<Solution_>> resolve(MultistageOperationImpl.Intent<Solution_> intent) {
    var first = intent.first();
    var second = intent.second();
    return switch (intent.kind()) {
      case ASSIGN, UNASSIGN_BASIC -> {
        requireMovable(first);
        var value = intent.kind() == MultistageOperationImpl.Kind.UNASSIGN_BASIC ? null : second;
        boolean unassign = intent.kind() == MultistageOperationImpl.Kind.UNASSIGN_BASIC;
        if (value == null
            && !unassign
            && !((BasicVariableDescriptor<Solution_>) variable).allowsUnassigned()) {
          throw new IllegalArgumentException(
              "Mandatory variable ("
                  + variable
                  + ") must use unassign() for a temporary unassignment.");
        }
        requireValue(first, value, unassign);
        yield Objects.equals(variable.getValue(first), value)
            ? List.of()
            : List.of(ResolvedMutation.basic(variable, first, value));
      }
      case SWAP_BASIC -> {
        requireMovable(first);
        requireMovable(second);
        var left = variable.getValue(first);
        var right = variable.getValue(second);
        requireValue(first, right, false);
        requireValue(second, left, false);
        yield Objects.equals(left, right)
            ? List.of()
            : List.of(
                ResolvedMutation.basic(variable, first, right),
                ResolvedMutation.basic(variable, second, left));
      }
      case PLACE -> place(first, second, intent.index());
      case UNASSIGN_LIST -> {
        requireKnownListValue(first);
        var source = position(first);
        if (source == null) yield List.of();
        requireRange(source.entity(), source.index(), source.index() + 1);
        yield List.of(
            splice(source.entity(), source.index(), 1, List.of(), List.of(first), List.of()));
      }
      case SWAP_LIST -> swapList(first, second);
      case REVERSE -> {
        requireRange(first, intent.from(), intent.to());
        var values =
            new ArrayList<>(listVariable().getValue(first).subList(intent.from(), intent.to()));
        Collections.reverse(values);
        yield values.size() < 2
            ? List.of()
            : List.of(splice(first, intent.from(), values.size(), values));
      }
      case RELOCATE -> relocate(first, intent.from(), intent.to(), second, intent.index());
    };
  }

  private List<ResolvedMutation<Solution_>> place(Object value, Object destination, int index) {
    requireKnownListValue(value);
    requireEntity(destination);
    requireValue(destination, value, false);
    var source = position(value);
    requireDestination(
        destination,
        index,
        listVariable().getListSize(destination)
            - (source != null && source.entity() == destination ? 1 : 0));
    if (source == null)
      return List.of(splice(destination, index, 0, List.of(value), List.of(), List.of(value)));
    requireRange(source.entity(), source.index(), source.index() + 1);
    return relocate(source.entity(), source.index(), source.index() + 1, destination, index);
  }

  private List<ResolvedMutation<Solution_>> relocate(
      Object source, int from, int to, Object destination, int index) {
    requireRange(source, from, to);
    requireEntity(destination);
    int length = to - from;
    requireDestination(
        destination,
        index,
        listVariable().getListSize(destination) - (source == destination ? length : 0));
    var values = List.copyOf(listVariable().getValue(source).subList(from, to));
    for (var value : values) requireValue(destination, value, false);
    if (length == 0 || source == destination && index == from) return List.of();
    if (source == destination) {
      int start = Math.min(from, index);
      int end = Math.max(to, index + length);
      var replacement = new ArrayList<>(listVariable().getValue(source).subList(start, end));
      replacement.subList(from - start, to - start).clear();
      replacement.addAll(index - start, values);
      return List.of(splice(source, start, end - start, replacement));
    }
    return List.of(splice(source, from, length, List.of()), splice(destination, index, 0, values));
  }

  private List<ResolvedMutation<Solution_>> swapList(Object leftValue, Object rightValue) {
    requireKnownListValue(leftValue);
    requireKnownListValue(rightValue);
    var left = position(leftValue);
    var right = position(rightValue);
    if (left == null || right == null)
      throw new IllegalArgumentException("Multistage swap requires two assigned list values.");
    requireRange(left.entity(), left.index(), left.index() + 1);
    requireRange(right.entity(), right.index(), right.index() + 1);
    requireValue(left.entity(), rightValue, false);
    requireValue(right.entity(), leftValue, false);
    if (leftValue == rightValue) return List.of();
    if (left.entity() == right.entity()) {
      int from = Math.min(left.index(), right.index());
      int to = Math.max(left.index(), right.index()) + 1;
      var values = new ArrayList<>(listVariable().getValue(left.entity()).subList(from, to));
      Collections.swap(values, left.index() - from, right.index() - from);
      return List.of(splice(left.entity(), from, to - from, values));
    }
    return List.of(
        splice(left.entity(), left.index(), 1, List.of(rightValue)),
        splice(right.entity(), right.index(), 1, List.of(leftValue)));
  }

  private ResolvedMutation<Solution_> splice(
      Object entity, int from, int removed, List<Object> inserted) {
    return splice(entity, from, removed, inserted, List.of(), List.of());
  }

  private ResolvedMutation<Solution_> splice(
      Object entity,
      int from,
      int removed,
      List<Object> inserted,
      List<Object> unassigned,
      List<Object> assigned) {
    return ResolvedMutation.splice(
        listVariable(), entity, from, removed, inserted, unassigned, assigned);
  }
}
