package greycos.solver.core.impl.localsearch.decider.acceptor;

import java.util.List;

/** Immutable acceptance state paired with an island's best solution. */
public sealed interface AcceptorMigrationState
    permits AcceptorMigrationState.Empty, AcceptorMigrationState.Composite, LateAcceptanceHistory {

  enum Empty implements AcceptorMigrationState {
    INSTANCE
  }

  /** Child positions preserve the structure of nested composite acceptors. */
  record Composite(List<AcceptorMigrationState> children) implements AcceptorMigrationState {
    public Composite {
      children = List.copyOf(children);
    }

    public AcceptorMigrationState child(int index) {
      return index < children.size() ? children.get(index) : Empty.INSTANCE;
    }
  }
}
