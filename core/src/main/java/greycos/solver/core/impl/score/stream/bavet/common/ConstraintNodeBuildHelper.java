package greycos.solver.core.impl.score.stream.bavet.common;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.profile.ConstraintProfile.Activity;
import greycos.solver.core.api.score.profile.ConstraintProfile.OwnershipState;
import greycos.solver.core.api.score.profile.ConstraintProfile.Role;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.impl.bavet.common.AbstractNode;
import greycos.solver.core.impl.bavet.common.AbstractNodeBuildHelper;
import greycos.solver.core.impl.bavet.common.AbstractRootNode;
import greycos.solver.core.impl.bavet.common.BavetAbstractConstraintStream;
import greycos.solver.core.impl.bavet.common.BavetSessionProfile;
import greycos.solver.core.impl.bavet.common.BavetStreamBinaryOperation;
import greycos.solver.core.impl.bavet.common.DefaultConstraintProfiler.Unit;
import greycos.solver.core.impl.bavet.common.DeferredSettleAware;
import greycos.solver.core.impl.bavet.common.ProfilingDeferredSettleAware;
import greycos.solver.core.impl.bavet.common.ProfilingPropagator;
import greycos.solver.core.impl.bavet.common.Propagator;
import greycos.solver.core.impl.bavet.common.tuple.ProfilingTupleLifecycle;
import greycos.solver.core.impl.bavet.common.tuple.Tuple;
import greycos.solver.core.impl.bavet.common.tuple.TupleLifecycle;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.declarative.ConsistencyTracker;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraint;
import greycos.solver.core.impl.score.stream.bavet.ConstraintStreamsBavetNodeNetwork;
import greycos.solver.core.impl.score.stream.common.ForEachFilteringCriteria;
import greycos.solver.core.impl.score.stream.common.inliner.AbstractScoreInliner;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ConstraintNodeBuildHelper<Solution_, Score_ extends Score<Score_>>
    extends AbstractNodeBuildHelper<BavetAbstractConstraintStream<Solution_>> {

  private final AbstractScoreInliner<Score_> scoreInliner;
  private final ConsistencyTracker<Solution_> consistencyTracker;
  private final @Nullable BavetSessionProfile sessionProfile;
  private final Map<
          EntityDescriptor<Solution_>, Map<ForEachFilteringCriteria, @Nullable Predicate<Object>>>
      entityDescriptorToForEachCriteriaToPredicateMap = new HashMap<>();
  private final Map<BavetScoringConstraintStream<Solution_>, Scorer<?>> streamToScorers =
      new HashMap<>();
  private final @Nullable
      Map<BavetAbstractConstraintStream<Solution_>, List<ProfilingTupleLifecycle<?>>>
      streamToLifecycles;
  private final @Nullable Map<BavetAbstractConstraintStream<Solution_>, List<AbstractNode>>
      streamToNodes;
  private final @Nullable Map<AbstractNode, List<Unit>> nodeToUnits;

  public ConstraintNodeBuildHelper(
      ConsistencyTracker<Solution_> consistencyTracker,
      Set<BavetAbstractConstraintStream<Solution_>> activeStreamSet,
      AbstractScoreInliner<Score_> scoreInliner,
      @Nullable BavetSessionProfile sessionProfile) {
    super(activeStreamSet);
    this.consistencyTracker = consistencyTracker;
    this.scoreInliner = scoreInliner;
    this.sessionProfile = sessionProfile;
    this.streamToLifecycles = sessionProfile == null ? null : new IdentityHashMap<>();
    this.streamToNodes = sessionProfile == null ? null : new IdentityHashMap<>();
    this.nodeToUnits = sessionProfile == null ? null : new IdentityHashMap<>();
  }

  @Override
  public <Tuple_ extends Tuple> void putInsertUpdateRetract(
      BavetAbstractConstraintStream<Solution_> stream, TupleLifecycle<Tuple_> tupleLifecycle) {
    if (sessionProfile == null) {
      super.putInsertUpdateRetract(stream, tupleLifecycle);
    } else {
      var out = TupleLifecycle.profiling(sessionProfile, stream, tupleLifecycle);
      super.putInsertUpdateRetract(stream, out);
      if (out instanceof ProfilingTupleLifecycle<?> lifecycle) {
        // Bind the original edge only. An aggregate does not transfer its sibling units to this
        // stream.
        streamToLifecycles.computeIfAbsent(stream, ignored -> new ArrayList<>()).add(lifecycle);
      }
    }
    if (tupleLifecycle instanceof Scorer<?> scorer) {
      streamToScorers.put((BavetScoringConstraintStream<Solution_>) stream, scorer);
    }
  }

  public AbstractScoreInliner<Score_> getScoreInliner() {
    return scoreInliner;
  }

  public Scorer<?> getScorer(BavetScoringConstraintStream<Solution_> stream) {
    return streamToScorers.get(stream);
  }

  @SuppressWarnings("unchecked")
  public <A> @Nullable Predicate<A> getForEachPredicateForEntityDescriptorAndCriteria(
      EntityDescriptor<Solution_> entityDescriptor, ForEachFilteringCriteria criteria) {
    var predicateMap =
        entityDescriptorToForEachCriteriaToPredicateMap.computeIfAbsent(
            entityDescriptor, ignored -> new HashMap<>());
    return (Predicate<A>)
        predicateMap.computeIfAbsent(
            criteria,
            ignored -> criteria.getFilterForEntityDescriptor(consistencyTracker, entityDescriptor));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public ConstraintStreamsBavetNodeNetwork buildNodeNetwork(
      List<AbstractNode> nodeList,
      Map<Class<?>, List<AbstractRootNode<?>>> declaredClassToNodeMap,
      Map<BavetConstraint<Solution_>, Scorer<?>> constraintToScorerMap,
      boolean scoreDirectorDerived) {
    if (sessionProfile == null) {
      return ConstraintStreamsBavetNodeNetwork.of(
          nodeList,
          declaredClassToNodeMap,
          (Map) constraintToScorerMap,
          AbstractNode::getPropagator,
          null,
          Map.of(),
          scoreDirectorDerived);
    }
    var profiler = sessionProfile.profiler();
    var generation = sessionProfile.generation();
    var propagators = new IdentityHashMap<AbstractNode, Propagator>();
    var preparations = new IdentityHashMap<AbstractNode, DeferredSettleAware>();
    var rootUnits = new Unit[nodeList.size()];
    for (var node : nodeList) {
      var stream = getNodeCreator(node);
      streamToNodes.computeIfAbsent(stream, ignored -> new ArrayList<>()).add(node);
      var units = new ArrayList<Unit>(3);
      nodeToUnits.put(node, units);
      if (node instanceof AbstractRootNode<?>) {
        var unit =
            profiler.registerUnit(
                generation, node.getStreamKind().name(), Role.ROOT, node.getLocationSet());
        rootUnits[Math.toIntExact(node.getId())] = unit;
        units.add(unit);
      }
      var propagation =
          profiler.registerUnit(
              generation, node.getStreamKind().name(), Role.PROPAGATOR, node.getLocationSet());
      units.add(propagation);
      propagators.put(node, new ProfilingPropagator(profiler, propagation, node.getPropagator()));
      if (node instanceof DeferredSettleAware deferred && deferred.canDeferWork()) {
        var preparation =
            profiler.registerUnit(
                generation, node.getStreamKind().name(), Role.PREPARATION, node.getLocationSet());
        units.add(preparation);
        preparations.put(node, new ProfilingDeferredSettleAware(profiler, preparation, deferred));
      }
    }
    sessionProfile.setRootUnits(rootUnits);
    generation.resolveOwnership(OwnershipState.ENABLED_CANDIDATES, owners(null));
    sessionProfile.setActivationListener(
        activeNodes -> {
          var activeOwners = owners(activeNodes);
          for (var entry : streamToScorers.entrySet()) {
            generation.setConstraintActivity(
                entry.getValue().getConstraintRef(),
                entry.getValue().isActive() ? Activity.ACTIVE : Activity.INACTIVE);
          }
          generation.resolveOwnership(OwnershipState.ACTIVE, activeOwners);
        });
    return ConstraintStreamsBavetNodeNetwork.of(
        nodeList,
        declaredClassToNodeMap,
        (Map) constraintToScorerMap,
        propagators::get,
        sessionProfile,
        preparations,
        scoreDirectorDerived);
  }

  /**
   * Traverse structural dependencies before loading, and surviving input edges after activation.
   */
  @SuppressWarnings("unchecked")
  private Map<Unit, Set<ConstraintRef>> owners(@Nullable Set<AbstractNode> activeNodes) {
    var owners = new IdentityHashMap<Unit, Set<ConstraintRef>>();
    for (var entry : streamToScorers.entrySet()) {
      if (activeNodes != null && !entry.getValue().isActive()) {
        continue;
      }
      var owner = entry.getValue().getConstraintRef();
      var seen =
          Collections.newSetFromMap(
              new IdentityHashMap<BavetAbstractConstraintStream<Solution_>, Boolean>());
      var queue = new ArrayDeque<BavetAbstractConstraintStream<Solution_>>();
      queue.add((BavetAbstractConstraintStream<Solution_>) entry.getKey());
      while (!queue.isEmpty()) {
        var stream = queue.removeFirst();
        if (!seen.add(stream)) {
          continue;
        }
        var nodes = streamToNodes.getOrDefault(stream, List.of());
        if (activeNodes != null
            && !nodes.isEmpty()
            && nodes.stream().noneMatch(activeNodes::contains)) {
          continue;
        }
        for (var node : nodes) {
          if (activeNodes == null || activeNodes.contains(node)) {
            for (var unit : nodeToUnits.get(node)) {
              owners.computeIfAbsent(unit, ignored -> new LinkedHashSet<>()).add(owner);
            }
          }
        }
        var lifecycles = streamToLifecycles.getOrDefault(stream, List.of());
        boolean inputActive = lifecycles.isEmpty();
        for (var lifecycle : lifecycles) {
          if (activeNodes == null || lifecycle.wasActive()) {
            inputActive = true;
            owners.computeIfAbsent(lifecycle.unit(), ignored -> new LinkedHashSet<>()).add(owner);
          }
        }
        if (!inputActive) {
          continue;
        }
        if (stream instanceof BavetStreamBinaryOperation<?> binary) {
          queue.add((BavetAbstractConstraintStream<Solution_>) binary.getLeftParent());
          queue.add((BavetAbstractConstraintStream<Solution_>) binary.getRightParent());
        } else if (stream.getParent() != null) {
          queue.add(stream.getParent());
        }
      }
    }
    return owners;
  }

  public ConstraintStreamsBavetNodeNetwork buildPrecomputeNodeNetwork(
      List<AbstractNode> nodeList,
      Map<Class<?>, List<AbstractRootNode<?>>> declaredClassToNodeMap) {
    return buildNodeNetwork(nodeList, declaredClassToNodeMap, Collections.emptyMap(), true);
  }
}
