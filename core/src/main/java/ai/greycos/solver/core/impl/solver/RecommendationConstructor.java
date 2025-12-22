package ai.greycos.solver.core.impl.solver;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.analysis.ScoreAnalysis;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
interface RecommendationConstructor<Score_ extends Score<Score_>, Recommendation_, Out_> {

  Recommendation_ apply(
      long moveIndex, @Nullable Out_ result, ScoreAnalysis<Score_> scoreDifference);
}
