package ai.greycos.solver.core.impl.score.stream.common.bi;

import java.util.Collection;
import java.util.function.BiFunction;

import ai.greycos.solver.core.api.function.TriFunction;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.score.stream.common.ConstraintConstructor;

@FunctionalInterface
public interface BiConstraintConstructor<A, B, Score_ extends Score<Score_>>
    extends ConstraintConstructor<
        Score_, TriFunction<A, B, Score_, Object>, BiFunction<A, B, Collection<?>>> {}
