package greycos.solver.core.impl.score.stream.bavet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.profile.ConstraintProfile.Activity;
import greycos.solver.core.api.score.profile.ConstraintProfile.Stage;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintMetaModel;
import greycos.solver.core.impl.bavet.common.AbstractRootNode;
import greycos.solver.core.impl.bavet.common.BavetAbstractConstraintStream;
import greycos.solver.core.impl.bavet.common.BavetSessionProfile;
import greycos.solver.core.impl.bavet.common.DefaultConstraintProfiler;
import greycos.solver.core.impl.bavet.uni.AbstractForEachUniNode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.cotwin.variable.declarative.ConsistencyTracker;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.stream.bavet.common.ConstraintNodeBuildHelper;
import greycos.solver.core.impl.score.stream.common.inliner.AbstractScoreInliner;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public final class BavetConstraintSessionFactory<Solution_, Score_ extends Score<Score_>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(BavetConstraintSessionFactory.class);
  public static final Level CONSTRAINT_WEIGHT_LOGGING_LEVEL = Level.DEBUG;

  private final SolutionDescriptor<Solution_> solutionDescriptor;
  private final ConstraintMetaModel constraintMetaModel;
  private final boolean entityPropertyRefreshSupported;

  @SuppressWarnings("unchecked")
  public BavetConstraintSessionFactory(
      SolutionDescriptor<Solution_> solutionDescriptor,
      ConstraintMetaModel constraintMetaModel,
      boolean profilingEnabled) {
    this.solutionDescriptor = Objects.requireNonNull(solutionDescriptor);
    this.constraintMetaModel = Objects.requireNonNull(constraintMetaModel);
    // Inspect every constraint, including those whose current weight is zero.
    var allStreams = new LinkedHashSet<BavetAbstractConstraintStream<Solution_>>();
    for (var constraint : constraintMetaModel.getConstraints()) {
      ((BavetConstraint<Solution_>) constraint).collectActiveConstraintStreams(allStreams);
    }
    this.entityPropertyRefreshSupported =
        solutionDescriptor.getListVariableDescriptor() == null
            && solutionDescriptor.getConstraintWeightSupplier() == null
            && solutionDescriptor.getEntityDescriptors().stream()
                .allMatch(entity -> entity.getShadowVariableDescriptors().isEmpty())
            && allStreams.stream().noneMatch(BavetPrecomputeStream.class::isInstance);
  }

  public boolean supportsEntityPropertyRefresh() {
    return entityPropertyRefreshSupported;
  }

  // ************************************************************************
  // Node creation
  // ************************************************************************

  public BavetConstraintSession<Score_> buildSession(
      Solution_ workingSolution,
      ConsistencyTracker<Solution_> consistencyTracker,
      ConstraintMatchPolicy constraintMatchPolicy,
      boolean scoreDirectorDerived) {
    return buildSession(
        workingSolution, consistencyTracker, constraintMatchPolicy, scoreDirectorDerived, null);
  }

  public BavetConstraintSession<Score_> buildSession(
      Solution_ workingSolution,
      ConsistencyTracker<Solution_> consistencyTracker,
      ConstraintMatchPolicy constraintMatchPolicy,
      boolean scoreDirectorDerived,
      @Nullable DefaultConstraintProfiler profiler) {
    Map<Constraint, Score_> constraintWeightMap;
    BavetConstraintSession<Score_> session;
    if (profiler == null) {
      constraintWeightMap = HashMap.newHashMap(constraintMetaModel.getConstraints().size());
      session =
          buildSessionInternal(
              workingSolution,
              consistencyTracker,
              constraintMatchPolicy,
              scoreDirectorDerived,
              null,
              constraintWeightMap);
    } else {
      profiler.enterWall(Stage.INITIALIZATION);
      Throwable failure = null;
      try {
        var generation =
            profiler.newGeneration(
                constraintMetaModel.getConstraints().stream()
                    .map(Constraint::getConstraintRef)
                    .toList());
        var profile = new BavetSessionProfile(profiler, generation);
        constraintWeightMap = HashMap.newHashMap(constraintMetaModel.getConstraints().size());
        session =
            buildSessionInternal(
                workingSolution,
                consistencyTracker,
                constraintMatchPolicy,
                scoreDirectorDerived,
                profile,
                constraintWeightMap);
      } catch (RuntimeException | Error e) {
        failure = e;
        throw e;
      } finally {
        profiler.exitWall(failure);
      }
    }
    // Rendering and logger callbacks are deliberately outside the construction wall boundary.
    logConstraintWeights(workingSolution, scoreDirectorDerived, constraintWeightMap, profiler);
    return session;
  }

  @SuppressWarnings("unchecked")
  private BavetConstraintSession<Score_> buildSessionInternal(
      Solution_ workingSolution,
      ConsistencyTracker<Solution_> consistencyTracker,
      ConstraintMatchPolicy constraintMatchPolicy,
      boolean scoreDirectorDerived,
      @Nullable BavetSessionProfile sessionProfile,
      Map<Constraint, Score_> constraintWeightMap) {
    var constraintWeightSupplier = solutionDescriptor.getConstraintWeightSupplier();
    var constraints = constraintMetaModel.getConstraints();
    if (constraintWeightSupplier != null) { // Fail fast on unknown constraints.
      var knownConstraints =
          constraints.stream().map(Constraint::getConstraintRef).collect(Collectors.toSet());
      constraintWeightSupplier.validate(workingSolution, knownConstraints);
    }
    var scoreDefinition = solutionDescriptor.<Score_>getScoreDefinition();
    var zeroScore = scoreDefinition.getZeroScore();
    var constraintStreamSet = new LinkedHashSet<BavetAbstractConstraintStream<Solution_>>();

    for (var constraint : constraints) {
      var constraintRef = constraint.getConstraintRef();
      var castConstraint = (BavetConstraint<Solution_>) constraint;
      var constraintWeight = (Score_) castConstraint.extractConstraintWeight(workingSolution);
      if (!constraintWeight.equals(zeroScore)) {
        /*
         * Relies on BavetConstraintFactory#share(Stream_) occurring for all constraint stream instances
         * to ensure there are no 2 equal ConstraintStream instances (with different child stream lists).
         */
        castConstraint.collectActiveConstraintStreams(constraintStreamSet);
        constraintWeightMap.put(constraint, constraintWeight);
      } else {
        if (sessionProfile != null) {
          sessionProfile
              .generation()
              .setConstraintActivity(constraintRef, Activity.DISABLED_WEIGHT);
        }
      }
    }

    var scoreInliner =
        AbstractScoreInliner.buildScoreInliner(
            scoreDefinition, constraintWeightMap, constraintMatchPolicy);
    if (constraintStreamSet.isEmpty() && sessionProfile == null) {
      return new BavetConstraintSession<>(scoreInliner);
    }

    return new BavetConstraintSession<>(
        scoreInliner,
        buildNodeNetwork(
            consistencyTracker,
            constraintStreamSet,
            scoreInliner,
            sessionProfile,
            scoreDirectorDerived),
        sessionProfile);
  }

  private void logConstraintWeights(
      Solution_ workingSolution,
      boolean scoreDirectorDerived,
      Map<Constraint, Score_> constraintWeightMap,
      @Nullable DefaultConstraintProfiler profiler) {
    if (constraintWeightMap.isEmpty()
        ? !LOGGER.isWarnEnabled()
        : scoreDirectorDerived || !LOGGER.isEnabledForLevel(CONSTRAINT_WEIGHT_LOGGING_LEVEL)) {
      return;
    }
    if (profiler == null) {
      logConstraintWeightsInternal(workingSolution, constraintWeightMap);
      return;
    }
    profiler.enterExcluded();
    Throwable failure = null;
    try {
      logConstraintWeightsInternal(workingSolution, constraintWeightMap);
    } catch (RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      profiler.exitExcluded(failure);
    }
  }

  private void logConstraintWeightsInternal(
      Solution_ workingSolution, Map<Constraint, Score_> constraintWeightMap) {
    if (constraintWeightMap.isEmpty()) {
      LOGGER.warn("No constraints enabled for solution ({}).", workingSolution);
      return;
    }
    var text =
        new StringBuilder("Constraint weights for solution (%s):%n".formatted(workingSolution));
    for (var constraint : constraintMetaModel.getConstraints()) {
      var ref = constraint.getConstraintRef();
      var weight = constraintWeightMap.get(constraint);
      var defaultWeight = ((BavetConstraint<?>) constraint).getConstraintWeight();
      if (weight == null) {
        text.append("  Constraint (%s) disabled.%n".formatted(ref));
      } else if (defaultWeight != null && !defaultWeight.equals(weight)) {
        text.append(
            "  Constraint (%s) weight overridden to (%s) from (%s).%n"
                .formatted(ref, weight, defaultWeight));
      } else {
        text.append("  Constraint (%s) weight set to (%s).%n".formatted(ref, weight));
      }
    }
    LOGGER.atLevel(CONSTRAINT_WEIGHT_LOGGING_LEVEL).log(text.toString().trim());
  }

  private ConstraintStreamsBavetNodeNetwork buildNodeNetwork(
      ConsistencyTracker<Solution_> consistencyTracker,
      Set<BavetAbstractConstraintStream<Solution_>> constraintStreamSet,
      AbstractScoreInliner<Score_> scoreInliner,
      @Nullable BavetSessionProfile sessionProfile,
      boolean scoreDirectorDerived) {
    var buildHelper =
        new ConstraintNodeBuildHelper<>(
            consistencyTracker, constraintStreamSet, scoreInliner, sessionProfile);
    var declaredClassToNodeMap = new LinkedHashMap<Class<?>, List<AbstractRootNode<?>>>();
    var nodeList =
        buildHelper.buildNodeList(
            constraintStreamSet,
            buildHelper,
            BavetAbstractConstraintStream::buildNode,
            node -> {
              if (!(node instanceof AbstractRootNode<?> tupleSourceRoot)) {
                return;
              }

              if (tupleSourceRoot instanceof AbstractForEachUniNode<?> forEachUniNode) {
                var forEachClass = forEachUniNode.getForEachClass();
                var forEachUniNodeList =
                    declaredClassToNodeMap.computeIfAbsent(forEachClass, k -> new ArrayList<>(2));
                if (forEachUniNodeList.stream()
                        .filter(sourceNode -> sourceNode instanceof AbstractForEachUniNode<?>)
                        .count()
                    == 3) {
                  // Each class can have at most three forEach nodes: one including everything, one
                  // including consistent + null vars, the last consistent + no null vars.
                  throw new IllegalStateException(
                      "Impossible state: For class (%s) there are already 3 nodes (%s), not adding another (%s)."
                          .formatted(forEachClass, forEachUniNodeList, forEachUniNode));
                }
                forEachUniNodeList.add(forEachUniNode);
              } else {
                for (var sourceClass : tupleSourceRoot.getSourceClasses()) {
                  var forEachUniNodeList =
                      declaredClassToNodeMap.computeIfAbsent(sourceClass, k -> new ArrayList<>(2));
                  forEachUniNodeList.add(tupleSourceRoot);
                }
              }
            });
    var constraintToScorerMap =
        scoreInliner.getConstraints().stream()
            .map(constraint -> (BavetConstraint<Solution_>) constraint)
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    constraint -> buildHelper.getScorer(constraint.getScoringConstraintStream()),
                    (a, b) -> a,
                    LinkedHashMap::new));

    return buildHelper.buildNodeNetwork(
        nodeList, declaredClassToNodeMap, (Map) constraintToScorerMap, scoreDirectorDerived);
  }
}
