package ai.greycos.solver.core.impl.neighborhood;

import java.util.List;
import java.util.Objects;

import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.DefaultPlanningSolutionMetaModel;
import ai.greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import ai.greycos.solver.core.impl.move.DefaultMoveTestContext;
import ai.greycos.solver.core.impl.move.DefaultMoveTester;
import ai.greycos.solver.core.impl.neighborhood.stream.DefaultMoveStreamFactory;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.preview.api.cotwin.metamodel.PlanningSolutionMetaModel;
import ai.greycos.solver.core.preview.api.neighborhood.MoveProvider;
import ai.greycos.solver.core.preview.api.neighborhood.test.NeighborhoodTestContext;
import ai.greycos.solver.core.preview.api.neighborhood.test.NeighborhoodTester;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DefaultNeighborhoodTester<Solution_> implements NeighborhoodTester<Solution_> {

  private final MoveProvider<Solution_> moveProvider;
  private final DefaultMoveStreamFactory<Solution_> moveStreamFactory;
  private final DefaultMoveTester<Solution_> moveTester;

  public DefaultNeighborhoodTester(
      MoveProvider<Solution_> moveProvider,
      PlanningSolutionMetaModel<Solution_> solutionMetaModel) {
    this.moveProvider = Objects.requireNonNull(moveProvider, "moveProvider");
    this.moveTester =
        new DefaultMoveTester<>(Objects.requireNonNull(solutionMetaModel, "solutionMetaModel"));
    var solutionDescriptor =
        ((DefaultPlanningSolutionMetaModel<Solution_>) solutionMetaModel).solutionDescriptor();
    this.moveStreamFactory =
        new DefaultMoveStreamFactory<>(solutionDescriptor, EnvironmentMode.FULL_ASSERT);
  }

  @Override
  public NeighborhoodTestContext<Solution_> using(Solution_ solution) {
    var repository =
        new NeighborhoodsBasedMoveRepository<>(moveStreamFactory, List.of(moveProvider), false);
    var moveTestContext = (DefaultMoveTestContext<Solution_>) moveTester.using(solution);
    var scoreDirector = moveTestContext.getScoreDirector();
    var solverScope = new SolverScope<Solution_>();
    solverScope.setScoreDirector(scoreDirector);
    repository.solvingStarted(solverScope);
    var phaseScope = new LocalSearchPhaseScope<>(solverScope, 0);
    repository.phaseStarted(phaseScope);
    return new DefaultNeighborhoodTestContext<>(repository, moveTestContext, phaseScope);
  }
}
