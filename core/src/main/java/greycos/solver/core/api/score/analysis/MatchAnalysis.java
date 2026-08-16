package greycos.solver.core.api.score.analysis;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;

import org.jspecify.annotations.NullMarked;

/**
 * @param <Score_>
 * @see ScoreAnalysis Description of score analysis and the purpose of this class.
 */
@NullMarked
public interface MatchAnalysis<Score_ extends Score<Score_>>
    extends Comparable<MatchAnalysis<Score_>> {

  ConstraintRef constraintRef();

  Score_ score();

  ConstraintJustification justification();
}
