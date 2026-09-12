package greycos.solver.core.impl.alns;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import greycos.solver.core.api.cotwin.lookup.Lookup;
import greycos.solver.core.api.solver.alns.AlnsAssignment;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationSource;
import greycos.solver.core.preview.api.move.Move;

/** Immutable alternatives at one baseline; list positions are represented by destination spans. */
final class AlnsPreparedAssignments<Solution_> implements MoveEvaluationSource<Solution_> {
  record Destination(Object entity, int firstIndex, int count) {}

  private final AlnsTarget<Solution_> target;
  private final GenuineVariableDescriptor<Solution_> descriptor;
  private final AlnsAssignment<Solution_> current;
  private final List<Destination> destinations;
  private final List<Object> basicValues;
  private final int size;
  private final boolean lazyProbeRebasing;

  AlnsPreparedAssignments(
      AlnsTarget<Solution_> target,
      GenuineVariableDescriptor<Solution_> descriptor,
      AlnsAssignment<Solution_> current,
      List<Destination> destinations,
      List<Object> basicValues) {
    this(
        target,
        descriptor,
        current,
        destinations,
        basicValues,
        Boolean.parseBoolean(System.getProperty("greycos.solver.alns.lazyProbeRebasing", "true")));
  }

  private AlnsPreparedAssignments(
      AlnsTarget<Solution_> target,
      GenuineVariableDescriptor<Solution_> descriptor,
      AlnsAssignment<Solution_> current,
      List<Destination> destinations,
      List<Object> basicValues,
      boolean lazyProbeRebasing) {
    this.target = target;
    this.descriptor = descriptor;
    this.current = current;
    this.lazyProbeRebasing = lazyProbeRebasing;
    this.destinations = List.copyOf(destinations);
    // A basic range may contain null, representing optional unassignment.
    this.basicValues = Collections.unmodifiableList(new ArrayList<>(basicValues));
    int count = basicValues.size();
    for (var destination : destinations) count = Math.addExact(count, destination.count());
    this.size = count;
  }

  @Override
  public int size() {
    return size;
  }

  AlnsAssignment<Solution_> assignment(int ordinal) {
    Objects.checkIndex(ordinal, size);
    if (!target.isList()) {
      return new AlnsAssignment<>(target, target.entity(), basicValues.get(ordinal), -1);
    }
    for (var destination : destinations) {
      if (ordinal < destination.count()) {
        return new AlnsAssignment<>(
            target, destination.entity(), target.value(), destination.firstIndex() + ordinal);
      }
      ordinal -= destination.count();
    }
    throw new IllegalStateException("Missing prepared ALNS destination.");
  }

  @Override
  public Move<Solution_> move(int ordinal) {
    Objects.checkIndex(ordinal, size);
    if (descriptor instanceof BasicVariableDescriptor<Solution_> basic) {
      Object value = basicValues.get(ordinal);
      return Objects.equals(current.value(), value)
          ? AlnsPrimitiveMove.composite(List.of())
          : AlnsPrimitiveMove.basic(basic, target.entity(), value);
    }
    for (var destination : destinations) {
      if (ordinal < destination.count()) {
        int index = destination.firstIndex() + ordinal;
        Object entity = destination.entity();
        if (current.entity() == entity && current.index() == index) {
          return AlnsPrimitiveMove.composite(List.of());
        }
        var list = (ListVariableDescriptor<Solution_>) descriptor;
        var removal =
            current.isUnassigned()
                ? null
                : AlnsPrimitiveMove.remove(list, current.entity(), current.index(), target.value());
        var insertion =
            entity == null ? null : AlnsPrimitiveMove.insert(list, entity, index, target.value());
        if (removal == null)
          return insertion == null ? AlnsPrimitiveMove.composite(List.of()) : insertion;
        return insertion == null
            ? removal
            : AlnsPrimitiveMove.composite(List.of(removal, insertion));
      }
      ordinal -= destination.count();
    }
    throw new IllegalStateException("Missing prepared ALNS destination.");
  }

  @Override
  public MoveEvaluationSource<Solution_> rebase(Lookup lookup) {
    if (lazyProbeRebasing && descriptor instanceof BasicVariableDescriptor<Solution_> basic) {
      return new RebasedBasicSource(basic, lookup);
    }
    return rebaseEager(lookup);
  }

  boolean usesLazyRebasing() {
    return lazyProbeRebasing;
  }

  AlnsPreparedAssignments<Solution_> rebaseEager(Lookup lookup) {
    var rebasedTarget =
        new AlnsTarget<Solution_>(
            target.variable(), rebase(lookup, target.entity()), rebase(lookup, target.value()));
    var rebasedCurrent =
        new AlnsAssignment<Solution_>(
            rebasedTarget,
            rebase(lookup, current.entity()),
            rebase(lookup, current.value()),
            current.index());
    var rebasedDestinations = new ArrayList<Destination>(destinations.size());
    for (var destination : destinations) {
      rebasedDestinations.add(
          new Destination(
              rebase(lookup, destination.entity()), destination.firstIndex(), destination.count()));
    }
    var rebasedValues = new ArrayList<Object>(basicValues.size());
    for (var value : basicValues) rebasedValues.add(rebase(lookup, value));
    return new AlnsPreparedAssignments<>(
        rebasedTarget,
        descriptor,
        rebasedCurrent,
        rebasedDestinations,
        rebasedValues,
        lazyProbeRebasing);
  }

  /** Original range references are immutable lookup payloads; only rebased values enter moves. */
  private final class RebasedBasicSource implements MoveEvaluationSource<Solution_> {
    private final BasicVariableDescriptor<Solution_> basic;
    private final Lookup lookup;
    private final Object entity;
    private final Object currentValue;

    private RebasedBasicSource(BasicVariableDescriptor<Solution_> basic, Lookup lookup) {
      this.basic = basic;
      this.lookup = Objects.requireNonNull(lookup);
      entity = AlnsPreparedAssignments.rebase(lookup, target.entity());
      currentValue = AlnsPreparedAssignments.rebase(lookup, current.value());
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    public Move<Solution_> move(int ordinal) {
      Objects.checkIndex(ordinal, size);
      var value = AlnsPreparedAssignments.rebase(lookup, basicValues.get(ordinal));
      return Objects.equals(currentValue, value)
          ? AlnsPrimitiveMove.composite(List.of())
          : AlnsPrimitiveMove.basic(basic, entity, value);
    }

    @Override
    public MoveEvaluationSource<Solution_> rebase(Lookup otherLookup) {
      return AlnsPreparedAssignments.this.rebase(otherLookup);
    }
  }

  private static Object rebase(Lookup lookup, Object value) {
    return value == null ? null : Objects.requireNonNull(lookup.lookUpWorkingObject(value));
  }
}
