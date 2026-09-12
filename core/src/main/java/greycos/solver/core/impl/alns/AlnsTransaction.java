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
  private Score_ initialScore;
  private boolean active;

  AlnsTransaction(InnerScoreDirector<Solution_, Score_> scoreDirector) {
    this.scoreDirector = Objects.requireNonNull(scoreDirector);
  }

  void begin() {
    if (active) throw new IllegalStateException("An ALNS trial is already active.");
    initialScore = scoreDirector.calculateScore().raw();
    active = true;
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
        scoreDirector.getSolutionDescriptor().getScore(scoreDirector.getWorkingSolution()));
  }

  void apply(
      VariableDescriptor<Solution_> descriptor,
      Object entity,
      Consumer<VariableChangeRecordingScoreDirector<Solution_, Score_>> primitive) {
    requireActive();
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

  void rollback(Savepoint<Score_> savepoint) {
    requireActive();
    if (savepoint.index() < 0 || savepoint.index() > undoMoves.size()) {
      throw new IllegalStateException("Invalid ALNS savepoint.");
    }
    try {
      for (int i = undoMoves.size() - 1; i >= savepoint.index(); i--) {
        scoreDirector.executeMove(undoMoves.get(i));
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
    rollback(new Savepoint<>(0, initialScore));
    clear();
  }

  /** Failed before/after notification sequences cannot safely replay ordinary undo entries. */
  private void recover(Throwable original) {
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

  record Savepoint<Score_>(int index, Score_ score) {}

  private record Snapshot<Solution_>(
      VariableDescriptor<Solution_> descriptor, Object entity, Object value) {}
}
