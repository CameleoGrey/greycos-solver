package ai.greycos.solver.core.impl.score.stream.common.tri;

import ai.greycos.solver.core.api.function.QuadFunction;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.score.stream.common.ConstraintConstructor;

@FunctionalInterface
public interface TriConstraintConstructor<A, B, C, Score_ extends Score<Score_>>
    extends ConstraintConstructor<Score_, QuadFunction<A, B, C, Score_, Object>> {}
