package greycos.solver.math.benchmark;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.random.RandomGenerator;

import greycos.solver.core.impl.heuristic.selector.move.factory.MoveIteratorFactory;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.preview.api.cotwin.metamodel.PlanningVariableMetaModel;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.preview.api.move.builtin.Moves;
import greycos.solver.math.benchmark.HandwrittenMathBaseline.HandDecision;
import greycos.solver.math.benchmark.HandwrittenMathBaseline.HandSolution;

/** Mirrors the module's declaration-ordered uniform entity/different-value scalar sampler. */
public final class HandwrittenChangeMoveIteratorFactory
    implements MoveIteratorFactory<HandSolution, Move<HandSolution>> {

  private List<HandDecision> decisions = List.of();
  private PlanningVariableMetaModel<HandSolution, HandDecision, Long> variable;

  @Override
  public void phaseStarted(ScoreDirector<HandSolution> scoreDirector) {
    decisions =
        scoreDirector.getWorkingSolution().getDecisions().stream()
            .filter(decision -> !decision.isPinned() && decision.getRange().getSize() > 1)
            .toList();
    variable =
        ((InnerScoreDirector<HandSolution, ?>) scoreDirector)
            .getSolutionDescriptor()
            .getMetaModel()
            .genuineEntity(HandDecision.class)
            .basicVariable("value", Long.class);
  }

  @Override
  public void phaseEnded(ScoreDirector<HandSolution> scoreDirector) {
    decisions = List.of();
    variable = null;
  }

  @Override
  public long getSize(ScoreDirector<HandSolution> scoreDirector) {
    long size = 0;
    for (var decision : scoreDirector.getWorkingSolution().getDecisions()) {
      if (!decision.isPinned()) {
        size = Math.addExact(size, decision.getRange().getSize() - 1);
      }
    }
    return size;
  }

  @Override
  public Iterator<Move<HandSolution>> createOriginalMoveIterator(
      ScoreDirector<HandSolution> scoreDirector) {
    throw new UnsupportedOperationException("Qualification uses RANDOM scalar selection.");
  }

  @Override
  public Iterator<Move<HandSolution>> createRandomMoveIterator(
      ScoreDirector<HandSolution> scoreDirector, RandomGenerator random) {
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return !decisions.isEmpty();
      }

      @Override
      public Move<HandSolution> next() {
        if (decisions.isEmpty()) {
          throw new NoSuchElementException();
        }
        var decision = decisions.get(random.nextInt(decisions.size()));
        var range = decision.getRange();
        long current = Math.subtractExact(decision.getValue(), range.get(0));
        long index = random.nextLong(range.getSize() - 1);
        if (index >= current) {
          index++;
        }
        return Moves.change(variable, decision, range.get(index));
      }
    };
  }
}
