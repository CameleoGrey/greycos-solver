package greycos.solver.jackson.api.score.analysis;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.ConstraintRef;

/** Executed against the baseline-compiled subclass by the binary compatibility runner. */
public final class RawAnalysisDeserializerCompatibilityProbe {
  private RawAnalysisDeserializerCompatibilityProbe() {}

  public static void main(String[] args) throws Exception {
    verifyOldHookDelegation();
    System.out.println(
        "Old deserializer hook compatibility passed on Java " + Runtime.version().feature() + ".");
  }

  public static void verifyOldHookDelegation() throws Exception {
    var fixture = new LegacyRawAnalysisDeserializerFixture();
    var ref = ConstraintRef.of("legacy");
    var result =
        fixture.deserializeConstraintJustification(
            ref,
            null,
            null,
            SimpleScore.ONE,
            id -> {
              throw new AssertionError("The old hook must not require raw-object resolution.");
            });
    if (result != fixture.justification
        || fixture.oldHookCalls != 1
        || fixture.lastConstraint != ref
        || fixture.lastScore != SimpleScore.ONE) {
      throw new AssertionError("The new hook must delegate to the baseline subclass's old hook.");
    }
  }
}
