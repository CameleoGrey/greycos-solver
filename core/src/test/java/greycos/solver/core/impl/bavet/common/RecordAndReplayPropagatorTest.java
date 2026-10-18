package greycos.solver.core.impl.bavet.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import greycos.solver.core.impl.bavet.AbstractBavetNodeNetwork;
import greycos.solver.core.impl.bavet.common.tuple.RecordingTupleLifecycle;
import greycos.solver.core.impl.bavet.common.tuple.TupleLifecycle;
import greycos.solver.core.impl.bavet.common.tuple.UniTuple;
import greycos.solver.core.impl.bavet.uni.ForEachUnfilteredUniNode;
import greycos.solver.core.impl.score.stream.bavet.common.BavetPrecomputeBuildHelper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RecordAndReplayPropagatorTest {

  @Test
  void repeatedUpdatesAreDeduplicatedWithinEachEpoch() {
    var downstream = downstream();
    var propagator = propagator(downstream);
    var entity = new TestEntity();
    propagator.insert(entity);
    propagator.propagateEverything();

    propagator.update(entity);
    propagator.update(entity);
    propagator.update(new TestEntity()); // No recorded output.
    propagator.propagateEverything();
    verify(downstream).update(argThat(tuple -> tuple.getA() == entity));

    propagator.update(entity);
    propagator.update(entity);
    propagator.propagateEverything();
    verify(downstream, times(2)).update(argThat(tuple -> tuple.getA() == entity));
  }

  @Test
  void reusedListAcceptsUpdatesInTheSameEpoch() {
    var downstream = downstream();
    var propagator = propagator(downstream);
    var oldEntity = new TestEntity();
    propagator.insert(oldEntity);
    propagator.propagateEverything();

    propagator.update(oldEntity);
    propagator.retract(oldEntity);
    var replacement = new TestEntity();
    propagator.insert(replacement);
    // Invalidating the cache returns its single list to the pool and immediately borrows it again.
    propagator.propagateRetracts();
    propagator.update(replacement);
    propagator.update(replacement);
    propagator.propagateUpdates();
    propagator.propagateInserts();

    verify(downstream, never()).update(argThat(tuple -> tuple.getA() == oldEntity));
    verify(downstream).retract(argThat(tuple -> tuple.getA() == oldEntity));
    verify(downstream).update(argThat(tuple -> tuple.getA() == replacement));
    verify(downstream).insert(argThat(tuple -> tuple.getA() == replacement));
  }

  @ParameterizedTest
  @ValueSource(longs = {Long.MAX_VALUE - 1, Long.MIN_VALUE, -1})
  void updatesSurviveEpochBoundaries(long epoch) throws ReflectiveOperationException {
    var downstream = downstream();
    var propagator = propagator(downstream);
    var epochField = RecordAndReplayPropagator.class.getDeclaredField("updateEpoch");
    epochField.setAccessible(true);
    epochField.setLong(propagator, epoch);
    var entity = new TestEntity();
    propagator.insert(entity);
    propagator.propagateEverything();

    propagator.update(entity);
    propagator.propagateEverything();
    propagator.update(entity);
    propagator.propagateEverything();
    verify(downstream, times(2)).update(argThat(tuple -> tuple.getA() == entity));
  }

  @Test
  void emptyRecordingsCanBecomeNonemptyAndBeRetracted() {
    var downstream = downstream();
    var propagator = propagator(downstream);
    var entity = new TestEntity();
    entity.included = false;
    propagator.insert(entity);
    propagator.propagateEverything();
    assertThat(propagator.canProduceTuples()).isFalse();
    propagator.update(entity);
    propagator.propagateEverything();
    verify(downstream, never()).update(any());

    entity.included = true;
    propagator.retract(entity);
    propagator.insert(entity);
    propagator.propagateEverything();
    assertThat(propagator.canProduceTuples()).isTrue();
    verify(downstream).insert(argThat(tuple -> tuple.getA() == entity));
    propagator.update(entity);
    propagator.propagateEverything();
    verify(downstream).update(argThat(tuple -> tuple.getA() == entity));

    entity.included = false;
    propagator.retract(entity);
    propagator.insert(entity);
    propagator.propagateEverything();
    assertThat(propagator.canProduceTuples()).isFalse();
    verify(downstream).retract(argThat(tuple -> tuple.getA() == entity));
  }

  @SuppressWarnings("unchecked")
  private static TupleLifecycle<UniTuple<TestEntity>> downstream() {
    return mock(TupleLifecycle.class);
  }

  private static RecordAndReplayPropagator<UniTuple<TestEntity>> propagator(
      TupleLifecycle<UniTuple<TestEntity>> downstream) {
    return new RecordAndReplayPropagator<>(
        RecordAndReplayPropagatorTest::buildHelper,
        tuple -> UniTuple.of(tuple.getA(), 0),
        downstream);
  }

  @SuppressWarnings("unchecked")
  private static BavetPrecomputeBuildHelper<UniTuple<TestEntity>> buildHelper() {
    var recording = new RecordingTupleLifecycle<UniTuple<TestEntity>>();
    var root =
        new ForEachUnfilteredUniNode<>(
            TestEntity.class,
            TupleLifecycle.conditionally(recording, entity -> entity.included),
            0);
    var network =
        new AbstractBavetNodeNetwork(
            Map.of(TestEntity.class, List.of(root)),
            new AbstractNode[][] {{root}},
            AbstractNode::getPropagator) {};
    BavetPrecomputeBuildHelper<UniTuple<TestEntity>> helper =
        mock(BavetPrecomputeBuildHelper.class);
    when(helper.getNodeNetwork()).thenReturn(network);
    when(helper.getRecordingTupleLifecycle()).thenReturn(recording);
    when(helper.isSourceEntityClass(TestEntity.class)).thenReturn(true);
    return helper;
  }

  private static final class TestEntity {
    private boolean included = true;
  }
}
