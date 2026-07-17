package ai.greycos.solver.core.impl.score.stream.bavet.uni;

import java.util.List;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.greycos.solver.core.impl.score.stream.bavet.BavetConstraintStreamImplSupport;
import ai.greycos.solver.core.impl.score.stream.common.uni.AbstractUniConstraintStreamPrecomputeTest;
import ai.greycos.solver.core.testcotwin.score.lavish.TestdataLavishSolution;
import ai.greycos.solver.core.testcotwin.score.lavish.TestdataLavishValue;

import org.junit.jupiter.api.TestTemplate;

final class BavetUniConstraintStreamPrecomputeTest
    extends AbstractUniConstraintStreamPrecomputeTest {

  public BavetUniConstraintStreamPrecomputeTest(ConstraintMatchPolicy constraintMatchPolicy) {
    super(new BavetConstraintStreamImplSupport(constraintMatchPolicy));
  }

  @TestTemplate
  void forEachUnfilteredFact() {
    var solution = TestdataLavishSolution.generateEmptySolution();
    var value1 = new TestdataLavishValue();
    var value2 = new TestdataLavishValue();
    var value3 = new TestdataLavishValue();
    solution.getValueList().addAll(List.of(value1, value2, value3));

    var scoreDirector =
        buildScoreDirector(
            factory ->
                factory
                    .precompute(
                        precomputeFactory ->
                            precomputeFactory.forEachUnfiltered(TestdataLavishValue.class))
                    .penalize(SimpleScore.ONE)
                    .asConstraint(TEST_CONSTRAINT_ID));

    scoreDirector.setWorkingSolution(solution);
    assertScore(scoreDirector, assertMatch(value1), assertMatch(value2), assertMatch(value3));

    scoreDirector.beforeProblemFactRemoved(value3);
    solution.getValueList().remove(value3);
    scoreDirector.afterProblemFactRemoved(value3);

    assertScore(scoreDirector, assertMatch(value1), assertMatch(value2));
  }
}
