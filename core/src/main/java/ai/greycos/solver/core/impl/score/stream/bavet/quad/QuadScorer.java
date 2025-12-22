package ai.greycos.solver.core.impl.score.stream.bavet.quad;

import ai.greycos.solver.core.api.function.PentaFunction;
import ai.greycos.solver.core.impl.bavet.common.AbstractScorer;
import ai.greycos.solver.core.impl.bavet.common.tuple.QuadTuple;
import ai.greycos.solver.core.impl.score.stream.common.inliner.UndoScoreImpacter;
import ai.greycos.solver.core.impl.score.stream.common.inliner.WeightedScoreImpacter;

final class QuadScorer<A, B, C, D> extends AbstractScorer<QuadTuple<A, B, C, D>> {

  private final PentaFunction<WeightedScoreImpacter<?, ?>, A, B, C, D, UndoScoreImpacter>
      scoreImpacter;

  public QuadScorer(
      WeightedScoreImpacter<?, ?> weightedScoreImpacter,
      PentaFunction<WeightedScoreImpacter<?, ?>, A, B, C, D, UndoScoreImpacter> scoreImpacter,
      int inputStoreIndex) {
    super(weightedScoreImpacter, inputStoreIndex);
    this.scoreImpacter = scoreImpacter;
  }

  @Override
  protected UndoScoreImpacter impact(QuadTuple<A, B, C, D> tuple) {
    try {
      return scoreImpacter.apply(
          weightedScoreImpacter, tuple.factA, tuple.factB, tuple.factC, tuple.factD);
    } catch (Exception e) {
      throw createExceptionOnImpact(tuple, e);
    }
  }
}
