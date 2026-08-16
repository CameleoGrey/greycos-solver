package greycos.solver.core.impl.score.stream.common.bi;

import greycos.solver.core.api.function.TriFunction;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.score.stream.common.ConstraintConstructor;

@FunctionalInterface
public interface BiConstraintConstructor<A, B, Score_ extends Score<Score_>>
    extends ConstraintConstructor<Score_, TriFunction<A, B, Score_, Object>> {}
