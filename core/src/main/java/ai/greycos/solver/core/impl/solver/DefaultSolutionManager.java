package ai.greycos.solver.core.impl.solver;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.analysis.ScoreAnalysis;
import ai.greycos.solver.core.api.solver.RecommendedAssignment;
import ai.greycos.solver.core.api.solver.ScoreAnalysisFetchPolicy;
import ai.greycos.solver.core.api.solver.SolutionManager;
import ai.greycos.solver.core.api.solver.SolutionUpdatePolicy;
import ai.greycos.solver.core.api.solver.SolverFactory;
import ai.greycos.solver.core.api.solver.SolverManager;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.config.solver.PreviewFeature;
import ai.greycos.solver.core.impl.cotwin.variable.listener.support.violation.VariableSnapshotTotal;
import ai.greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.impl.score.director.ScoreDirectorFactory;
import ai.greycos.solver.core.preview.api.cotwin.solution.diff.PlanningSolutionDiff;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 */
@NullMarked
public final class DefaultSolutionManager<Solution_, Score_ extends Score<Score_>>
    implements SolutionManager<Solution_, Score_> {

  private final DefaultSolverFactory<Solution_> solverFactory;
  private final ScoreDirectorFactory<Solution_, Score_> scoreDirectorFactory;

  public DefaultSolutionManager(SolverManager<Solution_> solverManager) {
    this(((DefaultSolverManager<Solution_>) solverManager).getSolverFactory());
  }

  public DefaultSolutionManager(SolverFactory<Solution_> solverFactory) {
    this.solverFactory = ((DefaultSolverFactory<Solution_>) solverFactory);
    this.scoreDirectorFactory = this.solverFactory.getScoreDirectorFactory();
  }

  public ScoreDirectorFactory<Solution_, Score_> getScoreDirectorFactory() {
    return scoreDirectorFactory;
  }

  @Override
  public Score_ update(Solution_ solution, SolutionUpdatePolicy solutionUpdatePolicy) {
    if (solutionUpdatePolicy == SolutionUpdatePolicy.NO_UPDATE) {
      throw new IllegalArgumentException(
          "Can not call "
              + this.getClass().getSimpleName()
              + ".update() with this solutionUpdatePolicy ("
              + solutionUpdatePolicy
              + ").");
    }
    return callScoreDirector(
        solution,
        solutionUpdatePolicy,
        s -> s.getSolutionDescriptor().getScore(s.getWorkingSolution()),
        ConstraintMatchPolicy.DISABLED,
        false);
  }

  private <Result_> Result_ callScoreDirector(
      Solution_ solution,
      SolutionUpdatePolicy solutionUpdatePolicy,
      Function<InnerScoreDirector<Solution_, Score_>, Result_> function,
      ConstraintMatchPolicy constraintMatchPolicy,
      boolean cloneSolution) {
    var isShadowVariableUpdateEnabled = solutionUpdatePolicy.isShadowVariableUpdateEnabled();
    var nonNullSolution = Objects.requireNonNull(solution);
    try (var scoreDirector =
        getScoreDirectorFactory()
            .createScoreDirectorBuilder()
            .withLookUpEnabled(cloneSolution)
            .withConstraintMatchPolicy(constraintMatchPolicy)
            .withExpectShadowVariablesInCorrectState(!isShadowVariableUpdateEnabled)
            .build()) {
      nonNullSolution =
          cloneSolution ? scoreDirector.cloneSolution(nonNullSolution) : nonNullSolution;
      if (isShadowVariableUpdateEnabled) {
        scoreDirector.setWorkingSolution(nonNullSolution);
      } else {
        // Some notifiables resetWorkingSolution update shadow variables
        var oldSnapshot =
            VariableSnapshotTotal.takeSnapshot(
                scoreDirectorFactory.getSolutionDescriptor(), nonNullSolution);
        scoreDirector.setWorkingSolutionWithoutUpdatingShadows(nonNullSolution);
        oldSnapshot.restore();
      }
      if (constraintMatchPolicy.isEnabled()
          && !scoreDirector.getConstraintMatchPolicy().isEnabled()) {
        throw new IllegalStateException(
            """
                        Requested constraint matching but score director doesn't support it.
                        Maybe use Constraint Streams instead of Easy or Incremental score calculator?""");
      }
      if (solutionUpdatePolicy.isScoreUpdateEnabled()) {
        scoreDirector.calculateScore();
      }
      return function.apply(scoreDirector);
    }
  }

  private void assertFreshScore(
      Solution_ solution,
      Score_ currentScore,
      Score_ calculatedScore,
      SolutionUpdatePolicy solutionUpdatePolicy) {
    if (!solutionUpdatePolicy.isScoreUpdateEnabled()) {
      // Score update is not enabled; this means the score is supposed to be valid.
      // Yet it is different from a freshly calculated score, suggesting previous score corruption.
      if (!Objects.equals(currentScore, calculatedScore)) {
        throw new IllegalStateException(
            """
                        Current score (%s) and freshly calculated score (%s) for solution (%s) do not match.
                        Maybe run %s environment mode to check for score corruptions.
                        Otherwise enable %s.%s to update the stale score.
                        """
                .formatted(
                    currentScore,
                    calculatedScore,
                    solution,
                    EnvironmentMode.TRACKED_FULL_ASSERT,
                    SolutionUpdatePolicy.class.getSimpleName(),
                    SolutionUpdatePolicy.UPDATE_ALL));
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public ScoreAnalysis<Score_> analyze(
      Solution_ solution,
      ScoreAnalysisFetchPolicy fetchPolicy,
      SolutionUpdatePolicy solutionUpdatePolicy) {
    Objects.requireNonNull(fetchPolicy, "fetchPolicy");
    var currentScore = (Score_) scoreDirectorFactory.getSolutionDescriptor().getScore(solution);
    var analysis =
        callScoreDirector(
            solution,
            solutionUpdatePolicy,
            scoreDirector -> scoreDirector.buildScoreAnalysis(fetchPolicy),
            ConstraintMatchPolicy.match(fetchPolicy),
            false);
    assertFreshScore(solution, currentScore, analysis.score(), solutionUpdatePolicy);
    return analysis;
  }

  @Override
  public PlanningSolutionDiff<Solution_> diff(Solution_ oldSolution, Solution_ newSolution) {
    solverFactory.ensurePreviewFeature(PreviewFeature.PLANNING_SOLUTION_DIFF);
    return solverFactory.getSolutionDescriptor().diff(oldSolution, newSolution);
  }

  @Override
  public <In_, Out_> List<RecommendedAssignment<Out_, Score_>> recommendAssignment(
      Solution_ solution,
      In_ evaluatedEntityOrElement,
      Function<In_, @Nullable Out_> propositionFunction,
      ScoreAnalysisFetchPolicy fetchPolicy) {
    var assigner =
        new Assigner<Solution_, Score_, RecommendedAssignment<Out_, Score_>, In_, Out_>(
            solverFactory,
            propositionFunction,
            DefaultRecommendedAssignment::new,
            fetchPolicy,
            solution,
            evaluatedEntityOrElement);
    return callScoreDirector(
        solution,
        SolutionUpdatePolicy.UPDATE_ALL,
        assigner,
        ConstraintMatchPolicy.match(fetchPolicy),
        true);
  }
}
