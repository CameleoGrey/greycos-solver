package ai.greycos.solver.core.impl.solver.thread;

import ai.greycos.solver.core.impl.partitionedsearch.PartitionedSearchPhase;

/**
 * Types of child threads used in multithreaded solving.
 */
public enum ChildThreadType {
  PART_THREAD,
  MOVE_THREAD;
}
