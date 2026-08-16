package greycos.solver.core.impl.score.stream.bavet.quad;

import greycos.solver.core.impl.bavet.common.tuple.QuadTuple;
import greycos.solver.core.impl.score.stream.bavet.common.ScoreImpacter;

import org.jspecify.annotations.NullMarked;

/** Instances are provided by {@link QuadImpactHandler}. */
@NullMarked
@FunctionalInterface
public interface QuadScoreImpacter<A, B, C, D> extends ScoreImpacter<QuadTuple<A, B, C, D>> {}
