package ai.greycos.solver.benchmark.impl.statistic;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.constraint.ConstraintRef;

public record ConstraintSummary<Score_ extends Score<Score_>>(
    ConstraintRef constraintRef, Score_ score, int count) {}
