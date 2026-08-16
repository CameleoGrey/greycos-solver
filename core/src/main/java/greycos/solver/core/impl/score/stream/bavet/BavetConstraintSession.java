package greycos.solver.core.impl.score.stream.bavet;

import java.util.Map;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.impl.bavet.AbstractSession;
import greycos.solver.core.impl.bavet.common.PropagationQueue;
import greycos.solver.core.impl.cotwin.variable.declarative.ConsistencyTracker;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.constraint.ConstraintMatchTotal;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.impl.score.stream.common.inliner.AbstractScoreInliner;

/**
 * The type is public to make it easier for Bavet-specific minimal bug reproducers to be created.
 * Instances should be created through {@link
 * BavetConstraintStreamScoreDirectorFactory#newSession(Object, ConsistencyTracker,
 * ConstraintMatchPolicy, boolean)}.
 *
 * @see PropagationQueue Description of the tuple propagation mechanism.
 * @param <Score_>
 */
public final class BavetConstraintSession<Score_ extends Score<Score_>>
    extends AbstractSession<ConstraintStreamsBavetNodeNetwork> {

  private final AbstractScoreInliner<Score_> scoreInliner;

  BavetConstraintSession(AbstractScoreInliner<Score_> scoreInliner) {
    this(scoreInliner, ConstraintStreamsBavetNodeNetwork.EMPTY);
  }

  BavetConstraintSession(
      AbstractScoreInliner<Score_> scoreInliner, ConstraintStreamsBavetNodeNetwork nodeNetwork) {
    super(nodeNetwork);
    this.scoreInliner = scoreInliner;
  }

  public Score_ calculateScore() {
    settle();
    return scoreInliner.extractScore();
  }

  public AbstractScoreInliner<Score_> getScoreInliner() {
    return scoreInliner;
  }

  public Map<ConstraintRef, ConstraintMatchTotal<Score_>> getConstraintMatchTotalMap() {
    return scoreInliner.getConstraintMatchTotalMap();
  }

  public void summarizeProfileIfPresent() {
    nodeNetwork.summarizeProfileIfPresent();
  }
}
