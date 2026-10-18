package greycos.solver.core.impl.score.analysis;

import java.util.List;
import java.util.Objects;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.analysis.IndictmentAnalysis;
import greycos.solver.core.api.score.analysis.IndictmentMatchAnalysis;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record DefaultIndictmentAnalysis<Score_ extends Score<Score_>>(
    Object indictedObject,
    Score_ score,
    List<IndictmentMatchAnalysis<Score_>> matches,
    long matchCount)
    implements IndictmentAnalysis<Score_> {

  public DefaultIndictmentAnalysis {
    Objects.requireNonNull(indictedObject, "indictedObject");
    Objects.requireNonNull(score, "score");
    matches = List.copyOf(matches);
    long actualMatchCount = 0;
    for (var match : matches) {
      actualMatchCount = Math.addExact(actualMatchCount, match.matchCount());
    }
    if (actualMatchCount != matchCount) {
      throw new IllegalArgumentException(
          "The indictment matchCount (%d) differs from the sum of its match counts (%d)."
              .formatted(matchCount, actualMatchCount));
    }
  }
}
