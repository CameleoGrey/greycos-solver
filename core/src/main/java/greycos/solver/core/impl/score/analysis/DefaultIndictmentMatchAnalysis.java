package greycos.solver.core.impl.score.analysis;

import java.util.Objects;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.analysis.IndictmentMatchAnalysis;
import greycos.solver.core.api.score.analysis.MatchAnalysis;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record DefaultIndictmentMatchAnalysis<Score_ extends Score<Score_>>(
    ConstraintRef constraintRef,
    Score_ score,
    ConstraintJustification justification,
    long matchCount)
    implements IndictmentMatchAnalysis<Score_> {

  public DefaultIndictmentMatchAnalysis {
    Objects.requireNonNull(constraintRef, "constraintRef");
    Objects.requireNonNull(score, "score");
    Objects.requireNonNull(justification, "justification");
  }

  @Override
  public int compareTo(MatchAnalysis<Score_> other) {
    return new DefaultMatchAnalysis<>(constraintRef, score, justification).compareTo(other);
  }
}
