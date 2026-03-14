package ai.greycos.solver.core.impl.heuristic.selector.move.generic;

import static ai.greycos.solver.core.testutil.PlannerAssert.assertAllCodesOfCollection;
import static ai.greycos.solver.core.testutil.PlannerTestUtils.mockRebasingScoreDirector;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.score.director.ScoreDirector;
import ai.greycos.solver.core.impl.score.director.ValueRangeManager;
import ai.greycos.solver.core.impl.score.director.VariableDescriptorAwareScoreDirector;
import ai.greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataSolution;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testcotwin.multivar.TestdataMultiVarEntity;
import ai.greycos.solver.core.testcotwin.valuerange.entityproviding.unassignedvar.TestdataAllowsUnassignedEntityProvidingEntity;
import ai.greycos.solver.core.testcotwin.valuerange.entityproviding.unassignedvar.TestdataAllowsUnassignedEntityProvidingSolution;

import org.junit.jupiter.api.Test;

class SelectorBasedPillarSwapMoveTest {

  @Test
  void isMoveDoableValueRangeProviderOnEntity() {
    var v1 = new TestdataValue("1");
    var v2 = new TestdataValue("2");
    var v3 = new TestdataValue("3");
    var v4 = new TestdataValue("4");
    var v5 = new TestdataValue("5");

    var a = new TestdataAllowsUnassignedEntityProvidingEntity("a", Arrays.asList(v1, v2, v3), null);
    var b =
        new TestdataAllowsUnassignedEntityProvidingEntity("b", Arrays.asList(v2, v3, v4, v5), null);
    var c = new TestdataAllowsUnassignedEntityProvidingEntity("c", Arrays.asList(v4, v5), null);
    var z =
        new TestdataAllowsUnassignedEntityProvidingEntity(
            "z", Arrays.asList(v1, v2, v3, v4, v5), null);
    var solution = new TestdataAllowsUnassignedEntityProvidingSolution();
    solution.setEntityList(Arrays.asList(a, b, c, z));

    var valueRangeManager =
        ValueRangeManager.of(
            TestdataAllowsUnassignedEntityProvidingSolution.buildSolutionDescriptor(), solution);
    var scoreDirector =
        (VariableDescriptorAwareScoreDirector<TestdataAllowsUnassignedEntityProvidingSolution>)
            mock(VariableDescriptorAwareScoreDirector.class);
    doReturn(valueRangeManager).when(scoreDirector).getValueRangeManager();

    var variableDescriptorList =
        TestdataAllowsUnassignedEntityProvidingEntity.buildEntityDescriptor()
            .getGenuineVariableDescriptorList();

    var abMove = new SelectorBasedPillarSwapMove<>(variableDescriptorList, List.of(a), List.of(b));
    a.setValue(v1);
    b.setValue(v2);
    assertThat(abMove.isMoveDoable(scoreDirector)).isFalse();
    a.setValue(v2);
    b.setValue(v2);
    assertThat(abMove.isMoveDoable(scoreDirector)).isFalse();
    a.setValue(v2);
    b.setValue(v3);
    assertThat(abMove.isMoveDoable(scoreDirector)).isTrue();
    a.setValue(v3);
    b.setValue(v2);
    assertThat(abMove.isMoveDoable(scoreDirector)).isTrue();
    a.setValue(v3);
    b.setValue(v3);
    assertThat(abMove.isMoveDoable(scoreDirector)).isFalse();
    a.setValue(v2);
    b.setValue(v4);
    assertThat(abMove.isMoveDoable(scoreDirector)).isFalse();

    var acMove = new SelectorBasedPillarSwapMove<>(variableDescriptorList, List.of(a), List.of(c));
    a.setValue(v1);
    c.setValue(v4);
    assertThat(acMove.isMoveDoable(scoreDirector)).isFalse();
    a.setValue(v2);
    c.setValue(v5);
    assertThat(acMove.isMoveDoable(scoreDirector)).isFalse();

    var bcMove = new SelectorBasedPillarSwapMove<>(variableDescriptorList, List.of(b), List.of(c));
    b.setValue(v2);
    c.setValue(v4);
    assertThat(bcMove.isMoveDoable(scoreDirector)).isFalse();
    b.setValue(v4);
    c.setValue(v5);
    assertThat(bcMove.isMoveDoable(scoreDirector)).isTrue();
    b.setValue(v5);
    c.setValue(v4);
    assertThat(bcMove.isMoveDoable(scoreDirector)).isTrue();
    b.setValue(v5);
    c.setValue(v5);
    assertThat(bcMove.isMoveDoable(scoreDirector)).isFalse();

    var abzMove =
        new SelectorBasedPillarSwapMove<>(variableDescriptorList, Arrays.asList(a, b), List.of(z));
    a.setValue(v2);
    b.setValue(v2);
    z.setValue(v4);
    assertThat(abzMove.isMoveDoable(scoreDirector)).isFalse();
    a.setValue(v2);
    b.setValue(v2);
    z.setValue(v1);
    assertThat(abzMove.isMoveDoable(scoreDirector)).isFalse();
    a.setValue(v2);
    b.setValue(v2);
    z.setValue(v3);
    assertThat(abzMove.isMoveDoable(scoreDirector)).isTrue();
    a.setValue(v3);
    b.setValue(v3);
    z.setValue(v2);
    assertThat(abzMove.isMoveDoable(scoreDirector)).isTrue();
    a.setValue(v2);
    b.setValue(v2);
    z.setValue(v2);
    assertThat(abzMove.isMoveDoable(scoreDirector)).isFalse();
  }

  @Test
  void doMove() {
    var v1 = new TestdataValue("1");
    var v2 = new TestdataValue("2");
    var v3 = new TestdataValue("3");
    var v4 = new TestdataValue("4");
    var v5 = new TestdataValue("5");

    var a =
        new TestdataAllowsUnassignedEntityProvidingEntity("a", Arrays.asList(v1, v2, v3, v4), null);
    var b =
        new TestdataAllowsUnassignedEntityProvidingEntity("b", Arrays.asList(v2, v3, v4, v5), null);
    var c = new TestdataAllowsUnassignedEntityProvidingEntity("c", Arrays.asList(v4, v5), null);
    var z =
        new TestdataAllowsUnassignedEntityProvidingEntity(
            "z", Arrays.asList(v1, v2, v3, v4, v5), null);

    var scoreDirectorFactory =
        new EasyScoreDirectorFactory<>(
            TestdataAllowsUnassignedEntityProvidingSolution.buildSolutionDescriptor(),
            solution -> SimpleScore.ZERO,
            EnvironmentMode.PHASE_ASSERT);
    var scoreDirector = scoreDirectorFactory.buildScoreDirector();
    var variableDescriptorList =
        TestdataAllowsUnassignedEntityProvidingEntity.buildEntityDescriptor()
            .getGenuineVariableDescriptorList();

    var abMove = new SelectorBasedPillarSwapMove<>(variableDescriptorList, List.of(a), List.of(b));

    a.setValue(v1);
    b.setValue(v1);
    abMove.doMoveOnly(scoreDirector);
    assertThat(a.getValue()).isEqualTo(v1);
    assertThat(b.getValue()).isEqualTo(v1);

    a.setValue(v2);
    b.setValue(v1);
    abMove.doMoveOnly(scoreDirector);
    assertThat(a.getValue()).isEqualTo(v1);
    assertThat(b.getValue()).isEqualTo(v2);

    a.setValue(v3);
    b.setValue(v2);
    abMove.doMoveOnly(scoreDirector);
    assertThat(a.getValue()).isEqualTo(v2);
    assertThat(b.getValue()).isEqualTo(v3);
    abMove.doMoveOnly(scoreDirector);
    assertThat(a.getValue()).isEqualTo(v3);
    assertThat(b.getValue()).isEqualTo(v2);

    var abzMove =
        new SelectorBasedPillarSwapMove<>(variableDescriptorList, Arrays.asList(a, b), List.of(z));

    a.setValue(v3);
    b.setValue(v3);
    z.setValue(v2);
    abzMove.doMoveOnly(scoreDirector);
    assertThat(a.getValue()).isEqualTo(v2);
    assertThat(b.getValue()).isEqualTo(v2);
    assertThat(z.getValue()).isEqualTo(v3);
    abzMove.doMoveOnly(scoreDirector);
    assertThat(a.getValue()).isEqualTo(v3);
    assertThat(b.getValue()).isEqualTo(v3);
    assertThat(z.getValue()).isEqualTo(v2);

    a.setValue(v3);
    b.setValue(v3);
    z.setValue(v4);
    abzMove.doMoveOnly(scoreDirector);
    assertThat(a.getValue()).isEqualTo(v4);
    assertThat(b.getValue()).isEqualTo(v4);
    assertThat(z.getValue()).isEqualTo(v3);
    abzMove.doMoveOnly(scoreDirector);
    assertThat(a.getValue()).isEqualTo(v3);
    assertThat(b.getValue()).isEqualTo(v3);
    assertThat(z.getValue()).isEqualTo(v4);

    var abczMove =
        new SelectorBasedPillarSwapMove<>(
            variableDescriptorList, List.of(a), Arrays.asList(b, c, z));

    a.setValue(v2);
    b.setValue(v3);
    c.setValue(v3);
    z.setValue(v3);
    abczMove.doMoveOnly(scoreDirector);
    assertThat(a.getValue()).isEqualTo(v3);
    assertThat(b.getValue()).isEqualTo(v2);
    assertThat(c.getValue()).isEqualTo(v2);
    assertThat(z.getValue()).isEqualTo(v2);
    abczMove.doMoveOnly(scoreDirector);
    assertThat(a.getValue()).isEqualTo(v2);
    assertThat(b.getValue()).isEqualTo(v3);
    assertThat(c.getValue()).isEqualTo(v3);
    assertThat(z.getValue()).isEqualTo(v3);

    var abczMove2 =
        new SelectorBasedPillarSwapMove<>(
            variableDescriptorList, Arrays.asList(a, b), Arrays.asList(c, z));

    a.setValue(v4);
    b.setValue(v4);
    c.setValue(v3);
    z.setValue(v3);
    abczMove2.doMoveOnly(scoreDirector);
    assertThat(a.getValue()).isEqualTo(v3);
    assertThat(b.getValue()).isEqualTo(v3);
    assertThat(c.getValue()).isEqualTo(v4);
    assertThat(z.getValue()).isEqualTo(v4);
    abczMove2.doMoveOnly(scoreDirector);
    assertThat(a.getValue()).isEqualTo(v4);
    assertThat(b.getValue()).isEqualTo(v4);
    assertThat(c.getValue()).isEqualTo(v3);
    assertThat(z.getValue()).isEqualTo(v3);
  }

  @Test
  void rebase() {
    var entityDescriptor = TestdataEntity.buildEntityDescriptor();
    var variableDescriptorList = entityDescriptor.getGenuineVariableDescriptorList();

    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    var v3 = new TestdataValue("v3");
    var e1 = new TestdataEntity("e1", v1);
    var e2 = new TestdataEntity("e2", null);
    var e3 = new TestdataEntity("e3", v1);
    var e4 = new TestdataEntity("e4", v3);

    var destinationV1 = new TestdataValue("v1");
    var destinationV2 = new TestdataValue("v2");
    var destinationV3 = new TestdataValue("v3");
    var destinationE1 = new TestdataEntity("e1", destinationV1);
    var destinationE2 = new TestdataEntity("e2", null);
    var destinationE3 = new TestdataEntity("e3", destinationV1);
    var destinationE4 = new TestdataEntity("e4", destinationV3);

    ScoreDirector<TestdataSolution> destinationScoreDirector =
        mockRebasingScoreDirector(
            entityDescriptor.getSolutionDescriptor(),
            new Object[][] {
              {v1, destinationV1},
              {v2, destinationV2},
              {v3, destinationV3},
              {e1, destinationE1},
              {e2, destinationE2},
              {e3, destinationE3},
              {e4, destinationE4},
            });

    assertSameProperties(
        Arrays.asList(destinationE1, destinationE3),
        List.of(destinationE2),
        new SelectorBasedPillarSwapMove<>(
                variableDescriptorList, Arrays.asList(e1, e3), List.of(e2))
            .rebase(destinationScoreDirector));
    assertSameProperties(
        List.of(destinationE4),
        Arrays.asList(destinationE1, destinationE3),
        new SelectorBasedPillarSwapMove<>(
                variableDescriptorList, List.of(e4), Arrays.asList(e1, e3))
            .rebase(destinationScoreDirector));
  }

  void assertSameProperties(
      List<Object> leftPillar, List<Object> rightPillar, SelectorBasedPillarSwapMove<?> move) {
    assertThat(move.getLeftPillar()).hasSameElementsAs(leftPillar);
    assertThat(move.getRightPillar()).hasSameElementsAs(rightPillar);
  }

  @Test
  void getters() {
    var move =
        new SelectorBasedPillarSwapMove<>(
            Collections.singletonList(
                TestdataMultiVarEntity.buildVariableDescriptorForPrimaryValue()),
            Arrays.asList(new TestdataMultiVarEntity("a"), new TestdataMultiVarEntity("b")),
            Collections.singletonList(new TestdataMultiVarEntity("c")));
    assertAllCodesOfCollection(move.getLeftPillar(), "a", "b");
    assertAllCodesOfCollection(move.getRightPillar(), "c");
    assertThat(move.getVariableNameList()).containsExactly("primaryValue");
  }
}
