package ai.greycos.solver.core.api.solver.phase;

import java.util.function.BooleanSupplier;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.director.ScoreDirector;
import ai.greycos.solver.core.api.solver.Solver;
import ai.greycos.solver.core.api.solver.change.ProblemChange;
import ai.greycos.solver.core.impl.phase.Phase;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;

import org.jspecify.annotations.NullMarked;

/**
 * Runs a custom algorithm as a {@link Phase} of the {@link Solver} that changes the planning
 * variables. To change problem facts, use {@link Solver#addProblemChange(ProblemChange)} instead.
 *
 * <p>To add custom properties, configure custom properties and add public setters for them.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@NullMarked
public interface PhaseCommand<Solution_> {

  /**
   * Changes {@link PlanningSolution working solution} of {@link
   * ScoreDirector#getWorkingSolution()}. When the {@link PlanningSolution working solution} is
   * modified, the {@link ScoreDirector} must be correctly notified (through {@link
   * ScoreDirector#beforeVariableChanged(Object, String)} and {@link
   * ScoreDirector#afterVariableChanged(Object, String)}), otherwise calculated {@link Score}s will
   * be corrupted.
   *
   * <p>Don't forget to call {@link ScoreDirector#triggerVariableListeners()} after each set of
   * changes (especially before every {@link InnerScoreDirector#calculateScore()} call) to ensure
   * all shadow variables are updated.
   *
   * @param scoreDirector the {@link ScoreDirector} that needs to get notified of the changes.
   * @param isPhaseTerminated long-running command implementations should check this periodically
   *     and terminate early if it returns true. Otherwise the terminations configured by the user
   *     will have no effect, as the solver can only terminate itself when a command has ended.
   */
  void changeWorkingSolution(
      ScoreDirector<Solution_> scoreDirector, BooleanSupplier isPhaseTerminated);
}
