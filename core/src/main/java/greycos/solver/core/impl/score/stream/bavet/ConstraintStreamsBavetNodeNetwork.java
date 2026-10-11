package greycos.solver.core.impl.score.stream.bavet;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import greycos.solver.core.impl.bavet.AbstractBavetNodeNetwork;
import greycos.solver.core.impl.bavet.common.AbstractNode;
import greycos.solver.core.impl.bavet.common.AbstractRootNode;
import greycos.solver.core.impl.bavet.common.BavetSessionProfile;
import greycos.solver.core.impl.bavet.common.DefaultConstraintProfiler;
import greycos.solver.core.impl.bavet.common.DeferredSettleAware;
import greycos.solver.core.impl.bavet.common.Propagator;
import greycos.solver.core.impl.bavet.uni.ForEachFilteredUniNode;
import greycos.solver.core.impl.bavet.uni.ForEachUnfilteredUniNode;
import greycos.solver.core.impl.score.stream.bavet.common.Scorer;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/**
 * Represents Constraint Streams' network of nodes, specific to a particular session. Nodes only
 * used by disabled constraints have already been removed.
 */
@NullMarked
public final class ConstraintStreamsBavetNodeNetwork extends AbstractBavetNodeNetwork {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ConstraintStreamsBavetNodeNetwork.class);

  public static ConstraintStreamsBavetNodeNetwork of(
      List<AbstractNode> nodeList,
      Map<Class<?>, List<AbstractRootNode<?>>> declaredClassToNodeMap,
      Map<BavetConstraint<?>, Scorer<?>> constraintToScorerMap,
      Function<AbstractNode, Propagator> propagatorFunction,
      @Nullable BavetSessionProfile sessionProfile,
      Map<AbstractNode, DeferredSettleAware> preparations,
      boolean scoreDirectorDerived) {
    var layeredNodes = AbstractBavetNodeNetwork.buildLayeredNodes(nodeList);
    return new ConstraintStreamsBavetNodeNetwork(
        declaredClassToNodeMap,
        constraintToScorerMap,
        layeredNodes,
        propagatorFunction,
        sessionProfile,
        preparations,
        scoreDirectorDerived);
  }

  public static final ConstraintStreamsBavetNodeNetwork EMPTY =
      new ConstraintStreamsBavetNodeNetwork(
          Map.of(),
          Map.of(),
          new AbstractNode[0][0],
          AbstractNode::getPropagator,
          null,
          Map.of(),
          true);

  private final Map<BavetConstraint<?>, Scorer<?>> constraintToScorerMap;
  private final @Nullable BavetSessionProfile sessionProfile;
  private final Map<AbstractNode, DeferredSettleAware> preparations;
  private final boolean scoreDirectorDerived;
  private final boolean ordinaryRootsOnly;
  private boolean printedInactiveConstraints = false;

  /**
   * @param declaredClassToNodeMap starting nodes, one for each class used in the constraints; root
   *     nodes, layer index 0.
   * @param layeredNodes nodes grouped first by their layer, then by their index within the layer;
   *     propagation needs to happen in this order.
   * @param propagatorFunction function to get the propagator for a given node
   */
  private ConstraintStreamsBavetNodeNetwork(
      Map<Class<?>, List<AbstractRootNode<?>>> declaredClassToNodeMap,
      Map<BavetConstraint<?>, Scorer<?>> constraintToScorerMap,
      AbstractNode[][] layeredNodes,
      Function<AbstractNode, Propagator> propagatorFunction,
      @Nullable BavetSessionProfile sessionProfile,
      Map<AbstractNode, DeferredSettleAware> preparations,
      boolean scoreDirectorDerived) {
    super(declaredClassToNodeMap, layeredNodes, propagatorFunction);
    this.constraintToScorerMap = constraintToScorerMap;
    this.sessionProfile = sessionProfile;
    this.preparations = preparations;
    this.scoreDirectorDerived = scoreDirectorDerived;
    this.ordinaryRootsOnly =
        declaredClassToNodeMap.values().stream()
            .flatMap(List::stream)
            .allMatch(
                node ->
                    node instanceof ForEachFilteredUniNode<?>
                        || node instanceof ForEachUnfilteredUniNode<?>);
  }

  public boolean hasOnlyOrdinaryRoots() {
    return ordinaryRootsOnly;
  }

  @Override
  public void settle() {
    super.settle();
    var loggingLevel =
        Level.DEBUG; // Makes sure the check and the logging always operate on the same level.
    if (!LOGGER.isEnabledForLevel(loggingLevel)) {
      return;
    }
    if (!scoreDirectorDerived && !printedInactiveConstraints && isActivationCheckComplete()) {
      printedInactiveConstraints = true;
      if (sessionProfile == null) {
        logInactiveConstraints(loggingLevel);
        return;
      }
      var profiler = sessionProfile.profiler();
      profiler.enterExcluded();
      Throwable failure = null;
      try {
        logInactiveConstraints(loggingLevel);
      } catch (RuntimeException | Error e) {
        failure = e;
        throw e;
      } finally {
        profiler.exitExcluded(failure);
      }
    }
  }

  private void logInactiveConstraints(Level loggingLevel) {
    var substring =
        constraintToScorerMap.entrySet().stream()
            .filter(entry -> !entry.getValue().isActive())
            .map(
                entry ->
                    "  Constraint (%s) with weight set to (%s)."
                        .formatted(entry.getKey().getConstraintRef(), entry.getValue().getWeight()))
            .collect(Collectors.joining(System.lineSeparator()));
    if (substring.isEmpty()) {
      return;
    }
    LOGGER
        .atLevel(loggingLevel)
        .log(
            """
              Constraints deactivated due to being useless in the given working solution:
              %s\
              """
                .formatted(substring));
  }

  @Override
  protected void activationCompleted(Set<AbstractNode> activeNodes) {
    if (sessionProfile != null) {
      sessionProfile.activationCompleted(activeNodes);
    }
  }

  @Override
  protected DeferredSettleAware getDeferredSettleAware(AbstractNode node) {
    return preparations.getOrDefault(node, (DeferredSettleAware) node);
  }

  public @Nullable DefaultConstraintProfiler getConstraintProfiler() {
    return sessionProfile == null ? null : sessionProfile.profiler();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ConstraintStreamsBavetNodeNetwork)) return false;
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
