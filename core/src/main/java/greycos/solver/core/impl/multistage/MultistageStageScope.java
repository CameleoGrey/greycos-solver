package greycos.solver.core.impl.multistage;

/** Shared lifetime and operation ownership for every variable view of one stage. */
final class MultistageStageScope {
  final Thread owner = Thread.currentThread();
  boolean active = true;
}
