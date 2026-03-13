package ai.greycos.solver.core.api.score.constraint;

import java.util.Set;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.ScoreExplanation;
import ai.greycos.solver.core.api.solver.SolutionManager;

import org.jspecify.annotations.NullMarked;

/**
 * Explains the {@link Score} of a {@link PlanningSolution}, from the opposite side than {@link
 * Indictment}. Retrievable from {@link ScoreExplanation#getConstraintMatchTotalMap()}.
 *
 * <p>If possible, prefer using {@link SolutionManager#analyze(Object)} instead.
 *
 * @param <Score_> the actual score type
 */
@NullMarked
public interface ConstraintMatchTotal<Score_ extends Score<Score_>> {

  ConstraintRef getConstraintRef();

  /**
   * The effective value of constraint weight after applying optional overrides. It is independent
   * to the state of the {@link PlanningVariable planning variables}. Do not confuse with {@link
   * #getScore()}.
   */
  Score_ getConstraintWeight();

  Set<ConstraintMatch<Score_>> getConstraintMatchSet();

  /**
   * @return {@code >= 0}
   */
  default int getConstraintMatchCount() {
    return getConstraintMatchSet().size();
  }

  /** Sum of the {@link #getConstraintMatchSet()}'s {@link ConstraintMatch#getScore()}. */
  Score_ getScore();
}
