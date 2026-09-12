package greycos.solver.core.impl.alns;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
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
import greycos.solver.core.impl.cotwin.lookup.LookUpManager;
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationPipeline;
import greycos.solver.core.impl.heuristic.thread.MoveEvaluationSource;
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
  private Runnable consumedProbe = () -> {};
  private Runnable baselineChanged = () -> {};
  private BooleanSupplier waitTerminated;
  private int enumerationChecks;
  private InnerScore<Score_> knownReplayScore;
  private long knownReplayRevision = -1;
  private final boolean preparedProbes =
      Boolean.parseBoolean(System.getProperty("greycos.solver.alns.preparedProbes", "true"));
  private final boolean replayKnownScores =
      Boolean.parseBoolean(System.getProperty("greycos.solver.alns.replayKnownScores", "true"));
  private Consumer<Boolean> queryListener = ignored -> {};
  private LookUpManager replayLookup;
  private LongSupplier repairQueryAllowance = () -> Long.MAX_VALUE;

  public DefaultAlnsContext(
      InnerScoreDirector<Solution_, Score_> scoreDirector,
      RandomGenerator random,
      BooleanSupplier terminated) {
    this.scoreDirector = Objects.requireNonNull(scoreDirector);
    this.random = Objects.requireNonNull(random);
    this.terminated = Objects.requireNonNull(terminated);
    waitTerminated = terminated;
    transaction = new AlnsTransaction<>(scoreDirector);
    model = new AlnsModel<>(scoreDirector, this::checkEnumerationTermination);
  }

  void configureTermination(
      Runnable consumedProbe, Runnable baselineChanged, BooleanSupplier waitTerminated) {
    this.consumedProbe = Objects.requireNonNull(consumedProbe);
    this.baselineChanged = Objects.requireNonNull(baselineChanged);
    this.waitTerminated = Objects.requireNonNull(waitTerminated);
  }

  private void countProbe() {
    probeCount++;
    consumedProbe.run();
  }

  private void checkEnumerationTermination() {
    if ((++enumerationChecks & 31) == 0) baselineChanged.run();
    checkTermination();
  }

  boolean usesPreparedProbes() {
    return preparedProbes;
  }

  void configureQueryListener(Consumer<Boolean> listener) {
    queryListener = Objects.requireNonNull(listener);
  }

  void configureRepairQueryAllowance(LongSupplier allowance) {
    repairQueryAllowance = Objects.requireNonNull(allowance);
  }

  long remainingRepairQueryAllowance() {
    return repairQueryAllowance.getAsLong();
  }

  void creditQuery(boolean probe) {
    scoreDirector.incrementCalculationCount();
    if (probe) countProbe();
  }

  void checkTerminationNow() {
    requireOpen();
    if (waitTerminated.getAsBoolean() || Thread.currentThread().isInterrupted())
      throw new AlnsTerminationException();
  }

  void invalidateTermination() {
    baselineChanged.run();
  }

  void enableReplayRecording() {
    transaction.enableReplication(() -> {});
  }

  List<Move<Solution_>> drainReplayJournal() {
    return transaction.publishReplay();
  }

  void applyRepairJournal(List<Move<Solution_>> journal, InnerScore<Score_> expected) {
    requireActive();
    if (!expected.isFullyAssigned())
      throw new IllegalArgumentException("Cannot apply an incomplete repair attempt.");
    if (replayLookup == null) {
      var descriptor = scoreDirector.getSolutionDescriptor();
      replayLookup = new LookUpManager(descriptor.getLookUpStrategyResolver());
      descriptor.visitAll(scoreDirector.getWorkingSolution(), replayLookup::addWorkingObject);
    }
    var lookup =
        new greycos.solver.core.api.cotwin.lookup.Lookup() {
          @Override
          public <T> T lookUpWorkingObject(T object) {
            return replayLookup.lookUpWorkingObject(object);
          }
        };
    baselineChanged.run();
    for (var move : journal) {
      checkTermination();
      AlnsPrimitiveMove.forEachPrimitive(move.rebase(lookup), transaction::apply);
    }
    pending.clear();
    knownReplayScore = expected;
    knownReplayRevision = transaction.revision();
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
            terminated,
            waitTerminated);
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
    if (score == null && replayKnownScores && knownReplayRevision == transaction.revision())
      score = knownReplayScore;
    if (Thread.currentThread().isInterrupted()) {
      probeEvaluator.abort();
    } else {
      probeEvaluator.replay(replay, score);
    }
  }

  void incumbentChanged(Move<Solution_> move, InnerScore<Score_> score) {
    if (transaction.isActive()) throw new IllegalStateException("Migration during an ALNS trial.");
    transaction.appendReplay(move);
    transaction.invalidate();
    baselineChanged.run();
    flushReplay(score);
  }

  public void beginTrial() {
    requireOpen();
    transaction.begin();
    baselineChanged.run();
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
    baselineChanged.run();
    repairTargets.clear();
    pendingLocked = false;
  }

  public void rollback() {
    var initialScore = transaction.isActive() ? transaction.initialScore() : null;
    transaction.rollback();
    baselineChanged.run();
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
    queryListener.accept(false);
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
      countProbe();
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
            countProbe();
            queryListener.accept(true);
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
    var score = transaction.evaluatePrimitives(move, this::checkTermination, this::countProbe);
    queryListener.accept(true);
    return new AlnsEvaluation<>(score.raw(), score.unassignedCount());
  }

  /**
   * Scores targets against one unchanged baseline, retaining only their ordered best alternatives.
   */
  List<List<BuiltinAlnsOperators.Candidate<Solution_, Score_>>> bestAssignments(
      List<AlnsTarget<Solution_>> targets, int retainedCount) {
    requireActive();
    if (retainedCount < 1)
      throw new IllegalArgumentException("Retained alternative count must be positive.");
    long revision = transaction.revision();
    var sources = new ArrayList<AlnsPreparedAssignments<Solution_>>(targets.size());
    for (var target : targets) {
      checkTermination();
      requirePending(target);
      var source = model.prepareAssignments(target);
      sources.add(source);
      // The original regret loop fails when it reaches this target, after scoring earlier ones.
      if (source.size() == 0) break;
    }
    var source = new PreparedBatch<>(sources);
    Comparator<Retained<Score_>> worstFirst =
        (a, b) -> {
          int comparison = a.score().compareTo(b.score());
          return comparison != 0 ? comparison : Integer.compare(b.ordinal(), a.ordinal());
        };
    var heaps = new ArrayList<PriorityQueue<Retained<Score_>>>(sources.size());
    for (int i = 0; i < sources.size(); i++)
      heaps.add(new PriorityQueue<>(Math.min(retainedCount, 16), worstFirst));
    java.util.function.ObjIntConsumer<InnerScore<Score_>> retain =
        (score, ordinal) -> {
          if (transaction.revision() != revision)
            throw new IllegalStateException("ALNS candidate baseline changed during evaluation.");
          int targetIndex = source.targetIndex(ordinal);
          var heap = heaps.get(targetIndex);
          if (heap.size() == retainedCount) {
            if (score.compareTo(heap.peek().score()) <= 0) return;
            heap.remove();
          }
          heap.add(new Retained<>(ordinal - source.offsets[targetIndex], score));
        };
    if (source.size() > 0) {
      checkTermination();
      if (probeEvaluator != null && probeEvaluator.isEnabledFor(source.size())) {
        flushReplay(null);
        probeEvaluator.evaluateSource(
            source,
            (score, ordinal) -> {
              scoreDirector.incrementCalculationCount();
              countProbe();
              queryListener.accept(true);
              retain.accept(score, ordinal);
            });
      } else {
        for (int i = 0; i < source.size(); i++) {
          checkTermination();
          var score =
              transaction.evaluatePrimitives(
                  source.move(i), this::checkTermination, this::countProbe);
          queryListener.accept(true);
          retain.accept(score, i);
        }
      }
    }
    var results =
        new ArrayList<List<BuiltinAlnsOperators.Candidate<Solution_, Score_>>>(sources.size());
    for (int i = 0; i < sources.size(); i++) {
      var prepared = sources.get(i);
      results.add(
          heaps.get(i).stream()
              .sorted(worstFirst.reversed())
              .map(
                  entry ->
                      new BuiltinAlnsOperators.Candidate<Solution_, Score_>(
                          prepared.assignment(entry.ordinal()),
                          new AlnsEvaluation<>(
                              entry.score().raw(), entry.score().unassignedCount()),
                          revision))
              .toList());
    }
    return results;
  }

  void assignEvaluated(BuiltinAlnsOperators.Candidate<Solution_, Score_> candidate) {
    if (candidate.baselineRevision() != transaction.revision()) {
      throw new IllegalStateException("ALNS winning evaluation belongs to a stale baseline.");
    }
    assign(candidate.assignment());
    knownReplayScore =
        InnerScore.withUnassignedCount(
            candidate.evaluation().score(), candidate.evaluation().unassignedCount());
    knownReplayRevision = transaction.revision();
  }

  private record Retained<Score_ extends Score<Score_>>(int ordinal, InnerScore<Score_> score) {}

  private static final class PreparedBatch<S> implements MoveEvaluationSource<S> {
    private final List<AlnsPreparedAssignments<S>> sources;
    private final int[] offsets;

    private PreparedBatch(List<AlnsPreparedAssignments<S>> sources) {
      this.sources = List.copyOf(sources);
      offsets = new int[sources.size() + 1];
      for (int i = 0; i < sources.size(); i++)
        offsets[i + 1] = Math.addExact(offsets[i], sources.get(i).size());
    }

    int targetIndex(int ordinal) {
      Objects.checkIndex(ordinal, size());
      int index = 0;
      while (ordinal >= offsets[index + 1]) index++;
      return index;
    }

    @Override
    public int size() {
      return offsets[offsets.length - 1];
    }

    @Override
    public Move<S> move(int ordinal) {
      int index = targetIndex(ordinal);
      return sources.get(index).move(ordinal - offsets[index]);
    }

    @Override
    public MoveEvaluationSource<S> rebase(greycos.solver.core.api.cotwin.lookup.Lookup lookup) {
      if (sources.isEmpty() || !sources.getFirst().usesLazyRebasing()) {
        return new PreparedBatch<>(
            sources.stream().map(source -> source.rebaseEager(lookup)).toList());
      }
      return new MoveEvaluationSource<>() {
        @SuppressWarnings("unchecked")
        private final MoveEvaluationSource<S>[] rebasedSources =
            (MoveEvaluationSource<S>[]) new MoveEvaluationSource<?>[sources.size()];

        @Override
        public int size() {
          return PreparedBatch.this.size();
        }

        @Override
        public Move<S> move(int ordinal) {
          int index = targetIndex(ordinal);
          var rebased = rebasedSources[index];
          if (rebased == null) {
            rebased = sources.get(index).rebase(lookup);
            rebasedSources[index] = rebased;
          }
          return rebased.move(ordinal - offsets[index]);
        }

        @Override
        public MoveEvaluationSource<S> rebase(
            greycos.solver.core.api.cotwin.lookup.Lookup otherLookup) {
          return PreparedBatch.this.rebase(otherLookup);
        }
      };
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
    baselineChanged.run();
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
    baselineChanged.run();
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
