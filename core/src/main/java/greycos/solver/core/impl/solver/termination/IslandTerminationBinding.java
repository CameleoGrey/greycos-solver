package greycos.solver.core.impl.solver.termination;

import java.util.function.Supplier;

import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.impl.solver.thread.ChildThreadType;

/** Copies an inherited termination graph while keeping score targets on the shared population. */
public final class IslandTerminationBinding {

  private IslandTerminationBinding() {}

  public static void validate(Termination<?> termination, String configurationPath) {
    if (termination instanceof SolverBridgePhaseTermination<?> bridge) {
      validate(bridge.solverTermination, configurationPath + ".solver");
    } else if (termination.getClass() == PhaseToSolverTerminationBridge.class) {
      validate(
          ((PhaseToSolverTerminationBridge<?>) termination).getSolverTermination(),
          configurationPath + ".solver");
    } else if (termination instanceof AbstractCompositeTermination<?> composite) {
      for (int index = 0; index < composite.terminationList.size(); index++) {
        validate(
            composite.terminationList.get(index),
            configurationPath + ".termination[" + index + "]");
      }
    } else if (!(termination instanceof BestScoreTermination)
        && !(termination instanceof BestScoreFeasibleTermination)
        && !(termination instanceof ChildThreadSupportingTermination<?, ?>)) {
      throw new IllegalArgumentException(
          "The island termination class (%s) at (%s) does not support child termination.\n"
                  .formatted(termination.getClass().getName(), configurationPath)
              + "Implement ChildThreadSupportingTermination with an independent child instance, "
              + "or move this termination to an explicitly configured inner phase.");
    }
  }

  static <Solution_> SolverTermination<Solution_> copy(
      SolverTermination<Solution_> definition,
      SolverScope<Solution_> childScope,
      Supplier<IslandTerminationBudget.Progress> progress) {
    var copy = copyTree(definition, childScope, progress);
    if (copy instanceof SolverTermination<Solution_> solverTermination) {
      return solverTermination;
    }
    return UniversalTermination.or(copy);
  }

  private static <Solution_> Termination<Solution_> copyTree(
      Termination<Solution_> definition,
      SolverScope<Solution_> childScope,
      Supplier<IslandTerminationBudget.Progress> progress) {
    if (definition instanceof AbstractCompositeTermination<Solution_> composite) {
      var children =
          composite.terminationList.stream()
              .map(child -> copyTree(child, childScope, progress))
              .toList();
      return composite instanceof AndCompositeTermination
          ? new AndCompositeTermination<>(children)
          : new OrCompositeTermination<>(children);
    }
    if (definition instanceof SolverBridgePhaseTermination<Solution_> bridge) {
      // PART_THREAD phase factories rebuild the solver bridge around the copied graph.
      return copyTree(bridge.solverTermination, childScope, progress);
    }
    if (definition.getClass() == PhaseToSolverTerminationBridge.class) {
      return copyTree(
          ((PhaseToSolverTerminationBridge<Solution_>) definition).getSolverTermination(),
          childScope,
          progress);
    }
    if (definition instanceof BestScoreTermination
        || definition instanceof BestScoreFeasibleTermination) {
      return new SharedScoreTermination<>(definition, progress, true);
    }
    return ChildThreadSupportingTermination
        .<Solution_, SolverScope<Solution_>>assertChildThreadSupport(definition)
        .createChildThreadTermination(childScope, ChildThreadType.PART_THREAD);
  }
}
