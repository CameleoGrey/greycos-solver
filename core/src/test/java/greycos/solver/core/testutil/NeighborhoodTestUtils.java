package greycos.solver.core.testutil;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.neighborhood.stream.DefaultMoveStreamFactory;
import greycos.solver.core.impl.neighborhood.stream.DefaultNeighborhoodSession;
import greycos.solver.core.impl.score.director.SessionContext;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class NeighborhoodTestUtils {

  /**
   * Builds and settles a {@code DatasetSession} directly (bypassing a real {@code ScoreDirector}
   * and solver), for the cached and just-in-time dataset cases that have no {@code MoveProvider}
   * route of their own.
   */
  public static <Solution_> DefaultNeighborhoodSession<Solution_> createSession(
      DefaultMoveStreamFactory<Solution_> moveStreamFactory, Solution_ solution) {
    var scoreDirector =
        new EasyScoreDirectorFactory<>(
                moveStreamFactory.getSolutionDescriptor(),
                s -> SimpleScore.ZERO,
                EnvironmentMode.PHASE_ASSERT)
            .buildScoreDirector();
    scoreDirector.setWorkingSolution(solution);
    var session = moveStreamFactory.createSession(new SessionContext<>(scoreDirector));
    moveStreamFactory.getSolutionDescriptor().visitAll(solution, session::insert);
    session.settle();
    return session;
  }

  private NeighborhoodTestUtils() {}
}
