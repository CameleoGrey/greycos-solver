package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class GeneticAlgorithmListSnapshotTest {

  @Test
  void inputAndOutputArraysCannotChangeSharedAssignmentsOrFingerprint() {
    var arrays = new int[][] {{0, 1}, {}, {2}};
    var basics = new Object[] {"old", null};
    var original = new GeneticAlgorithmGenome(basics, arrays);
    int fingerprint = original.listFingerprint();
    var replacementBasics = new Object[] {"new", null};
    var changed = original.withBasicValues(replacementBasics);
    assertThat(changed.listSnapshot()).isSameAs(original.listSnapshot());

    arrays[0][0] = 99;
    arrays[2] = new int[0];
    basics[0] = "damaged";
    replacementBasics[0] = "damaged";
    original.lists()[0][0] = 88;
    original.list(2)[0] = 77;
    original.listSnapshot().copyLists()[0][1] = 66;
    changed.listSnapshot().copyList(0)[0] = 55;
    changed.toArray()[0] = "damaged";

    var expected = new int[][] {{0, 1}, {}, {2}};
    assertThat(original.listSnapshot()).isEqualTo(new GeneticAlgorithmListSnapshot(expected));
    assertThat(original.listFingerprint()).isEqualTo(fingerprint);
    assertThat(changed.listFingerprint()).isEqualTo(fingerprint);
    assertThat(original.value(0)).isEqualTo("old");
    assertThat(changed.value(0)).isEqualTo("new");
    assertThat(original.hashCode())
        .isEqualTo(31 * Arrays.hashCode(new Object[] {"old", null}) + fingerprint);
    assertThat(original.toString()).isEqualTo("[old, null]; lists=[[0, 1], [], [2]]");
  }

  @Test
  void replacingListsKeepsBasicOwnershipAndDoesNotChangeTheParent() {
    var original = new GeneticAlgorithmGenome(new Object[] {"basic"}, new int[][] {{0, 1}});
    var arrays = new int[][] {{1, 0}};
    var changed = original.withLists(arrays);
    arrays[0][0] = 2;
    changed.toArray()[0] = "damaged";
    original.toArray()[0] = "damaged";
    assertThat(original.list(0)).containsExactly(0, 1);
    assertThat(changed.list(0)).containsExactly(1, 0);
    assertThat(original.value(0)).isEqualTo("basic");
    assertThat(changed.value(0)).isEqualTo("basic");
  }

  @Test
  void hashCollisionsStillRequireExactAssignmentEquality() {
    var first = new GeneticAlgorithmListSnapshot(new int[][] {{0, 31}});
    var second = new GeneticAlgorithmListSnapshot(new int[][] {{1, 0}});
    assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
    assertThat(first).isNotEqualTo(second);
    assertThat(first).isEqualTo(new GeneticAlgorithmListSnapshot(first.copyLists()));
    assertThat(new GeneticAlgorithmListSnapshot(new int[][] {{0}, {31}})).isNotEqualTo(first);
    assertThat(GeneticAlgorithmListSnapshot.EMPTY.ownerCount()).isZero();
    assertThat(new GeneticAlgorithmListSnapshot(new int[][] {{}}))
        .isNotEqualTo(GeneticAlgorithmListSnapshot.EMPTY);
  }

  @Test
  void constructorRejectsNullRowsButRetainsInvalidIdsForWorkspacePreflight() {
    assertThatThrownBy(() -> new GeneticAlgorithmListSnapshot(new int[][] {null}))
        .isInstanceOf(NullPointerException.class);
    var invalid = new GeneticAlgorithmListSnapshot(new int[][] {{-1, Integer.MAX_VALUE}});
    assertThat(invalid.copyList(0)).containsExactly(-1, Integer.MAX_VALUE);
  }
}
