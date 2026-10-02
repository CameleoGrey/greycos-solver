package greycos.solver.core.impl.heuristic.selector.move.generic;

import static greycos.solver.core.impl.heuristic.selector.SelectorTestUtils.phaseStarted;
import static greycos.solver.core.impl.heuristic.selector.SelectorTestUtils.solvingStarted;
import static greycos.solver.core.impl.heuristic.selector.SelectorTestUtils.stepStarted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.heuristic.selector.move.composite.UnionMoveSelector;
import greycos.solver.core.impl.multistage.MultistageDefinition;
import greycos.solver.core.impl.multistage.MultistageMoveRequest;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MultistageMoveSelectorTest {

  @Test
  void originalHonorsAttemptBudgetAcrossIterators() {
    var fixture = start(false, 100, 3);
    var first = fixture.selector.iterator();
    var second = fixture.selector.iterator();
    assertThat(request(first.next()).candidateIndex()).isZero();
    assertThat(request(second.next()).candidateIndex()).isEqualTo(1);
    assertThat(request(first.next()).candidateIndex()).isEqualTo(2);
    assertThat(first.hasNext()).isFalse();
    assertThat(second.hasNext()).isFalse();
    assertThat(fixture.selector.iterator().hasNext()).isFalse();
    assertThatThrownBy(first::next).isInstanceOf(NoSuchElementException.class);
    assertThat(fixture.selector.isNeverEnding()).isFalse();
    assertThat(fixture.selector.getSize()).isEqualTo(3);
    finish(fixture);
  }

  @Test
  void originalStopsAtPopulationSize() {
    var fixture = start(false, 2, 64);
    assertThat(drain(fixture).stream().map(MultistageMoveRequest::candidateIndex))
        .containsExactly(0L, 1L);
    assertThat(fixture.selector.getSize()).isEqualTo(2);
    finish(fixture);
  }

  @Test
  void randomSamplingIsBoundedAndDoesNotEnumerateThePopulation() {
    var fixture = start(true, Long.MAX_VALUE, 64);
    var requests = drain(fixture);
    assertThat(requests).hasSize(64);
    assertThat(requests)
        .allSatisfy(
            request -> {
              assertThat(request.candidateIndex()).isBetween(0L, Long.MAX_VALUE - 1);
              assertThat(request.definition()).isSameAs(fixture.definition);
            });
    assertThat(requests.stream().map(MultistageMoveRequest::candidateIndex).distinct().count())
        .isGreaterThan(1);
    assertThat(fixture.selector.isNeverEnding()).isFalse();
    finish(fixture);
    verify(fixture.definition, times(2)).candidateCount(fixture.director);
    verify(fixture.definition).closeEvaluationContext(fixture.director);
    verify(fixture.definition).invalidateEvaluationContexts();
    verifyNoMoreInteractions(fixture.definition);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void emptyPopulationHasAnEmptyIterator(boolean random) {
    var fixture = start(random, 0, 64);
    var iterator = fixture.selector.iterator();
    assertThat(fixture.selector.getSize()).isZero();
    assertThat(iterator.hasNext()).isFalse();
    assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
    finish(fixture);
  }

  @Test
  void randomCanResampleASingleCandidateButRemainsFinite() {
    var fixture = start(true, 1, 4);
    var requests = drain(fixture);
    assertThat(requests.stream().map(MultistageMoveRequest::candidateIndex))
        .containsExactly(0L, 0L, 0L, 0L);
    assertThat(requests.stream().map(MultistageMoveRequest::seed).distinct().count()).isEqualTo(4);
    finish(fixture);
  }

  @Test
  void requestRandomnessDoesNotDependOnOtherCoordinatorRandomConsumption() {
    var first = start(true, 1000, 5);
    var second = start(true, 1000, 5);
    var expected = drain(first);
    var actual = new ArrayList<MultistageMoveRequest<TestdataSolution>>();
    var iterator = second.selector.iterator();
    while (iterator.hasNext()) {
      for (var i = 0; i < 100; i++) {
        second.random.nextLong();
      }
      actual.add(request(iterator.next()));
    }
    assertThat(actual.stream().map(MultistageMoveRequest::candidateIndex))
        .containsExactlyElementsOf(
            expected.stream().map(MultistageMoveRequest::candidateIndex).toList());
    assertThat(actual.stream().map(MultistageMoveRequest::seed))
        .containsExactlyElementsOf(expected.stream().map(MultistageMoveRequest::seed).toList());
    finish(first);
    finish(second);
  }

  @Test
  void populationRefreshAndBudgetResetHappenAtStepStart() {
    var fixture = start(false, 2, 3);
    var stale = fixture.selector.iterator();
    stale.next();
    fixture.selector.stepEnded(fixture.step);
    assertThat(stale.hasNext()).isFalse();
    assertThatIllegalStateException().isThrownBy(fixture.selector::iterator);
    when(fixture.definition.candidateCount(fixture.director)).thenReturn(10L);
    var nextStep = stepStarted(fixture.selector, fixture.phase);
    assertThat(stale.hasNext()).isFalse();
    assertThat(drain(fixture).stream().map(MultistageMoveRequest::candidateIndex))
        .containsExactly(0L, 1L, 2L);
    fixture.selector.stepEnded(nextStep);
    fixture.selector.phaseEnded(fixture.phase);
    fixture.selector.solvingEnded(fixture.solver);
    verify(fixture.definition, times(3)).candidateCount(fixture.director);
    verify(fixture.definition).closeEvaluationContext(fixture.director);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void randomUnionExhaustsEachChildAndCannotReplenishTheirStepBudgets(boolean weighted) {
    MultistageDefinition<TestdataSolution> firstDefinition = mock(MultistageDefinition.class);
    MultistageDefinition<TestdataSolution> secondDefinition = mock(MultistageDefinition.class);
    InnerScoreDirector<TestdataSolution, SimpleScore> director = mock(InnerScoreDirector.class);
    when(firstDefinition.candidateCount(director)).thenReturn(100L);
    when(secondDefinition.candidateCount(director)).thenReturn(1L);
    var first = new MultistageMoveSelector<>(firstDefinition, true, 5);
    var second = new MultistageMoveSelector<>(secondDefinition, true, 3);
    var union =
        new UnionMoveSelector<>(
            List.of(first, second),
            true,
            weighted ? (scoreDirector, selector) -> selector == first ? 10.0 : 1.0 : null);
    var solver = solvingStarted(union, director, new Random(17));
    var phase = phaseStarted(union, solver);
    var step = stepStarted(union, phase);
    var requests = new ArrayList<MultistageMoveRequest<TestdataSolution>>();
    union.forEach(move -> requests.add(request(move)));
    assertThat(requests).hasSize(8);
    assertThat(requests.stream().filter(move -> move.definition() == firstDefinition).count())
        .isEqualTo(5);
    assertThat(requests.stream().filter(move -> move.definition() == secondDefinition).count())
        .isEqualTo(3);
    assertThat(union.isNeverEnding()).isFalse();
    assertThat(union.iterator().hasNext()).isFalse();
    union.stepEnded(step);
    union.phaseEnded(phase);
    union.solvingEnded(solver);
    verify(firstDefinition).closeEvaluationContext(director);
    verify(secondDefinition).closeEvaluationContext(director);
  }

  @Test
  void failedInitializationPreservesOriginalFailureAndCleansUp() {
    MultistageDefinition<TestdataSolution> definition = mock(MultistageDefinition.class);
    InnerScoreDirector<TestdataSolution, SimpleScore> director = mock(InnerScoreDirector.class);
    var originalFailure = new IllegalStateException("provider count failure");
    var cleanupFailure = new IllegalStateException("provider cleanup failure");
    when(definition.candidateCount(director)).thenThrow(originalFailure);
    doThrow(cleanupFailure).when(definition).closeEvaluationContext(director);
    var selector = new MultistageMoveSelector<>(definition, false, 64);
    var solver = solvingStarted(selector, director, new Random(7));
    assertThatThrownBy(() -> phaseStarted(selector, solver))
        .isSameAs(originalFailure)
        .hasSuppressedException(cleanupFailure);
    selector.solvingEnded(solver);
    verify(definition).closeEvaluationContext(director);
  }

  @Test
  void repeatedFailureInstanceDoesNotReplaceInitializationFailure() {
    MultistageDefinition<TestdataSolution> definition = mock(MultistageDefinition.class);
    InnerScoreDirector<TestdataSolution, SimpleScore> director = mock(InnerScoreDirector.class);
    var failure = new IllegalStateException("provider failure");
    when(definition.candidateCount(director)).thenThrow(failure);
    doThrow(failure).when(definition).closeEvaluationContext(director);
    var selector = new MultistageMoveSelector<>(definition, false, 64);
    var solver = solvingStarted(selector, director, new Random(7));
    assertThatThrownBy(() -> phaseStarted(selector, solver)).isSameAs(failure);
    assertThat(failure.getSuppressed()).isEmpty();
    selector.solvingEnded(solver);
    verify(definition).invalidateEvaluationContexts();
    verify(definition).closeEvaluationContext(director);
  }

  private static Fixture start(boolean randomSelection, long population, int limit) {
    MultistageDefinition<TestdataSolution> definition = mock(MultistageDefinition.class);
    InnerScoreDirector<TestdataSolution, SimpleScore> director = mock(InnerScoreDirector.class);
    when(definition.candidateCount(director)).thenReturn(population);
    var selector = new MultistageMoveSelector<>(definition, randomSelection, limit);
    var random = new Random(37);
    var solver = solvingStarted(selector, director, random);
    var phase = phaseStarted(selector, solver);
    var step = stepStarted(selector, phase);
    return new Fixture(definition, director, selector, random, solver, phase, step);
  }

  private static void finish(Fixture fixture) {
    fixture.selector.stepEnded(fixture.step);
    fixture.selector.phaseEnded(fixture.phase);
    fixture.selector.solvingEnded(fixture.solver);
  }

  private static List<MultistageMoveRequest<TestdataSolution>> drain(Fixture fixture) {
    var result = new ArrayList<MultistageMoveRequest<TestdataSolution>>();
    fixture.selector.forEach(move -> result.add(request(move)));
    return result;
  }

  @SuppressWarnings("unchecked")
  private static MultistageMoveRequest<TestdataSolution> request(Object move) {
    return (MultistageMoveRequest<TestdataSolution>) move;
  }

  private record Fixture(
      MultistageDefinition<TestdataSolution> definition,
      InnerScoreDirector<TestdataSolution, SimpleScore> director,
      MultistageMoveSelector<TestdataSolution> selector,
      Random random,
      SolverScope<TestdataSolution> solver,
      AbstractPhaseScope<TestdataSolution> phase,
      AbstractStepScope<TestdataSolution> step) {}
}
