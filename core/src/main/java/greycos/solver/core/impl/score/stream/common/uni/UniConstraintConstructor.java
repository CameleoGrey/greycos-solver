package greycos.solver.core.impl.score.stream.common.uni;

import java.util.function.BiFunction;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.impl.score.stream.common.ConstraintConstructor;

@FunctionalInterface
public interface UniConstraintConstructor<A, Score_ extends Score<Score_>>
    extends ConstraintConstructor<Score_, BiFunction<A, Score_, Object>> {}
