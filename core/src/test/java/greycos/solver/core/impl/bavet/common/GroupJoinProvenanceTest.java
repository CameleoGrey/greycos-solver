package greycos.solver.core.impl.bavet.common;

import static greycos.solver.core.impl.bavet.common.DeferredSettleAware.MIN_DEFER_DISTANCE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.bavet.bi.IndexedJoinBiNode;
import greycos.solver.core.impl.bavet.bi.UnindexedJoinBiNode;
import greycos.solver.core.impl.bavet.bi.joiner.DefaultBiJoiner;
import greycos.solver.core.impl.bavet.common.index.IndexerFactory;
import greycos.solver.core.impl.bavet.common.joiner.JoinerType;
import greycos.solver.core.impl.bavet.common.provenance.LineageHandle;
import greycos.solver.core.impl.bavet.common.provenance.LineageResolver;
import greycos.solver.core.impl.bavet.common.provenance.ProvenanceAccessor;
import greycos.solver.core.impl.bavet.common.provenance.TupleProvenance;
import greycos.solver.core.impl.bavet.common.tuple.BiTuple;
import greycos.solver.core.impl.bavet.common.tuple.InOutTupleStorePositionTracker;
import greycos.solver.core.impl.bavet.common.tuple.Tuple;
import greycos.solver.core.impl.bavet.common.tuple.TupleLifecycle;
import greycos.solver.core.impl.bavet.common.tuple.TupleState;
import greycos.solver.core.impl.bavet.common.tuple.UniTuple;
import greycos.solver.core.impl.bavet.uni.Group0Mapping1CollectorUniNode;
import greycos.solver.core.impl.bavet.uni.Group1Mapping0CollectorUniNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GroupJoinProvenanceTest {

  private static final ProvenanceAccessor GROUP_INPUT = new ProvenanceAccessor(15);
  private static final ProvenanceAccessor GROUP_OUTPUT = new ProvenanceAccessor(0);

  @Test
  void groupRetainsDuplicateOccurrencesAndFollowsLiveSourcesWithoutEvents() {
    var downstream = new Capture<UniTuple<String>>(GROUP_OUTPUT);
    var node =
        new Group1Mapping0CollectorUniNode<String, String>(
            key -> key,
            new AtomicInteger()::getAndIncrement,
            downstream,
            1,
            EnvironmentMode.NO_ASSERT);
    node.setProvenance(new TupleProvenance(GROUP_INPUT, null, GROUP_OUTPUT));
    var originalSource = new Object();
    var replacementSource = new Object();
    var parent = LineageHandle.union(LineageHandle.source(originalSource));
    var first = input("group", GROUP_INPUT, parent, 16);
    var duplicate = input("group", GROUP_INPUT, parent, 16);
    node.insert(first);
    node.insert(duplicate);
    node.getPropagator().propagateEverything();
    var output = downstream.active.getFirst();
    var groupLineage = GROUP_OUTPUT.get(output);
    assertSources(groupLineage, originalSource);
    assertThat(downstream.events).containsExactly("insert");

    parent.clear();
    parent.add(LineageHandle.source(replacementSource));
    assertSources(groupLineage, replacementSource);
    assertThat(downstream.events).containsExactly("insert");

    // Removal must use the captured contributor, even if upstream cleared its own tuple slot.
    GROUP_INPUT.clear(first);
    node.retract(first);
    node.getPropagator().propagateEverything();
    assertThat(GROUP_OUTPUT.get(output)).isSameAs(groupLineage);
    assertSources(groupLineage, replacementSource);

    GROUP_INPUT.clear(duplicate);
    node.retract(duplicate);
    node.getPropagator().propagateEverything();
    assertThat(downstream.active).isEmpty();
    assertThat(GROUP_OUTPUT.get(output)).isSameAs(groupLineage);
    assertSources(groupLineage);
    assertThat(downstream.retracted).containsExactly(output);
  }

  @Test
  void keyChangesMoveOnlyTheActualContributorBetweenStableGroups() {
    var downstream = new Capture<UniTuple<String>>(GROUP_OUTPUT);
    var node =
        new Group1Mapping0CollectorUniNode<String, String>(
            key -> key,
            new AtomicInteger()::getAndIncrement,
            downstream,
            1,
            EnvironmentMode.NO_ASSERT);
    node.setProvenance(new TupleProvenance(GROUP_INPUT, null, GROUP_OUTPUT));
    var sourceA = new Object();
    var sourceB = new Object();
    var sourceC = new Object();
    var a = input("left", GROUP_INPUT, LineageHandle.source(sourceA), 16);
    var b = input("left", GROUP_INPUT, LineageHandle.source(sourceB), 16);
    var c = input("right", GROUP_INPUT, LineageHandle.source(sourceC), 16);
    node.insert(a);
    node.insert(b);
    node.insert(c);
    node.getPropagator().propagateEverything();
    var left =
        downstream.active.stream()
            .filter(tuple -> tuple.getA().equals("left"))
            .findFirst()
            .orElseThrow();
    var right =
        downstream.active.stream()
            .filter(tuple -> tuple.getA().equals("right"))
            .findFirst()
            .orElseThrow();
    var leftLineage = GROUP_OUTPUT.get(left);
    var rightLineage = GROUP_OUTPUT.get(right);

    a.setA("right");
    node.update(a);
    node.getPropagator().propagateEverything();
    assertSources(leftLineage, sourceB);
    assertSources(rightLineage, sourceC, sourceA);
    assertThat(GROUP_OUTPUT.get(left)).isSameAs(leftLineage);
    assertThat(GROUP_OUTPUT.get(right)).isSameAs(rightLineage);

    // The unchanged-key path must not add a second occurrence for an ordinary update.
    node.update(a);
    node.getPropagator().propagateEverything();
    node.retract(a);
    node.getPropagator().propagateEverything();
    assertSources(rightLineage, sourceC);
  }

  @Test
  void equalCollectorResultStillReplacesContributorLineage() {
    var downstream = new Capture<UniTuple<Long>>(GROUP_OUTPUT);
    var node =
        new Group0Mapping1CollectorUniNode<>(
            new AtomicInteger()::getAndIncrement,
            ConstraintCollectors.<String>count(),
            downstream,
            1,
            EnvironmentMode.NO_ASSERT);
    node.setProvenance(new TupleProvenance(GROUP_INPUT, null, GROUP_OUTPUT));
    var retainedSource = new Object();
    var removedSource = new Object();
    var newSource = new Object();
    var retained = input("same", GROUP_INPUT, LineageHandle.source(retainedSource), 16);
    var removed = input("same", GROUP_INPUT, LineageHandle.source(removedSource), 16);
    var added = input("same", GROUP_INPUT, LineageHandle.source(newSource), 16);
    node.insert(retained);
    node.insert(removed);
    node.getPropagator().propagateEverything();
    var output = downstream.active.getFirst();
    var lineage = GROUP_OUTPUT.get(output);
    assertThat(output.getA()).isEqualTo(2L);
    assertSources(lineage, retainedSource, removedSource);

    node.retract(removed);
    node.insert(added);
    node.getPropagator().propagateEverything();
    assertThat(downstream.active).containsExactly(output);
    assertThat(output.getA()).isEqualTo(2L);
    assertThat(GROUP_OUTPUT.get(output)).isSameAs(lineage);
    assertSources(lineage, retainedSource, newSource);
  }

  static Stream<Arguments> joins() {
    return Stream.of(false, true)
        .flatMap(
            indexed ->
                Stream.of(0L, MIN_DEFER_DISTANCE)
                    .map(settleDistance -> Arguments.of(indexed, settleDistance)));
  }

  @ParameterizedTest
  @MethodSource("joins")
  void joinTracksActualPartnersAndKeepsOutputHandleUntilRetraction(
      boolean indexed, long settleDistance) {
    var stores = new TestTracker();
    var leftAccessor = new ProvenanceAccessor(stores.reserveNextLeft());
    var rightAccessor = new ProvenanceAccessor(stores.reserveNextRight());
    var outputAccessor = new ProvenanceAccessor(stores.reserveNextOut());
    var downstream = new Capture<BiTuple<Integer, Integer>>(outputAccessor);
    AbstractJoinNode<UniTuple<Integer>, Integer, BiTuple<Integer, Integer>> node =
        indexed
            ? new IndexedJoinBiNode<>(
                new IndexerFactory<>(
                    new DefaultBiJoiner<Integer, Integer>(
                        value -> value, JoinerType.EQUAL, value -> value)),
                downstream,
                (a, b) -> true,
                stores)
            : new UnindexedJoinBiNode<>(downstream, Integer::equals, stores);
    node.setProvenance(new TupleProvenance(leftAccessor, rightAccessor, outputAccessor));
    node.setSettleDistance(settleDistance);
    var leftSource = new Object();
    var updatedLeftSource = new Object();
    var firstRightSource = new Object();
    var secondRightSource = new Object();
    var leftLineage = LineageHandle.union(LineageHandle.source(leftSource));
    var left = input(1, leftAccessor, leftLineage, stores.left);
    var firstRight = input(1, rightAccessor, LineageHandle.source(firstRightSource), stores.right);
    var secondRight =
        input(2, rightAccessor, LineageHandle.source(secondRightSource), stores.right);
    node.insertLeft(left);
    node.insertRight(firstRight);
    node.insertRight(secondRight);
    settle(node);
    var firstOutput = downstream.active.getFirst();
    var firstLineage = outputAccessor.get(firstOutput);
    assertSources(firstLineage, leftSource, firstRightSource);

    leftLineage.clear();
    leftLineage.add(LineageHandle.source(updatedLeftSource));
    assertSources(firstLineage, updatedLeftSource, firstRightSource);
    assertThat(downstream.events).containsExactly("insert");
    node.updateLeft(left);
    settle(node);
    assertThat(outputAccessor.get(firstOutput)).isSameAs(firstLineage);

    left.setA(2);
    node.updateLeft(left);
    settle(node);
    assertThat(downstream.active).hasSize(1);
    var secondOutput = downstream.active.getFirst();
    assertThat(secondOutput).isNotSameAs(firstOutput);
    assertSources(outputAccessor.get(secondOutput), updatedLeftSource, secondRightSource);
    assertThat(downstream.retracted).containsExactly(firstOutput);
    assertThat(outputAccessor.get(firstOutput)).isSameAs(firstLineage);

    rightAccessor.clear(secondRight);
    node.retractRight(secondRight);
    settle(node);
    assertThat(downstream.active).isEmpty();
    assertSources(outputAccessor.get(secondOutput), updatedLeftSource, secondRightSource);
  }

  private static void settle(AbstractJoinNode<?, ?, ?> node) {
    node.prepareForSettle();
    node.getPropagator().propagateEverything();
  }

  private static <T> UniTuple<T> input(
      T fact, ProvenanceAccessor accessor, LineageHandle lineage, int size) {
    var tuple = UniTuple.of(fact, size);
    tuple.setState(TupleState.OK);
    accessor.set(tuple, lineage);
    return tuple;
  }

  private static void assertSources(LineageHandle handle, Object... expected) {
    var actual = new LineageResolver().resolve(handle);
    assertThat(actual).hasSize(expected.length);
    for (int i = 0; i < expected.length; i++) {
      assertThat(actual.get(i)).isSameAs(expected[i]);
    }
  }

  private static final class Capture<T extends Tuple> implements TupleLifecycle<T> {
    private final ProvenanceAccessor accessor;
    private final List<T> active = new ArrayList<>();
    private final List<T> retracted = new ArrayList<>();
    private final List<String> events = new ArrayList<>();

    private Capture(ProvenanceAccessor accessor) {
      this.accessor = accessor;
    }

    @Override
    public void insert(T tuple) {
      accessor.get(tuple);
      active.add(tuple);
      events.add("insert");
    }

    @Override
    public void update(T tuple) {
      accessor.get(tuple);
      events.add("update");
    }

    @Override
    public void retract(T tuple) {
      accessor.get(tuple);
      active.remove(tuple);
      retracted.add(tuple);
      events.add("retract");
    }

    @Override
    public void afterAllFactsInserted(boolean upstreamCanProduceTuples) {}

    @Override
    public boolean isActive() {
      return true;
    }
  }

  private static final class TestTracker implements InOutTupleStorePositionTracker {
    private int left;
    private int right;
    private int output;

    @Override
    public int reserveNextLeft() {
      return left++;
    }

    @Override
    public int reserveNextRight() {
      return right++;
    }

    @Override
    public int reserveNextOut() {
      return output++;
    }

    @Override
    public int computeStoreSize() {
      return output;
    }
  }
}
