package greycos.solver.core.impl.score.stream.bavet.uni;

import greycos.solver.core.impl.bavet.common.tuple.UniTuple;
import greycos.solver.core.impl.score.stream.bavet.common.ScoreImpacter;

import org.jspecify.annotations.NullMarked;

/** Instances are provided by {@link UniImpactHandler}. */
@NullMarked
@FunctionalInterface
public interface UniScoreImpacter<A> extends ScoreImpacter<UniTuple<A>> {}
