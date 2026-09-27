package greycos.solver.core.impl.heuristic.selector.entity.nearby;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.NoSuchElementException;

import greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyTestUtils;
import greycos.solver.core.impl.heuristic.selector.entity.mimic.MimicRecordingEntitySelector;
import greycos.solver.core.impl.heuristic.selector.entity.mimic.MimicReplayingEntitySelector;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.PlannerTestUtils;
import greycos.solver.core.testutil.TestNearbyRandom;
import greycos.solver.core.testutil.TestRandom;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NearbyEntitySelectorRegressionTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void excludesSelfByIdentityBeforeApplyingTheCap(boolean random) {
    var descriptor =
        TestdataSolution.buildSolutionDescriptor().findEntityDescriptorOrFail(TestdataEntity.class);
    var origin = new TestdataEntity("origin");
    var neighbor = new TestdataEntity("neighbor");
    var child = SelectorTestUtils.mockEntitySelector(descriptor, origin, neighbor);
    var replay = SelectorTestUtils.mockReplayingEntitySelector(descriptor, origin);
    var selector =
        new NearEntityNearbyEntitySelector<>(
            child, replay, (from, to) -> 0, new TestNearbyRandom(), random, 1, false);
    InnerScoreDirector<TestdataSolution, ?> director = mock(InnerScoreDirector.class);
    NearbyTestUtils.mockSupplyManager(director, null);
    var scope = SelectorTestUtils.solvingStarted(selector, director, new TestRandom(0, 0));
    var phase = PlannerTestUtils.delegatingPhaseScope(scope);
    selector.phaseStarted(phase);
    var iterator = selector.iterator();
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isSameAs(neighbor);
    assertThat(iterator.hasNext()).isEqualTo(random);
    if (random) {
      assertThat(iterator.next()).isSameAs(neighbor);
    } else {
      assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
      var listIterator = selector.listIterator();
      assertThat(listIterator.next()).isSameAs(neighbor);
      assertThat(listIterator.hasNext()).isFalse();
      assertThat(listIterator.previous()).isSameAs(neighbor);
    }
    selector.phaseEnded(phase);
    selector.solvingEnded(scope);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void emptyAndSingletonNeighborhoodsAreEmpty(boolean singleton) {
    var descriptor =
        TestdataSolution.buildSolutionDescriptor().findEntityDescriptorOrFail(TestdataEntity.class);
    var origin = new TestdataEntity("origin");
    var child =
        SelectorTestUtils.mockEntitySelector(
            descriptor, singleton ? new Object[] {origin} : new Object[0]);
    var replay = SelectorTestUtils.mockReplayingEntitySelector(descriptor, origin);
    var selector =
        new NearEntityNearbyEntitySelector<>(
            child, replay, (from, to) -> 0, new TestNearbyRandom(), true);
    InnerScoreDirector<TestdataSolution, ?> director = mock(InnerScoreDirector.class);
    NearbyTestUtils.mockSupplyManager(director, null);
    var scope = SelectorTestUtils.solvingStarted(selector, director, new TestRandom(new int[0]));
    var phase = PlannerTestUtils.delegatingPhaseScope(scope);
    selector.phaseStarted(phase);
    var iterator = selector.iterator();
    assertThat(iterator.hasNext()).isFalse();
    assertThat(iterator.hasNext()).isFalse();
    assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
    selector.phaseEnded(phase);
    selector.solvingEnded(scope);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void randomIteratorUsesTheCurrentRecording(boolean eager) {
    var descriptor =
        TestdataSolution.buildSolutionDescriptor().findEntityDescriptorOrFail(TestdataEntity.class);
    var first = new TestdataEntity("first");
    var second = new TestdataEntity("second");
    var child = SelectorTestUtils.mockEntitySelector(descriptor, first, second);
    var recorder =
        new MimicRecordingEntitySelector<>(
            SelectorTestUtils.mockEntitySelector(descriptor, first, second));
    var replay = new MimicReplayingEntitySelector<>(recorder);
    var selector =
        new NearEntityNearbyEntitySelector<>(
            child, replay, (from, to) -> 0, new TestNearbyRandom(), true, 1, eager);
    InnerScoreDirector<TestdataSolution, ?> director = mock(InnerScoreDirector.class);
    NearbyTestUtils.mockSupplyManager(director, null);
    var scope = SelectorTestUtils.solvingStarted(selector, director, new TestRandom(0, 0));
    var phase = PlannerTestUtils.delegatingPhaseScope(scope);
    selector.phaseStarted(phase);
    var recording = recorder.iterator();
    var iterator = selector.iterator();
    recording.next();
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.hasNext()).isTrue();
    assertThat(iterator.next()).isSameAs(second);
    recording.next();
    assertThat(iterator.next()).isSameAs(first);
    selector.phaseEnded(phase);
    selector.solvingEnded(scope);
  }
}
