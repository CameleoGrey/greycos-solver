package ai.greycos.solver.core.api.score.constraint;

import static ai.greycos.solver.core.api.score.SimpleScore.ONE;
import static ai.greycos.solver.core.api.score.SimpleScore.ZERO;

import java.util.Arrays;

import ai.greycos.solver.core.api.score.Score;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.api.score.stream.DefaultConstraintJustification;
import ai.greycos.solver.core.testutil.PlannerAssert;

import org.junit.jupiter.api.Test;

class ConstraintMatchTest {

  @Test
  void equalsAndHashCode() { // No CM should equal any other.
    ConstraintMatch<SimpleScore> constraintMatch = buildConstraintMatch("a. b", "c", ZERO, "e1");
    PlannerAssert.assertObjectsAreEqual(constraintMatch, constraintMatch);
    ConstraintMatch<SimpleScore> constraintMatch2 = buildConstraintMatch("a. b", "c", ZERO, "e1");
    // Cast to avoid Comparable checks.
    PlannerAssert.assertObjectsAreNotEqual(constraintMatch, (Object) constraintMatch2);
  }

  private <Score_ extends Score<Score_>> ConstraintMatch<Score_> buildConstraintMatch(
      String constraintPackage, String constraintName, Score_ score, Object... indictments) {
    return new ConstraintMatch<>(
        ConstraintRef.of(constraintPackage, constraintName),
        DefaultConstraintJustification.of(score, indictments),
        Arrays.asList(indictments),
        score);
  }

  @Test
  void compareTo() {
    PlannerAssert.assertCompareToOrder(
        buildConstraintMatch("a.b", "a", ZERO, "a"),
        buildConstraintMatch("a.b", "a", ZERO, "a", "aa"),
        buildConstraintMatch("a.b", "a", ZERO, "a", "ab"),
        buildConstraintMatch("a.b", "a", ZERO, "a", "c"),
        buildConstraintMatch("a.b", "a", ZERO, "a", "aa", "a"),
        buildConstraintMatch("a.b", "a", ZERO, "a", "aa", "b"),
        buildConstraintMatch("a.b", "a", ONE, "a", "aa"),
        buildConstraintMatch("a.b", "b", ZERO, "a", "aa"),
        buildConstraintMatch("a.b", "b", ZERO, "a", "ab"),
        buildConstraintMatch("a.b", "b", ZERO, "a", "c"),
        buildConstraintMatch("a.c", "a", ZERO, "a", "aa"),
        buildConstraintMatch("a.c", "a", ZERO, "a", "ab"),
        buildConstraintMatch("a.c", "a", ZERO, "a", "c"));
  }
}
