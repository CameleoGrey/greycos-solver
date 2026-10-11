package greycos.solver.core.impl.bavet;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import greycos.solver.core.api.score.profile.ConstraintProfile.Operation;
import greycos.solver.core.impl.bavet.common.AbstractRootNode;
import greycos.solver.core.impl.bavet.common.AbstractRootNode.LifecycleOperation;
import greycos.solver.core.impl.bavet.common.BavetSessionProfile;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public abstract class AbstractSession<Network_ extends AbstractBavetNodeNetwork> {

  @SuppressWarnings("unchecked")
  private static final AbstractRootNode<Object>[] EMPTY_NODE_ARRAY = new AbstractRootNode[0];

  protected final Network_ nodeNetwork;
  protected final @Nullable BavetSessionProfile sessionProfile;
  private final Map<Class<?>, AbstractRootNode<Object>[]> insertEffectiveClassToNodeArrayMap;
  private final Map<Class<?>, AbstractRootNode<Object>[]> updateEffectiveClassToNodeArrayMap;
  private final Map<Class<?>, AbstractRootNode<Object>[]> retractEffectiveClassToNodeArrayMap;

  /**
   * The fact class of the most recent operation, and the nodes it resolved to. Consecutive
   * operations typically carry the same class, in which case the map read is skipped.
   */
  private @Nullable Class<?> lastInsertClass;

  private @Nullable Class<?> lastUpdateClass;
  private @Nullable Class<?> lastRetractClass;
  private AbstractRootNode<Object>[] lastInsertNodeArray = EMPTY_NODE_ARRAY;
  private AbstractRootNode<Object>[] lastUpdateNodeArray = EMPTY_NODE_ARRAY;
  private AbstractRootNode<Object>[] lastRetractNodeArray = EMPTY_NODE_ARRAY;
  private boolean initialized = false;
  private boolean settled = false;

  protected AbstractSession(Network_ nodeNetwork) {
    this(nodeNetwork, null);
  }

  protected AbstractSession(Network_ nodeNetwork, @Nullable BavetSessionProfile sessionProfile) {
    this.nodeNetwork = nodeNetwork;
    this.sessionProfile = sessionProfile;
    this.insertEffectiveClassToNodeArrayMap = HashMap.newHashMap(nodeNetwork.forEachNodeCount());
    this.updateEffectiveClassToNodeArrayMap = HashMap.newHashMap(nodeNetwork.forEachNodeCount());
    this.retractEffectiveClassToNodeArrayMap = HashMap.newHashMap(nodeNetwork.forEachNodeCount());
  }

  public final void insert(Object fact) {
    if (sessionProfile != null) {
      profiledInsert(fact);
      return;
    }
    settled = false;
    for (var node : getInsertNodes(fact.getClass())) {
      node.insert(fact);
    }
  }

  private AbstractRootNode<Object>[] getInsertNodes(Class<?> factClass) {
    if (factClass != lastInsertClass) {
      lastInsertNodeArray = findNodes(factClass, LifecycleOperation.INSERT);
      lastInsertClass = factClass;
    }
    return lastInsertNodeArray;
  }

  @SuppressWarnings("unchecked")
  private AbstractRootNode<Object>[] findNodes(
      Class<?> factClass, LifecycleOperation lifecycleOperation) {
    var effectiveClassToNodeArrayMap =
        switch (lifecycleOperation) {
          case INSERT -> insertEffectiveClassToNodeArrayMap;
          case UPDATE -> updateEffectiveClassToNodeArrayMap;
          case RETRACT -> retractEffectiveClassToNodeArrayMap;
        };
    // Map.computeIfAbsent() would have created lambdas on the hot path, this will not.
    var nodeArray = effectiveClassToNodeArrayMap.get(factClass);
    if (nodeArray == null) {
      nodeArray =
          nodeNetwork
              .getRootNodesAcceptingType(factClass)
              .filter(node -> node.supports(lifecycleOperation))
              .toArray(AbstractRootNode[]::new);
      effectiveClassToNodeArrayMap.put(factClass, nodeArray);
    }
    return nodeArray;
  }

  public final void update(Object fact) {
    if (sessionProfile != null) {
      profiledUpdate(fact);
      return;
    }
    settled = false;
    for (var node : getUpdateNodes(fact.getClass())) {
      node.update(fact);
    }
  }

  private AbstractRootNode<Object>[] getUpdateNodes(Class<?> factClass) {
    if (factClass != lastUpdateClass) {
      lastUpdateNodeArray = findNodes(factClass, LifecycleOperation.UPDATE);
      lastUpdateClass = factClass;
    }
    return lastUpdateNodeArray;
  }

  public final void retract(Object fact) {
    if (sessionProfile != null) {
      profiledRetract(fact);
      return;
    }
    settled = false;
    for (var node : getRetractNodes(fact.getClass())) {
      node.retract(fact);
    }
  }

  private AbstractRootNode<Object>[] getRetractNodes(Class<?> factClass) {
    if (factClass != lastRetractClass) {
      lastRetractNodeArray = findNodes(factClass, LifecycleOperation.RETRACT);
      lastRetractClass = factClass;
    }
    return lastRetractNodeArray;
  }

  public final void settle() {
    if (sessionProfile == null) {
      settleInternal();
      return;
    }
    var profiler = sessionProfile.profiler();
    profiler.enterWall(sessionProfile.generation().stage());
    Throwable failure = null;
    try {
      settleInternal();
      sessionProfile.generation().completeInitialization();
    } catch (RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      profiler.exitWall(failure);
    }
  }

  private void settleInternal() {
    if (settled) {
      return;
    }
    nodeNetwork.settle();
    if (!initialized && nodeNetwork.isActivationCheckComplete()) {
      removeInactiveRootNodes(insertEffectiveClassToNodeArrayMap);
      removeInactiveRootNodes(updateEffectiveClassToNodeArrayMap);
      removeInactiveRootNodes(retractEffectiveClassToNodeArrayMap);
      lastInsertClass = null; // The cached arrays may include nodes which are now inactive.
      lastUpdateClass = null;
      lastRetractClass = null;
      initialized = true;
    }
    settled = true;
  }

  private void removeInactiveRootNodes(
      Map<Class<?>, AbstractRootNode<Object>[]> effectiveClassToNodeArrayMap) {
    // Use getActiveNodes() for this, to not rerun the activity checking logic again.
    effectiveClassToNodeArrayMap.replaceAll(
        (k, v) ->
            Arrays.stream(v)
                .filter(nodeNetwork.getActiveNodes()::contains)
                .toArray(AbstractRootNode[]::new));
  }

  private void profiledInsert(Object fact) {
    var profiler = sessionProfile.profiler();
    profiler.enterWall(sessionProfile.generation().stage());
    Throwable failure = null;
    try {
      settled = false;
      for (var node : getInsertNodes(fact.getClass())) {
        profiler.enterUnit(sessionProfile.rootUnit(node), Operation.INSERT);
        Throwable nodeFailure = null;
        try {
          node.insert(fact);
        } catch (RuntimeException | Error e) {
          nodeFailure = e;
          throw e;
        } finally {
          profiler.exitUnit(nodeFailure);
        }
      }
    } catch (RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      profiler.exitWall(failure);
    }
  }

  private void profiledUpdate(Object fact) {
    var profiler = sessionProfile.profiler();
    profiler.enterWall(sessionProfile.generation().stage());
    Throwable failure = null;
    try {
      settled = false;
      for (var node : getUpdateNodes(fact.getClass())) {
        profiler.enterUnit(sessionProfile.rootUnit(node), Operation.UPDATE);
        Throwable nodeFailure = null;
        try {
          node.update(fact);
        } catch (RuntimeException | Error e) {
          nodeFailure = e;
          throw e;
        } finally {
          profiler.exitUnit(nodeFailure);
        }
      }
    } catch (RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      profiler.exitWall(failure);
    }
  }

  private void profiledRetract(Object fact) {
    var profiler = sessionProfile.profiler();
    profiler.enterWall(sessionProfile.generation().stage());
    Throwable failure = null;
    try {
      settled = false;
      for (var node : getRetractNodes(fact.getClass())) {
        profiler.enterUnit(sessionProfile.rootUnit(node), Operation.RETRACT);
        Throwable nodeFailure = null;
        try {
          node.retract(fact);
        } catch (RuntimeException | Error e) {
          nodeFailure = e;
          throw e;
        } finally {
          profiler.exitUnit(nodeFailure);
        }
      }
    } catch (RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      profiler.exitWall(failure);
    }
  }

  public Network_ getNodeNetwork() {
    return nodeNetwork;
  }
}
