package ai.greycos.solver.core.impl.score.stream.common.quad;

import ai.greycos.solver.core.api.function.PentaFunction;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.impl.score.stream.common.ConstraintConstructor;

@FunctionalInterface
public interface QuadConstraintConstructor<A, B, C, D, Score_ extends Score<Score_>>
    extends ConstraintConstructor<Score_, PentaFunction<A, B, C, D, Score_, Object>> {}
