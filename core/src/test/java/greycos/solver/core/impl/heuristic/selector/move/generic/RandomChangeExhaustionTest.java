package greycos.solver.core.impl.heuristic.selector.move.generic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import greycos.solver.core.impl.heuristic.selector.entity.EntitySelector;
import greycos.solver.core.impl.heuristic.selector.entity.mimic.MimicRecordingEntitySelector;
import greycos.solver.core.impl.heuristic.selector.entity.mimic.MimicReplayingEntitySelector;
import greycos.solver.core.impl.heuristic.selector.value.ValueSelector;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.termination.BasicPlumbingTermination;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RandomChangeExhaustionTest {
  @Test
  void rareEligibleEntitySurvivesManyRetriesAndMimicRecordingIsPreserved() {
    var fixture = new Fixture(true);
    fixture.sample(index -> index <= 10_000 ? fixture.assigned : fixture.unassigned);
    var iterator = fixture.selector.iterator();
    assertThat(iterator.hasNext()).isTrue();
    var move = (ChangeMove<?>) iterator.next();
    assertThat(move.getEntity()).isSameAs(fixture.unassigned);
    assertThat(move.getToPlanningValue()).isSameAs(fixture.value);
    assertThat(fixture.draws).hasValue(10_001);
    fixture.unassigned.setValue(fixture.value);
    assertThat(fixture.selector.iterator()).isExhausted();
    assertThat(fixture.draws).hasValue(10_002);
    verify(fixture.source, never()).endingIterator();
    verify(fixture.source, never()).getSize();
    verify(fixture.values, never()).endingIterator(any());
    verify(fixture.values, never()).getSize(any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void inconclusiveSupersetDoesNotEmitAnExcludedOriginAndHonorsTermination(boolean construction) {
    var fixture = new Fixture(construction);
    // The source admits only the initialized entity, although the raw solution contains another.
    fixture.sample(index -> fixture.assigned);
    when(fixture.termination.isPhaseTerminated(fixture.phase))
        .thenAnswer(invocation -> fixture.draws.get() >= 7);
    assertThat(fixture.selector.iterator()).isExhausted();
    assertThat(fixture.draws).hasValue(7);
  }

  @Test
  void interruptionStopsEmptyValueRetriesWithoutClearingTheInterrupt() {
    var fixture = new Fixture(false);
    fixture.sample(
        index -> {
          if (index == 3) {
            Thread.currentThread().interrupt();
          }
          return fixture.assigned;
        });
    try {
      assertThat(fixture.selector.iterator()).isExhausted();
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(fixture.draws).hasValue(3);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void eligibilityProofDoesNotChangeConditionalUniformSamplingOrRandomConsumption() {
    var fixture = new Fixture(true);
    var other = new TestdataEntity("other");
    fixture.solution.setEntityList(List.of(fixture.assigned, fixture.unassigned, other));
    var generator = new Random(17);
    fixture.sample(index -> fixture.solution.getEntityList().get(generator.nextInt(3)));
    var oracle = new Random(17);
    var moves = fixture.selector.iterator();
    for (var i = 0; i < 500; i++) {
      int selected;
      do {
        selected = oracle.nextInt(3);
      } while (selected == 0);
      assertThat(((ChangeMove<?>) moves.next()).getEntity())
          .isSameAs(fixture.solution.getEntityList().get(selected));
    }
    assertThat(generator.nextLong()).isEqualTo(oracle.nextLong());
  }

  private static final class Fixture {
    final TestdataValue value = new TestdataValue("v");
    final TestdataEntity assigned = new TestdataEntity("assigned", value);
    final TestdataEntity unassigned = new TestdataEntity("unassigned");
    final TestdataSolution solution = new TestdataSolution();
    final AtomicInteger draws = new AtomicInteger();
    final EntitySelector<TestdataSolution> source = mock(EntitySelector.class);
    final ValueSelector<TestdataSolution> values = mock(ValueSelector.class);
    final AbstractPhaseScope<TestdataSolution> phase = mock(AbstractPhaseScope.class);
    final PhaseTermination<TestdataSolution> termination = mock(BasicPlumbingTermination.class);
    final ChangeMoveSelector<TestdataSolution> selector;

    Fixture(boolean construction) {
      solution.setValueList(List.of(value));
      solution.setEntityList(List.of(assigned, unassigned));
      var descriptor = TestdataSolution.buildSolutionDescriptor();
      var entityDescriptor = descriptor.findEntityDescriptorOrFail(TestdataEntity.class);
      when(source.getEntityDescriptor()).thenReturn(entityDescriptor);
      when(source.isNeverEnding()).thenReturn(true);
      when(values.getVariableDescriptor())
          .thenReturn(entityDescriptor.getGenuineVariableDescriptor("value"));
      var recorder = new MimicRecordingEntitySelector<>(source);
      var replayer = new MimicReplayingEntitySelector<>(recorder);
      when(values.iterator(any()))
          .thenAnswer(
              invocation -> {
                var entity = (TestdataEntity) invocation.getArgument(0);
                var replay = replayer.iterator();
                assertThat(replay.hasNext()).isTrue();
                assertThat(replay.next()).isSameAs(entity);
                return entity.getValue() == null
                    ? List.of((Object) value).iterator()
                    : Collections.emptyIterator();
              });
      InnerScoreDirector<TestdataSolution, ?> director = mock(InnerScoreDirector.class);
      when(director.getSolutionDescriptor()).thenReturn(descriptor);
      doReturn(director).when(phase).getScoreDirector();
      when(phase.getWorkingSolution()).thenReturn(solution);
      when(phase.getTermination()).thenReturn(termination);
      selector = new ChangeMoveSelector<>(recorder, values, true, construction);
      selector.phaseStarted(phase);
    }

    void sample(IntFunction<TestdataEntity> sampler) {
      when(source.iterator())
          .thenAnswer(
              invocation ->
                  new Iterator<Object>() {
                    @Override
                    public boolean hasNext() {
                      return true;
                    }

                    @Override
                    public Object next() {
                      return sampler.apply(draws.incrementAndGet());
                    }
                  });
    }
  }
}
