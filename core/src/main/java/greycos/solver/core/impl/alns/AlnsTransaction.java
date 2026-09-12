package greycos.solver.core.impl.alns;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import greycos.solver.core.impl.move.VariableChangeRecordingScoreDirector;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.preview.api.move.Move;

/**
 * One candidate journal. Each entry is a balanced recorded primitive, not a stochastic operator.
 */
final class AlnsTransaction<Solution_, Score_ extends Score<Score_>> implements AutoCloseable {
  private final InnerScoreDirector<Solution_, Score_> scoreDirector;
  private final List<Move<Solution_>> undoMoves = new ArrayList<>();
  private final List<Snapshot<Solution_>> snapshots = new ArrayList<>();
  private final Map<VariableDescriptor<Solution_>, Map<Object, Snapshot<Solution_>>> snapshotMap =
      new IdentityHashMap<>();
  private final List<Move<Solution_>> unpublishedReplay = new ArrayList<>();
  private final List<AlnsPrimitiveMove<Solution_>> probePrimitives = new ArrayList<>(2);
  private final List<VariableChangeRecordingScoreDirector<Solution_, Score_>> probeRecorders =
      new ArrayList<>(2);
  private long publicationGeneration;
  private long trialPublicationGeneration;
  private int trialReplaySize;
  private boolean replicationEnabled;
  private Runnable recoveryListener = () -> {};
  private Score_ initialScore;
  private boolean active;
  private long revision;

  long revision() {
    return revision;
  }

  void invalidate() {
    revision++;
  }

  AlnsTransaction(InnerScoreDirector<Solution_, Score_> scoreDirector) {
    this.scoreDirector = Objects.requireNonNull(scoreDirector);
  }

  void enableReplication(Runnable recoveryListener) {
    replicationEnabled = true;
    this.recoveryListener = Objects.requireNonNull(recoveryListener);
  }

  /** Called at an immutable worker baseline, including the initial lazy clone. */
  List<Move<Solution_>> publishReplay() {
    var replay = List.copyOf(unpublishedReplay);
    unpublishedReplay.clear();
    publicationGeneration++;
    return replay;
  }

  void appendReplay(Move<Solution_> move) {
    if (replicationEnabled) unpublishedReplay.add(move);
  }

  void begin() {
    if (active) throw new IllegalStateException("An ALNS trial is already active.");
    initialScore = scoreDirector.calculateScore().raw();
    trialPublicationGeneration = publicationGeneration;
    trialReplaySize = unpublishedReplay.size();
    active = true;
    invalidate();
  }

  Score_ initialScore() {
    return initialScore;
  }

  boolean isActive() {
    return active;
  }

  Savepoint<Score_> savepoint() {
    requireActive();
    return new Savepoint<>(
        undoMoves.size(),
        scoreDirector.getSolutionDescriptor().getScore(scoreDirector.getWorkingSolution()),
        unpublishedReplay.size(),
        publicationGeneration);
  }

  void apply(AlnsPrimitiveMove<Solution_> primitive) {
    apply(primitive.descriptor(), primitive.entity(), primitive::apply);
    appendReplay(primitive);
  }

  void apply(
      VariableDescriptor<Solution_> descriptor,
      Object entity,
      Consumer<VariableChangeRecordingScoreDirector<Solution_, Score_>> primitive) {
    requireActive();
    invalidate();
    snapshot(descriptor, entity);
    var recorder = new VariableChangeRecordingScoreDirector<Solution_, Score_>(scoreDirector);
    try {
      primitive.accept(recorder);
      recorder.updateShadowVariables();
      undoMoves.add(recorder.createUndoMove());
    } catch (RuntimeException | Error failure) {
      recover(failure);
      throw failure;
    }
  }

  private void snapshot(VariableDescriptor<Solution_> descriptor, Object entity) {
    snapshotMap
        .computeIfAbsent(descriptor, ignored -> new IdentityHashMap<>())
        .computeIfAbsent(
            entity,
            ignored -> {
              Object oldValue = descriptor.getValue(entity);
              var snapshot =
                  new Snapshot<>(
                      descriptor,
                      entity,
                      descriptor instanceof ListVariableDescriptor
                          ? new ArrayList<>((List<?>) oldValue)
                          : oldValue);
              snapshots.add(snapshot);
              return snapshot;
            });
  }

  /**
   * Framework probes do not mutate the pending set or retain trial undo entries. Recorders are
   * reused, but recovery snapshots still cover every primitive before its first notification.
   */
  InnerScore<Score_> evaluatePrimitives(
      Move<Solution_> move, Runnable checkTermination, Runnable countProbe) {
    requireActive();
    Score_ previousScore =
        scoreDirector.getSolutionDescriptor().getScore(scoreDirector.getWorkingSolution());
    probePrimitives.clear();
    AlnsPrimitiveMove.forEachPrimitive(move, probePrimitives::add);
    int applied = 0;
    boolean incompletePrimitive = false;
    Throwable originalFailure = null;
    try {
      for (var primitive : probePrimitives) {
        checkTermination.run();
        snapshot(primitive.descriptor(), primitive.entity());
        if (applied == probeRecorders.size()) {
          probeRecorders.add(new VariableChangeRecordingScoreDirector<>(scoreDirector));
        }
        var recorder = probeRecorders.get(applied);
        incompletePrimitive = true;
        primitive.apply(recorder);
        recorder.updateShadowVariables();
        incompletePrimitive = false;
        applied++;
      }
      checkTermination.run();
      countProbe.run();
      return scoreDirector.calculateScore();
    } catch (RuntimeException | Error failure) {
      originalFailure = failure;
      if (incompletePrimitive) {
        probeRecorders.clear();
        recover(failure);
      }
      throw failure;
    } finally {
      try {
        if (active) {
          try {
            for (int i = applied - 1; i >= 0; i--) probeRecorders.get(i).undoChanges();
            scoreDirector
                .getSolutionDescriptor()
                .setScore(scoreDirector.getWorkingSolution(), previousScore);
          } catch (RuntimeException | Error rollbackFailure) {
            probeRecorders.clear();
            recover(rollbackFailure);
            if (originalFailure == null) throw rollbackFailure;
            if (originalFailure != rollbackFailure) originalFailure.addSuppressed(rollbackFailure);
          }
        }
      } finally {
        probePrimitives.clear();
      }
    }
  }

  void rollback(Savepoint<Score_> savepoint) {
    requireActive();
    invalidate();
    if (savepoint.index() < 0 || savepoint.index() > undoMoves.size()) {
      throw new IllegalStateException("Invalid ALNS savepoint.");
    }
    try {
      for (int i = undoMoves.size() - 1; i >= savepoint.index(); i--) {
        var undo = undoMoves.get(i);
        scoreDirector.executeMove(undo);
        if (publicationGeneration != savepoint.publicationGeneration()) appendReplay(undo);
      }
      if (publicationGeneration == savepoint.publicationGeneration()) {
        unpublishedReplay.subList(savepoint.replaySize(), unpublishedReplay.size()).clear();
      }
      undoMoves.subList(savepoint.index(), undoMoves.size()).clear();
      scoreDirector
          .getSolutionDescriptor()
          .setScore(scoreDirector.getWorkingSolution(), savepoint.score());
    } catch (RuntimeException | Error failure) {
      recover(failure);
      throw failure;
    }
  }

  boolean isChanged() {
    requireActive();
    for (var snapshot : snapshots) {
      Object current = snapshot.descriptor().getValue(snapshot.entity());
      if (snapshot.descriptor() instanceof ListVariableDescriptor) {
        var oldList = (List<?>) snapshot.value();
        var newList = (List<?>) current;
        if (oldList.size() != newList.size()) return true;
        for (int i = 0; i < oldList.size(); i++) if (oldList.get(i) != newList.get(i)) return true;
      } else if (!Objects.equals(snapshot.value(), current)) return true;
    }
    return false;
  }

  void commit() {
    requireActive();
    clear();
  }

  void rollback() {
    if (!active) return;
    rollback(new Savepoint<>(0, initialScore, trialReplaySize, trialPublicationGeneration));
    clear();
  }

  /** Failed before/after notification sequences cannot safely replay ordinary undo entries. */
  private void recover(Throwable original) {
    try {
      recoveryListener.run();
    } catch (RuntimeException | Error workerFailure) {
      if (workerFailure != original) original.addSuppressed(workerFailure);
    }
    unpublishedReplay.clear();
    try {
      for (var snapshot : snapshots) {
        if (snapshot.descriptor() instanceof ListVariableDescriptor<Solution_> listDescriptor) {
          var list = listDescriptor.getValue(snapshot.entity());
          list.clear();
          list.addAll((List<?>) snapshot.value());
        } else {
          snapshot.descriptor().setValue(snapshot.entity(), snapshot.value());
        }
      }
      scoreDirector.setWorkingSolution(scoreDirector.getWorkingSolution());
      scoreDirector.calculateScore();
    } catch (RuntimeException | Error recoveryFailure) {
      if (recoveryFailure != original) original.addSuppressed(recoveryFailure);
    } finally {
      clear();
    }
  }

  private void clear() {
    invalidate();
    undoMoves.clear();
    snapshots.clear();
    snapshotMap.clear();
    active = false;
  }

  private void requireActive() {
    if (!active) throw new IllegalStateException("No active ALNS trial.");
  }

  @Override
  public void close() {
    rollback();
  }

  record Savepoint<Score_>(int index, Score_ score, int replaySize, long publicationGeneration) {}

  private record Snapshot<Solution_>(
      VariableDescriptor<Solution_> descriptor, Object entity, Object value) {}
}
