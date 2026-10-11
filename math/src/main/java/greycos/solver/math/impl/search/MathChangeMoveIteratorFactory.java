package greycos.solver.math.impl.search;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.random.RandomGenerator;

import greycos.solver.core.impl.heuristic.selector.move.factory.MoveIteratorFactory;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;
import greycos.solver.core.preview.api.cotwin.metamodel.PlanningVariableMetaModel;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.builtin.Moves;
import greycos.solver.math.impl.cotwin.MathDecision;
import greycos.solver.math.impl.cotwin.MathSolution;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Scalar changes sampled directly from each decision's finite domain, without enumerating a union
 * of all domains. The existing move lifecycle supplies notifications, undo, and tabu metadata.
 */
@NullMarked
public final class MathChangeMoveIteratorFactory
    implements MoveIteratorFactory<MathSolution, Move<MathSolution>> {

  private final PlanningVariableMetaModel<MathSolution, MathDecision, Long> variable =
      PlanningSolutionMetaModel.of(MathSolution.class, MathDecision.class)
          .genuineEntity(MathDecision.class)
          .basicVariable("value", Long.class);
  private @Nullable MathSolution workingSolution;
  private List<MathDecision> decisions = List.of();
  private long size;

  @Override
  public void phaseStarted(ScoreDirector<MathSolution> scoreDirector) {
    initialize(scoreDirector.getWorkingSolution());
  }

  @Override
  public void phaseEnded(ScoreDirector<MathSolution> scoreDirector) {
    workingSolution = null;
    decisions = List.of();
    size = 0;
  }

  private void initialize(MathSolution solution) {
    workingSolution = solution;
    decisions =
        solution.getDecisions().stream()
            .filter(decision -> !decision.isPinned() && decision.getValueRange().getSize() > 1)
            .toList();
    size = 0;
    for (var decision : decisions) {
      long alternatives = decision.getValueRange().getSize() - 1;
      size = size > Long.MAX_VALUE - alternatives ? Long.MAX_VALUE : size + alternatives;
    }
  }

  private List<MathDecision> decisions(ScoreDirector<MathSolution> scoreDirector) {
    var solution = scoreDirector.getWorkingSolution();
    if (solution != workingSolution) {
      initialize(solution);
    }
    return decisions;
  }

  @Override
  public long getSize(ScoreDirector<MathSolution> scoreDirector) {
    decisions(scoreDirector);
    return size;
  }

  @Override
  public Iterator<Move<MathSolution>> createOriginalMoveIterator(
      ScoreDirector<MathSolution> scoreDirector) {
    var candidates = decisions(scoreDirector);
    return new Iterator<>() {
      private int decisionIndex;
      private long valueIndex;
      private @Nullable Move<MathSolution> next;

      @Override
      public boolean hasNext() {
        if (next != null) {
          return true;
        }
        while (decisionIndex < candidates.size()) {
          var decision = candidates.get(decisionIndex);
          var range = decision.getValueRange();
          while (valueIndex < range.getSize()) {
            var value = range.get(valueIndex++);
            if (!Objects.equals(value, decision.getValue())) {
              next = Moves.change(variable, decision, value);
              return true;
            }
          }
          decisionIndex++;
          valueIndex = 0;
        }
        return false;
      }

      @Override
      public Move<MathSolution> next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        var result = Objects.requireNonNull(next);
        next = null;
        return result;
      }
    };
  }

  @Override
  public Iterator<Move<MathSolution>> createRandomMoveIterator(
      ScoreDirector<MathSolution> scoreDirector, RandomGenerator workingRandom) {
    var candidates = decisions(scoreDirector);
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return !candidates.isEmpty();
      }

      @Override
      public Move<MathSolution> next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        var decision = candidates.get(workingRandom.nextInt(candidates.size()));
        var range = decision.getValueRange();
        long currentIndex =
            Math.subtractExact(Objects.requireNonNull(decision.getValue()), range.get(0));
        long valueIndex = workingRandom.nextLong(range.getSize() - 1);
        if (valueIndex >= currentIndex) {
          valueIndex++;
        }
        return Moves.change(variable, decision, range.get(valueIndex));
      }
    };
  }
}
