package greycos.solver.core.impl.score.stream.bavet.uni;

import greycos.solver.core.api.function.ToFloatFunction;
import greycos.solver.core.impl.bavet.common.tuple.UniTuple;
import greycos.solver.core.impl.score.stream.common.inliner.ConstraintMatchSupplier;
import greycos.solver.core.impl.score.stream.common.inliner.ScoreImpact;
import greycos.solver.core.impl.score.stream.common.inliner.WeightedScoreImpacter;

import org.jspecify.annotations.NullMarked;

@NullMarked
record UniFloatImpactHandler<A>(ToFloatFunction<A> matchWeigher) implements UniImpactHandler<A> {

  @Override
  public ScoreImpact<?> impactNaked(WeightedScoreImpacter<?, ?> impacter, UniTuple<A> tuple) {
    return impacter.impactScore(matchWeigher.applyAsFloat(tuple.getA()), null);
  }

  @Override
  public ScoreImpact<?> impactFull(WeightedScoreImpacter<?, ?> impacter, UniTuple<A> tuple) {
    var a = tuple.getA();
    var constraint = impacter.getContext().getConstraint();
    return impacter.impactScore(
        matchWeigher.applyAsFloat(a),
        ConstraintMatchSupplier.of(constraint.getJustificationMapping(), a));
  }

  @Override
  public ScoreImpact<?> impactWithoutJustification(
      WeightedScoreImpacter<?, ?> impacter, UniTuple<A> tuple) {
    return impacter.impactScore(
        matchWeigher.applyAsFloat(tuple.getA()), ConstraintMatchSupplier.empty());
  }
}
