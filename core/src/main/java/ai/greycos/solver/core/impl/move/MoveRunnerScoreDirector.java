package ai.greycos.solver.core.impl.move;

import java.util.Collections;
import java.util.Map;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.greycos.solver.core.api.score.constraint.Indictment;
import ai.greycos.solver.core.impl.score.director.AbstractScoreDirector;
import ai.greycos.solver.core.impl.score.director.InnerScore;

import org.jspecify.annotations.NullMarked;

@NullMarked
final class MoveRunnerScoreDirector<Solution_, Score_ extends Score<Score_>>
    extends AbstractScoreDirector<
        Solution_, Score_, MoveRunnerScoreDirectorFactory<Solution_, Score_>> {

  private MoveRunnerScoreDirector(Builder<Solution_, Score_> builder) {
    super(builder);
  }

  @Override
  public void setWorkingSolutionWithoutUpdatingShadows(Solution_ workingSolution) {
    super.setWorkingSolutionWithoutUpdatingShadows(workingSolution, ignored -> {});
  }

  @Override
  public InnerScore<Score_> calculateScore() {
    return InnerScore.fullyAssigned(scoreDirectorFactory.getScoreDefinition().getZeroScore());
  }

  @Override
  public Map<String, ConstraintMatchTotal<Score_>> getConstraintMatchTotalMap() {
    return Collections.emptyMap();
  }

  @Override
  public Map<Object, Indictment<Score_>> getIndictmentMap() {
    return Collections.emptyMap();
  }

  @Override
  public boolean requiresFlushing() {
    return false;
  }

  @NullMarked
  static final class Builder<Solution_, Score_ extends Score<Score_>>
      extends AbstractScoreDirectorBuilder<
          Solution_,
          Score_,
          MoveRunnerScoreDirectorFactory<Solution_, Score_>,
          Builder<Solution_, Score_>> {

    public Builder(MoveRunnerScoreDirectorFactory<Solution_, Score_> scoreDirectorFactory) {
      super(scoreDirectorFactory);
    }

    @Override
    public MoveRunnerScoreDirector<Solution_, Score_> build() {
      return new MoveRunnerScoreDirector<>(this);
    }

    @Override
    public MoveRunnerScoreDirector<Solution_, Score_> buildDerived() {
      throw new UnsupportedOperationException();
    }
  }
}
