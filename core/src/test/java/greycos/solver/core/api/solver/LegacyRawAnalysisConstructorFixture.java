package greycos.solver.core.api.solver;

import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.impl.score.analysis.DefaultConstraintAnalysis;

/** An intentional internal regression guard, not a public-API compatibility guarantee. */
public final class LegacyRawAnalysisConstructorFixture {
  private LegacyRawAnalysisConstructorFixture() {}

  public static void main(String[] args) {
    verifyFiveArgumentConstructor();
    System.out.println("Pre-extension internal five-argument constructor regression passed.");
  }

  public static void verifyFiveArgumentConstructor() {
    var ref = ConstraintRef.of("legacy-constructor");
    var unavailable =
        new DefaultConstraintAnalysis<>(ref, SimpleScore.ONE, SimpleScore.ZERO, null, -1);
    var counted = new DefaultConstraintAnalysis<>(ref, SimpleScore.ONE, SimpleScore.ZERO, null, 4);
    var fetched =
        new DefaultConstraintAnalysis<>(ref, SimpleScore.ONE, SimpleScore.ZERO, List.of(), 0);
    if (unavailable.matches() != null
        || unavailable.matchCount() != -1
        || counted.matches() != null
        || counted.matchCount() != 4
        || !fetched.matches().isEmpty()
        || fetched.matchCount() != 0) {
      throw new AssertionError("The internal five-argument constructor changed legacy values.");
    }
  }
}
