package greycos.solver.core.impl.multistage;

import java.util.List;

import greycos.solver.core.api.solver.multistage.MultistageOperation;

/** Operations are intents resolved against live state, including within a sequence. */
record MultistageOperationImpl<Solution_>(Object owner, List<Intent> intents)
    implements MultistageOperation<Solution_> {
  MultistageOperationImpl {
    intents = List.copyOf(intents);
  }

  enum Kind {
    ASSIGN,
    UNASSIGN_BASIC,
    SWAP_BASIC,
    PLACE,
    UNASSIGN_LIST,
    SWAP_LIST,
    REVERSE,
    RELOCATE
  }

  record Intent(Kind kind, Object first, Object second, int from, int to, int index) {}
}
