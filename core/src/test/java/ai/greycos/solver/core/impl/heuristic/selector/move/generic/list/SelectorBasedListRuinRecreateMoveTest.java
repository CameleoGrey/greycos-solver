package ai.greycos.solver.core.impl.heuristic.selector.move.generic.list;

import static ai.greycos.solver.core.testutil.PlannerTestUtils.mockRebasingScoreDirector;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ai.greycos.solver.core.impl.cotwin.variable.descriptor.ListVariableDescriptor;
import ai.greycos.solver.core.impl.heuristic.selector.move.generic.RuinRecreateConstructionHeuristicPhaseBuilder;
import ai.greycos.solver.core.impl.heuristic.selector.move.generic.list.ruin.SelectorBasedListRuinRecreateMove;
import ai.greycos.solver.core.impl.solver.scope.SolverScope;
import ai.greycos.solver.core.testcotwin.list.TestdataListEntity;
import ai.greycos.solver.core.testcotwin.list.TestdataListSolution;
import ai.greycos.solver.core.testcotwin.list.TestdataListValue;

import org.junit.jupiter.api.Test;

class SelectorBasedListRuinRecreateMoveTest {

  @SuppressWarnings("unchecked")
  @Test
  void rebase() {
    var variableDescriptor = TestdataListEntity.buildVariableDescriptorForValueList();

    var v1 = new TestdataListValue("v1");
    var v2 = new TestdataListValue("v2");
    var e1 = TestdataListEntity.createWithValues("e1", v1);
    var e2 = new TestdataListEntity("e2");
    var e3 = TestdataListEntity.createWithValues("e3", v1);

    var destinationV1 = new TestdataListValue("v1");
    var destinationV2 = new TestdataListValue("v2");
    var destinationE1 = TestdataListEntity.createWithValues("e1", destinationV1);
    var destinationE2 = new TestdataListEntity("e2");
    var destinationE3 = TestdataListEntity.createWithValues("e3", destinationV1);

    var destinationScoreDirector =
        mockRebasingScoreDirector(
            variableDescriptor.getEntityDescriptor().getSolutionDescriptor(),
            new Object[][] {
              {v1, destinationV1},
              {v2, destinationV2},
              {e1, destinationE1},
              {e2, destinationE2},
              {e3, destinationE3},
            });

    var move =
        new SelectorBasedListRuinRecreateMove<TestdataListSolution>(
            mock(ListVariableDescriptor.class),
            mock(RuinRecreateConstructionHeuristicPhaseBuilder.class),
            mock(SolverScope.class),
            Arrays.asList(v1, v2),
            new LinkedHashSet<>(Set.of(e1, e2, e3)));
    var rebasedMove = move.rebase(destinationScoreDirector);

    assertThat(rebasedMove).isInstanceOf(SelectorBasedListRuinRecreateMove.class);
    assertSoftly(
        softly -> {
          softly
              .assertThat((Iterable) rebasedMove.getPlanningEntities())
              .containsExactlyInAnyOrderElementsOf(
                  Set.of(destinationE1, destinationE2, destinationE3));
          softly
              .assertThat((Iterable) rebasedMove.getPlanningValues())
              .containsExactlyElementsOf(List.of(destinationV1, destinationV2));
        });
  }

  @SuppressWarnings("unchecked")
  @Test
  void equality() {
    var v1 = new TestdataListValue("v1");
    var v2 = new TestdataListValue("v2");
    var e1 = TestdataListEntity.createWithValues("e1", v1);
    var e2 = new TestdataListEntity("e2");

    var descriptor = mock(ListVariableDescriptor.class);
    var move =
        new SelectorBasedListRuinRecreateMove<TestdataListSolution>(
            descriptor,
            mock(RuinRecreateConstructionHeuristicPhaseBuilder.class),
            mock(SolverScope.class),
            List.of(v1),
            new LinkedHashSet<>(Set.of(e1)));
    var sameMove =
        new SelectorBasedListRuinRecreateMove<TestdataListSolution>(
            descriptor,
            mock(RuinRecreateConstructionHeuristicPhaseBuilder.class),
            mock(SolverScope.class),
            List.of(v1),
            new LinkedHashSet<>(Set.of(e1)));
    assertThat(move).isEqualTo(sameMove);

    var differentMove =
        new SelectorBasedListRuinRecreateMove<TestdataListSolution>(
            descriptor,
            mock(RuinRecreateConstructionHeuristicPhaseBuilder.class),
            mock(SolverScope.class),
            List.of(v2),
            new LinkedHashSet<>(Set.of(e1)));
    assertThat(move).isNotEqualTo(differentMove);

    var anotherDifferentMove =
        new SelectorBasedListRuinRecreateMove<TestdataListSolution>(
            descriptor,
            mock(RuinRecreateConstructionHeuristicPhaseBuilder.class),
            mock(SolverScope.class),
            List.of(v1),
            new LinkedHashSet<>(Set.of(e2)));
    assertThat(move).isNotEqualTo(anotherDifferentMove);
  }
}
