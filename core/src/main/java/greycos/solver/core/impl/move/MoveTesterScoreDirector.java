package greycos.solver.core.impl.move;

import java.util.Collections;
import java.util.Map;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.impl.score.constraint.ConstraintMatchTotal;
import greycos.solver.core.impl.score.director.AbstractScoreDirector;
import greycos.solver.core.impl.score.director.InnerScore;

import org.jspecify.annotations.NullMarked;

@NullMarked
final class MoveTesterScoreDirector<Solution_, Score_ extends Score<Score_>>
    extends AbstractScoreDirector<
        Solution_, Score_, MoveTesterScoreDirectorFactory<Solution_, Score_>> {

  private MoveTesterScoreDirector(Builder<Solution_, Score_> builder) {
    super(builder);
  }

  @Override
  public void setWorkingSolutionWithoutUpdatingShadows(Solution_ workingSolution) {
    super.setWorkingSolutionWithoutUpdatingShadows(workingSolution, ignore -> {});
  }

  @Override
  public InnerScore<Score_> calculateScore() {
    return InnerScore.fullyAssigned(scoreDirectorFactory.getScoreDefinition().getZeroScore());
  }

  @Override
  public Map<ConstraintRef, ConstraintMatchTotal<Score_>> getConstraintMatchTotalMap() {
    return Collections.emptyMap();
  }

  @Override
  public boolean requiresFlushing() {
    return false;
  }

  @NullMarked
  public static final class Builder<Solution_, Score_ extends Score<Score_>>
      extends AbstractScoreDirectorBuilder<
          Solution_,
          Score_,
          MoveTesterScoreDirectorFactory<Solution_, Score_>,
          MoveTesterScoreDirector.Builder<Solution_, Score_>> {

    public Builder(MoveTesterScoreDirectorFactory<Solution_, Score_> scoreDirectorFactory) {
      super(scoreDirectorFactory);
    }

    @Override
    public MoveTesterScoreDirector<Solution_, Score_> build() {
      return new MoveTesterScoreDirector<>(this);
    }

    @Override
    public MoveTesterScoreDirector<Solution_, Score_> buildDerived() {
      throw new UnsupportedOperationException();
    }
  }
}
