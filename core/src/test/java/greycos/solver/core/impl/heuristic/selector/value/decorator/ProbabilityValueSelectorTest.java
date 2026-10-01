package greycos.solver.core.impl.heuristic.selector.value.decorator;

import static greycos.solver.core.impl.heuristic.selector.SelectorTestUtils.mockIterableValueSelector;
import static greycos.solver.core.impl.heuristic.selector.SelectorTestUtils.phaseStarted;
import static greycos.solver.core.impl.heuristic.selector.SelectorTestUtils.solvingStarted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

import java.util.NoSuchElementException;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testutil.TestRandom;

import org.junit.jupiter.api.Test;

class ProbabilityValueSelectorTest {

  @Test
  void repeatedSamplingIsNeverEndingWhilePopulationIterationEnds() {
    var first = new TestdataValue("first");
    var second = new TestdataValue("second");
    var child =
        mockIterableValueSelector(TestdataEntity.buildVariableDescriptorForValue(), first, second);
    var selector =
        new ProbabilityValueSelector<>(
            child, SelectionCacheType.PHASE, (scoreDirector, value) -> 1.0);
    InnerScoreDirector<TestdataSolution, SimpleScore> director = mock(InnerScoreDirector.class);
    var solverScope = solvingStarted(selector, director, new TestRandom(0.0, 0.75, 0.0, 0.75));
    var phaseScope = phaseStarted(selector, solverScope);

    assertThat(selector.isNeverEnding()).isTrue();
    assertThat(selector.getSize()).isEqualTo(2);
    var iterator = selector.iterator();
    assertThat(iterator.next()).isSameAs(first);
    assertThat(iterator.next()).isSameAs(second);
    assertThat(iterator.next()).isSameAs(first);
    assertThat(iterator.next()).isSameAs(second);
    assertThat(iterator.hasNext()).isTrue();
    var endingIterator = selector.endingIterator(null);
    assertThat(endingIterator).toIterable().containsExactly(first, second);
    assertThat(endingIterator.hasNext()).isFalse();
    assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(endingIterator::next);

    selector.phaseEnded(phaseScope);
    selector.solvingEnded(solverScope);
  }
}
