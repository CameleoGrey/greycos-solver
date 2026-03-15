package ai.greycos.solver.core.impl.bavet.common;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import ai.greycos.solver.core.impl.bavet.NodeNetwork;
import ai.greycos.solver.core.impl.bavet.common.tuple.RecordingTupleLifecycle;
import ai.greycos.solver.core.impl.bavet.common.tuple.Tuple;
import ai.greycos.solver.core.impl.bavet.common.tuple.TupleLifecycle;
import ai.greycos.solver.core.impl.bavet.common.tuple.TupleState;
import ai.greycos.solver.core.impl.score.stream.bavet.common.BavetPrecomputeBuildHelper;
import ai.greycos.solver.core.impl.util.CollectionUtils;

import org.jspecify.annotations.NullMarked;

/**
 * The implementation records the tuples each object affects inside an internal {@link NodeNetwork}
 * and replays them on update. Used by {@link AbstractPrecomputeNode} to precompute constraint
 * streams.
 *
 * @param <Tuple_>
 */
@NullMarked
public final class RecordAndReplayPropagator<Tuple_ extends Tuple> implements Propagator {

  private static final int MAX_REUSABLE_TUPLE_LIST_POOL_SIZE = 256;
  private static final int MAX_REUSABLE_TUPLE_LIST_SIZE = 1024;

  private final Set<Object> retractQueue;
  private final Set<Object> insertQueue;

  // Store entities and facts separately; we don't need to precompute
  // the tuples for facts, since facts never update
  private final Set<Object> seenEntitySet;
  private final Set<Object> seenFactSet;

  private final Supplier<BavetPrecomputeBuildHelper<Tuple_>> precomputeBuildHelperSupplier;
  private final UnaryOperator<Tuple_> internalTupleToOutputTupleMapper;
  private final Map<Object, List<Tuple_>> objectToOutputTuplesMap;
  private final Set<Object> alreadyUpdatingSet = Collections.newSetFromMap(new IdentityHashMap<>());
  private final Map<Class<?>, Boolean> objectClassToIsEntitySourceClassMap;
  private final Map<Class<?>, List<BavetRootNode<?>>> rootNodesByClassScratch;
  private final IdentityHashMap<Tuple_, Tuple_> internalTupleToOutputTupleMapScratch;
  private final ArrayDeque<ArrayList<Tuple_>> reusableTupleListPool;

  private final StaticPropagationQueue<Tuple_> propagationQueue;

  public RecordAndReplayPropagator(
      Supplier<BavetPrecomputeBuildHelper<Tuple_>> precomputeBuildHelperSupplier,
      UnaryOperator<Tuple_> internalTupleToOutputTupleMapper,
      TupleLifecycle<Tuple_> nextNodesTupleLifecycle,
      int size) {
    this.precomputeBuildHelperSupplier = precomputeBuildHelperSupplier;
    this.internalTupleToOutputTupleMapper = internalTupleToOutputTupleMapper;
    this.objectToOutputTuplesMap = CollectionUtils.newIdentityHashMap(size);

    // Guesstimate that updates are dominant.
    this.retractQueue = CollectionUtils.newIdentityHashSet(size / 20);
    this.insertQueue = CollectionUtils.newIdentityHashSet(size / 20);
    this.objectClassToIsEntitySourceClassMap = new HashMap<>();
    this.seenEntitySet = CollectionUtils.newIdentityHashSet(size);
    this.seenFactSet = CollectionUtils.newIdentityHashSet(size);
    this.rootNodesByClassScratch = new HashMap<>();
    this.internalTupleToOutputTupleMapScratch = new IdentityHashMap<>(size);
    this.reusableTupleListPool = new ArrayDeque<>();

    this.propagationQueue = new StaticPropagationQueue<>(nextNodesTupleLifecycle);
  }

  public RecordAndReplayPropagator(
      Supplier<BavetPrecomputeBuildHelper<Tuple_>> precomputeBuildHelperSupplier,
      UnaryOperator<Tuple_> internalTupleToOutputTupleMapper,
      TupleLifecycle<Tuple_> nextNodesTupleLifecycle) {
    this(
        precomputeBuildHelperSupplier,
        internalTupleToOutputTupleMapper,
        nextNodesTupleLifecycle,
        1000);
  }

  public void insert(Object object) {
    // do not remove a retract of the same fact (a fact was updated)
    insertQueue.add(object);
  }

  public void update(Object object) {
    if (!alreadyUpdatingSet.add(object)) {
      // The list was already sent to the propagation queue.
      // Don't iterate over it again, even though the queue would deduplicate its contents.
      return;
    }
    // Updates happen very frequently, so we optimize by avoiding the update queue
    // and going straight to the propagation queue.
    // The propagation queue deduplicates updates internally.
    var outTupleList = objectToOutputTuplesMap.get(object);
    if (outTupleList != null) {
      for (int i = 0, outTupleListSize = outTupleList.size(); i < outTupleListSize; i++) {
        propagationQueue.update(outTupleList.get(i));
      }
    }
  }

  public void retract(Object object) {
    // remove an insert then retract (a fact was inserted but retracted before settling)
    // do not remove a retract then insert (a fact was updated)
    if (!insertQueue.remove(object)) {
      retractQueue.add(object);
    }
  }

  @Override
  public void propagateRetracts() {
    if (!retractQueue.isEmpty() || !insertQueue.isEmpty()) {
      var precomputeBuildHelper = precomputeBuildHelperSupplier.get();
      var internalNodeNetwork = precomputeBuildHelper.getNodeNetwork();
      rootNodesByClassScratch.clear();
      var recordingTupleLifecycle = precomputeBuildHelper.getRecordingTupleLifecycle();

      invalidateCache();
      seenEntitySet.removeAll(retractQueue);
      seenFactSet.removeAll(retractQueue);

      for (var entity : seenEntitySet) {
        for (var rootNode : getRootNodes(entity, internalNodeNetwork, rootNodesByClassScratch)) {
          rootNode.insert(entity);
        }
      }

      for (var fact : seenFactSet) {
        for (var rootNode : getRootNodes(fact, internalNodeNetwork, rootNodesByClassScratch)) {
          rootNode.insert(fact);
        }
      }

      // Do not remove queued retracts from inserts; if a fact property
      // change, there will be both a retract and insert for that fact
      for (var object : insertQueue) {
        if (objectClassToIsEntitySourceClassMap.computeIfAbsent(
            object.getClass(), precomputeBuildHelper::isSourceEntityClass)) {
          seenEntitySet.add(object);
        } else {
          seenFactSet.add(object);
        }
        for (var rootNode : getRootNodes(object, internalNodeNetwork, rootNodesByClassScratch)) {
          rootNode.insert(object);
        }
      }

      retractQueue.clear();
      insertQueue.clear();

      // settle the inner node network, so the inserts/retracts do not interfere
      // with the recording of the first object's tuples
      internalNodeNetwork.settle();
      recalculateTuples(internalNodeNetwork, rootNodesByClassScratch, recordingTupleLifecycle);

      propagationQueue.propagateRetracts();
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static <A> List<BavetRootNode<A>> getRootNodes(
      Object object,
      NodeNetwork internalNodeNetwork,
      Map<Class<?>, List<BavetRootNode<?>>> objectClassToRootNodes) {
    return (List)
        objectClassToRootNodes.computeIfAbsent(
            object.getClass(),
            clazz -> {
              var out = new ArrayList<BavetRootNode<?>>();
              internalNodeNetwork.getRootNodesAcceptingType(clazz).forEach(out::add);
              return out;
            });
  }

  @Override
  public void propagateUpdates() {
    propagationQueue.propagateUpdates();
    alreadyUpdatingSet.clear();
  }

  @Override
  public void propagateInserts() {
    // propagateRetracts clears/process the insertQueue
    propagationQueue.propagateInserts();
  }

  private void insertIfAbsent(Tuple_ tuple) {
    var state = tuple.getState();
    if (state != TupleState.CREATING) {
      propagationQueue.insert(tuple);
    }
  }

  private void retractIfPresent(Tuple_ tuple) {
    var state = tuple.getState();
    if (state.isDirty()) {
      if (state == TupleState.DYING || state == TupleState.ABORTING) {
        // We already retracted this tuple from another list, so we
        // don't need to do anything
        return;
      }
      propagationQueue.retract(
          tuple, state == TupleState.CREATING ? TupleState.ABORTING : TupleState.DYING);
    } else {
      propagationQueue.retract(tuple, TupleState.DYING);
    }
  }

  private void invalidateCache() {
    for (var tupleList : objectToOutputTuplesMap.values()) {
      for (int i = 0, tupleListSize = tupleList.size(); i < tupleListSize; i++) {
        retractIfPresent(tupleList.get(i));
      }
      recycleTupleList(tupleList);
    }
    objectToOutputTuplesMap.clear();
  }

  private void recalculateTuples(
      NodeNetwork internalNodeNetwork,
      Map<Class<?>, List<BavetRootNode<?>>> classToRootNodeList,
      RecordingTupleLifecycle<Tuple_> recordingTupleLifecycle) {
    internalTupleToOutputTupleMapScratch.clear();
    for (var invalidated : seenEntitySet) {
      var mappedTuples = borrowTupleList();
      try (var unusedActiveRecordingLifecycle =
          recordingTupleLifecycle.recordInto(
              new TupleRecorder<>(
                  mappedTuples,
                  internalTupleToOutputTupleMapper,
                  internalTupleToOutputTupleMapScratch))) {
        // Do a fake update on the object and settle the network; this will update precisely the
        // tuples mapped to this node, which will then be recorded
        var rootNodeList = classToRootNodeList.get(invalidated.getClass());
        for (int i = 0, rootNodeListSize = rootNodeList.size(); i < rootNodeListSize; i++) {
          ((BavetRootNode<Object>) rootNodeList.get(i)).update(invalidated);
        }
        internalNodeNetwork.settle();
      }
      if (mappedTuples.isEmpty()) {
        recycleTupleList(mappedTuples);
        objectToOutputTuplesMap.put(invalidated, Collections.emptyList());
      } else {
        objectToOutputTuplesMap.put(invalidated, mappedTuples);
      }
    }
    for (var tupleList : objectToOutputTuplesMap.values()) {
      for (int i = 0, tupleListSize = tupleList.size(); i < tupleListSize; i++) {
        insertIfAbsent(tupleList.get(i));
      }
    }
  }

  private ArrayList<Tuple_> borrowTupleList() {
    var tupleList = reusableTupleListPool.pollLast();
    return tupleList != null ? tupleList : new ArrayList<>();
  }

  @SuppressWarnings("unchecked")
  private void recycleTupleList(List<Tuple_> tupleList) {
    if (tupleList instanceof ArrayList<?> arrayList) {
      int tupleListSize = tupleList.size();
      var reusableTupleList = (ArrayList<Tuple_>) arrayList;
      reusableTupleList.clear();
      if (tupleListSize <= MAX_REUSABLE_TUPLE_LIST_SIZE
          && reusableTupleListPool.size() < MAX_REUSABLE_TUPLE_LIST_POOL_SIZE) {
        reusableTupleListPool.addLast(reusableTupleList);
      }
    }
  }
}
