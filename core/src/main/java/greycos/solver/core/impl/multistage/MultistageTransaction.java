package greycos.solver.core.impl.multistage;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.multistage.MultistageEvaluation;
import greycos.solver.core.api.solver.multistage.MultistageVariableReference;
import greycos.solver.core.impl.cotwin.variable.descriptor.GenuineVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.move.VariableChangeRecordingScoreDirector;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;

/** One candidate's journal, with balanced undo and snapshots for exceptional recovery. */
final class MultistageTransaction<Solution_, Score_ extends Score<Score_>> {
  final InnerScoreDirector<Solution_, Score_> director;
  private final Map<MultistageVariableReference<?, ?>, MultistageVariableContext<Solution_>>
      contexts = new LinkedHashMap<>();
  final MultistageVariableContext<Solution_> defaultContext;
  private final Map<GenuineVariableDescriptor<Solution_>, Map<Object, Object>> snapshots =
      new IdentityHashMap<>();
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
    this.probeLimit = probeLimit;
    this.termination = termination;
    originalScore = director.getSolutionDescriptor().getScore(director.getWorkingSolution());
    defaultContext = new MultistageVariableContext<>(this, variable, domain);
  }

  MultistageTransaction(
      InnerScoreDirector<Solution_, Score_> director,
      Map<MultistageVariableBinding<Solution_>, MultistageDomain<Solution_>> domains,
      long probeLimit,
      Runnable termination) {
    this.director = director;
    this.probeLimit = probeLimit;
    this.termination = termination;
    originalScore = director.getSolutionDescriptor().getScore(director.getWorkingSolution());
    domains.forEach(
        (binding, domain) ->
            contexts.put(
                binding.reference(),
                new MultistageVariableContext<>(this, binding.variable(), domain)));
    defaultContext = contexts.values().iterator().next();
  }

  MultistageVariableContext<Solution_> context(MultistageVariableReference<?, ?> reference) {
    checkActive();
    var context = contexts.get(Objects.requireNonNull(reference));
    if (context == null) {
      throw new IllegalArgumentException(
          "Variable reference ("
              + reference
              + ") is not declared by this cross-variable selector. "
              + "Declared references are "
              + contexts.keySet()
              + ".");
    }
    return context;
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
      if (intent.context().transaction != this) {
        throw new IllegalArgumentException("The multistage intent belongs to another candidate.");
      }
      var mutations = intent.context().resolve(intent);
      if (mutations.isEmpty()) {
        checkpoint();
        continue;
      }
      for (var mutation : mutations) snapshot(mutation.descriptor(), mutation.entity());
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
    for (var variableSnapshots : snapshots.entrySet()) {
      var variable = variableSnapshots.getKey();
      for (var snapshot : variableSnapshots.getValue().entrySet()) {
        var current = variable.getValue(snapshot.getKey());
        if (variable instanceof ListVariableDescriptor) {
          var oldList = (List<?>) snapshot.getValue();
          var newList = (List<?>) current;
          if (oldList.size() != newList.size()) return true;
          for (int i = 0; i < oldList.size(); i++)
            if (oldList.get(i) != newList.get(i)) return true;
        } else if (!Objects.equals(snapshot.getValue(), current)) return true;
      }
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

  private void snapshot(GenuineVariableDescriptor<Solution_> variable, Object entity) {
    var variableSnapshots = snapshots.computeIfAbsent(variable, ignored -> new IdentityHashMap<>());
    if (!variableSnapshots.containsKey(entity)) {
      var value = variable.getValue(entity);
      variableSnapshots.put(
          entity,
          variable instanceof ListVariableDescriptor ? new ArrayList<>((List<?>) value) : value);
    }
  }

  /** Unbalanced notifications cannot be undone using the normal journal. */
  private void recover(Throwable original) {
    active = false;
    try {
      // Continue restoring other coordinates even if a user setter fails during recovery.
      for (var variableSnapshots : snapshots.entrySet()) {
        var variable = variableSnapshots.getKey();
        for (var snapshot : variableSnapshots.getValue().entrySet()) {
          try {
            if (variable instanceof ListVariableDescriptor<Solution_> listVariable) {
              var list = listVariable.getValue(snapshot.getKey());
              list.clear();
              list.addAll((List<?>) snapshot.getValue());
            } else variable.setValue(snapshot.getKey(), snapshot.getValue());
          } catch (RuntimeException | Error recoveryFailure) {
            if (recoveryFailure != original) original.addSuppressed(recoveryFailure);
          }
        }
      }
      try {
        director.setWorkingSolution(director.getWorkingSolution());
        director.calculateScore();
      } catch (RuntimeException | Error recoveryFailure) {
        if (recoveryFailure != original) original.addSuppressed(recoveryFailure);
      }
      try {
        director.getSolutionDescriptor().setScore(director.getWorkingSolution(), originalScore);
      } catch (RuntimeException | Error recoveryFailure) {
        if (recoveryFailure != original) original.addSuppressed(recoveryFailure);
      }
    } finally {
      undo.clear();
      forward.clear();
    }
  }

  static final class ProbeLimitExceeded extends RuntimeException {
    ProbeLimitExceeded() {
      super("The multistage candidate exhausted its probe limit.", null, false, false);
    }
  }
}
