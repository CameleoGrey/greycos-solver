package greycos.solver.jackson.api.score.analysis;

import org.junit.jupiter.api.Test;

class RawAnalysisDeserializerCompatibilityTest {
  @Test
  void newReferenceAwareHookDelegatesToTheOldCustomHook() throws Exception {
    RawAnalysisDeserializerCompatibilityProbe.verifyOldHookDelegation();
  }
}
