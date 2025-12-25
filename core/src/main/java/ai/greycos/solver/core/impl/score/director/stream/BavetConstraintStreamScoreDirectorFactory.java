package ai.greycos.solver.core.impl.score.director.stream;

import java.util.Arrays;
import java.util.function.Consumer;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.stream.ConstraintMetaModel;
import ai.greycos.solver.core.api.score.stream.ConstraintProvider;
import ai.greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.config.util.ConfigUtils;
import ai.greycos.solver.core.enterprise.GreycosSolverEnterpriseService;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.domain.variable.declarative.ConsistencyTracker;
import ai.greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.greycos.solver.core.impl.score.director.AbstractScoreDirector;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraintFactory;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraintSession;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraintSessionFactory;
import ai.greycos.solver.core.impl.score.stream.common.AbstractConstraintStreamScoreDirectorFactory;
import ai.greycos.solver.core.impl.score.stream.common.inliner.AbstractScoreInliner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BavetConstraintStreamScoreDirectorFactory<
        Solution_, Score_ extends Score<Score_>>
    extends AbstractConstraintStreamScoreDirectorFactory<
        Solution_, Score_, BavetConstraintStreamScoreDirectorFactory<Solution_, Score_>> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BavetConstraintStreamScoreDirectorFactory.class);

  public static <Solution_, Score_ extends Score<Score_>>
      BavetConstraintStreamScoreDirectorFactory<Solution_, Score_> buildScoreDirectorFactory(
          SolutionDescriptor<Solution_> solutionDescriptor,
          ScoreDirectorFactoryConfig config,
          EnvironmentMode environmentMode) {
    var providedConstraintProviderClass = config.getConstraintProviderClass();
    if (providedConstraintProviderClass == null
        || !ConstraintProvider.class.isAssignableFrom(providedConstraintProviderClass)) {
      throw new IllegalArgumentException(
          "The constraintProviderClass (%s) does not implement %s."
              .formatted(
                  providedConstraintProviderClass, ConstraintProvider.class.getSimpleName()));
    }
    var constraintProviderClass =
        getConstraintProviderClass(config, providedConstraintProviderClass);
    var constraintProvider =
        ConfigUtils.newInstance(config, "constraintProviderClass", constraintProviderClass);
    ConfigUtils.applyCustomProperties(
        constraintProvider,
        "constraintProviderClass",
        config.getConstraintProviderCustomProperties(),
        "constraintProviderCustomProperties");
    return new BavetConstraintStreamScoreDirectorFactory<>(
        solutionDescriptor, constraintProvider, environmentMode);
  }

  private static Class<? extends ConstraintProvider> getConstraintProviderClass(
      ScoreDirectorFactoryConfig config,
      Class<? extends ConstraintProvider> providedConstraintProviderClass) {
    if (Boolean.TRUE.equals(config.getConstraintStreamAutomaticNodeSharing())) {
      LOGGER.info(
          "Automatic node sharing enabled for ConstraintProvider: {}",
          providedConstraintProviderClass.getName());
      try {
        var enterpriseService =
            GreycosSolverEnterpriseService.loadOrFail(
                GreycosSolverEnterpriseService.Feature.AUTOMATIC_NODE_SHARING);
        Class<? extends ConstraintProvider> transformedClass =
            enterpriseService
                .createNodeSharer()
                .buildNodeSharedConstraintProvider(providedConstraintProviderClass);
        LOGGER.info(
            "Successfully applied node sharing transformation. Transformed class: {}",
            transformedClass.getName());
        return transformedClass;
      } catch (Exception e) {
        LOGGER.warn(
            "Node sharing transformation failed for {}. Falling back to original class. Error: {}",
            providedConstraintProviderClass.getName(),
            e.getMessage());
        // Fall back to original class
        return providedConstraintProviderClass;
      }
    } else {
      return providedConstraintProviderClass;
    }
  }

  private final BavetConstraintSessionFactory<Solution_, Score_> constraintSessionFactory;
  private final ConstraintMetaModel constraintMetaModel;

  public BavetConstraintStreamScoreDirectorFactory(
      SolutionDescriptor<Solution_> solutionDescriptor,
      ConstraintProvider constraintProvider,
      EnvironmentMode environmentMode) {
    super(solutionDescriptor);
    var constraintFactory = new BavetConstraintFactory<>(solutionDescriptor, environmentMode);
    constraintMetaModel =
        DefaultConstraintMetaModel.of(constraintFactory.buildConstraints(constraintProvider));
    constraintSessionFactory =
        new BavetConstraintSessionFactory<>(solutionDescriptor, constraintMetaModel);
  }

  public BavetConstraintSession<Score_> newSession(
      Solution_ workingSolution,
      ConsistencyTracker<Solution_> consistencyTracker,
      ConstraintMatchPolicy constraintMatchPolicy,
      boolean scoreDirectorDerived) {
    return newSession(
        workingSolution, consistencyTracker, constraintMatchPolicy, scoreDirectorDerived, null);
  }

  public BavetConstraintSession<Score_> newSession(
      Solution_ workingSolution,
      ConsistencyTracker<Solution_> consistencyTracker,
      ConstraintMatchPolicy constraintMatchPolicy,
      boolean scoreDirectorDerived,
      Consumer<String> nodeNetworkVisualizationConsumer) {
    return constraintSessionFactory.buildSession(
        workingSolution,
        consistencyTracker,
        constraintMatchPolicy,
        scoreDirectorDerived,
        nodeNetworkVisualizationConsumer);
  }

  @Override
  public AbstractScoreInliner<Score_> fireAndForget(Object... facts) {
    var consistencyTracker = ConsistencyTracker.frozen(solutionDescriptor, facts);
    var session = newSession(null, consistencyTracker, ConstraintMatchPolicy.ENABLED, true);
    Arrays.stream(facts).forEach(session::insert);
    session.calculateScore();
    return session.getScoreInliner();
  }

  @Override
  public ConstraintMetaModel getConstraintMetaModel() {
    return constraintMetaModel;
  }

  @Override
  public BavetConstraintStreamScoreDirector.Builder<Solution_, Score_>
      createScoreDirectorBuilder() {
    return new BavetConstraintStreamScoreDirector.Builder<>(this);
  }

  @Override
  public AbstractScoreDirector<Solution_, Score_, ?> buildScoreDirector() {
    return createScoreDirectorBuilder().build();
  }
}
