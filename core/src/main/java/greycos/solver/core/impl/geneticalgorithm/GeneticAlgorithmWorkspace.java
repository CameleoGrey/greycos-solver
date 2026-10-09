package greycos.solver.core.impl.geneticalgorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import greycos.solver.core.impl.move.InnerMutableSolutionView;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.preview.api.move.Move;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * One retained working graph and score-director session for all offspring in a phase. A successful
 * transition only materializes assignments; the phase owns normal scoring and its assertions.
 */
@NullMarked
public final class GeneticAlgorithmWorkspace<Solution_, Score_ extends Score<Score_>> {

  private final InnerScoreDirector<Solution_, Score_> director;
  private final List<GeneticAlgorithmSlot<Solution_>> slots;
  private final @Nullable GeneticAlgorithmListModel<Solution_> listModel;
  // Scratch belongs to this serial workspace; prepared changes retain only their final results.
  private final int[] oldOwners;
  private final int[] newOwners;
  private final int[] oldIndexes;
  private final int[] newIndexes;
  private GeneticAlgorithmGenome genome;
  private InnerScore<Score_> score;
  private boolean awaitingScore;

  public GeneticAlgorithmWorkspace(
      InnerScoreDirector<Solution_, Score_> director, InnerScore<Score_> initialScore) {
    this.director = Objects.requireNonNull(director);
    requireValidScore(initialScore);
    if (!director.isLastVariableUpdateSuccessful()) {
      throw new IllegalStateException(
          "The genetic algorithm requires a structurally valid initial solution.");
    }
    var capturedSlots = new ArrayList<GeneticAlgorithmSlot<Solution_>>();
    var values = new ArrayList<Object>();
    var solution = director.getWorkingSolution();
    var solutionDescriptor = director.getSolutionDescriptor();
    var valueRangeManager = director.getValueRangeManager();
    solutionDescriptor.visitAllEntities(
        solution,
        entity -> {
          var entityDescriptor = solutionDescriptor.findEntityDescriptorOrFail(entity.getClass());
          for (var variableDescriptor : entityDescriptor.getGenuineVariableDescriptorList()) {
            if (!(variableDescriptor
                instanceof BasicVariableDescriptor<Solution_> basicVariableDescriptor)) {
              continue; // List assignments are captured together, including global membership.
            }
            ValueRange<Object> range =
                valueRangeManager.getFromEntity(
                    basicVariableDescriptor.getValueRangeDescriptor(), entity);
            if (range.getSize() <= 0) {
              throw new IllegalArgumentException(
                  "The genetic algorithm requires a nonempty value range for variable (%s) on entity (%s)."
                      .formatted(basicVariableDescriptor.getSimpleEntityAndVariableName(), entity));
            }
            var value = basicVariableDescriptor.getValue(entity);
            if (!basicVariableDescriptor.isInitialized(entity) || !range.contains(value)) {
              throw new IllegalArgumentException(
                  "The genetic algorithm requires an initialized assignment in the value range for variable (%s) on entity (%s), but the value is (%s)."
                      .formatted(
                          basicVariableDescriptor.getSimpleEntityAndVariableName(), entity, value));
            }
            capturedSlots.add(
                new GeneticAlgorithmSlot<>(
                    entity,
                    basicVariableDescriptor,
                    range,
                    entityDescriptor.isMovable(solution, entity) && range.getSize() > 1));
            values.add(value);
          }
        });
    slots = List.copyOf(capturedSlots);
    listModel =
        solutionDescriptor.hasListVariable() ? new GeneticAlgorithmListModel<>(director) : null;
    int listValueCount = listModel == null ? 0 : listModel.valueCount();
    oldOwners = new int[listValueCount];
    newOwners = new int[listValueCount];
    oldIndexes = new int[listValueCount];
    newIndexes = new int[listValueCount];
    genome =
        new GeneticAlgorithmGenome(
            values.toArray(),
            listModel == null ? GeneticAlgorithmListSnapshot.EMPTY : listModel.initialSnapshot());
    score = initialScore;
  }

  public List<GeneticAlgorithmSlot<Solution_>> slots() {
    return slots;
  }

  public @Nullable GeneticAlgorithmListModel<Solution_> listModel() {
    return listModel;
  }

  public GeneticAlgorithmGenome genome() {
    return genome;
  }

  /** Captures a scored temporary move without changing ownership of the retained baseline. */
  public GeneticAlgorithmGenome captureGenome() {
    var values = new Object[slots.size()];
    for (var i = 0; i < slots.size(); i++) {
      var slot = slots.get(i);
      var value = slot.variableDescriptor().getValue(slot.entity());
      if (!isInRange(slot, value) || (!slot.movable() && !Objects.equals(value, genome.value(i)))) {
        throw new IllegalStateException(
            "The local improvement changed variable (%s) on entity (%s) to an invalid or pinned value (%s)."
                .formatted(
                    slot.variableDescriptor().getSimpleEntityAndVariableName(),
                    slot.entity(),
                    value));
      }
      values[i] = value;
    }
    var lists =
        listModel == null
            ? GeneticAlgorithmListSnapshot.EMPTY
            : new GeneticAlgorithmListSnapshot(listModel.captureLists());
    if (listModel != null && !listModel.isValid(lists)) {
      throw new IllegalStateException("The local improvement produced invalid list assignments.");
    }
    return new GeneticAlgorithmGenome(values, lists);
  }

  public InnerScore<Score_> score() {
    if (awaitingScore) {
      throw new IllegalStateException(
          "The materialized genetic algorithm candidate has not been scored yet.");
    }
    return score;
  }

  /** Records fitness only after the phase has completed the standard scoring/assertion path. */
  public void scored(InnerScore<Score_> score) {
    requireValidScore(score);
    if (!director.isLastVariableUpdateSuccessful()) {
      throw new IllegalStateException(
          "Cannot score a structurally invalid genetic algorithm candidate.");
    }
    this.score = score;
    awaitingScore = false;
  }

  /**
   * Rejects invalid range/pin proposals before any notification. Structural rejection restores
   * genuine assignments through notifications, then recalculates and verifies the restored score.
   * Exceptions during setters, shadows, scoring or restoration deliberately abort the caller.
   */
  public Transition transition(GeneticAlgorithmGenome candidate) {
    if (awaitingScore) {
      throw new IllegalStateException(
          "Score the materialized genetic algorithm candidate before another transition.");
    }
    if (candidate.size() != slots.size()) {
      throw new IllegalArgumentException(
          "The genetic algorithm candidate assignment count (%d) differs from the workspace slot count (%d)."
              .formatted(candidate.size(), slots.size()));
    }
    var candidateLists = candidate.listSnapshot();
    requireListCount(candidateLists);
    // Validate the entire mixed proposal before notifying even its first basic assignment.
    if (listModel != null && !listModel.isValid(candidateLists)) {
      return new Transition(false, 0);
    }
    var changedIndices = new ArrayList<Integer>();
    for (var i = 0; i < slots.size(); i++) {
      var value = candidate.value(i);
      if (Objects.equals(genome.value(i), value)) {
        continue;
      }
      var slot = slots.get(i);
      if (!slot.movable() || !isInRange(slot, value)) {
        return new Transition(false, 0);
      }
      changedIndices.add(i);
    }
    var listChanges = prepareListChanges(candidateLists);
    var changedCount = changedIndices.size() + listChanges.changedAssignmentCount();
    if (changedCount == 0) {
      return new Transition(true, 0);
    }
    apply(candidate, changedIndices, listChanges);
    if (!director.isLastVariableUpdateSuccessful()) {
      // The cached genome is still the baseline; restoration must diff against the actual graph.
      restore(genome, score);
      return new Transition(false, changedCount);
    }
    genome = candidate;
    awaitingScore = true;
    return new Transition(true, changedCount);
  }

  /**
   * Restores a previous valid materialization after a completed trial or interrupted evaluation.
   */
  public void restore(GeneticAlgorithmGenome previous, InnerScore<Score_> previousScore) {
    requireValidScore(previousScore);
    if (previous.size() != slots.size()) {
      throw new IllegalArgumentException(
          "The restored genetic algorithm genome has an incompatible slot count.");
    }
    var previousLists = previous.listSnapshot();
    requireListCount(previousLists);
    if (listModel != null && !listModel.isValid(previousLists)) {
      throw new IllegalArgumentException(
          "The restored genetic algorithm list assignments are invalid.");
    }
    var changedIndices = new ArrayList<Integer>();
    for (var i = 0; i < slots.size(); i++) {
      var slot = slots.get(i);
      var value = previous.value(i);
      if (!Objects.equals(slot.variableDescriptor().getValue(slot.entity()), value)) {
        if (!slot.movable() || !isInRange(slot, value)) {
          throw new IllegalArgumentException(
              "The restored genetic algorithm assignment (%s) is invalid for variable (%s) on entity (%s)."
                  .formatted(
                      value,
                      slot.variableDescriptor().getSimpleEntityAndVariableName(),
                      slot.entity()));
        }
        changedIndices.add(i);
      }
    }
    var listChanges = prepareListChanges(previousLists);
    if (!changedIndices.isEmpty() || listChanges.changedAssignmentCount() != 0) {
      apply(previous, changedIndices, listChanges);
    }
    if (!director.isLastVariableUpdateSuccessful()) {
      throw new IllegalStateException(
          "Restoring a genetic algorithm candidate failed to restore valid shadows.");
    }
    var restoredScore = director.calculateScore();
    requireValidScore(restoredScore);
    if (!restoredScore.equals(previousScore)) {
      throw new IllegalStateException(
          "Restoring a genetic algorithm candidate produced score (%s), expected (%s)."
              .formatted(restoredScore, previousScore));
    }
    genome = previous;
    score = restoredScore;
    awaitingScore = false;
  }

  private void requireListCount(GeneticAlgorithmListSnapshot lists) {
    var expected = listModel == null ? 0 : listModel.ownerCount();
    if (lists.ownerCount() != expected) {
      throw new IllegalArgumentException(
          "The genetic algorithm candidate list count (%d) differs from the workspace owner count (%d)."
              .formatted(lists.ownerCount(), expected));
    }
  }

  private PreparedListChanges prepareListChanges(GeneticAlgorithmListSnapshot targetLists) {
    if (listModel == null) {
      return new PreparedListChanges(List.of(), List.of(), List.of(), 0);
    }
    var currentLists = listModel.captureLists();
    Arrays.fill(oldOwners, -1);
    Arrays.fill(newOwners, -1);
    Arrays.fill(oldIndexes, 0);
    Arrays.fill(newIndexes, 0);
    var changes = new ArrayList<PreparedListChange>();
    for (var owner = 0; owner < currentLists.length; owner++) {
      var current = currentLists[owner];
      var targetSize = targetLists.size(owner);
      for (var i = 0; i < current.length; i++) {
        oldOwners[current[i]] = owner;
        oldIndexes[current[i]] = i;
      }
      for (var i = 0; i < targetSize; i++) {
        var value = targetLists.get(owner, i);
        newOwners[value] = owner;
        newIndexes[value] = i;
      }
      var from = 0;
      var commonLength = Math.min(current.length, targetSize);
      while (from < commonLength && current[from] == targetLists.get(owner, from)) {
        from++;
      }
      if (from == current.length && from == targetSize) {
        continue;
      }
      var oldEnd = current.length;
      var newEnd = targetSize;
      while (oldEnd > from
          && newEnd > from
          && current[oldEnd - 1] == targetLists.get(owner, newEnd - 1)) {
        oldEnd--;
        newEnd--;
      }
      var replacement = new ArrayList<Object>(newEnd - from);
      for (var i = from; i < newEnd; i++) {
        replacement.add(listModel.value(targetLists.get(owner, i)));
      }
      var entity = listModel.owner(owner);
      changes.add(
          new PreparedListChange(
              entity,
              listModel.variableDescriptor().getValue(entity),
              replacement,
              from,
              oldEnd,
              newEnd));
    }
    var assignedValues = new ArrayList<Object>();
    var unassignedValues = new ArrayList<Object>();
    var changedCount = 0;
    for (var id = 0; id < oldOwners.length; id++) {
      if (oldOwners[id] != newOwners[id] || oldIndexes[id] != newIndexes[id]) {
        changedCount++;
      }
      if (oldOwners[id] < 0 && newOwners[id] >= 0) {
        assignedValues.add(listModel.value(id));
      } else if (oldOwners[id] >= 0 && newOwners[id] < 0) {
        unassignedValues.add(listModel.value(id));
      }
    }
    return new PreparedListChanges(changes, assignedValues, unassignedValues, changedCount);
  }

  private void apply(
      GeneticAlgorithmGenome target,
      List<Integer> changedIndices,
      PreparedListChanges listChanges) {
    Move<Solution_> delta =
        solutionView -> {
          var notifyingDirector =
              ((InnerMutableSolutionView<Solution_>) solutionView).getScoreDirector();
          for (var index : changedIndices) {
            var slot = slots.get(index);
            notifyingDirector.changeVariableFacade(
                slot.variableDescriptor(), slot.entity(), target.value(index));
          }
          if (listModel != null) {
            var variableDescriptor = listModel.variableDescriptor();
            for (var value : listChanges.assignedValues()) {
              notifyingDirector.beforeListVariableElementAssigned(variableDescriptor, value);
            }
            for (var value : listChanges.unassignedValues()) {
              notifyingDirector.beforeListVariableElementUnassigned(variableDescriptor, value);
            }
            for (var change : listChanges.changes()) {
              notifyingDirector.beforeListVariableChanged(
                  variableDescriptor, change.entity(), change.from(), change.oldEnd());
            }
            for (var change : listChanges.changes()) {
              change.currentList().subList(change.from(), change.oldEnd()).clear();
              change.currentList().addAll(change.from(), change.replacement());
            }
            for (var change : listChanges.changes()) {
              notifyingDirector.afterListVariableChanged(
                  variableDescriptor, change.entity(), change.from(), change.newEnd());
            }
            for (var value : listChanges.unassignedValues()) {
              notifyingDirector.afterListVariableElementUnassigned(variableDescriptor, value);
            }
            for (var value : listChanges.assignedValues()) {
              notifyingDirector.afterListVariableElementAssigned(variableDescriptor, value);
            }
          }
        };
    // Updating shadows only after the whole delta also supports compound dependency changes.
    director.getMoveDirector().executeAllowingStructurallyFlawedSolutions(delta);
  }

  private record PreparedListChanges(
      List<PreparedListChange> changes,
      List<Object> assignedValues,
      List<Object> unassignedValues,
      int changedAssignmentCount) {}

  private record PreparedListChange(
      Object entity,
      List<Object> currentList,
      List<Object> replacement,
      int from,
      int oldEnd,
      int newEnd) {}

  private static boolean isInRange(GeneticAlgorithmSlot<?> slot, @Nullable Object value) {
    // Numeric range implementations require a correctly typed value even for contains().
    if (value == null) {
      return slot.variableDescriptor().allowsUnassigned() && slot.valueRange().contains(null);
    }
    return slot.variableDescriptor().acceptsValueType(value.getClass())
        && slot.valueRange().contains(value);
  }

  private static <Score_ extends Score<Score_>> void requireValidScore(InnerScore<Score_> score) {
    if (!score.isFullyAssigned() || score.isStructurallyFlawed()) {
      throw new IllegalArgumentException(
          "The genetic algorithm requires a fully assigned, structurally valid score, but got (%s)."
              .formatted(score));
    }
  }

  public record Transition(boolean valid, int changedAssignmentCount) {}
}
