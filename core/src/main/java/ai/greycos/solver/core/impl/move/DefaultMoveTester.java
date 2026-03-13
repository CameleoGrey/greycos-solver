package ai.greycos.solver.core.impl.move;

import java.util.Objects;

import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.DefaultPlanningSolutionMetaModel;
import ai.greycos.solver.core.impl.score.director.AbstractScoreDirectorFactory;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;
import ai.greycos.solver.core.preview.api.move.test.MoveTestContext;
import ai.greycos.solver.core.preview.api.move.test.MoveTester;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DefaultMoveTester<Solution_> implements MoveTester<Solution_> {

  private final AbstractScoreDirectorFactory<Solution_, ?, ?> scoreDirectorFactory;

  public DefaultMoveTester(PlanningSolutionMetaModel<Solution_> solutionMetaModel) {
    this(
        new MoveTesterScoreDirectorFactory<>(
            ((DefaultPlanningSolutionMetaModel<Solution_>)
                    Objects.requireNonNull(solutionMetaModel))
                .solutionDescriptor(),
            EnvironmentMode.PHASE_ASSERT));
  }

  private DefaultMoveTester(AbstractScoreDirectorFactory<Solution_, ?, ?> scoreDirectorFactory) {
    this.scoreDirectorFactory =
        Objects.requireNonNull(scoreDirectorFactory, "scoreDirectorFactory");
  }

  @Override
  public MoveTestContext<Solution_> using(Solution_ solution) {
    var scoreDirector =
        scoreDirectorFactory.createScoreDirectorBuilder().withLookUpEnabled(false).build();
    scoreDirector.setWorkingSolution(Objects.requireNonNull(solution, "solution"));
    return new DefaultMoveTestContext<>(scoreDirector);
  }
}
