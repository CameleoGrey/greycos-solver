package greycos.solver.core.impl.score.director.easy;

import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.AbstractScoreDirectorFactory;
import greycos.solver.core.impl.score.director.ScoreDirectorFactory;

import org.jspecify.annotations.NonNull;

/**
 * Easy implementation of {@link ScoreDirectorFactory}.
 *
 * @param <Solution_> the solution type, the class with the {@link PlanningSolution} annotation
 * @param <Score_> the score type to go with the solution
 * @see EasyScoreDirector
 * @see ScoreDirectorFactory
 */
public final class EasyScoreDirectorFactory<Solution_, Score_ extends Score<Score_>>
    extends AbstractScoreDirectorFactory<
        Solution_, Score_, EasyScoreDirectorFactory<Solution_, Score_>> {

  public static <Solution_, Score_ extends Score<Score_>>
      EasyScoreDirectorFactory<Solution_, Score_> buildScoreDirectorFactory(
          SolutionDescriptor<Solution_> solutionDescriptor,
          ScoreDirectorFactoryConfig config,
          EnvironmentMode globalEnvironmentMode) {
    var easyScoreCalculatorClass = config.getEasyScoreCalculatorClass();
    if (easyScoreCalculatorClass == null
        || !EasyScoreCalculator.class.isAssignableFrom(easyScoreCalculatorClass)) {
      throw new IllegalArgumentException(
          "The easyScoreCalculatorClass (%s) does not implement %s."
              .formatted(
                  config.getEasyScoreCalculatorClass(), EasyScoreCalculator.class.getSimpleName()));
    }
    EasyScoreCalculator<Solution_, Score_> easyScoreCalculator =
        ConfigUtils.newInstance(config, "easyScoreCalculatorClass", easyScoreCalculatorClass);
    ConfigUtils.applyCustomProperties(
        easyScoreCalculator,
        "easyScoreCalculatorClass",
        config.getEasyScoreCalculatorCustomProperties(),
        "easyScoreCalculatorCustomProperties");
    return new EasyScoreDirectorFactory<>(
        solutionDescriptor, easyScoreCalculator, globalEnvironmentMode);
  }

  public static <Solution_, Score_ extends Score<Score_>>
      EasyScoreDirectorFactory<Solution_, Score_> buildScoreDirectorFactory(
          SolutionDescriptor<Solution_> solutionDescriptor, ScoreDirectorFactoryConfig config) {
    return buildScoreDirectorFactory(solutionDescriptor, config, null);
  }

  private final EasyScoreCalculator<Solution_, Score_> easyScoreCalculator;

  public EasyScoreDirectorFactory(
      SolutionDescriptor<Solution_> solutionDescriptor,
      EasyScoreCalculator<Solution_, Score_> easyScoreCalculator,
      EnvironmentMode globalEnvironmentMode) {
    super(solutionDescriptor, globalEnvironmentMode);
    this.easyScoreCalculator = easyScoreCalculator;
  }

  public EasyScoreDirectorFactory(
      SolutionDescriptor<Solution_> solutionDescriptor,
      EasyScoreCalculator<Solution_, Score_> easyScoreCalculator) {
    this(solutionDescriptor, easyScoreCalculator, null);
  }

  @Override
  public EasyScoreDirector.Builder<Solution_, Score_> createScoreDirectorBuilder() {
    return createScoreDirectorBuilder(globalEnvironmentMode);
  }

  @Override
  public EasyScoreDirector.Builder<Solution_, Score_> createScoreDirectorBuilder(
      @NonNull EnvironmentMode environmentMode) {
    return new EasyScoreDirector.Builder<>(this, environmentMode)
        .withEasyScoreCalculator(easyScoreCalculator);
  }
}
