package greycos.solver.core.impl.localsearch.decider.acceptor;

import java.util.Arrays;
import java.util.List;

import greycos.solver.core.impl.localsearch.scope.LocalSearchMoveScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchPhaseScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.solver.scope.SolverScope;

/**
 * Combines several acceptors into one. Does a logical AND over the accepted status of its
 * acceptors. For example: combine planning entity and planning value tabu to do tabu on both.
 */
public class CompositeAcceptor<Solution_> extends AbstractAcceptor<Solution_> {

  protected final List<Acceptor<Solution_>> acceptorList;

  public CompositeAcceptor(List<Acceptor<Solution_>> acceptorList) {
    this.acceptorList = acceptorList;
  }

  public CompositeAcceptor(Acceptor<Solution_>... acceptors) {
    this(Arrays.asList(acceptors));
  }

  // ************************************************************************
  // Worker methods
  // ************************************************************************

  @Override
  public void solvingStarted(SolverScope<Solution_> solverScope) {
    for (Acceptor<Solution_> acceptor : acceptorList) {
      acceptor.solvingStarted(solverScope);
    }
  }

  @Override
  public void phaseStarted(LocalSearchPhaseScope<Solution_> phaseScope) {
    for (Acceptor<Solution_> acceptor : acceptorList) {
      acceptor.phaseStarted(phaseScope);
    }
  }

  @Override
  public void stepStarted(LocalSearchStepScope<Solution_> stepScope) {
    for (Acceptor<Solution_> acceptor : acceptorList) {
      acceptor.stepStarted(stepScope);
    }
  }

  @Override
  public boolean isStructurallyValidSolutionAccepted(LocalSearchMoveScope<Solution_> moveScope) {
    for (Acceptor<Solution_> acceptor : acceptorList) {
      boolean accepted = acceptor.isAccepted(moveScope);
      if (!accepted) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void moveEvaluated(LocalSearchMoveScope<Solution_> moveScope) {
    // Every child observes the final decision, including those skipped by short-circuiting.
    for (var acceptor : acceptorList) {
      acceptor.moveEvaluated(moveScope);
    }
  }

  @Override
  public boolean requiresPlanningValues() {
    for (var acceptor : acceptorList) {
      if (acceptor.requiresPlanningValues()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void stepEnded(LocalSearchStepScope<Solution_> stepScope) {
    for (Acceptor<Solution_> acceptor : acceptorList) {
      acceptor.stepEnded(stepScope);
    }
  }

  @Override
  public void migrationStepEnded(LocalSearchStepScope<Solution_> stepScope) {
    for (var acceptor : acceptorList) {
      acceptor.migrationStepEnded(stepScope);
    }
  }

  @Override
  public void migrationStepEnded(
      LocalSearchStepScope<Solution_> stepScope, AcceptorMigrationState state) {
    for (var i = 0; i < acceptorList.size(); i++) {
      var childState =
          state instanceof AcceptorMigrationState.Composite composite
              ? composite.child(i)
              : AcceptorMigrationState.Empty.INSTANCE;
      acceptorList.get(i).migrationStepEnded(stepScope, childState);
    }
  }

  @Override
  public AcceptorMigrationState snapshotMigrationState(
      LocalSearchPhaseScope<Solution_> phaseScope) {
    var children =
        acceptorList.stream().map(acceptor -> acceptor.snapshotMigrationState(phaseScope)).toList();
    return children.stream().allMatch(state -> state == AcceptorMigrationState.Empty.INSTANCE)
        ? AcceptorMigrationState.Empty.INSTANCE
        : new AcceptorMigrationState.Composite(children);
  }

  @Override
  public void resetAfterMigration(LocalSearchPhaseScope<Solution_> phaseScope) {
    for (var acceptor : acceptorList) {
      acceptor.resetAfterMigration(phaseScope);
    }
  }

  @Override
  public void phaseEnded(LocalSearchPhaseScope<Solution_> phaseScope) {
    for (Acceptor<Solution_> acceptor : acceptorList) {
      acceptor.phaseEnded(phaseScope);
    }
  }

  @Override
  public void solvingEnded(SolverScope<Solution_> solverScope) {
    for (Acceptor<Solution_> acceptor : acceptorList) {
      acceptor.solvingEnded(solverScope);
    }
  }
}
