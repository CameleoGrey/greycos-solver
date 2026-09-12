package greycos.solver.core.impl.alns;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;

/** Framework context for one sequential island. Acceptance never re-executes an operator. */
public final class DefaultAlnsContext<Solution_, Score_ extends Score<Score_>>
    implements AlnsContext<Solution_, Score_>, AutoCloseable {
  private final InnerScoreDirector<Solution_, Score_> scoreDirector;
  private final RandomGenerator random;
  private final BooleanSupplier terminated;
  private final AlnsTransaction<Solution_, Score_> transaction;
  private final AlnsModel<Solution_> model;
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
    if (!score().isComplete())
      throw new IllegalStateException("Cannot commit an incompletely assigned ALNS candidate.");
    transaction.commit();
    repairTargets.clear();
    pendingLocked = false;
  }

  public void rollback() {
    transaction.rollback();
    pending.clear();
    repairTargets.clear();
    pendingLocked = false;
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
    var target = assignment.target();
    requirePending(target);
    model.validateAssignment(assignment);
    var descriptor = model.descriptor(target);
    var current = model.current(target);
    if (descriptor instanceof ListVariableDescriptor<Solution_> list) {
      if (current.entity() != assignment.entity() || current.index() != assignment.index()) {
        if (!current.isUnassigned()) remove(list, target, current);
        if (!assignment.isUnassigned()) {
          checkTermination();
          transaction.apply(
              list,
              assignment.entity(),
              recorder -> {
                recorder.beforeListVariableElementAssigned(list, target.value());
                recorder.beforeListVariableChanged(
                    list, assignment.entity(), assignment.index(), assignment.index());
                list.addElement(assignment.entity(), assignment.index(), target.value());
                recorder.afterListVariableChanged(
                    list, assignment.entity(), assignment.index(), assignment.index() + 1);
                recorder.afterListVariableElementAssigned(list, target.value());
              });
        }
      }
    } else if (!Objects.equals(current.value(), assignment.value())) {
      changeBasic(
          (BasicVariableDescriptor<Solution_>) descriptor, target.entity(), assignment.value());
    }
    pending.remove(target);
  }

  @Override
  public void destroy(AlnsTarget<Solution_> target) {
    requireActive();
    checkTermination();
    requirePending(target);
    var descriptor = model.descriptor(target);
    var current = model.current(target);
    model.assertMovable(target, current);
    if (pendingLocked) pending.add(target);
    if (current.isUnassigned()) return;
    if (descriptor instanceof ListVariableDescriptor<Solution_> list) {
      remove(list, target, current);
    } else {
      changeBasic((BasicVariableDescriptor<Solution_>) descriptor, target.entity(), null);
    }
  }

  private void changeBasic(
      BasicVariableDescriptor<Solution_> descriptor, Object entity, Object value) {
    transaction.apply(
        descriptor,
        entity,
        recorder -> {
          recorder.beforeVariableChanged(descriptor, entity);
          descriptor.setValue(entity, value);
          recorder.afterVariableChanged(descriptor, entity);
        });
  }

  private void remove(
      ListVariableDescriptor<Solution_> descriptor,
      AlnsTarget<Solution_> target,
      AlnsAssignment<Solution_> current) {
    transaction.apply(
        descriptor,
        current.entity(),
        recorder -> {
          recorder.beforeListVariableElementUnassigned(descriptor, target.value());
          recorder.beforeListVariableChanged(
              descriptor, current.entity(), current.index(), current.index() + 1);
          Object removed = descriptor.removeElement(current.entity(), current.index());
          if (removed != target.value())
            throw new IllegalStateException("ALNS list position changed unexpectedly.");
          recorder.afterListVariableChanged(
              descriptor, current.entity(), current.index(), current.index());
          recorder.afterListVariableElementUnassigned(descriptor, target.value());
        });
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
      try {
        rollback();
      } finally {
        closed = true;
      }
    }
  }
}
