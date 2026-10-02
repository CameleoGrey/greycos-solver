package greycos.solver.core.api.solver.multistage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.score.SimpleScore;

import org.junit.jupiter.api.Test;

class MultistageApiTest {

  @Test
  void evaluationPreservesStructuralAndInitializationOrderingWithoutNumericConversion() {
    var flawed = new MultistageEvaluation<>(new SimpleScore(-1, Long.MAX_VALUE), 0);
    var incomplete = new MultistageEvaluation<>(SimpleScore.of(Long.MAX_VALUE), 1);
    var complete = new MultistageEvaluation<>(SimpleScore.of(Long.MIN_VALUE), 0);
    var better = new MultistageEvaluation<>(SimpleScore.of(Long.MIN_VALUE + 1), 0);

    assertThat(flawed).isLessThan(incomplete);
    assertThat(incomplete).isLessThan(complete);
    assertThat(complete).isLessThan(better);
    assertThat(flawed.isStructurallyFlawed()).isTrue();
    assertThat(incomplete.isComplete()).isFalse();
    assertThat(complete.isComplete()).isTrue();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new MultistageEvaluation<>(SimpleScore.ZERO, -1))
        .withMessageContaining("unassignedCount (-1)");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new MultistageEvaluation<>(new SimpleScore(1, 0), 0))
        .withMessageContaining("structuralScore (1)");
  }

  @Test
  void bestFitPreservesInputOrderOnTiesAndRestoresNoSelectionForAnEmptyPopulation() {
    var evaluator = new Evaluator();
    var first = evaluator.operation(SimpleScore.of(1), 0);
    var second = evaluator.operation(SimpleScore.of(1), 0);
    var incomplete = evaluator.operation(SimpleScore.of(100), 1);

    var selected = evaluator.bestFit(List.of(first, incomplete, second));

    assertThat(selected.kind()).isEqualTo(MultistageStageResult.Kind.APPLY);
    assertThat(selected.operation()).isSameAs(first);
    assertThat(evaluator.evaluated).containsExactly(first, incomplete, second);
    assertThat(evaluator.terminationChecks).isEqualTo(3);
    assertThat(evaluator.bestFit(List.of()).kind())
        .isEqualTo(MultistageStageResult.Kind.ABORT_CANDIDATE);
  }

  @Test
  void evaluateAllPreservesOrderAndReturnsAnImmutableResult() {
    var evaluator = new Evaluator();
    var first = evaluator.operation(SimpleScore.of(4), 0);
    var second = evaluator.operation(SimpleScore.of(9), 0);

    var result = evaluator.evaluateAll(List.of(second, first));

    assertThat(result)
        .containsExactly(
            new MultistageEvaluation<>(SimpleScore.of(9), 0),
            new MultistageEvaluation<>(SimpleScore.of(4), 0));
    assertThat(evaluator.evaluated).containsExactly(second, first);
    assertThatThrownBy(() -> result.add(new MultistageEvaluation<>(SimpleScore.ZERO, 0)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void helperStopsBeforeAnotherProbeWhenTerminationThrows() {
    var evaluator = new Evaluator();
    var operation = evaluator.operation(SimpleScore.ZERO, 0);
    evaluator.failOnTerminationCheck = 2;

    assertThatThrownBy(() -> evaluator.bestFit(List.of(operation, operation)))
        .isSameAs(evaluator.failure);
    assertThat(evaluator.evaluated).containsExactly(operation);
  }

  @Test
  void resultAndPositionCannotRepresentAmbiguousStates() {
    assertThatNullPointerException().isThrownBy(() -> MultistageStageResult.apply(null));
    assertThat(MultistageStageResult.skip().kind()).isEqualTo(MultistageStageResult.Kind.SKIP);
    assertThat(MultistageStageResult.skip().operation()).isNull();
    assertThat(MultistageStageResult.abortCandidate().operation()).isNull();
    assertThat(MultistagePosition.unassigned().isUnassigned()).isTrue();
    assertThat(MultistagePosition.assigned("entity", 0).isUnassigned()).isFalse();
    assertThatIllegalArgumentException().isThrownBy(() -> new MultistagePosition<>(null, 0));
    assertThatIllegalArgumentException().isThrownBy(() -> new MultistagePosition<>("entity", -1));
    assertThatNullPointerException().isThrownBy(() -> MultistagePosition.assigned(null, 0));
  }

  private static final class Evaluator implements MultistageMoveEvaluator<Object, SimpleScore> {
    private final Map<MultistageOperation<Object>, MultistageEvaluation<SimpleScore>> scores =
        new IdentityHashMap<>();
    private final List<MultistageOperation<Object>> evaluated = new ArrayList<>();
    private final RuntimeException failure = new RuntimeException("terminated");
    private int terminationChecks;
    private int failOnTerminationCheck = Integer.MAX_VALUE;

    @SuppressWarnings("unchecked")
    private MultistageOperation<Object> operation(SimpleScore score, int unassignedCount) {
      MultistageOperation<Object> operation = mock(MultistageOperation.class);
      scores.put(operation, new MultistageEvaluation<>(score, unassignedCount));
      return operation;
    }

    @Override
    public Object workingSolution() {
      throw new UnsupportedOperationException();
    }

    @Override
    public MultistageEvaluation<SimpleScore> evaluate(MultistageOperation<Object> operation) {
      evaluated.add(operation);
      return scores.get(operation);
    }

    @Override
    public MultistageEvaluation<SimpleScore> currentEvaluation() {
      throw new UnsupportedOperationException();
    }

    @Override
    public MultistageOperation<Object> sequence(
        List<? extends MultistageOperation<Object>> operations) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void checkTermination() {
      if (++terminationChecks == failOnTerminationCheck) throw failure;
    }
  }
}
