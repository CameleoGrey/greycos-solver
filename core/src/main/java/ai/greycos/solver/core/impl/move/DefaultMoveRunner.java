package ai.greycos.solver.core.impl.move;

import java.util.Objects;

import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.DefaultPlanningSolutionMetaModel;
import ai.greycos.solver.core.impl.score.director.AbstractScoreDirectorFactory;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;
import ai.greycos.solver.core.preview.api.move.MoveRunContext;
import ai.greycos.solver.core.preview.api.move.MoveRunner;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DefaultMoveRunner<Solution_> implements MoveRunner<Solution_> {

  private final AbstractScoreDirectorFactory<Solution_, ?, ?> scoreDirectorFactory;

  public DefaultMoveRunner(PlanningSolutionMetaModel<Solution_> solutionMetaModel) {
    // We use PHASE_ASSERT by default.
    this(
        new MoveRunnerScoreDirectorFactory<>(
            ((DefaultPlanningSolutionMetaModel<Solution_>)
                    Objects.requireNonNull(solutionMetaModel))
                .solutionDescriptor(),
            EnvironmentMode.PHASE_ASSERT));
  }

  private DefaultMoveRunner(AbstractScoreDirectorFactory<Solution_, ?, ?> scoreDirectorFactory) {
    this.scoreDirectorFactory =
        Objects.requireNonNull(scoreDirectorFactory, "scoreDirectorFactory");
  }

  @Override
  public MoveRunContext<Solution_> using(Solution_ solution) {
    var scoreDirector =
        scoreDirectorFactory.createScoreDirectorBuilder().withLookUpEnabled(false).build();
    scoreDirector.setWorkingSolution(Objects.requireNonNull(solution, "solution"));
    return new DefaultMoveRunContext<>(scoreDirector);
  }
}
