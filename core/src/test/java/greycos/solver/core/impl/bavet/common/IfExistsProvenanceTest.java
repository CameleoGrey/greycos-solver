package greycos.solver.core.impl.bavet.common;

import static greycos.solver.core.impl.bavet.common.DeferredSettleAware.MIN_DEFER_DISTANCE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import greycos.solver.core.impl.bavet.bi.joiner.DefaultBiJoiner;
import greycos.solver.core.impl.bavet.common.index.IndexerFactory;
import greycos.solver.core.impl.bavet.common.joiner.JoinerType;
import greycos.solver.core.impl.bavet.common.provenance.LineageHandle;
import greycos.solver.core.impl.bavet.common.provenance.LineageResolver;
import greycos.solver.core.impl.bavet.common.provenance.ProvenanceAccessor;
import greycos.solver.core.impl.bavet.common.provenance.TupleProvenance;
import greycos.solver.core.impl.bavet.common.tuple.InTupleStorePositionTracker;
import greycos.solver.core.impl.bavet.common.tuple.TupleLifecycle;
import greycos.solver.core.impl.bavet.common.tuple.TupleState;
import greycos.solver.core.impl.bavet.common.tuple.UniTuple;
import greycos.solver.core.impl.bavet.uni.IndexedIfExistsUniNode;
import greycos.solver.core.impl.bavet.uni.UnindexedIfExistsUniNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IfExistsProvenanceTest {

  enum Kind {
    UNINDEXED,
    EQUAL,
    COMPARISON,
    EQUAL_COMPARISON,
    FILTER_UNINDEXED,
    FILTER_EQUAL;

    boolean filtering() {
      return this == FILTER_UNINDEXED || this == FILTER_EQUAL;
    }

    boolean matches(Fact left, Fact right) {
      var indexedMatch =
          switch (this) {
            case UNINDEXED, FILTER_UNINDEXED -> true;
            case EQUAL, FILTER_EQUAL -> left.group == right.group;
            case COMPARISON -> left.key > right.key;
            case EQUAL_COMPARISON -> left.group == right.group && left.key > right.key;
          };
      return indexedMatch && (!filtering() || (left.enabled && right.enabled));
    }
  }

  static Stream<Arguments> modes() {
    return Stream.of(Kind.values())
        .flatMap(
            kind ->
                Stream.of(true, false)
                    .flatMap(
                        shouldExist ->
                            Stream.of(0L, MIN_DEFER_DISTANCE)
                                .map(distance -> Arguments.of(kind, shouldExist, distance))));
  }

  @ParameterizedTest
  @MethodSource("modes")
  void incrementalWitnessesMatchIndependentReplay(Kind kind, boolean shouldExist, long distance) {
    var fixture = new Fixture(kind, shouldExist, distance);
    var leftA = fixture.insertLeft(new Fact(1, 10));
    var leftB = fixture.insertLeft(new Fact(2, 20));
    var rightA = fixture.insertRight(new Fact(1, 1));
    var rightB = fixture.insertRight(new Fact(1, 2));
    var rightC = fixture.insertRight(new Fact(2, 30));
    var disabled = new Fact(1, 3);
    disabled.enabled = false;
    fixture.insertRight(disabled);
    fixture.settleAndAssert();
    var eventCount = fixture.downstream.events;

    // Parent lineage can change while tuple facts, matches and scores remain unchanged.
    fixture.replaceSources(leftA);
    fixture.replaceSources(rightA);
    fixture.assertSnapshot();
    assertThat(fixture.downstream.events).isEqualTo(eventCount);

    // All affected left tuples still have other witnesses; no boolean transition or score event.
    fixture.removeRight(rightB);
    fixture.settleAndAssert();
    assertThat(fixture.downstream.events).isEqualTo(eventCount);

    // The equality+comparison path reuses its bucket while its suffix changes.
    rightA.getA().key = 12;
    fixture.node.updateRight(rightA);
    leftA.getA().key = 15;
    fixture.node.updateLeft(leftA);
    fixture.settleAndAssert();

    rightC.getA().group = 1;
    rightC.getA().key = 4;
    fixture.node.updateRight(rightC);
    leftB.getA().group = 1;
    leftB.getA().key = 5;
    fixture.node.updateLeft(leftB);
    fixture.settleAndAssert();

    // Same-key filtering updates remove and rebuild only the actual satisfying pairs.
    rightA.getA().enabled = false;
    fixture.node.updateRight(rightA);
    leftA.getA().enabled = false;
    fixture.node.updateLeft(leftA);
    fixture.settleAndAssert();

    // Reinsertion may replace a logical occurrence on the same physical left tuple.
    fixture.node.retractLeft(leftA);
    fixture.leftHandles.put(leftA, LineageHandle.union(LineageHandle.source(new Object())));
    fixture.inputLeft.set(leftA, fixture.leftHandles.get(leftA));
    fixture.outputHandles.remove(leftA);
    leftA.getA().enabled = true;
    fixture.node.insertLeft(leftA);
    fixture.settleAndAssert();

    // Two right occurrences may carry the identical upstream handle; retracting one retains it.
    var duplicate = fixture.insertRight(new Fact(1, 4), fixture.rightHandles.get(rightC));
    fixture.settleAndAssert();
    fixture.removeRight(rightC);
    fixture.settleAndAssert();
    fixture.removeRight(duplicate);
    fixture.settleAndAssert();

    for (var right : new ArrayList<>(fixture.rightHandles.keySet())) {
      fixture.removeRight(right);
    }
    fixture.settleAndAssert();
    for (var left : new ArrayList<>(fixture.leftHandles.keySet())) {
      fixture.inputLeft.clear(left);
      fixture.node.retractLeft(left);
      fixture.leftHandles.remove(left);
    }
    fixture.settleAndAssert();
    assertThat(fixture.downstream.active).isEmpty();

    // Empty equality groups must be reusable after both sides have drained.
    fixture.insertRight(new Fact(1, 0));
    fixture.insertLeft(new Fact(1, 1));
    fixture.settleAndAssert();
  }

  @Test
  void notExistsNeedsOnlyLeftLineage() {
    var stores = new Stores();
    var input = new ProvenanceAccessor(stores.reserveNextLeft());
    var output = new ProvenanceAccessor(stores.reserveNextLeft());
    var downstream = new Capture(output);
    var node = new UnindexedIfExistsUniNode<Fact, Fact>(false, downstream, stores);
    node.setProvenance(new TupleProvenance(input, null, output));
    var source = new Object();
    var left = tuple(new Fact(0, 0), input, LineageHandle.source(source), stores.left);
    var right = UniTuple.of(new Fact(0, 0), stores.right);
    right.setState(TupleState.OK);
    node.insertLeft(left);
    settle(node);
    var handle = output.get(left);
    assertSources(handle, List.of(source));
    node.insertRight(right);
    settle(node);
    assertThat(downstream.active).isEmpty();
    node.retractRight(right);
    settle(node);
    assertThat(downstream.active.get(left)).isSameAs(handle);
    assertSources(handle, List.of(source));
  }

  @Test
  void siblingBranchesNeverModifyTheirSharedLeftInputLineage() {
    var stores = new Stores();
    var input = new ProvenanceAccessor(stores.reserveNextLeft());
    var rightInput = new ProvenanceAccessor(stores.reserveNextRight());
    var outputA = new ProvenanceAccessor(stores.reserveNextLeft());
    var outputB = new ProvenanceAccessor(stores.reserveNextLeft());
    var nodeA = new UnindexedIfExistsUniNode<Fact, Fact>(true, new Capture(outputA), stores);
    var nodeB = new UnindexedIfExistsUniNode<Fact, Fact>(true, new Capture(outputB), stores);
    nodeA.setProvenance(new TupleProvenance(input, rightInput, outputA));
    nodeB.setProvenance(new TupleProvenance(input, rightInput, outputB));
    var leftSource = new Object();
    var rightSourceA = new Object();
    var rightSourceB = new Object();
    var left = tuple(new Fact(0, 0), input, LineageHandle.source(leftSource), stores.left);
    var rightA =
        tuple(new Fact(0, 0), rightInput, LineageHandle.source(rightSourceA), stores.right);
    var rightB =
        tuple(new Fact(0, 0), rightInput, LineageHandle.source(rightSourceB), stores.right);
    nodeA.insertLeft(left);
    nodeB.insertLeft(left);
    nodeA.insertRight(rightA);
    nodeB.insertRight(rightB);
    settle(nodeA);
    settle(nodeB);
    assertSources(input.get(left), List.of(leftSource));
    assertSources(outputA.get(left), List.of(leftSource, rightSourceA));
    assertSources(outputB.get(left), List.of(leftSource, rightSourceB));
  }

  private static final class Fixture {
    final Kind kind;
    final boolean shouldExist;
    final Stores stores = new Stores();
    final ProvenanceAccessor inputLeft = new ProvenanceAccessor(stores.reserveNextLeft());
    final ProvenanceAccessor inputRight = new ProvenanceAccessor(stores.reserveNextRight());
    final ProvenanceAccessor output = new ProvenanceAccessor(stores.reserveNextLeft());
    final Capture downstream = new Capture(output);
    final AbstractIfExistsNode<UniTuple<Fact>, Fact> node;
    final Map<UniTuple<Fact>, LineageHandle> leftHandles = new IdentityHashMap<>();
    final Map<UniTuple<Fact>, LineageHandle> rightHandles = new IdentityHashMap<>();
    final Map<UniTuple<Fact>, LineageHandle> outputHandles = new IdentityHashMap<>();

    Fixture(Kind kind, boolean shouldExist, long distance) {
      this.kind = kind;
      this.shouldExist = shouldExist;
      BiPredicate<Fact, Fact> filter =
          kind.filtering() ? (left, right) -> left.enabled && right.enabled : null;
      var equal =
          new DefaultBiJoiner<Fact, Fact>(fact -> fact.group, JoinerType.EQUAL, fact -> fact.group);
      var comparison =
          new DefaultBiJoiner<Fact, Fact>(
              fact -> fact.key, JoinerType.GREATER_THAN, fact -> fact.key);
      node =
          switch (kind) {
            case UNINDEXED, FILTER_UNINDEXED ->
                new UnindexedIfExistsUniNode<>(shouldExist, downstream, filter, stores);
            case EQUAL, FILTER_EQUAL ->
                new IndexedIfExistsUniNode<>(
                    shouldExist, new IndexerFactory<>(equal), downstream, filter, stores);
            case COMPARISON ->
                new IndexedIfExistsUniNode<>(
                    shouldExist, new IndexerFactory<>(comparison), downstream, stores);
            case EQUAL_COMPARISON ->
                new IndexedIfExistsUniNode<>(
                    shouldExist, new IndexerFactory<>(equal.and(comparison)), downstream, stores);
          };
      node.setProvenance(new TupleProvenance(inputLeft, inputRight, output));
      node.setSettleDistance(distance);
    }

    UniTuple<Fact> insertLeft(Fact fact) {
      var handle = LineageHandle.union(LineageHandle.source(new Object()));
      var tuple = tuple(fact, inputLeft, handle, stores.left);
      leftHandles.put(tuple, handle);
      node.insertLeft(tuple);
      return tuple;
    }

    UniTuple<Fact> insertRight(Fact fact) {
      return insertRight(fact, LineageHandle.union(LineageHandle.source(new Object())));
    }

    UniTuple<Fact> insertRight(Fact fact, LineageHandle handle) {
      var tuple = tuple(fact, inputRight, handle, stores.right);
      rightHandles.put(tuple, handle);
      node.insertRight(tuple);
      return tuple;
    }

    void replaceSources(UniTuple<Fact> tuple) {
      var handle =
          leftHandles.containsKey(tuple) ? leftHandles.get(tuple) : rightHandles.get(tuple);
      handle.clear();
      handle.add(LineageHandle.source(new Object()));
    }

    void removeRight(UniTuple<Fact> right) {
      inputRight.clear(right); // Removal must use the captured membership handle.
      node.retractRight(right);
      rightHandles.remove(right);
    }

    void settleAndAssert() {
      settle(node);
      assertSnapshot();
    }

    void assertSnapshot() {
      var expectedActive =
          Collections.newSetFromMap(new IdentityHashMap<UniTuple<Fact>, Boolean>());
      for (var left : leftHandles.keySet()) {
        var witnesses =
            rightHandles.keySet().stream()
                .filter(right -> kind.matches(left.getA(), right.getA()))
                .toList();
        if (shouldExist == !witnesses.isEmpty()) {
          expectedActive.add(left);
          var expected = new ArrayList<>(new LineageResolver().resolve(leftHandles.get(left)));
          if (shouldExist) {
            for (var witness : witnesses) {
              expected.addAll(new LineageResolver().resolve(rightHandles.get(witness)));
            }
          }
          var handle = output.get(left);
          var old = outputHandles.putIfAbsent(left, handle);
          if (old != null) {
            assertThat(handle).isSameAs(old);
          }
          assertSources(handle, expected);
          assertThat(downstream.active.get(left)).isSameAs(handle);
        }
        assertThat(inputLeft.get(left)).isSameAs(leftHandles.get(left));
      }
      assertThat(downstream.active.keySet()).containsExactlyInAnyOrderElementsOf(expectedActive);
    }
  }

  private static void assertSources(LineageHandle handle, List<Object> expected) {
    Set<Object> expectedSet = Collections.newSetFromMap(new IdentityHashMap<>());
    expectedSet.addAll(expected);
    var actual = new LineageResolver().resolve(handle);
    assertThat(actual).hasSize(expectedSet.size());
    actual.forEach(source -> assertThat(expectedSet.contains(source)).isTrue());
  }

  private static void settle(AbstractIfExistsNode<?, ?> node) {
    node.prepareForSettle();
    node.getPropagator().propagateEverything();
  }

  private static UniTuple<Fact> tuple(
      Fact fact, ProvenanceAccessor accessor, LineageHandle handle, int size) {
    var tuple = UniTuple.of(fact, size);
    tuple.setState(TupleState.OK);
    accessor.set(tuple, handle);
    return tuple;
  }

  private static final class Fact {
    int group;
    int key;
    boolean enabled = true;

    Fact(int group, int key) {
      this.group = group;
      this.key = key;
    }
  }

  private static final class Stores implements InTupleStorePositionTracker {
    int left;
    int right;

    @Override
    public int reserveNextLeft() {
      return left++;
    }

    @Override
    public int reserveNextRight() {
      return right++;
    }
  }

  private static final class Capture implements TupleLifecycle<UniTuple<Fact>> {
    final ProvenanceAccessor output;
    final Map<UniTuple<Fact>, LineageHandle> active = new IdentityHashMap<>();
    int events;

    Capture(ProvenanceAccessor output) {
      this.output = output;
    }

    @Override
    public void insert(UniTuple<Fact> tuple) {
      assertThat(active.put(tuple, output.get(tuple))).isNull();
      events++;
    }

    @Override
    public void update(UniTuple<Fact> tuple) {
      assertThat(active.get(tuple)).isSameAs(output.get(tuple));
      events++;
    }

    @Override
    public void retract(UniTuple<Fact> tuple) {
      assertThat(active.remove(tuple)).isNotNull();
      events++;
    }

    @Override
    public void afterAllFactsInserted(boolean upstreamCanProduceTuples) {}

    @Override
    public boolean isActive() {
      return true;
    }
  }
}
