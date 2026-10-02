package greycos.solver.core.impl.multistage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;

import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.multistage.MultistageEvaluation;
import greycos.solver.core.impl.cotwin.solution.cloner.DeepCloningUtils;
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.move.VariableChangeRecordingScoreDirector;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.preview.api.cotwin.metamodel.PositionInList;

/** One candidate's journal, with balanced undo and snapshots for exceptional recovery. */
final class MultistageTransaction<Solution_, Score_ extends Score<Score_>> {
  final InnerScoreDirector<Solution_, Score_> director;
  final GenuineVariableDescriptor<Solution_> variable;
  final List<Object> entities;
  private final MultistageDomain<Solution_> domain;
  private final Map<Object, Object> snapshots = new IdentityHashMap<>();
  private final List<VariableChangeRecordingScoreDirector<Solution_, Score_>> undo =
      new ArrayList<>();
  private final List<List<ResolvedMutation<Solution_>>> forward = new ArrayList<>();
  private final Runnable termination;
  private final long probeLimit;
  private final Score_ originalScore;
  private long probes;
  private boolean active = true;
  private boolean budgetExhausted;
  private CancellationException cancellation;

  MultistageTransaction(
      InnerScoreDirector<Solution_, Score_> director,
      GenuineVariableDescriptor<Solution_> variable,
      MultistageDomain<Solution_> domain,
      long probeLimit,
      Runnable termination) {
    this.director = director;
    this.variable = variable;
    this.probeLimit = probeLimit;
    this.termination = termination;
    originalScore = director.getSolutionDescriptor().getScore(director.getWorkingSolution());
    this.domain = domain;
    entities = domain.entities;
  }

  /** Constant-time validation for internal reads; does not evaluate the phase termination tree. */
  void checkActive() {
    if (!active) throw new IllegalStateException("The multistage transaction is closed.");
    if (budgetExhausted) throw new ProbeLimitExceeded();
    if (cancellation != null) throw cancellation;
    if (Thread.currentThread().isInterrupted()) {
      cancellation = new CancellationException("Multistage evaluation interrupted.");
      throw cancellation;
    }
  }

  void checkpoint() {
    checkActive();
    try {
      termination.run();
    } catch (CancellationException cancelled) {
      cancellation = cancelled;
      throw cancelled;
    }
  }

  private void countProbe() {
    checkpoint();
    if (probes >= probeLimit) {
      budgetExhausted = true;
      throw new ProbeLimitExceeded();
    }
    probes++;
  }

  MultistageEvaluation<Score_> currentEvaluation() {
    countProbe();
    Score_ previousScore = director.getSolutionDescriptor().getScore(director.getWorkingSolution());
    try {
      return evaluation(calculateScore());
    } finally {
      if (active) {
        director.getSolutionDescriptor().setScore(director.getWorkingSolution(), previousScore);
        checkpoint();
      }
    }
  }

  MultistageEvaluation<Score_> evaluate(MultistageOperationImpl<Solution_> operation) {
    countProbe();
    int savepoint = undo.size();
    Score_ score = director.getSolutionDescriptor().getScore(director.getWorkingSolution());
    Throwable failure = null;
    try {
      apply(operation);
      return evaluation(calculateScore());
    } catch (RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      rollbackPreservingFailure(savepoint, score, failure);
      if (failure == null && active) checkpoint();
    }
  }

  private static <Q extends Score<Q>> MultistageEvaluation<Q> evaluation(InnerScore<Q> score) {
    return new MultistageEvaluation<>(score.raw(), score.unassignedCount());
  }

  InnerScore<Score_> calculateScore() {
    checkpoint();
    InnerScore<Score_> score;
    try {
      score = director.calculateScore();
    } catch (RuntimeException | Error failure) {
      // A scoring function can fail after an incremental tuple queue has been partly drained.
      // Ordinary variable undo does not repair that queue; rebuild from the candidate snapshot.
      recover(failure);
      throw failure;
    }
    checkpoint();
    return score;
  }

  void apply(MultistageOperationImpl<Solution_> operation) {
    for (var intent : operation.intents()) {
      checkpoint();
      var mutations = resolve(intent);
      if (mutations.isEmpty()) {
        checkpoint();
        continue;
      }
      for (var mutation : mutations) snapshot(mutation.entity());
      var recorder = new VariableChangeRecordingScoreDirector<Solution_, Score_>(director);
      checkpoint();
      try {
        ResolvedMutation.applyAll(mutations, recorder);
        recorder.updateShadowVariables();
        undo.add(recorder);
        forward.add(List.copyOf(mutations));
      } catch (RuntimeException | Error failure) {
        recover(failure);
        throw failure;
      }
      // Only poll after all before/after notifications and the undo entry are balanced.
      checkpoint();
    }
  }

  PreparedMultistageMove<Solution_> freeze(String description) {
    return new PreparedMultistageMove<>(forward, description);
  }

  boolean isChanged() {
    for (var snapshot : snapshots.entrySet()) {
      var current = variable.getValue(snapshot.getKey());
      if (variable instanceof ListVariableDescriptor) {
        var oldList = (List<?>) snapshot.getValue();
        var newList = (List<?>) current;
        if (oldList.size() != newList.size()) return true;
        for (int i = 0; i < oldList.size(); i++) if (oldList.get(i) != newList.get(i)) return true;
      } else if (!Objects.equals(snapshot.getValue(), current)) return true;
    }
    return false;
  }

  void close(Throwable failure) {
    try {
      rollbackPreservingFailure(0, originalScore, failure);
    } finally {
      active = false;
    }
  }

  void abort(Throwable failure) {
    if (active) recover(failure);
  }

  private void rollbackPreservingFailure(int size, Score_ score, Throwable original) {
    if (!active) return;
    try {
      for (int i = undo.size() - 1; i >= size; i--) undo.get(i).undoChanges();
      undo.subList(size, undo.size()).clear();
      forward.subList(size, forward.size()).clear();
      director.getSolutionDescriptor().setScore(director.getWorkingSolution(), score);
    } catch (RuntimeException | Error rollbackFailure) {
      recover(rollbackFailure);
      if (original == null) throw rollbackFailure;
      if (original != rollbackFailure) original.addSuppressed(rollbackFailure);
    }
  }

  private void snapshot(Object entity) {
    if (!snapshots.containsKey(entity)) {
      var value = variable.getValue(entity);
      snapshots.put(
          entity,
          variable instanceof ListVariableDescriptor ? new ArrayList<>((List<?>) value) : value);
    }
  }

  /** Unbalanced notifications cannot be undone using the normal journal. */
  private void recover(Throwable original) {
    try {
      for (var snapshot : snapshots.entrySet()) {
        if (variable instanceof ListVariableDescriptor<Solution_> listVariable) {
          var list = listVariable.getValue(snapshot.getKey());
          list.clear();
          list.addAll((List<?>) snapshot.getValue());
        } else variable.setValue(snapshot.getKey(), snapshot.getValue());
      }
      director.setWorkingSolution(director.getWorkingSolution());
      director.calculateScore();
      director.getSolutionDescriptor().setScore(director.getWorkingSolution(), originalScore);
    } catch (RuntimeException | Error recoveryFailure) {
      if (recoveryFailure != original) original.addSuppressed(recoveryFailure);
    } finally {
      active = false;
      undo.clear();
      forward.clear();
    }
  }

  void requireEntity(Object entity) {
    checkActive();
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
    checkActive();
    if (value == null && allowUnassigned) return;
    if (value != null
        && !DeepCloningUtils.isImmutable(value.getClass())
        && !domain.containsValue(value, variable, director, this::checkpoint)) {
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
    checkActive();
    Objects.requireNonNull(value, "A list value must not be null.");
    var position = director.getListVariableState(listVariable()).getElementPosition(value);
    return position instanceof PositionInList assigned ? assigned : null;
  }

  void requireKnownListValue(Object value) {
    checkActive();
    Objects.requireNonNull(value, "A list value must not be null.");
    if (domain.containsValue(value, variable, director, this::checkpoint)) return;
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

  private List<ResolvedMutation<Solution_>> resolve(MultistageOperationImpl.Intent intent) {
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

  static final class ProbeLimitExceeded extends RuntimeException {
    ProbeLimitExceeded() {
      super("The multistage candidate exhausted its probe limit.", null, false, false);
    }
  }
}
