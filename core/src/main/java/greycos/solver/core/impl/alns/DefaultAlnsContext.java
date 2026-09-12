package greycos.solver.core.impl.alns;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.function.BooleanSupplier;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.alns.AlnsAssignment;
import greycos.solver.core.api.solver.alns.AlnsChange;
import greycos.solver.core.api.solver.alns.AlnsContext;
import greycos.solver.core.api.solver.alns.AlnsEvaluation;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.api.solver.alns.AlnsTerminationException;
import greycos.solver.core.api.solver.alns.AlnsVariable;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationPipeline;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.preview.api.move.Move;

/** Framework context for one sequential island. Acceptance never re-executes an operator. */
public final class DefaultAlnsContext<Solution_, Score_ extends Score<Score_>>
    implements AlnsContext<Solution_, Score_>, AutoCloseable {
  private final InnerScoreDirector<Solution_, Score_> scoreDirector;
  private final RandomGenerator random;
  private final BooleanSupplier terminated;
  private final AlnsTransaction<Solution_, Score_> transaction;
  private final AlnsModel<Solution_> model;
  private AlnsProbeEvaluator<Solution_, Score_> probeEvaluator;
  private final Set<AlnsTarget<Solution_>> pending = new LinkedHashSet<>();
  private final Set<AlnsTarget<Solution_>> repairTargets = new LinkedHashSet<>();
  private boolean pendingLocked;
  private long probeCount;
  private boolean closed;

  public DefaultAlnsContext(
      InnerScoreDirector<Solution_, Score_> scoreDirector,
      RandomGenerator random,
      BooleanSupplier terminated) {
    this.scoreDirector = Objects.requireNonNull(scoreDirector);
    this.random = Objects.requireNonNull(random);
    this.terminated = Objects.requireNonNull(terminated);
    transaction = new AlnsTransaction<>(scoreDirector);
    model = new AlnsModel<>(scoreDirector, this::checkTermination);
  }

  void configureMoveThreads(
      Integer threadCount,
      int bufferSize,
      ThreadFactory threadFactory,
      int phaseIndex,
      EnvironmentMode environmentMode) {
    if (threadCount == null) return;
    probeEvaluator =
        new AlnsProbeEvaluator<>(
            scoreDirector,
            threadCount,
            bufferSize,
            threadFactory,
            phaseIndex,
            environmentMode,
            terminated);
    transaction.enableReplication(probeEvaluator::abort);
  }

  long additionalCalculationCount() {
    return probeEvaluator == null ? 0 : probeEvaluator.getAdditionalCalculationCount();
  }

  MoveEvaluationPipeline.Diagnostics moveEvaluationDiagnostics() {
    return probeEvaluator == null ? null : probeEvaluator.getDiagnostics();
  }

  private void flushReplay(InnerScore<Score_> score) {
    if (probeEvaluator == null) return;
    var replay = transaction.publishReplay();
    if (Thread.currentThread().isInterrupted()) {
      probeEvaluator.abort();
    } else {
      probeEvaluator.replay(replay, score);
    }
  }

  void incumbentChanged(Move<Solution_> move, InnerScore<Score_> score) {
    if (transaction.isActive()) throw new IllegalStateException("Migration during an ALNS trial.");
    transaction.appendReplay(move);
    flushReplay(score);
  }

  public void beginTrial() {
    requireOpen();
    transaction.begin();
    pending.clear();
    repairTargets.clear();
    pendingLocked = false;
    probeCount = 0;
  }

  public void setPendingTargets(List<AlnsTarget<Solution_>> targets) {
    requireActive();
    if (pendingLocked) throw new IllegalStateException("The repair target set is already locked.");
    for (var target : targets) {
      checkTermination();
      model.descriptor(target);
      if (!pending.add(target))
        throw new IllegalArgumentException("Duplicate ALNS target: " + target);
    }
    repairTargets.addAll(pending);
    pendingLocked = true;
  }

  public boolean isChanged() {
    return transaction.isChanged();
  }

  public boolean isActive() {
    return transaction.isActive();
  }

  public long probeCount() {
    return probeCount;
  }

  public long getProbeCount() {
    return probeCount;
  }

  public void commit() {
    requireActive();
    if (!pending.isEmpty())
      throw new IllegalStateException("Cannot commit with unresolved ALNS repair targets.");
    var evaluation = score();
    if (!evaluation.isComplete())
      throw new IllegalStateException("Cannot commit an incompletely assigned ALNS candidate.");
    flushReplay(InnerScore.fullyAssigned(evaluation.score()));
    transaction.commit();
    repairTargets.clear();
    pendingLocked = false;
  }

  public void rollback() {
    var initialScore = transaction.isActive() ? transaction.initialScore() : null;
    transaction.rollback();
    pending.clear();
    repairTargets.clear();
    pendingLocked = false;
    if (initialScore != null) flushReplay(null);
  }

  @Override
  public Solution_ workingSolution() {
    requireOpen();
    return scoreDirector.getWorkingSolution();
  }

  @Override
  public List<AlnsVariable<Solution_>> variables() {
    requireOpen();
    return model.variables();
  }

  @Override
  public List<AlnsTarget<Solution_>> targets() {
    requireOpen();
    return model.targets(false);
  }

  @Override
  public List<AlnsTarget<Solution_>> unassignedTargets() {
    requireOpen();
    return model.targets(true);
  }

  @Override
  public List<AlnsTarget<Solution_>> pendingTargets() {
    requireOpen();
    return List.copyOf(pending);
  }

  @Override
  public List<AlnsAssignment<Solution_>> assignments(AlnsTarget<Solution_> target) {
    requireOpen();
    checkTermination();
    return model.assignments(target);
  }

  @Override
  public AlnsAssignment<Solution_> currentAssignment(AlnsTarget<Solution_> target) {
    requireOpen();
    return model.current(target);
  }

  @Override
  public Score_ incumbentScore() {
    requireActive();
    return transaction.initialScore();
  }

  @Override
  public AlnsEvaluation<Score_> score() {
    requireOpen();
    var score = scoreDirector.calculateScore();
    return new AlnsEvaluation<>(score.raw(), score.unassignedCount());
  }

  @Override
  public AlnsEvaluation<Score_> evaluate(AlnsChange<Solution_> change) {
    requireActive();
    checkTermination();
    var savepoint = transaction.savepoint();
    var savedPending = new ArrayList<>(pending);
    Throwable originalFailure = null;
    try {
      change.apply(this);
      checkTermination();
      probeCount++;
      return score();
    } catch (RuntimeException | Error failure) {
      originalFailure = failure;
      throw failure;
    } finally {
      try {
        if (transaction.isActive()) transaction.rollback(savepoint);
      } catch (RuntimeException | Error rollbackFailure) {
        if (originalFailure == null) throw rollbackFailure;
        if (rollbackFailure != originalFailure) originalFailure.addSuppressed(rollbackFailure);
      } finally {
        pending.clear();
        pending.addAll(savedPending);
      }
    }
  }

  @Override
  public AlnsEvaluation<Score_> evaluate(AlnsAssignment<Solution_> assignment) {
    requireActive();
    checkTermination();
    return evaluateLocally(compileAssignment(assignment));
  }

  @Override
  public List<AlnsEvaluation<Score_>> evaluateAssignments(
      List<AlnsAssignment<Solution_>> assignments) {
    Objects.requireNonNull(assignments);
    return evaluateBatch(
        new AbstractList<>() {
          @Override
          public Move<Solution_> get(int index) {
            return compileAssignment(assignments.get(index));
          }

          @Override
          public int size() {
            return assignments.size();
          }
        });
  }

  @Override
  public List<AlnsEvaluation<Score_>> evaluateRemovals(List<AlnsTarget<Solution_>> targets) {
    Objects.requireNonNull(targets);
    return evaluateBatch(
        new AbstractList<>() {
          @Override
          public Move<Solution_> get(int index) {
            return compileRemoval(targets.get(index));
          }

          @Override
          public int size() {
            return targets.size();
          }
        });
  }

  private List<AlnsEvaluation<Score_>> evaluateBatch(List<Move<Solution_>> moves) {
    requireActive();
    var results = new ArrayList<AlnsEvaluation<Score_>>(moves.size());
    if (moves.isEmpty()) return results;
    checkTermination();
    if (probeEvaluator != null && probeEvaluator.isEnabledFor(moves.size())) {
      // Initial lazy cloning observes this same enclosing state; publication also marks scratch
      // savepoints whose rollback must subsequently be replicated.
      flushReplay(null);
      probeEvaluator.evaluateTo(
          moves,
          score -> {
            scoreDirector.incrementCalculationCount();
            probeCount++;
            results.add(new AlnsEvaluation<>(score.raw(), score.unassignedCount()));
          });
    } else {
      for (var move : moves) {
        checkTermination();
        results.add(evaluateLocally(move));
      }
    }
    // The caller owns the checkpoint following the final alternative, including any RNG draw.
    return results;
  }

  private AlnsEvaluation<Score_> evaluateLocally(Move<Solution_> move) {
    var score = transaction.evaluatePrimitives(move, this::checkTermination, () -> probeCount++);
    return new AlnsEvaluation<>(score.raw(), score.unassignedCount());
  }

  @Override
  public void execute(AlnsChange<Solution_> change) {
    requireActive();
    checkTermination();
    try {
      change.apply(this);
    } catch (RuntimeException | Error failure) {
      try {
        rollback();
      } catch (RuntimeException | Error rollbackFailure) {
        if (failure != rollbackFailure) failure.addSuppressed(rollbackFailure);
      }
      throw failure;
    }
  }

  @Override
  public void assign(AlnsAssignment<Solution_> assignment) {
    requireActive();
    checkTermination();
    var move = compileAssignment(assignment);
    AlnsPrimitiveMove.forEachPrimitive(
        move,
        primitive -> {
          checkTermination();
          transaction.apply(primitive);
        });
    pending.remove(assignment.target());
  }

  private Move<Solution_> compileAssignment(AlnsAssignment<Solution_> assignment) {
    checkTermination();
    var target = assignment.target();
    requirePending(target);
    model.validateAssignment(assignment);
    var descriptor = model.descriptor(target);
    var current = model.current(target);
    if (descriptor instanceof ListVariableDescriptor<Solution_> list) {
      if (current.entity() != assignment.entity() || current.index() != assignment.index()) {
        var removal =
            current.isUnassigned()
                ? null
                : AlnsPrimitiveMove.remove(list, current.entity(), current.index(), target.value());
        var insertion =
            assignment.isUnassigned()
                ? null
                : AlnsPrimitiveMove.insert(
                    list, assignment.entity(), assignment.index(), target.value());
        if (removal == null && insertion != null) return insertion;
        if (removal != null && insertion == null) return removal;
        if (removal != null) return AlnsPrimitiveMove.composite(List.of(removal, insertion));
      }
    } else if (!Objects.equals(current.value(), assignment.value())) {
      return AlnsPrimitiveMove.basic(
          (BasicVariableDescriptor<Solution_>) descriptor, target.entity(), assignment.value());
    }
    return AlnsPrimitiveMove.composite(List.of());
  }

  @Override
  public void destroy(AlnsTarget<Solution_> target) {
    requireActive();
    checkTermination();
    var move = compileRemoval(target);
    if (pendingLocked) pending.add(target);
    AlnsPrimitiveMove.forEachPrimitive(move, transaction::apply);
  }

  private Move<Solution_> compileRemoval(AlnsTarget<Solution_> target) {
    checkTermination();
    requirePending(target);
    var descriptor = model.descriptor(target);
    var current = model.current(target);
    model.assertMovable(target, current);
    if (current.isUnassigned()) return AlnsPrimitiveMove.composite(List.of());
    if (descriptor instanceof ListVariableDescriptor<Solution_> list) {
      return AlnsPrimitiveMove.remove(list, current.entity(), current.index(), target.value());
    } else {
      return AlnsPrimitiveMove.basic(
          (BasicVariableDescriptor<Solution_>) descriptor, target.entity(), null);
    }
  }

  private void requirePending(AlnsTarget<Solution_> target) {
    if (pendingLocked && !repairTargets.contains(target)) {
      throw new IllegalArgumentException(
          "A repair may only mutate its pending ALNS targets: " + target);
    }
  }

  @Override
  public RandomGenerator random() {
    requireOpen();
    return random;
  }

  @Override
  public void checkTermination() {
    requireOpen();
    if (terminated.getAsBoolean() || Thread.currentThread().isInterrupted())
      throw new AlnsTerminationException();
  }

  private void requireOpen() {
    if (closed) throw new IllegalStateException("ALNS context is closed.");
  }

  private void requireActive() {
    requireOpen();
    if (!transaction.isActive()) throw new IllegalStateException("No active ALNS trial.");
  }

  @Override
  public void close() {
    if (!closed) {
      Throwable originalFailure = null;
      try {
        rollback();
      } catch (RuntimeException | Error failure) {
        originalFailure = failure;
        throw failure;
      } finally {
        closed = true;
        if (probeEvaluator != null) {
          try {
            probeEvaluator.close();
          } catch (RuntimeException | Error workerFailure) {
            if (originalFailure == null) throw workerFailure;
            if (originalFailure != workerFailure) originalFailure.addSuppressed(workerFailure);
          }
        }
      }
    }
  }
}
