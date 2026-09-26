package greycos.solver.core.impl.score.director.incremental;

import java.util.function.Supplier;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.AbstractScoreDirectorFactory;
import greycos.solver.core.impl.score.director.ScoreDirectorFactory;

import org.jspecify.annotations.NonNull;

/**
 * Incremental implementation of {@link ScoreDirectorFactory}.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @param <Score_> the score type to go with the solution
 * @see IncrementalScoreDirector
 * @see ScoreDirectorFactory
 */
public final class IncrementalScoreDirectorFactory<Solution_, Score_ extends Score<Score_>>
    extends AbstractScoreDirectorFactory<
        Solution_, Score_, IncrementalScoreDirectorFactory<Solution_, Score_>> {

  public static <Solution_, Score_ extends Score<Score_>>
      IncrementalScoreDirectorFactory<Solution_, Score_> buildScoreDirectorFactory(
          SolutionDescriptor<Solution_> solutionDescriptor,
          ScoreDirectorFactoryConfig config,
          EnvironmentMode globalEnvironmentMode) {
    if (!IncrementalScoreCalculator.class.isAssignableFrom(
        config.getIncrementalScoreCalculatorClass())) {
      throw new IllegalArgumentException(
          "The incrementalScoreCalculatorClass (%s) does not implement %s."
              .formatted(
                  config.getIncrementalScoreCalculatorClass(),
                  IncrementalScoreCalculator.class.getSimpleName()));
    }
    return new IncrementalScoreDirectorFactory<>(
        solutionDescriptor,
        () -> {
          var incrementalScoreCalculator =
              (IncrementalScoreCalculator<Solution_, Score_>)
                  ConfigUtils.newInstance(
                      config,
                      "incrementalScoreCalculatorClass",
                      (Class) config.getIncrementalScoreCalculatorClass());
          ConfigUtils.applyCustomProperties(
              incrementalScoreCalculator,
              "incrementalScoreCalculatorClass",
              config.getIncrementalScoreCalculatorCustomProperties(),
              "incrementalScoreCalculatorCustomProperties");
          return incrementalScoreCalculator;
        },
        globalEnvironmentMode);
  }

  private final Supplier<IncrementalScoreCalculator<Solution_, Score_>>
      incrementalScoreCalculatorSupplier;

  public IncrementalScoreDirectorFactory(
      SolutionDescriptor<Solution_> solutionDescriptor,
      Supplier<IncrementalScoreCalculator<Solution_, Score_>> incrementalScoreCalculatorSupplier,
      EnvironmentMode globalEnvironmentMode) {
    super(solutionDescriptor, globalEnvironmentMode);
    this.incrementalScoreCalculatorSupplier = incrementalScoreCalculatorSupplier;
  }

  @Override
  public IncrementalScoreDirector.Builder<Solution_, Score_> createScoreDirectorBuilder() {
    return createScoreDirectorBuilder(globalEnvironmentMode);
  }

  @Override
  public IncrementalScoreDirector.Builder<Solution_, Score_> createScoreDirectorBuilder(
      @NonNull EnvironmentMode environmentMode) {
    return new IncrementalScoreDirector.Builder<>(this, environmentMode)
        .withIncrementalScoreCalculator(incrementalScoreCalculatorSupplier.get());
  }
}
