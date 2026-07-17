package ai.greycos.solver.core.impl.neighborhood;

import static org.assertj.core.api.Assertions.assertThat;

import ai.greycos.solver.core.impl.neighborhood.stream.DefaultNeighborhood;
import ai.greycos.solver.core.impl.neighborhood.stream.DefaultNeighborhoodBuilder;
import ai.greycos.solver.core.preview.api.move.builtin.AssignMoveProvider;
import ai.greycos.solver.core.preview.api.move.builtin.ChangeMoveProvider;
import ai.greycos.solver.core.preview.api.move.builtin.ListAssignMoveProvider;
import ai.greycos.solver.core.preview.api.move.builtin.ListChangeMoveProvider;
import ai.greycos.solver.core.preview.api.move.builtin.ListSwapMoveProvider;
import ai.greycos.solver.core.preview.api.move.builtin.ListUnassignMoveProvider;
import ai.greycos.solver.core.preview.api.move.builtin.SwapMoveProvider;
import ai.greycos.solver.core.preview.api.move.builtin.UnassignMoveProvider;
import ai.greycos.solver.core.testcotwin.TestdataSolution;
import ai.greycos.solver.core.testcotwin.list.TestdataListSolution;
import ai.greycos.solver.core.testcotwin.list.unassignedvar.TestdataAllowsUnassignedValuesListSolution;
import ai.greycos.solver.core.testcotwin.unassignedvar.TestdataAllowsUnassignedSolution;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

@NullMarked
class DefaultNeighborhoodProviderTest {

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Test
  void basicVariable() {
    var solutionMetaModel = TestdataSolution.buildMetaModel();
    var builder = new DefaultNeighborhoodBuilder<>(solutionMetaModel);
    var neighborhood =
        (DefaultNeighborhood<TestdataSolution>)
            new DefaultNeighborhoodProvider<TestdataSolution>().defineNeighborhood(builder);
    assertThat(neighborhood.getMoveProviderList())
        .map(c -> (Class) c.getClass())
        .containsExactly(ChangeMoveProvider.class, SwapMoveProvider.class);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Test
  void basicVariableAllowsUnassigned() {
    var solutionMetaModel = TestdataAllowsUnassignedSolution.buildMetaModel();
    var builder = new DefaultNeighborhoodBuilder<>(solutionMetaModel);
    var neighborhood =
        (DefaultNeighborhood<TestdataAllowsUnassignedSolution>)
            new DefaultNeighborhoodProvider<TestdataAllowsUnassignedSolution>()
                .defineNeighborhood(builder);
    assertThat(neighborhood.getMoveProviderList())
        .map(c -> (Class) c.getClass())
        .containsExactly(
            ChangeMoveProvider.class,
            AssignMoveProvider.class,
            UnassignMoveProvider.class,
            SwapMoveProvider.class);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Test
  void listVariable() {
    var solutionMetaModel = TestdataListSolution.buildMetaModel();
    var builder = new DefaultNeighborhoodBuilder<>(solutionMetaModel);
    var neighborhood =
        (DefaultNeighborhood<TestdataListSolution>)
            new DefaultNeighborhoodProvider<TestdataListSolution>().defineNeighborhood(builder);
    assertThat(neighborhood.getMoveProviderList())
        .map(c -> (Class) c.getClass())
        .containsExactly(ListChangeMoveProvider.class, ListSwapMoveProvider.class);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Test
  void listVariableAllowsUnassignedValues() {
    var solutionMetaModel = TestdataAllowsUnassignedValuesListSolution.buildMetaModel();
    var builder = new DefaultNeighborhoodBuilder<>(solutionMetaModel);
    var neighborhood =
        (DefaultNeighborhood<TestdataAllowsUnassignedValuesListSolution>)
            new DefaultNeighborhoodProvider<TestdataAllowsUnassignedValuesListSolution>()
                .defineNeighborhood(builder);
    assertThat(neighborhood.getMoveProviderList())
        .map(c -> (Class) c.getClass())
        .containsExactly(
            ListChangeMoveProvider.class,
            ListSwapMoveProvider.class,
            ListAssignMoveProvider.class,
            ListUnassignMoveProvider.class);
  }
}
