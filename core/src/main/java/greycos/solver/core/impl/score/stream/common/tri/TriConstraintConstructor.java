package greycos.solver.core.impl.score.stream.common.tri;

import greycos.solver.core.api.function.QuadFunction;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.score.stream.common.ConstraintConstructor;

@FunctionalInterface
public interface TriConstraintConstructor<A, B, C, Score_ extends Score<Score_>>
    extends ConstraintConstructor<Score_, QuadFunction<A, B, C, Score_, Object>> {}
