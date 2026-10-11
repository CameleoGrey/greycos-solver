package greycos.solver.core.impl.islandmodel.resumable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Job;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Machine;

/**
 * Maintains actual machine-load and preference impacts, including temporary evaluation and undo.
 */
public final class ResumableIncrementalScoreCalculator
    implements IncrementalScoreCalculator<BasicSolution, SimpleScore> {

  static final ConcurrentLinkedQueue<ResumableIncrementalScoreCalculator> INSTANCES =
      new ConcurrentLinkedQueue<>();

  private final Map<Job, Machine> assignments = new IdentityHashMap<>();
  private final Map<Job, Boolean> pending = new IdentityHashMap<>();
  private long[] loads;
  private long penalty;
  private Thread mutationOwner;
  long beforeCount;
  long afterCount;
  long resetCount;

  public ResumableIncrementalScoreCalculator() {
    INSTANCES.add(this);
  }

  @Override
  public void resetWorkingSolution(BasicSolution solution) {
    if (!pending.isEmpty()) throw new IllegalStateException("Reset during a variable transaction.");
    assignments.clear();
    loads = new long[solution.getMachines().size()];
    penalty = 0;
    mutationOwner = null;
    resetCount++;
    for (var job : solution.getJobs()) {
      assignments.put(job, job.getMachine());
      add(job, job.getMachine());
    }
  }

  @Override
  public void beforeVariableChanged(Object entity, String variableName) {
    claimMutationOwnership();
    var job = (Job) entity;
    if (!variableName.equals("machine")
        || !assignments.containsKey(job)
        || assignments.get(job) != job.getMachine()
        || pending.put(job, true) != null)
      throw new IllegalStateException(
          "Unbalanced or foreign before notification for (" + job + ").");
    remove(job, assignments.get(job));
    beforeCount++;
  }

  @Override
  public void afterVariableChanged(Object entity, String variableName) {
    claimMutationOwnership();
    var job = (Job) entity;
    if (!variableName.equals("machine") || pending.remove(job) == null)
      throw new IllegalStateException("Unbalanced after notification for (" + job + ").");
    assignments.put(job, job.getMachine());
    add(job, job.getMachine());
    afterCount++;
  }

  private void claimMutationOwnership() {
    if (mutationOwner == null) mutationOwner = Thread.currentThread();
    else if (mutationOwner != Thread.currentThread())
      throw new IllegalStateException("Incremental calculator migrated between mutation owners.");
  }

  private void add(Job job, Machine machine) {
    if (machine == null) return;
    long previousLoad = loads[machine.id()];
    long newLoad = previousLoad + job.getUnits();
    loads[machine.id()] = newLoad;
    penalty += newLoad * newLoad - previousLoad * previousLoad;
    penalty += Math.abs((long) machine.id() - job.getPreferredMachine()) * job.getUnits();
  }

  private void remove(Job job, Machine machine) {
    if (machine == null) return;
    long previousLoad = loads[machine.id()];
    long newLoad = previousLoad - job.getUnits();
    loads[machine.id()] = newLoad;
    penalty += newLoad * newLoad - previousLoad * previousLoad;
    penalty -= Math.abs((long) machine.id() - job.getPreferredMachine()) * job.getUnits();
  }

  @Override
  public SimpleScore calculateScore() {
    if (!pending.isEmpty())
      throw new IllegalStateException("Score requested during a variable transaction.");
    return SimpleScore.of(-penalty);
  }

  boolean hasOpenTransaction() {
    return !pending.isEmpty();
  }
}
