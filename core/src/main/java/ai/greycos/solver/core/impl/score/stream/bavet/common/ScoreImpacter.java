package ai.greycos.solver.core.impl.score.stream.bavet.common;

import java.util.function.BiFunction;

import ai.greycos.solver.core.impl.bavet.common.tuple.Tuple;
import ai.greycos.solver.core.impl.score.stream.common.inliner.ScoreImpact;
import ai.greycos.solver.core.impl.score.stream.common.inliner.WeightedScoreImpacter;

import org.jspecify.annotations.NullMarked;

@NullMarked
@FunctionalInterface
public interface ScoreImpacter<Tuple_ extends Tuple>
    extends BiFunction<WeightedScoreImpacter<?, ?>, Tuple_, ScoreImpact<?>> {}
