package greycos.solver.core.impl.score.stream.bavet.tri;

import java.math.BigDecimal;

import greycos.solver.core.api.function.TriFunction;
import greycos.solver.core.impl.bavet.common.tuple.TriTuple;
import greycos.solver.core.impl.score.stream.common.inliner.ConstraintMatchSupplier;
import greycos.solver.core.impl.score.stream.common.inliner.ScoreImpact;
import greycos.solver.core.impl.score.stream.common.inliner.WeightedScoreImpacter;

import org.jspecify.annotations.NullMarked;

@NullMarked
record TriBigDecimalImpactHandler<A, B, C>(TriFunction<A, B, C, BigDecimal> matchWeigher)
    implements TriImpactHandler<A, B, C> {

  @Override
  public ScoreImpact<?> impactNaked(WeightedScoreImpacter<?, ?> impacter, TriTuple<A, B, C> tuple) {
    return impacter.impactScore(matchWeigher.apply(tuple.getA(), tuple.getB(), tuple.getC()), null);
  }

  @Override
  public ScoreImpact<?> impactFull(WeightedScoreImpacter<?, ?> impacter, TriTuple<A, B, C> tuple) {
    var a = tuple.getA();
    var b = tuple.getB();
    var c = tuple.getC();
    var constraint = impacter.getContext().getConstraint();
    return impacter.impactScore(
        matchWeigher.apply(a, b, c),
        ConstraintMatchSupplier.of(constraint.getJustificationMapping(), a, b, c));
  }

  @Override
  public ScoreImpact<?> impactWithoutJustification(
      WeightedScoreImpacter<?, ?> impacter, TriTuple<A, B, C> tuple) {
    return impacter.impactScore(
        matchWeigher.apply(tuple.getA(), tuple.getB(), tuple.getC()),
        ConstraintMatchSupplier.empty());
  }
}
