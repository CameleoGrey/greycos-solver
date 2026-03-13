package ai.greycos.solver.core.impl.solver;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.random.RandomGenerator;

import ai.greycos.solver.core.api.solver.phase.PhaseCommand;
import ai.greycos.solver.core.api.solver.phase.PhaseCommandContext;
import ai.greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.greycos.solver.core.config.heuristic.selector.move.factory.MoveIteratorFactoryConfig;
import ai.greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.heuristic.move.AbstractMove;
import ai.greycos.solver.core.impl.heuristic.selector.move.factory.MoveIteratorFactory;
import ai.greycos.solver.core.impl.score.DummySimpleScoreEasyScoreCalculator;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;
import ai.greycos.solver.core.preview.api.move.builtin.Moves;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testcotwin.list.TestdataListEntity;
import ai.greycos.solver.core.testcotwin.list.TestdataListSolution;
import ai.greycos.solver.core.testcotwin.list.TestdataListValue;
import ai.greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingEntity;
import ai.greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingSolution;
import ai.greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingValue;
import ai.greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingEntity;
import ai.greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingSolution;
import ai.greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;

class InvalidValueRangeFailFastTest {

  @Test
  void failBasicVariableInvalidValueRange() {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(
                TestdataEntityProvidingSolution.class, TestdataEntityProvidingEntity.class)
            .withEasyScoreCalculatorClass(DummySimpleScoreEasyScoreCalculator.class);

    var problem = new TestdataEntityProvidingSolution();
    var v1 = new TestdataValue("1");
    var v2 = new TestdataValue("2");
    var v3 = new TestdataValue("3");
    var e1 = new TestdataEntityProvidingEntity("e1", List.of(v1, v2), v3);
    var e2 = new TestdataEntityProvidingEntity("e2", List.of(v1, v2), v1);
    problem.setEntityList(new ArrayList<>(Arrays.asList(e1, e2)));

    assertThatCode(() -> PlannerTestUtils.solve(solverConfig, problem))
        .hasMessageContaining(
            "The value (3) from the planning variable (value) has been assigned to the entity (e1), but it is outside of the related value range [1-2]");
  }

  @Test
  void failListVariableInvalidValueRange() {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(
                TestdataListEntityProvidingSolution.class,
                TestdataListEntityProvidingEntity.class,
                TestdataListEntityProvidingValue.class)
            .withEasyScoreCalculatorClass(DummySimpleScoreEasyScoreCalculator.class);

    var problem = new TestdataListEntityProvidingSolution();
    var v1 = new TestdataListEntityProvidingValue("1");
    var v2 = new TestdataListEntityProvidingValue("2");
    var v3 = new TestdataListEntityProvidingValue("3");
    var e1 = new TestdataListEntityProvidingEntity("e1", List.of(v1, v2), List.of(v3));
    var e2 = new TestdataListEntityProvidingEntity("e2", List.of(v1, v2), List.of(v1));
    problem.setEntityList(new ArrayList<>(Arrays.asList(e1, e2)));

    assertThatCode(() -> PlannerTestUtils.solve(solverConfig, problem))
        .hasMessageContaining(
            "The value (3) from the planning variable (valueList) has been assigned to the entity (e1), but it is outside of the related value range [1-2]");
  }

  @Test
  void failLocalSearchValueRangeAssertion() {
    var solverConfig =
        PlannerTestUtils.buildSolverConfig(
            TestdataListSolution.class, TestdataListEntity.class, TestdataListValue.class);
    solverConfig.setEnvironmentMode(EnvironmentMode.FULL_ASSERT);
    var localSearchPhaseConfig = new LocalSearchPhaseConfig();
    localSearchPhaseConfig.setMoveSelectorConfig(
        new MoveIteratorFactoryConfig().withMoveIteratorFactoryClass(InvalidMoveListFactory.class));
    solverConfig.setPhaseConfigList(
        List.of(new ConstructionHeuristicPhaseConfig(), localSearchPhaseConfig));

    var problem = TestdataListSolution.generateUninitializedSolution(2, 2);
    assertThatCode(() -> PlannerTestUtils.solve(solverConfig, problem))
        .hasMessageContaining(
            "The value (bad value) from the planning variable (valueList) has been assigned to the entity (Generated Entity 0), but it is outside of the related value range [Generated Value 0-Generated Value 1]");
  }

  public static final class InvalidCustomPhaseCommand
      implements PhaseCommand<TestdataListSolution> {

    @Override
    public void changeWorkingSolution(PhaseCommandContext<TestdataListSolution> context) {
      var variableMetaModel =
          context
              .getSolutionMetaModel()
              .genuineEntity(TestdataListEntity.class)
              .listVariable("valueList", TestdataListValue.class);
      var entity = context.getWorkingSolution().getEntityList().get(0);
      context.executeAndCalculateScore(
          Moves.assign(variableMetaModel, new TestdataListValue("bad value"), entity, 0));
    }
  }

  public static final class InvalidMoveListFactory
      implements MoveIteratorFactory<TestdataListSolution, InvalidMove> {

    @Override
    public long getSize(ScoreDirector<TestdataListSolution> scoreDirector) {
      return 1;
    }

    @Override
    public Iterator<InvalidMove> createOriginalMoveIterator(
        ScoreDirector<TestdataListSolution> scoreDirector) {
      return List.of(new InvalidMove()).iterator();
    }

    @Override
    public Iterator<InvalidMove> createRandomMoveIterator(
        ScoreDirector<TestdataListSolution> scoreDirector, RandomGenerator workingRandom) {
      return createOriginalMoveIterator(scoreDirector);
    }
  }

  public static final class InvalidMove extends AbstractMove<TestdataListSolution> {

    @Override
    protected void doMoveOnGenuineVariables(ScoreDirector<TestdataListSolution> scoreDirector) {
      var entity = scoreDirector.getWorkingSolution().getEntityList().get(0);
      scoreDirector.beforeListVariableChanged(entity, "valueList", 0, 0);
      entity.getValueList().add(new TestdataListValue("bad value"));
      scoreDirector.afterListVariableChanged(entity, "valueList", 0, entity.getValueList().size());
    }

    @Override
    public boolean isMoveDoable(ScoreDirector<TestdataListSolution> scoreDirector) {
      return true;
    }
  }
}
