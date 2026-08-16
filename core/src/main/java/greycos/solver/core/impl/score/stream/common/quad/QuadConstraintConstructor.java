package greycos.solver.core.impl.score.stream.common.quad;

import greycos.solver.core.api.function.PentaFunction;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.score.stream.common.ConstraintConstructor;

@FunctionalInterface
public interface QuadConstraintConstructor<A, B, C, D, Score_ extends Score<Score_>>
    extends ConstraintConstructor<Score_, PentaFunction<A, B, C, D, Score_, Object>> {}
