package greycos.solver.core.impl.score.stream.bavet;

import java.util.Map;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.impl.bavet.AbstractSession;
import greycos.solver.core.impl.bavet.common.BavetSessionProfile;
import greycos.solver.core.impl.bavet.common.PropagationQueue;
import greycos.solver.core.impl.cotwin.variable.declarative.ConsistencyTracker;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.constraint.ConstraintMatchTotal;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.impl.score.stream.common.inliner.AbstractScoreInliner;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The type is public to make it easier for Bavet-specific minimal bug reproducers to be created.
 * Instances should be created through {@link
 * BavetConstraintStreamScoreDirectorFactory#newSession(Object, ConsistencyTracker,
 * ConstraintMatchPolicy, boolean)}.
 *
 * @see PropagationQueue Description of the tuple propagation mechanism.
 * @param <Score_>
 */
@NullMarked
public final class BavetConstraintSession<Score_ extends Score<Score_>>
    extends AbstractSession<ConstraintStreamsBavetNodeNetwork> {

  private final AbstractScoreInliner<Score_> scoreInliner;

  BavetConstraintSession(AbstractScoreInliner<Score_> scoreInliner) {
    this(scoreInliner, ConstraintStreamsBavetNodeNetwork.EMPTY);
  }

  BavetConstraintSession(
      AbstractScoreInliner<Score_> scoreInliner, ConstraintStreamsBavetNodeNetwork nodeNetwork) {
    this(scoreInliner, nodeNetwork, null);
  }

  BavetConstraintSession(
      AbstractScoreInliner<Score_> scoreInliner,
      ConstraintStreamsBavetNodeNetwork nodeNetwork,
      @Nullable BavetSessionProfile sessionProfile) {
    super(nodeNetwork, sessionProfile);
    this.scoreInliner = scoreInliner;
  }

  public Score_ calculateScore() {
    if (sessionProfile != null) {
      return calculateProfiledScore();
    }
    settle();
    return scoreInliner.extractScore();
  }

  private Score_ calculateProfiledScore() {
    var profiler = sessionProfile.profiler();
    profiler.enterWall(sessionProfile.generation().stage());
    Throwable failure = null;
    try {
      settle();
      return scoreInliner.extractScore();
    } catch (RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      profiler.exitWall(failure);
    }
  }

  public AbstractScoreInliner<Score_> getScoreInliner() {
    return scoreInliner;
  }

  public Map<ConstraintRef, ConstraintMatchTotal<Score_>> getConstraintMatchTotalMap() {
    return scoreInliner.getConstraintMatchTotalMap();
  }
}
