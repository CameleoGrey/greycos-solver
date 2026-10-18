package greycos.solver.core.impl.score.stream.bavet.uni;

import java.util.List;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.stream.bavet.BavetConstraintStreamImplSupport;
import greycos.solver.core.impl.score.stream.common.uni.AbstractUniConstraintStreamPrecomputeTest;
import greycos.solver.core.testcotwin.score.lavish.TestdataLavishEntity;
import greycos.solver.core.testcotwin.score.lavish.TestdataLavishSolution;
import greycos.solver.core.testcotwin.score.lavish.TestdataLavishValue;

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

  @TestTemplate
  void repeatedVariableChangesAcrossCacheRebuild() {
    var solution = TestdataLavishSolution.generateSolution(2, 1);
    var firstValue = solution.getFirstValue();
    var secondValue = solution.getValueList().get(1);
    var entity = solution.getFirstEntity();
    var scoreDirector =
        buildScoreDirector(
            factory ->
                factory
                    .precompute(
                        precomputeFactory ->
                            precomputeFactory.forEachUnfiltered(TestdataLavishEntity.class))
                    .filter(item -> item.getValue() == firstValue)
                    .penalize(SimpleScore.ONE)
                    .asConstraint(TEST_CONSTRAINT_ID));

    scoreDirector.setWorkingSolution(solution);
    assertScore(scoreDirector, assertMatch(entity));
    scoreDirector.beforeVariableChanged(entity, "value");
    entity.setValue(secondValue);
    scoreDirector.afterVariableChanged(entity, "value");
    scoreDirector.beforeVariableChanged(entity, "value");
    entity.setValue(firstValue);
    scoreDirector.afterVariableChanged(entity, "value");
    assertScore(scoreDirector, assertMatch(entity));

    scoreDirector.beforeVariableChanged(entity, "value");
    entity.setValue(secondValue);
    scoreDirector.afterVariableChanged(entity, "value");
    assertScore(scoreDirector);

    var replacement = new TestdataLavishEntity("Replacement", entity.getEntityGroup(), secondValue);
    scoreDirector.beforeEntityRemoved(entity);
    solution.getEntityList().remove(entity);
    scoreDirector.afterEntityRemoved(entity);
    scoreDirector.beforeEntityAdded(replacement);
    solution.getEntityList().add(replacement);
    scoreDirector.afterEntityAdded(replacement);
    assertScore(scoreDirector);

    scoreDirector.beforeVariableChanged(replacement, "value");
    replacement.setValue(firstValue);
    scoreDirector.afterVariableChanged(replacement, "value");
    assertScore(scoreDirector, assertMatch(replacement));
    scoreDirector.beforeVariableChanged(replacement, "value");
    replacement.setValue(secondValue);
    scoreDirector.afterVariableChanged(replacement, "value");
    assertScore(scoreDirector);
  }
}
