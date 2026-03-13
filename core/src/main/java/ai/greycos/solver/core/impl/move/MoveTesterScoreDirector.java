package ai.greycos.solver.core.impl.move;

import java.util.Map;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.greycos.solver.core.api.score.constraint.Indictment;
import ai.greycos.solver.core.impl.score.director.AbstractScoreDirector;
import ai.greycos.solver.core.impl.score.director.InnerScore;

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
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<String, ConstraintMatchTotal<Score_>> getConstraintMatchTotalMap() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Map<Object, Indictment<Score_>> getIndictmentMap() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean requiresFlushing() {
    throw new UnsupportedOperationException();
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
      return build();
    }
  }
}
