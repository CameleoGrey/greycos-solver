package greycos.solver.core.impl.geneticalgorithm;

import java.util.ArrayList;
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
              throw new IllegalArgumentException(
                  "The genetic algorithm supports only basic planning variables, but found (%s)."
                      .formatted(variableDescriptor.getSimpleEntityAndVariableName()));
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
    genome = new GeneticAlgorithmGenome(values.toArray());
    score = initialScore;
  }

  public List<GeneticAlgorithmSlot<Solution_>> slots() {
    return slots;
  }

  public GeneticAlgorithmGenome genome() {
    return genome;
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
    if (changedIndices.isEmpty()) {
      return new Transition(true, 0);
    }
    apply(candidate, changedIndices);
    if (!director.isLastVariableUpdateSuccessful()) {
      apply(genome, changedIndices);
      if (!director.isLastVariableUpdateSuccessful()) {
        throw new IllegalStateException(
            "Restoring a structurally invalid genetic algorithm candidate failed to restore valid shadows.");
      }
      var restoredScore = director.calculateScore();
      if (!restoredScore.equals(score)) {
        throw new IllegalStateException(
            "Restoring a structurally invalid genetic algorithm candidate produced score (%s), expected (%s)."
                .formatted(restoredScore, score));
      }
      requireValidScore(restoredScore);
      return new Transition(false, changedIndices.size());
    }
    genome = candidate;
    awaitingScore = true;
    return new Transition(true, changedIndices.size());
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
    if (!changedIndices.isEmpty()) {
      apply(previous, changedIndices);
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

  private void apply(GeneticAlgorithmGenome target, List<Integer> changedIndices) {
    Move<Solution_> delta =
        solutionView -> {
          var notifyingDirector =
              ((InnerMutableSolutionView<Solution_>) solutionView).getScoreDirector();
          for (var index : changedIndices) {
            var slot = slots.get(index);
            notifyingDirector.changeVariableFacade(
                slot.variableDescriptor(), slot.entity(), target.value(index));
          }
        };
    // Updating shadows only after the whole delta also supports compound dependency changes.
    director.getMoveDirector().executeAllowingStructurallyFlawedSolutions(delta);
  }

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
