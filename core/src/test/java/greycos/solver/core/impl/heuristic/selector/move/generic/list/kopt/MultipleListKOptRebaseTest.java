package greycos.solver.core.impl.heuristic.selector.move.generic.list.kopt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MultipleListKOptRebaseTest {

  @ParameterizedTest
  @ValueSource(strings = {"firstValueList", "secondValueList"})
  void rebaseMovesAndFlipActionsToIndependentModel(String variableName) {
    try (var source = TestdataMultipleListSolution.buildScoreDirector();
        var destination = TestdataMultipleListSolution.buildScoreDirector()) {
      var sourceSolution = TestdataMultipleListSolution.generateInitializedSolution(6, 6, 1);
      var destinationSolution = TestdataMultipleListSolution.generateInitializedSolution(6, 6, 1);
      source.setWorkingSolution(sourceSolution);
      destination.setWorkingSolution(destinationSolution);
      var descriptor =
          source
              .getSolutionDescriptor()
              .resolveListVariableDescriptor(TestdataMultipleListEntity.class, variableName);
      var destinationDescriptor =
          destination
              .getSolutionDescriptor()
              .resolveListVariableDescriptor(TestdataMultipleListEntity.class, variableName);
      var owner = sourceSolution.getEntityList().getFirst();
      var values = descriptor.getValue(owner);
      var edges =
          new Object[] {
            null,
            values.get(5),
            values.get(0),
            values.get(1),
            values.get(2),
            values.get(3),
            values.get(4)
          };
      var state = source.getListVariableState(descriptor);
      var kOptDescriptor =
          new KOptDescriptor<>(
              edges,
              new int[] {0, 5, 4, 6, 2, 1, 3},
              KOptUtils.getMultiEntitySuccessorFunction(values.toArray(), state),
              KOptUtils.getMultiEntityBetweenPredicate(values.toArray(), state));
      var selector = kOptDescriptor.getKOptListMove(state);
      assertThat(selector.isMoveDoable(source)).isTrue();
      var plain =
          new KOptListMove<>(
              descriptor,
              kOptDescriptor,
              selector.getEquivalent2Opts(),
              selector.getPostShiftAmount(),
              selector.getNewEndIndices(),
              selector.getOriginalEntities());
      var baseline =
          List.copyOf(
              destinationDescriptor.getValue(destinationSolution.getEntityList().getFirst()));
      for (var move : List.of(plain, selector)) {
        var rebased = move.rebase(destination);
        assertThat(rebased.getClass()).isEqualTo(move.getClass());
        assertThat(rebased.getListVariableDescriptor()).isSameAs(destinationDescriptor);
        assertThat(rebased.getEquivalent2Opts().getFirst().variableDescriptor())
            .isSameAs(destinationDescriptor);
        for (int i = 1; i < edges.length; i++) {
          assertThat(rebased.getDescriptor().removedEdges()[i])
              .isSameAs(destination.lookUpWorkingObject(edges[i]));
        }
        var expected = new AtomicReference<List<String>>();
        var expectedScore =
            source.executeTemporaryMove(
                move,
                ignored ->
                    expected.set(
                        descriptor.getValue(owner).stream().map(Object::toString).toList()),
                true);
        assertThat(
                destination.executeTemporaryMove(
                    rebased,
                    ignored ->
                        assertThat(
                                destinationDescriptor
                                    .getValue(destinationSolution.getEntityList().getFirst())
                                    .stream()
                                    .map(Object::toString)
                                    .toList())
                            .isEqualTo(expected.get()),
                    true))
            .isEqualTo(expectedScore);
        assertThat(destinationDescriptor.getValue(destinationSolution.getEntityList().getFirst()))
            .containsExactlyElementsOf(baseline);
      }
    }
  }
}
