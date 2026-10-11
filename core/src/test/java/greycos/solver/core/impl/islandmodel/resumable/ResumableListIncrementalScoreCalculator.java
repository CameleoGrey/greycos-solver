package greycos.solver.core.impl.islandmodel.resumable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.ListSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Visit;

/** Incrementally maintains completion/lateness impacts from actual cascading-shadow callbacks. */
public final class ResumableListIncrementalScoreCalculator
    implements IncrementalScoreCalculator<ListSolution, SimpleScore> {
  static final ConcurrentLinkedQueue<ResumableListIncrementalScoreCalculator> INSTANCES =
      new ConcurrentLinkedQueue<>();
  private final Map<Visit, Long> contributions = new IdentityHashMap<>();
  private final Map<Object, Integer> openLists = new IdentityHashMap<>();
  private final Map<Visit, Boolean> openCompletions = new IdentityHashMap<>();
  private long penalty;
  long listChanges;
  long completionChanges;

  public ResumableListIncrementalScoreCalculator() {
    INSTANCES.add(this);
  }

  @Override
  public void resetWorkingSolution(ListSolution solution) {
    if (hasOpenTransaction())
      throw new IllegalStateException("Reset with pending incremental list callbacks.");
    contributions.clear();
    penalty = 0;
    for (var visit : solution.getVisits()) {
      long contribution = contribution(visit);
      contributions.put(visit, contribution);
      penalty += contribution;
    }
  }

  private static long contribution(Visit visit) {
    if (visit.getCompletion() == null) return 0;
    long completion = visit.getCompletion();
    long due = 100L + visit.getId() % 300;
    long late = Math.max(0, completion - due);
    return completion + late * late;
  }

  @Override
  public void beforeVariableChanged(Object entity, String variableName) {
    if (!variableName.equals("completion")) return;
    var visit = (Visit) entity;
    if (openCompletions.put(visit, true) != null
        || !contributions.containsKey(visit)
        || contributions.get(visit) != contribution(visit))
      throw new IllegalStateException("Unbalanced completion callback for (" + visit + ").");
    penalty -= contributions.get(visit);
  }

  @Override
  public void afterVariableChanged(Object entity, String variableName) {
    if (!variableName.equals("completion")) return;
    var visit = (Visit) entity;
    if (openCompletions.remove(visit) == null)
      throw new IllegalStateException("Completion updated without its before callback.");
    long contribution = contribution(visit);
    contributions.put(visit, contribution);
    penalty += contribution;
    completionChanges++;
  }

  @Override
  public void beforeListVariableChanged(
      Object entity, String variableName, int fromIndex, int toIndex) {
    if (!variableName.equals("visits") || fromIndex < 0 || toIndex < fromIndex)
      throw new IllegalStateException("Invalid list change interval.");
    openLists.merge(entity, 1, Integer::sum);
  }

  @Override
  public void afterListVariableChanged(
      Object entity, String variableName, int fromIndex, int toIndex) {
    var depth = openLists.get(entity);
    if (!variableName.equals("visits") || depth == null || fromIndex < 0 || toIndex < fromIndex)
      throw new IllegalStateException("Unbalanced list change interval.");
    if (depth == 1) openLists.remove(entity);
    else openLists.put(entity, depth - 1);
    listChanges++;
  }

  @Override
  public SimpleScore calculateScore() {
    if (hasOpenTransaction())
      throw new IllegalStateException("Score requested before list callbacks settled.");
    return SimpleScore.of(-penalty);
  }

  boolean hasOpenTransaction() {
    return !openLists.isEmpty() || !openCompletions.isEmpty();
  }
}
