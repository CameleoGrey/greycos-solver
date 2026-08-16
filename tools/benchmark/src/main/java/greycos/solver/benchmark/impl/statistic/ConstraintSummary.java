package greycos.solver.benchmark.impl.statistic;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.ConstraintRef;

public record ConstraintSummary<Score_ extends Score<Score_>>(
    ConstraintRef constraintRef, Score_ score, int count) {}
