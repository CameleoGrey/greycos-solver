package greycos.solver.core.impl.score.director.stream;

import java.util.Arrays;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.ConstraintMetaModel;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.util.ConfigUtils;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.variable.declarative.ConsistencyTracker;
import greycos.solver.core.impl.nodesharing.DefaultConstraintProviderNodeSharer;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.director.AbstractScoreDirectorFactory;
import greycos.solver.core.impl.score.director.ScoreDirectorFactoryFactory;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector.Builder;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraintFactory;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraintSession;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraintSessionFactory;
import greycos.solver.core.impl.score.stream.common.AbstractConstraintStreamScoreDirectorFactory;
import greycos.solver.core.impl.score.stream.common.inliner.AbstractScoreInliner;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NullMarked
public sealed class BavetConstraintStreamScoreDirectorFactory<
        Solution_, Score_ extends Score<Score_>>
    extends AbstractConstraintStreamScoreDirectorFactory<
        Solution_, Score_, BavetConstraintStreamScoreDirectorFactory<Solution_, Score_>>
    permits MultiEnvironmentBavetConstraintStreamScoreDirectorFactory {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(BavetConstraintStreamScoreDirectorFactory.class);

  public static <Solution_, Score_ extends Score<Score_>>
      BavetConstraintStreamScoreDirectorFactory<Solution_, Score_> buildScoreDirectorFactory(
          SolutionDescriptor<Solution_> solutionDescriptor,
          ScoreDirectorFactoryConfig config,
          EnvironmentMode globalEnvironmentMode) {
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
        solutionDescriptor,
        constraintProvider,
        globalEnvironmentMode,
        Boolean.TRUE.equals(config.getConstraintStreamProfilingEnabled()));
  }

  private static Class<? extends ConstraintProvider> getConstraintProviderClass(
      ScoreDirectorFactoryConfig config,
      Class<? extends ConstraintProvider> providedConstraintProviderClass) {
    if (Boolean.TRUE.equals(config.getConstraintStreamAutomaticNodeSharing())) {
      LOGGER.info(
          "Automatic node sharing enabled for ConstraintProvider: {}",
          providedConstraintProviderClass.getName());
      var nodeSharer = new DefaultConstraintProviderNodeSharer();
      Class<? extends ConstraintProvider> transformedClass =
          nodeSharer.buildNodeSharedConstraintProvider(providedConstraintProviderClass);
      LOGGER.info(
          "Successfully applied node sharing transformation. Transformed class: {}",
          transformedClass.getName());
      return transformedClass;
    } else {
      return providedConstraintProviderClass;
    }
  }

  private final BavetConstraintSessionFactory<Solution_, Score_> constraintSessionFactory;
  private final ConstraintMetaModel constraintMetaModel;
  private final boolean constraintStreamProfilingEnabled;

  public BavetConstraintStreamScoreDirectorFactory(
      SolutionDescriptor<Solution_> solutionDescriptor,
      ConstraintProvider constraintProvider,
      EnvironmentMode globalEnvironmentMode) {
    this(solutionDescriptor, constraintProvider, globalEnvironmentMode, false);
  }

  public BavetConstraintStreamScoreDirectorFactory(
      SolutionDescriptor<Solution_> solutionDescriptor,
      ConstraintProvider constraintProvider,
      EnvironmentMode globalEnvironmentMode,
      boolean profilingEnabled) {
    super(solutionDescriptor, globalEnvironmentMode);
    this.constraintStreamProfilingEnabled = profilingEnabled;
    var constraintFactory = new BavetConstraintFactory<>(solutionDescriptor, globalEnvironmentMode);
    constraintMetaModel =
        DefaultConstraintMetaModel.of(constraintFactory.buildConstraints(constraintProvider));
    constraintSessionFactory =
        new BavetConstraintSessionFactory<>(
            solutionDescriptor, constraintMetaModel, constraintStreamProfilingEnabled);
  }

  /**
   * Create a new factory from one already built for the same environment mode, sharing the
   * constraint network it built rather than building a second one.
   */
  BavetConstraintStreamScoreDirectorFactory(
      BavetConstraintStreamScoreDirectorFactory<Solution_, Score_> inheritedScoreDirectorFactory) {
    super(
        inheritedScoreDirectorFactory.solutionDescriptor,
        inheritedScoreDirectorFactory.globalEnvironmentMode);
    this.constraintStreamProfilingEnabled =
        inheritedScoreDirectorFactory.constraintStreamProfilingEnabled;
    this.constraintSessionFactory = inheritedScoreDirectorFactory.constraintSessionFactory;
    this.constraintMetaModel = inheritedScoreDirectorFactory.constraintMetaModel;
    this.initializingScoreTrend = inheritedScoreDirectorFactory.initializingScoreTrend;
    this.assertionScoreDirectorFactory =
        inheritedScoreDirectorFactory.assertionScoreDirectorFactory;
  }

  public BavetConstraintSession<Score_> newSession(
      @Nullable Solution_ workingSolution,
      ConsistencyTracker<Solution_> consistencyTracker,
      ConstraintMatchPolicy constraintMatchPolicy,
      boolean scoreDirectorDerived) {
    return constraintSessionFactory.buildSession(
        workingSolution, consistencyTracker, constraintMatchPolicy, scoreDirectorDerived);
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
    return createScoreDirectorBuilder(globalEnvironmentMode);
  }

  @Override
  public Builder<Solution_, Score_> createScoreDirectorBuilder(EnvironmentMode environmentMode) {
    return new Builder<>(this, environmentMode);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <Factory_ extends AbstractScoreDirectorFactory<Solution_, Score_, Factory_>>
      AbstractScoreDirectorFactory<Solution_, Score_, Factory_> adaptToMultiEnvironmentMode(
          ScoreDirectorFactoryFactory<Solution_, Score_> scoreDirectorFactoryFactory) {
    return (AbstractScoreDirectorFactory<Solution_, Score_, Factory_>)
        new MultiEnvironmentBavetConstraintStreamScoreDirectorFactory<>(
            scoreDirectorFactoryFactory, this);
  }
}
