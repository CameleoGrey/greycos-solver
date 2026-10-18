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
import greycos.solver.core.api.solver.IndictmentFetchPolicy;
import greycos.solver.core.impl.bavet.common.AbstractNode;
import greycos.solver.core.impl.bavet.common.AbstractNodeBuildHelper;
import greycos.solver.core.impl.bavet.common.AbstractRootNode;
import greycos.solver.core.impl.bavet.common.BavetAbstractConstraintStream;
import greycos.solver.core.impl.bavet.common.BavetStream;
import greycos.solver.core.impl.bavet.common.BavetStreamBinaryOperation;
import greycos.solver.core.impl.bavet.common.ConstraintNodeProfileId;
import greycos.solver.core.impl.bavet.common.InnerConstraintProfiler;
import greycos.solver.core.impl.bavet.common.ProfilingPropagator;
import greycos.solver.core.impl.bavet.common.TupleSource;
import greycos.solver.core.impl.bavet.common.provenance.ProvenanceAccessor;
import greycos.solver.core.impl.bavet.common.provenance.ProvenanceNode;
import greycos.solver.core.impl.bavet.common.provenance.TupleProvenance;
import greycos.solver.core.impl.bavet.common.tuple.AggregatedTupleLifecycle;
import greycos.solver.core.impl.bavet.common.tuple.ProfilingTupleLifecycle;
import greycos.solver.core.impl.bavet.common.tuple.Tuple;
import greycos.solver.core.impl.bavet.common.tuple.TupleLifecycle;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.variable.declarative.ConsistencyTracker;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraint;
import greycos.solver.core.impl.score.stream.bavet.BavetPrecomputeStream;
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
  private final @Nullable InnerConstraintProfiler constraintProfiler;
  private final Map<
          EntityDescriptor<Solution_>, Map<ForEachFilteringCriteria, @Nullable Predicate<Object>>>
      entityDescriptorToForEachCriteriaToPredicateMap;
  private final Map<BavetAbstractConstraintStream<Solution_>, List<Set<ConstraintNodeProfileId>>>
      streamToProfileIdSets;
  private final Map<BavetScoringConstraintStream<Solution_>, Scorer<?>> streamToScorers =
      new HashMap<>();

  private int nextLifecycleProfilingId = 0;
  private final @Nullable Map<BavetAbstractConstraintStream<Solution_>, ProvenanceAccessor>
      provenanceAccessors;
  private final @Nullable Map<BavetAbstractConstraintStream<Solution_>, ProvenanceAccessor>
      nodeOutputProvenanceAccessors;

  public ConstraintNodeBuildHelper(
      ConsistencyTracker<Solution_> consistencyTracker,
      Set<BavetAbstractConstraintStream<Solution_>> activeStreamSet,
      AbstractScoreInliner<Score_> scoreInliner,
      @Nullable InnerConstraintProfiler profiler) {
    this(
        consistencyTracker,
        activeStreamSet,
        scoreInliner,
        profiler,
        scoreInliner.getConstraintMatchPolicy().getIndictmentFetchPolicy()
            == IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL);
  }

  public ConstraintNodeBuildHelper(
      ConsistencyTracker<Solution_> consistencyTracker,
      Set<BavetAbstractConstraintStream<Solution_>> activeStreamSet,
      AbstractScoreInliner<Score_> scoreInliner,
      @Nullable InnerConstraintProfiler profiler,
      boolean originalContributorTracking) {
    super(activeStreamSet);
    this.consistencyTracker = consistencyTracker;
    this.scoreInliner = scoreInliner;
    this.constraintProfiler = profiler;
    this.entityDescriptorToForEachCriteriaToPredicateMap = new HashMap<>();
    this.streamToProfileIdSets = HashMap.newHashMap(Math.max(16, activeStreamSet.size() / 2));
    if (originalContributorTracking) {
      provenanceAccessors = new IdentityHashMap<>();
      nodeOutputProvenanceAccessors = new IdentityHashMap<>();
      // Reserve every lineage slot before reverse node construction extracts any tuple stores.
      for (var stream : activeStreamSet) {
        if (stream instanceof TupleSource && !(stream instanceof BavetPrecomputeStream)) {
          var accessor = new ProvenanceAccessor(reserveTupleStoreIndex(stream));
          provenanceAccessors.put(stream, accessor);
          nodeOutputProvenanceAccessors.putIfAbsent(stream, accessor);
          // An aft bridge owns the output tuple store of its map/group/flatten/precompute parent.
          var parent = stream.getParent();
          if (parent != null) {
            nodeOutputProvenanceAccessors.put(parent, accessor);
          }
        } else if (stream instanceof BavetIfExistsConstraintStream<?>) {
          // Existence passes the same physical tuple down another logical branch.
          var accessor = new ProvenanceAccessor(reserveTupleStoreIndex(stream.getTupleSource()));
          provenanceAccessors.put(stream, accessor);
          nodeOutputProvenanceAccessors.put(stream, accessor);
        }
      }
    } else {
      provenanceAccessors = null;
      nodeOutputProvenanceAccessors = null;
    }
  }

  public boolean isOriginalContributorTrackingEnabled() {
    return provenanceAccessors != null;
  }

  /**
   * Returns the lineage visible on this logical branch, or null when tracking was not requested.
   */
  public @Nullable ProvenanceAccessor getProvenanceAccessor(
      BavetAbstractConstraintStream<?> stream) {
    if (provenanceAccessors == null) {
      return null;
    }
    var accessor = provenanceAccessors.get(stream);
    if (accessor == null) {
      accessor = nodeOutputProvenanceAccessors.get(stream);
    }
    if (accessor != null) {
      return accessor;
    }
    var parent = stream.getParent();
    if (parent == null) {
      throw new IllegalStateException("No provenance source for stream (%s).".formatted(stream));
    }
    return getProvenanceAccessor(parent);
  }

  private void configureProvenance(
      AbstractNode node, BavetAbstractConstraintStream<Solution_> creator) {
    if (provenanceAccessors == null) {
      return;
    }
    if (!(node instanceof ProvenanceNode provenanceNode)) {
      throw new IllegalStateException(
          "The node (%s) does not support original-source analysis.".formatted(node.getClass()));
    }
    var output = nodeOutputProvenanceAccessors.get(creator);
    if (output == null) {
      throw new IllegalStateException("No provenance output for stream (%s).".formatted(creator));
    }
    if (creator instanceof BavetConstraintStreamBinaryOperation<?> binary) {
      provenanceNode.setProvenance(
          new TupleProvenance(
              getProvenanceAccessor((BavetAbstractConstraintStream<?>) binary.getLeftParent()),
              getProvenanceAccessor((BavetAbstractConstraintStream<?>) binary.getRightParent()),
              output));
    } else {
      var parent = creator.getParent();
      provenanceNode.setProvenance(
          new TupleProvenance(parent == null ? null : getProvenanceAccessor(parent), null, output));
    }
  }

  @Override
  public void addNode(
      AbstractNode node,
      BavetAbstractConstraintStream<Solution_> creator,
      @Nullable BavetAbstractConstraintStream<Solution_> parent) {
    configureProvenance(node, creator);
    super.addNode(node, creator, parent);
  }

  @Override
  public void addNode(
      AbstractNode node,
      BavetAbstractConstraintStream<Solution_> creator,
      BavetAbstractConstraintStream<Solution_> leftParent,
      BavetAbstractConstraintStream<Solution_> rightParent) {
    configureProvenance(node, creator);
    super.addNode(node, creator, leftParent, rightParent);
  }

  @Override
  public <Tuple_ extends Tuple> void putInsertUpdateRetract(
      BavetAbstractConstraintStream<Solution_> stream, TupleLifecycle<Tuple_> tupleLifecycle) {
    if (constraintProfiler != null) {
      var out =
          TupleLifecycle.profiling(
              constraintProfiler, nextLifecycleProfilingId, stream, tupleLifecycle);
      super.putInsertUpdateRetract(stream, out);
      updateConstraintProfileIdSet(stream, out);

      if (tupleLifecycle instanceof Scorer<Tuple_> scorer) {
        // This is a scorer, so we can navigate up its parents
        // to find all locations corresponding to this constraint
        var queue = new ArrayDeque<BavetStream>();
        var constraintSet = new LinkedHashSet<ConstraintNodeProfileId>();
        queue.add(stream);
        while (!queue.isEmpty()) {
          var currentStream = (BavetAbstractConstraintStream<Solution_>) queue.poll();
          var streamSets =
              streamToProfileIdSets.computeIfAbsent(currentStream, ignored -> new ArrayList<>());
          streamSets.add(constraintSet);
          var lifecycle = getTupleLifecycle(currentStream);
          if (lifecycle instanceof ProfilingTupleLifecycle<?> profilingTupleLifecycle) {
            constraintSet.add(profilingTupleLifecycle.profileId());
          }
          if (currentStream instanceof BavetStreamBinaryOperation<?> binaryOperation) {
            queue.add(binaryOperation.getLeftParent());
            queue.add(binaryOperation.getRightParent());
          } else if (currentStream.getParent() != null) {
            queue.add(currentStream.getParent());
          }
        }
        constraintProfiler.registerConstraint(scorer.getConstraintRef(), constraintSet);
      }
      nextLifecycleProfilingId++;
    } else {
      super.putInsertUpdateRetract(stream, tupleLifecycle);
    }
    if (tupleLifecycle instanceof Scorer<?> scorer) {
      streamToScorers.put((BavetScoringConstraintStream<Solution_>) stream, scorer);
    }
  }

  private void updateConstraintProfileIdSet(
      BavetAbstractConstraintStream<Solution_> stream, TupleLifecycle<?> tupleLifecycle) {
    if (tupleLifecycle instanceof ProfilingTupleLifecycle<?> profilingTupleLifecycle) {
      var affectedSets = streamToProfileIdSets.getOrDefault(stream, Collections.emptyList());
      for (var affectedSet : affectedSets) {
        affectedSet.add(profilingTupleLifecycle.profileId());
      }
    } else if (tupleLifecycle instanceof AggregatedTupleLifecycle<?> aggregated) {
      for (var innerLifecycle : aggregated.downstream()) {
        updateConstraintProfileIdSet(stream, innerLifecycle);
      }
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

  public ConstraintStreamsBavetNodeNetwork buildNodeNetwork(
      List<AbstractNode> nodeList,
      Map<Class<?>, List<AbstractRootNode<?>>> declaredClassToNodeMap,
      Map<BavetConstraint<Solution_>, Scorer<?>> constraintToScorerMap,
      boolean scoreDirectorDerived) {
    return ConstraintStreamsBavetNodeNetwork.of(
        nodeList,
        declaredClassToNodeMap,
        (Map) constraintToScorerMap,
        node -> {
          if (constraintProfiler == null) {
            return node.getPropagator();
          }
          var profileKey = nextLifecycleProfilingId++;
          var profileId =
              new ConstraintNodeProfileId(
                  profileKey,
                  node.getStreamKind(),
                  ConstraintNodeProfileId.Qualifier.NODE,
                  node.getLocationSet());
          constraintProfiler.register(profileId);
          var stream = getNodeCreator(node);
          for (var affectedSet :
              streamToProfileIdSets.getOrDefault(stream, Collections.emptyList())) {
            affectedSet.add(profileId);
          }
          return new ProfilingPropagator(constraintProfiler, profileId, node.getPropagator());
        },
        constraintProfiler,
        scoreDirectorDerived);
  }

  public ConstraintStreamsBavetNodeNetwork buildPrecomputeNodeNetwork(
      List<AbstractNode> nodeList,
      Map<Class<?>, List<AbstractRootNode<?>>> declaredClassToNodeMap) {
    return buildNodeNetwork(
        nodeList, declaredClassToNodeMap, Collections.emptyMap(), true); // Reduces logging.
  }
}
