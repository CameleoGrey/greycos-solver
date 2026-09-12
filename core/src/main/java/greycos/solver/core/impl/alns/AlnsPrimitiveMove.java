package greycos.solver.core.impl.alns;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import greycos.solver.core.api.cotwin.lookup.Lookup;
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import greycos.solver.core.impl.move.InnerMutableSolutionView;
import greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.MutableSolutionView;

import org.jspecify.annotations.Nullable;

/** A fully resolved, immutable primitive shared by coordinator mutations and worker probes. */
final class AlnsPrimitiveMove<Solution_> implements Move<Solution_> {

  private static final Move<?> NO_OP = new SequenceMove<>(List.of());

  private enum Kind {
    BASIC,
    REMOVE,
    INSERT
  }

  private final Kind kind;
  private final VariableDescriptor<Solution_> descriptor;
  private final Object entity;
  private final int index;
  private final @Nullable Object value;

  private AlnsPrimitiveMove(
      Kind kind,
      VariableDescriptor<Solution_> descriptor,
      Object entity,
      int index,
      @Nullable Object value) {
    this.kind = kind;
    this.descriptor = Objects.requireNonNull(descriptor);
    this.entity = Objects.requireNonNull(entity);
    this.index = index;
    this.value = value;
  }

  static <Solution_> AlnsPrimitiveMove<Solution_> basic(
      BasicVariableDescriptor<Solution_> descriptor, Object entity, @Nullable Object value) {
    return new AlnsPrimitiveMove<>(Kind.BASIC, descriptor, entity, -1, value);
  }

  static <Solution_> AlnsPrimitiveMove<Solution_> remove(
      ListVariableDescriptor<Solution_> descriptor,
      Object entity,
      int index,
      Object expectedValue) {
    return new AlnsPrimitiveMove<>(
        Kind.REMOVE, descriptor, entity, index, Objects.requireNonNull(expectedValue));
  }

  static <Solution_> AlnsPrimitiveMove<Solution_> insert(
      ListVariableDescriptor<Solution_> descriptor, Object entity, int index, Object value) {
    return new AlnsPrimitiveMove<>(
        Kind.INSERT, descriptor, entity, index, Objects.requireNonNull(value));
  }

  @SuppressWarnings("unchecked")
  static <Solution_> Move<Solution_> composite(List<? extends Move<Solution_>> moves) {
    return switch (moves.size()) {
      case 0 -> (Move<Solution_>) NO_OP;
      case 1 -> Objects.requireNonNull(moves.getFirst());
      default -> new SequenceMove<>(List.copyOf(moves));
    };
  }

  /** Visits only the primitive probes accepted by the ALNS batch API, never arbitrary callbacks. */
  static <Solution_> void forEachPrimitive(
      Move<Solution_> move, Consumer<AlnsPrimitiveMove<Solution_>> consumer) {
    if (move instanceof AlnsPrimitiveMove<Solution_> primitive) {
      consumer.accept(primitive);
    } else if (move instanceof SequenceMove<Solution_> sequence) {
      for (var child : sequence.moves()) {
        forEachPrimitive(child, consumer);
      }
    } else {
      throw new IllegalArgumentException("An ALNS primitive probe contains an unsupported move.");
    }
  }

  VariableDescriptor<Solution_> descriptor() {
    return descriptor;
  }

  Object entity() {
    return entity;
  }

  /** Emits one balanced primitive; the caller flushes shadows immediately afterwards. */
  void apply(VariableDescriptorAwareScoreDirector<Solution_> director) {
    switch (kind) {
      case BASIC -> {
        director.beforeVariableChanged(descriptor, entity);
        descriptor.setValue(entity, value);
        director.afterVariableChanged(descriptor, entity);
      }
      case REMOVE -> {
        var list = (ListVariableDescriptor<Solution_>) descriptor;
        director.beforeListVariableElementUnassigned(list, value);
        director.beforeListVariableChanged(list, entity, index, index + 1);
        Object removed = list.removeElement(entity, index);
        if (removed != value) {
          throw new IllegalStateException("ALNS list position changed unexpectedly.");
        }
        director.afterListVariableChanged(list, entity, index, index);
        director.afterListVariableElementUnassigned(list, value);
      }
      case INSERT -> {
        var list = (ListVariableDescriptor<Solution_>) descriptor;
        director.beforeListVariableElementAssigned(list, value);
        director.beforeListVariableChanged(list, entity, index, index);
        list.addElement(entity, index, value);
        director.afterListVariableChanged(list, entity, index, index + 1);
        director.afterListVariableElementAssigned(list, value);
      }
    }
  }

  @Override
  public void execute(MutableSolutionView<Solution_> solutionView) {
    var director = ((InnerMutableSolutionView<Solution_>) solutionView).getScoreDirector();
    apply(director);
    director.updateShadowVariables();
  }

  @Override
  public Move<Solution_> rebase(Lookup lookup) {
    return new AlnsPrimitiveMove<>(
        kind,
        descriptor,
        Objects.requireNonNull(lookup.lookUpWorkingObject(entity)),
        index,
        value == null ? null : Objects.requireNonNull(lookup.lookUpWorkingObject(value)));
  }

  @Override
  public String toString() {
    return "ALNS " + kind + "(" + descriptor.getVariableName() + ", " + index + ")";
  }

  private record SequenceMove<Solution_>(List<? extends Move<Solution_>> moves)
      implements Move<Solution_> {

    @Override
    public void execute(MutableSolutionView<Solution_> solutionView) {
      for (var move : moves) {
        move.execute(solutionView);
        // Recorded undo moves also need the same flush boundary as transaction.rollback().
        if (!(move instanceof AlnsPrimitiveMove<?>) && !(move instanceof SequenceMove<?>)) {
          ((InnerMutableSolutionView<Solution_>) solutionView)
              .getScoreDirector()
              .updateShadowVariables();
        }
      }
    }

    @Override
    public Move<Solution_> rebase(Lookup lookup) {
      return moves.isEmpty()
          ? this
          : new SequenceMove<>(moves.stream().map(move -> move.rebase(lookup)).toList());
    }
  }
}
