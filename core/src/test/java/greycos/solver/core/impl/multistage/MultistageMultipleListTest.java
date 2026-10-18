package greycos.solver.core.impl.multistage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.multistage.BasicVariableReference;
import greycos.solver.core.api.solver.multistage.CrossVariableCustomStage;
import greycos.solver.core.api.solver.multistage.CrossVariableStageProvider;
import greycos.solver.core.api.solver.multistage.ListVariableReference;
import greycos.solver.core.api.solver.multistage.MultistageStageResult;
import greycos.solver.core.api.solver.multistage.MultistageVariableReference;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.move.PreparedMoveEvaluation;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListFirstValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSecondValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.api.Test;

class MultistageMultipleListTest {
  private static final ListVariableReference<
          TestdataMultipleListEntity, TestdataMultipleListFirstValue>
      FIRST =
          ListVariableReference.of(
              TestdataMultipleListEntity.class,
              "firstValueList",
              TestdataMultipleListFirstValue.class);
  private static final ListVariableReference<
          TestdataMultipleListEntity, TestdataMultipleListSecondValue>
      SECOND =
          ListVariableReference.of(
              TestdataMultipleListEntity.class,
              "secondValueList",
              TestdataMultipleListSecondValue.class);
  private static final BasicVariableReference<TestdataMultipleListEntity, Integer> BASIC =
      BasicVariableReference.of(TestdataMultipleListEntity.class, "basicValue", Integer.class);

  @Test
  void compoundCandidatePropagatesBothListsBetweenStagesAndReplaysInAnotherModel() {
    try (var source = TestdataMultipleListSolution.buildScoreDirector();
        var destination = TestdataMultipleListSolution.buildScoreDirector()) {
      var solution = solution();
      source.setWorkingSolution(solution);
      destination.setWorkingSolution(solution());
      var baseline = source.calculateScore();
      var definition = definition(source.getSolutionDescriptor());
      var request = new MultistageMoveRequest<>(definition, 0, 0);
      try {
        var result = request.prepare(source, () -> {}, true, null);
        assertThat(result.status()).isEqualTo(PreparedMoveEvaluation.Status.EVALUATED);
        assertBaseline(solution);
        assertThat(source.calculateScore()).isEqualTo(baseline);
        var replay = result.move().rebase(destination.getMoveDirector());
        destination.getMoveDirector().execute(replay);
        assertChanged(destination.getWorkingSolution());
        assertThat(destination.calculateScore()).isEqualTo(result.score());
        assertBaseline(solution);
        // Preparation with the same definition must resolve this independent model's descriptors
        // too.
        destination.setWorkingSolution(solution());
        var independentlyPrepared = request.prepare(destination, () -> {}, true, null);
        assertThat(independentlyPrepared.score()).isEqualTo(result.score());
        assertBaseline(destination.getWorkingSolution());
      } finally {
        definition.closeEvaluationContext(source);
        definition.closeEvaluationContext(destination);
      }
    }
  }

  @Test
  void cancellationAfterCompoundMutationRestoresBothFieldsAndBasic() {
    try (var director = TestdataMultipleListSolution.buildScoreDirector()) {
      var solution = solution();
      director.setWorkingSolution(solution);
      var baseline = director.calculateScore();
      var definition = definition(director.getSolutionDescriptor());
      try {
        var result =
            new MultistageMoveRequest<>(definition, 0, 0)
                .prepare(
                    director,
                    () -> {
                      if (solution.getEntityList().getFirst().getBasicValue() == 3) {
                        throw new CancellationException(
                            "Stop after both list fields and basic changed.");
                      }
                    },
                    true,
                    null);
        assertThat(result.status()).isEqualTo(PreparedMoveEvaluation.Status.CANCELLED);
        assertBaseline(solution);
        assertThat(director.calculateScore()).isEqualTo(baseline);
      } finally {
        definition.closeEvaluationContext(director);
      }
    }
  }

  private static TestdataMultipleListSolution solution() {
    var solution = TestdataMultipleListSolution.generateInitializedSolution(4, 4, 2);
    solution.getEntityList().getFirst().setFirstPinnedIndex(1);
    solution.getEntityList().getFirst().setSecondPinnedIndex(2);
    return solution;
  }

  private static MultistageDefinition<TestdataMultipleListSolution> definition(
      SolutionDescriptor<TestdataMultipleListSolution> solutionDescriptor) {
    var owner = solutionDescriptor.findEntityDescriptorOrFail(TestdataMultipleListEntity.class);
    List<MultistageVariableReference<?, ?>> references = List.of(FIRST, SECOND, BASIC);
    var bindings =
        references.stream()
            .map(
                reference ->
                    new MultistageVariableBinding<>(
                        reference,
                        owner.getGenuineVariableDescriptor(reference.variableName()),
                        owner))
            .toList();
    return new MultistageDefinition<>(solutionDescriptor, bindings, Stages.class, 10);
  }

  private static void assertBaseline(TestdataMultipleListSolution solution) {
    var left = solution.getEntityList().getFirst();
    var right = solution.getEntityList().getLast();
    assertThat(left.getFirstValueList())
        .containsExactly(solution.getFirstValueList().get(0), solution.getFirstValueList().get(2));
    assertThat(left.getSecondValueList())
        .containsExactly(
            solution.getSecondValueList().get(0), solution.getSecondValueList().get(2));
    assertThat(solution.getFirstValueList().get(2).getEntity()).isSameAs(left);
    assertThat(solution.getSecondValueList().get(1).getEntity()).isSameAs(right);
    assertThat(left.getBasicValue()).isZero();
  }

  private static void assertChanged(TestdataMultipleListSolution solution) {
    var left = solution.getEntityList().getFirst();
    var right = solution.getEntityList().getLast();
    assertThat(left.getFirstValueList()).containsExactly(solution.getFirstValueList().get(0));
    assertThat(left.getSecondValueList())
        .containsExactly(
            solution.getSecondValueList().get(0),
            solution.getSecondValueList().get(2),
            solution.getSecondValueList().get(1));
    assertThat(solution.getFirstValueList().get(2).getEntity()).isSameAs(right);
    assertThat(solution.getFirstValueList().get(2).getIndex()).isZero();
    assertThat(solution.getSecondValueList().get(1).getEntity()).isSameAs(left);
    assertThat(solution.getSecondValueList().get(1).getIndex()).isEqualTo(2);
    assertThat(left.getBasicValue()).isEqualTo(3);
  }

  public static class Stages
      implements CrossVariableStageProvider<TestdataMultipleListSolution, SimpleScore> {
    private TestdataMultipleListSolution solution;

    @Override
    public void initialize(TestdataMultipleListSolution solution) {
      this.solution = solution;
    }

    @Override
    public long getCandidateCount() {
      return 1;
    }

    @Override
    public List<CrossVariableCustomStage<TestdataMultipleListSolution, SimpleScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      return List.of(
          evaluator -> {
            var left = solution.getEntityList().getFirst();
            var right = solution.getEntityList().getLast();
            var operation =
                evaluator.sequence(
                    List.of(
                        evaluator.list(FIRST).place(solution.getFirstValueList().get(2), right, 0),
                        evaluator.list(SECOND).place(solution.getSecondValueList().get(1), left, 2),
                        evaluator.basic(BASIC).assign(left, 3)));
            assertThat(evaluator.evaluate(operation).isComplete()).isTrue();
            assertBaseline(solution);
            return MultistageStageResult.apply(operation);
          },
          evaluator -> {
            assertChanged(solution);
            return MultistageStageResult.skip();
          });
    }
  }
}
