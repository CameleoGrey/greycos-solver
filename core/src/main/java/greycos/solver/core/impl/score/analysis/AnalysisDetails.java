package greycos.solver.core.impl.score.analysis;

import java.util.Objects;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.analysis.RawScoreAnalysis;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Detached object views and optional raw occurrences built from the same settled match set. */
@NullMarked
public record AnalysisDetails<Score_ extends Score<Score_>>(
    IndictmentMaps<Score_> indictments, @Nullable RawScoreAnalysis<Score_> rawAnalysis) {
  @SuppressWarnings({"rawtypes", "unchecked"})
  private static final AnalysisDetails<?> UNAVAILABLE =
      new AnalysisDetails(IndictmentMaps.unavailable(), null);

  @SuppressWarnings("unchecked")
  public static <Score_ extends Score<Score_>> AnalysisDetails<Score_> unavailable() {
    return (AnalysisDetails<Score_>) UNAVAILABLE;
  }

  public AnalysisDetails {
    Objects.requireNonNull(indictments, "indictments");
  }
}
