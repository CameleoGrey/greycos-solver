package ai.greycos.solver.core.impl.score.director.incremental;

import java.util.function.Supplier;

import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.calculator.ConstraintMatchAwareIncrementalScoreCalculator;
import ai.greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import ai.greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.config.util.ConfigUtils;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.score.director.AbstractScoreDirectorFactory;
import ai.greycos.solver.core.impl.score.director.ScoreDirectorFactory;

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
          EnvironmentMode environmentMode) {
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
          IncrementalScoreCalculator<Solution_, Score_> incrementalScoreCalculator =
              ConfigUtils.newInstance(
                  config,
                  "incrementalScoreCalculatorClass",
                  config.getIncrementalScoreCalculatorClass());
          ConfigUtils.applyCustomProperties(
              incrementalScoreCalculator,
              "incrementalScoreCalculatorClass",
              config.getIncrementalScoreCalculatorCustomProperties(),
              "incrementalScoreCalculatorCustomProperties");
          return incrementalScoreCalculator;
        },
        environmentMode);
  }

  public static <Solution_, Score_ extends Score<Score_>>
      IncrementalScoreDirectorFactory<Solution_, Score_> buildScoreDirectorFactory(
          SolutionDescriptor<Solution_> solutionDescriptor, ScoreDirectorFactoryConfig config) {
    return buildScoreDirectorFactory(solutionDescriptor, config, null);
  }

  private final Supplier<IncrementalScoreCalculator<Solution_, Score_>>
      incrementalScoreCalculatorSupplier;

  public IncrementalScoreDirectorFactory(
      SolutionDescriptor<Solution_> solutionDescriptor,
      Supplier<IncrementalScoreCalculator<Solution_, Score_>> incrementalScoreCalculatorSupplier,
      EnvironmentMode environmentMode) {
    super(solutionDescriptor, environmentMode);
    this.incrementalScoreCalculatorSupplier = incrementalScoreCalculatorSupplier;
  }

  public IncrementalScoreDirectorFactory(
      SolutionDescriptor<Solution_> solutionDescriptor,
      Supplier<IncrementalScoreCalculator<Solution_, Score_>> incrementalScoreCalculatorSupplier) {
    this(solutionDescriptor, incrementalScoreCalculatorSupplier, null);
  }

  @Override
  public boolean supportsConstraintMatching() {
    return incrementalScoreCalculatorSupplier.get()
        instanceof ConstraintMatchAwareIncrementalScoreCalculator;
  }

  @Override
  public IncrementalScoreDirector.Builder<Solution_, Score_> createScoreDirectorBuilder() {
    return new IncrementalScoreDirector.Builder<>(this)
        .withIncrementalScoreCalculator(incrementalScoreCalculatorSupplier.get());
  }

  @Override
  public IncrementalScoreDirector<Solution_, Score_> buildScoreDirector() {
    return createScoreDirectorBuilder().build();
  }
}
