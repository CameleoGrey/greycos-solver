package greycos.solver.core.impl.score.stream.bavet.bi;

import java.util.function.ToDoubleBiFunction;

import greycos.solver.core.impl.bavet.common.tuple.BiTuple;
import greycos.solver.core.impl.score.stream.common.inliner.ConstraintMatchSupplier;
import greycos.solver.core.impl.score.stream.common.inliner.ScoreImpact;
import greycos.solver.core.impl.score.stream.common.inliner.WeightedScoreImpacter;

import org.jspecify.annotations.NullMarked;

@NullMarked
record BiDoubleImpactHandler<A, B>(ToDoubleBiFunction<A, B> matchWeigher)
    implements BiImpactHandler<A, B> {

  @Override
  public ScoreImpact<?> impactNaked(WeightedScoreImpacter<?, ?> impacter, BiTuple<A, B> tuple) {
    return impacter.impactScore(matchWeigher.applyAsDouble(tuple.getA(), tuple.getB()), null);
  }

  @Override
  public ScoreImpact<?> impactFull(WeightedScoreImpacter<?, ?> impacter, BiTuple<A, B> tuple) {
    var a = tuple.getA();
    var b = tuple.getB();
    var constraint = impacter.getContext().getConstraint();
    return impacter.impactScore(
        matchWeigher.applyAsDouble(a, b),
        ConstraintMatchSupplier.of(constraint.getJustificationMapping(), a, b));
  }

  @Override
  public ScoreImpact<?> impactWithoutJustification(
      WeightedScoreImpacter<?, ?> impacter, BiTuple<A, B> tuple) {
    return impacter.impactScore(
        matchWeigher.applyAsDouble(tuple.getA(), tuple.getB()), ConstraintMatchSupplier.empty());
  }
}
