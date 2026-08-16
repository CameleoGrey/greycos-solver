package greycos.solver.quarkus.testcotwin.dummy;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;

import org.jspecify.annotations.NonNull;

public class DummyTestdataQuarkusIncrementalScoreCalculator
    implements IncrementalScoreCalculator<Object, SimpleScore> {

  @Override
  public void resetWorkingSolution(@NonNull Object workingSolution) {
    // Ignore
  }

  @Override
  public void beforeVariableChanged(@NonNull Object entity, @NonNull String variableName) {
    // Ignore
  }

  @Override
  public void afterVariableChanged(@NonNull Object entity, @NonNull String variableName) {
    // Ignore
  }

  @Override
  public @NonNull SimpleScore calculateScore() {
    return null;
  }
}
