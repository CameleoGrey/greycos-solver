package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.kopt;

import static ai.greycos.solver.core.testutil.PlannerTestUtils.mockRebasingScoreDirector;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.List;

import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.score.director.InnerScoreDirector;
import ai.greycos.solver.core.testcotwin.list.TestdataListEntity;
import ai.greycos.solver.core.testcotwin.list.TestdataListSolution;
import ai.greycos.solver.core.testcotwin.list.TestdataListValue;
import ai.greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SelectorBasedTwoOptListMoveTest {

  private final ListVariableDescriptor<TestdataListSolution> variableDescriptor =
      TestdataListEntity.buildVariableDescriptorForValueList();
  private final InnerScoreDirector<TestdataListSolution, ?> scoreDirector =
      PlannerTestUtils.mockScoreDirector(
          variableDescriptor.getEntityDescriptor().getSolutionDescriptor());

  @Test
  void doMove() {
    var v1 = new TestdataListValue("1");
    var v2 = new TestdataListValue("2");
    var v3 = new TestdataListValue("3");
    var v4 = new TestdataListValue("4");
    var v5 = new TestdataListValue("5");
    var v6 = new TestdataListValue("6");
    var v7 = new TestdataListValue("7");
    var v8 = new TestdataListValue("8");
    var e1 = TestdataListEntity.createWithValues("e1", v1, v2, v5, v4, v3, v6, v7, v8);
    var solution = new TestdataListSolution();
    solution.setEntityList(List.of(e1));
    solution.setValueList(List.of(v1, v2, v5, v4, v3, v6, v7, v8));
    scoreDirector.setWorkingSolution(solution);

    var move = new SelectorBasedTwoOptListMove<>(variableDescriptor, e1, e1, 2, 5);
    move.doMoveOnly(scoreDirector);
    assertThat(e1.getValueList()).containsExactly(v1, v2, v3, v4, v5, v6, v7, v8);

    verify(scoreDirector).beforeListVariableChanged(variableDescriptor, e1, 2, 5);
    verify(scoreDirector).afterListVariableChanged(variableDescriptor, e1, 2, 5);
  }

  @Test
  void rebase() {
    var v1 = new TestdataListValue("1");
    var v2 = new TestdataListValue("2");
    var e1 = TestdataListEntity.createWithValues("e1", v1, v2);

    var destinationV1 = new TestdataListValue("1");
    var destinationV2 = new TestdataListValue("2");
    var destinationE1 = TestdataListEntity.createWithValues("e1", destinationV1, destinationV2);

    InnerScoreDirector<TestdataListSolution, SimpleScore> destinationScoreDirector =
        mockRebasingScoreDirector(
            variableDescriptor.getEntityDescriptor().getSolutionDescriptor(),
            new Object[][] {
              {v1, destinationV1},
              {v2, destinationV2},
              {e1, destinationE1},
            });
    Mockito.doReturn(scoreDirector.getSupplyManager())
        .when(destinationScoreDirector)
        .getSupplyManager();

    var rebasedMove =
        new SelectorBasedTwoOptListMove<>(variableDescriptor, e1, e1, 0, 1)
            .rebase(destinationScoreDirector);

    assertThat(rebasedMove).isInstanceOf(SelectorBasedTwoOptListMove.class);
    assertThat(rebasedMove.getFirstEntity()).isSameAs(destinationE1);
    assertThat(rebasedMove.getFirstEdgeEndpoint()).isEqualTo(0);
    assertThat(rebasedMove.getSecondEdgeEndpoint()).isEqualTo(1);
  }
}
