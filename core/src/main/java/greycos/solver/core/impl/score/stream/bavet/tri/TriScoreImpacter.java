package greycos.solver.core.impl.score.stream.bavet.tri;

import greycos.solver.core.impl.bavet.common.tuple.TriTuple;
import greycos.solver.core.impl.score.stream.bavet.common.ScoreImpacter;

import org.jspecify.annotations.NullMarked;

/** Instances are provided by {@link TriImpactHandler}. */
@NullMarked
@FunctionalInterface
public interface TriScoreImpacter<A, B, C> extends ScoreImpacter<TriTuple<A, B, C>> {}
