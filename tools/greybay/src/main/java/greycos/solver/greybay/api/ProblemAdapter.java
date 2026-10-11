package greycos.solver.greybay.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Supplies fresh, independently owned planning graphs and content identities for one model. */
public interface ProblemAdapter<Solution_> {
  ComponentIdentity identity();

  ProblemDescriptor describe(String problemId);

  /**
   * Loads a fresh graph and verifies that its content matches {@link #describe(String)}. Problem
   * facts must remain immutable during evaluation. The adapter must not reuse mutable assignments
   * or shadow state from a previous invocation.
   */
  Solution_ loadFresh(String problemId);

  default Optional<BusinessVerifier<Solution_>> verifier() {
    return Optional.empty();
  }

  /** File-backed adapters declare all input files so exports can reject filesystem aliases. */
  default List<Path> inputPaths() {
    return List.of();
  }
}
