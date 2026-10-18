package greycos.solver.core.impl.bavet.common.provenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.impl.bavet.common.tuple.UniTuple;

import org.junit.jupiter.api.Test;

class LineageResolverTest {

  @Test
  void repeatedOccurrencesRetainTheirSourceUntilLastRemoval() {
    var source = new Object();
    var parent = LineageHandle.source(source);
    var group = LineageHandle.mutable();
    group.add(parent);
    group.add(parent);

    assertThat(new LineageResolver().resolve(group)).containsExactly(source);
    group.remove(parent);
    assertThat(new LineageResolver().resolve(group)).containsExactly(source);
    group.remove(parent);
    assertThat(new LineageResolver().resolve(group)).isEmpty();
    assertThatIllegalStateException().isThrownBy(() -> group.remove(parent));
  }

  @Test
  void retainsDistinctSourceIdentitiesUntilPublicAggregation() {
    var first = new EqualSource(1);
    var equalButDistinct = new EqualSource(1);
    var lineage =
        LineageHandle.union(
            LineageHandle.source(first),
            LineageHandle.source(equalButDistinct),
            LineageHandle.source(first));

    var sources = new LineageResolver().resolve(lineage);
    assertThat(sources).hasSize(2);
    assertThat(sources.get(0)).isSameAs(first);
    assertThat(sources.get(1)).isSameAs(equalButDistinct);
  }

  @Test
  void sharedDiamondPreservesFirstEncounterOrderAndSourceIdentity() {
    var first = new EqualSource(1);
    var equalButDistinct = new EqualSource(1);
    var last = new EqualSource(2);
    var shared = LineageHandle.frozen(List.of(first, equalButDistinct));
    var left = LineageHandle.union(LineageHandle.source(equalButDistinct), shared);
    var right = LineageHandle.union(LineageHandle.source(last), shared);
    var root = LineageHandle.union(left, right, shared);

    assertSources(new LineageResolver().resolve(root), equalButDistinct, first, last);
  }

  @Test
  void requestedRootOrderDoesNotChangeAncestorResults() {
    var first = new Object();
    var second = new Object();
    var third = new Object();
    var shared = LineageHandle.frozen(List.of(first, second));
    var left = LineageHandle.union(LineageHandle.source(second), shared);
    var right = LineageHandle.union(LineageHandle.source(third), shared);
    var root = LineageHandle.union(left, right);

    var descendantsFirst = new LineageResolver();
    assertSources(descendantsFirst.resolve(right), third, first, second);
    assertSources(descendantsFirst.resolve(left), second, first);
    var result = descendantsFirst.resolve(root);
    assertSources(result, second, first, third);
    assertThat(descendantsFirst.resolve(root)).isSameAs(result);
    assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);

    var ancestorFirst = new LineageResolver();
    assertSources(ancestorFirst.resolve(root), second, first, third);
    assertSources(ancestorFirst.resolve(left), second, first);
    assertSources(ancestorFirst.resolve(right), third, first, second);
  }

  @Test
  void branchSlotsAndSharedAncestryRemainIndependent() {
    var left = new Object();
    var witness = new Object();
    var tuple = UniTuple.of(left, 2);
    var baseAccessor = new ProvenanceAccessor(0);
    var existsAccessor = new ProvenanceAccessor(1);
    var base = LineageHandle.source(left);
    var witnessLineage = LineageHandle.source(witness);
    var exists = LineageHandle.union(base, witnessLineage);
    baseAccessor.set(tuple, base);
    existsAccessor.set(tuple, exists);

    var resolver = new LineageResolver();
    assertThat(resolver.resolve(baseAccessor.get(tuple))).containsExactly(left);
    assertThat(resolver.resolve(existsAccessor.get(tuple))).containsExactly(left, witness);
    exists.clear();
    assertThat(new LineageResolver().resolve(baseAccessor.get(tuple))).containsExactly(left);
  }

  @Test
  void sourceOnlyChangeAppearsInNextSnapshotWithoutReplacingTerminalHandle() {
    var originalWitness = new Object();
    var replacementWitness = new Object();
    var oldParent = LineageHandle.source(originalWitness);
    var live = LineageHandle.union(oldParent);
    var terminal = LineageHandle.union(live);
    var firstSnapshot = new LineageResolver().resolve(terminal);

    live.remove(oldParent);
    live.add(LineageHandle.source(replacementWitness));

    assertThat(firstSnapshot).containsExactly(originalWitness);
    assertThat(new LineageResolver().resolve(terminal)).containsExactly(replacementWitness);
  }

  @Test
  void frozenPrecomputeExportSurvivesInnerNetworkChanges() {
    var source = new Object();
    var inner = LineageHandle.union(LineageHandle.source(source));
    var exportedSources = new ArrayList<>(new LineageResolver().resolve(inner));
    var exported = LineageHandle.frozen(exportedSources);
    exportedSources.clear();
    inner.clear();

    assertThat(new LineageResolver().resolve(exported)).containsExactly(source);
  }

  @Test
  void deepLineageDoesNotUseTheCallStack() {
    var source = new Object();
    var lineage = LineageHandle.source(source);
    for (var i = 0; i < 20_000; i++) {
      lineage = LineageHandle.union(lineage);
    }

    assertThat(new LineageResolver().resolve(lineage)).containsExactly(source);
  }

  @Test
  void distinctRootsWithDeepSharedAncestryKeepTheirOwnSourcesAndOrder() {
    var first = new Object();
    var second = new Object();
    var shared = LineageHandle.frozen(List.of(first, second));
    for (var i = 0; i < 20_000; i++) {
      shared = LineageHandle.union(shared);
    }
    var resolver = new LineageResolver();
    var roots = new ArrayList<LineageHandle>();
    var results = new ArrayList<List<Object>>();
    for (var i = 0; i < 16; i++) {
      var ownSource = new Object();
      var root = LineageHandle.union(LineageHandle.source(ownSource), shared);
      var result = resolver.resolve(root);
      assertSources(result, ownSource, first, second);
      roots.add(root);
      results.add(result);
    }
    for (var i = roots.size() - 1; i >= 0; i--) {
      assertThat(resolver.resolve(roots.get(i))).isSameAs(results.get(i));
    }
  }

  @Test
  void allWitnessesSharedByManyContributorsAppearOnlyOnceInTheirGroup() {
    var originals = new ArrayList<Object>();
    var contributors = new ArrayList<LineageHandle>();
    var witnesses = LineageHandle.mutable();
    for (var i = 0; i < 256; i++) {
      var source = new Object();
      var contributor = LineageHandle.source(source);
      originals.add(source);
      contributors.add(contributor);
      witnesses.add(contributor);
    }
    var group = LineageHandle.mutable();
    for (var contributor : contributors) {
      group.add(LineageHandle.union(contributor, LineageHandle.union(witnesses)));
    }

    assertSources(new LineageResolver().resolve(group), originals.toArray());
  }

  @Test
  void rejectsCyclesInsteadOfHangingDuringAnalysis() {
    var first = LineageHandle.mutable();
    var second = LineageHandle.union(first);
    first.add(second);

    assertThatIllegalStateException()
        .isThrownBy(() -> new LineageResolver().resolve(first))
        .withMessageContaining("cycle");
  }

  @Test
  void cycleAfterAlreadySeenSourcesDoesNotPoisonOtherRootResults() {
    var source = new Object();
    var valid = LineageHandle.source(source);
    var first = LineageHandle.union(valid);
    var second = LineageHandle.union(valid, first);
    first.add(second);
    var resolver = new LineageResolver();
    var validResult = resolver.resolve(valid);

    assertThatIllegalStateException()
        .isThrownBy(() -> resolver.resolve(first))
        .withMessageContaining("cycle");
    assertThat(resolver.resolve(valid)).isSameAs(validResult);
    var otherSource = new Object();
    assertSources(
        resolver.resolve(LineageHandle.union(LineageHandle.source(otherSource), valid)),
        otherSource,
        source);
    assertThatIllegalStateException()
        .isThrownBy(() -> resolver.resolve(first))
        .withMessageContaining("cycle");
  }

  private static void assertSources(List<Object> actual, Object... expected) {
    assertThat(actual).hasSize(expected.length);
    for (var i = 0; i < expected.length; i++) {
      assertThat(actual.get(i)).isSameAs(expected[i]);
    }
  }

  private record EqualSource(int value) {}
}
