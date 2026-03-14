package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import static ai.greycos.solver.core.testutil.PlannerTestUtils.mockRebasingScoreDirector;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataSolution;
import ai.greycos.solver.core.testcotwin.list.TestdataListEntity;
import ai.greycos.solver.core.testcotwin.list.TestdataListSolution;
import ai.greycos.solver.core.testcotwin.list.TestdataListValue;

import org.junit.jupiter.api.Test;

class SelectorBasedListUnassignMoveTest {

  @Test
  void doMove() {
    var v1 = new TestdataListValue("1");
    var v2 = new TestdataListValue("2");
    var v3 = new TestdataListValue("3");
    var e1 = new TestdataListEntity("e1", v1, v2, v3);

    var scoreDirector =
        (InnerScoreDirector<TestdataListSolution, SimpleScore>) mock(InnerScoreDirector.class);
    var variableDescriptor = TestdataListEntity.buildVariableDescriptorForValueList();

    var move = new SelectorBasedListUnassignMove<>(variableDescriptor, e1, 2);
    move.doMoveOnly(scoreDirector);
    assertThat(e1.getValueList()).containsExactly(v1, v2);

    verify(scoreDirector).beforeListVariableChanged(variableDescriptor, e1, 2, 3);
    verify(scoreDirector).beforeListVariableElementUnassigned(variableDescriptor, v3);
    verify(scoreDirector).afterListVariableElementUnassigned(variableDescriptor, v3);
    verify(scoreDirector).afterListVariableChanged(variableDescriptor, e1, 2, 2);
    verify(scoreDirector, atLeastOnce()).triggerVariableListeners();

    new SelectorBasedListUnassignMove<>(variableDescriptor, e1, 0).doMoveOnly(scoreDirector);
    new SelectorBasedListUnassignMove<>(variableDescriptor, e1, 0).doMoveOnly(scoreDirector);
    assertThat(e1.getValueList()).isEmpty();
  }

  @Test
  void undoMove() {
    var v1 = new TestdataListValue("1");
    var v2 = new TestdataListValue("2");
    var v3 = new TestdataListValue("3");
    var e1 = new TestdataListEntity("e1", v1, v2, v3);

    var scoreDirector =
        (InnerScoreDirector<TestdataListSolution, SimpleScore>) mock(InnerScoreDirector.class);
    var variableDescriptor = TestdataListEntity.buildVariableDescriptorForValueList();

    var move = new SelectorBasedListUnassignMove<>(variableDescriptor, e1, 2);
    move.doMoveOnly(scoreDirector);
    assertThat(e1.getValueList()).hasSize(2);
    verify(scoreDirector).beforeListVariableElementUnassigned(variableDescriptor, v3);
    verify(scoreDirector).afterListVariableElementUnassigned(variableDescriptor, v3);
  }

  @Test
  void rebase() {
    var solutionDescriptor = TestdataSolution.buildSolutionDescriptor();
    var source = new TestdataEntity();
    var destination = new TestdataEntity();
    var destinationScoreDirector =
        mockRebasingScoreDirector(
            solutionDescriptor,
            new Object[][] {
              {source, destination},
            });
    var move = new SelectorBasedListUnassignMove<TestdataSolution>(null, source, 0);
    var rebasedMove = move.rebase(destinationScoreDirector);
    assertThat(rebasedMove).isNotSameAs(move);
  }

  @Test
  void toStringTest() {
    var v1 = new TestdataListValue("1");
    var e1 = new TestdataListEntity("E1", v1);

    var variableDescriptor = TestdataListEntity.buildVariableDescriptorForValueList();

    assertThat(new SelectorBasedListUnassignMove<>(variableDescriptor, e1, 0))
        .hasToString("1 {E1[0] -> null}");
  }
}
