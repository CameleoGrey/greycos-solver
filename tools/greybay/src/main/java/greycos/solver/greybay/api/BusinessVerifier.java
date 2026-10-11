package greycos.solver.greybay.api;

/** Independently replays business assignments; expected invalid results return a verdict. */
public interface BusinessVerifier<Solution_> {
  ComponentIdentity identity();

  VerificationResult verify(String problemId, Solution_ solution);
}
