package greycos.solver.core.impl.score.stream.bavet.common;

import java.util.function.BiFunction;

import greycos.solver.core.impl.bavet.common.tuple.Tuple;
import greycos.solver.core.impl.score.stream.common.inliner.ScoreImpact;
import greycos.solver.core.impl.score.stream.common.inliner.WeightedScoreImpacter;

import org.jspecify.annotations.NullMarked;

@NullMarked
@FunctionalInterface
public interface ScoreImpacter<Tuple_ extends Tuple>
    extends BiFunction<WeightedScoreImpacter<?, ?>, Tuple_, ScoreImpact<?>> {}
