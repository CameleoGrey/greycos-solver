package greycos.solver.core.impl.score.stream.collector.consecutive;

import static greycos.solver.core.impl.score.stream.collector.consecutive.ConsecutiveSequenceTestUtils.assertChain;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.impl.util.Pair;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConsecutiveSetTreeMutationTest {

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 10})
  void capturedContributionsMatchIndependentPartition(int maxDifference) {
    for (int seed = 0; seed < 3; seed++) {
      var random = new Random(seed);
      var tree =
          new ConsecutiveSetTree<AtomicInteger, Integer, Integer>(
              (a, b) -> b - a, Integer::sum, maxDifference, 0);
      var values = new ArrayList<AtomicInteger>();
      for (int i = 0; i < 32; i++) {
        values.add(new AtomicInteger());
      }
      var contributions = new ArrayList<Pair<AtomicInteger, Integer>>();
      // Insertion order independently supplies the fallback order when identity hashes collide.
      var counts = new LinkedHashMap<Pair<AtomicInteger, Integer>, Integer>();
      for (int step = 0; step < 1000; step++) {
        int operation = random.nextInt(3);
        try {
          if (contributions.isEmpty() || (operation == 0 && contributions.size() < 64)) {
            var value = values.get(random.nextInt(values.size()));
            value.set(random.nextInt(33) - 16);
            var contribution = new Pair<>(value, value.get());
            contributions.add(contribution);
            add(tree, counts, contribution);
          } else {
            var contribution = contributions.remove(random.nextInt(contributions.size()));
            remove(tree, counts, contribution);
            assertPartition(tree, counts, maxDifference);
            if (operation == 1) {
              var value =
                  random.nextBoolean()
                      ? contribution.key()
                      : values.get(random.nextInt(values.size()));
              value.set(random.nextInt(33) - 16);
              var replacement = new Pair<>(value, value.get());
              contributions.add(replacement);
              add(tree, counts, replacement);
            }
          }
          assertPartition(tree, counts, maxDifference);
        } catch (AssertionError error) {
          throw new AssertionError(
              "difference=" + maxDifference + ", seed=" + seed + ", step=" + step, error);
        }
      }
      while (!contributions.isEmpty()) {
        remove(tree, counts, contributions.remove(random.nextInt(contributions.size())));
        assertPartition(tree, counts, maxDifference);
      }
      assertChain(tree, List.of(), List.of(), List.of());
    }
  }

  private static void add(
      ConsecutiveSetTree<AtomicInteger, Integer, Integer> tree,
      Map<Pair<AtomicInteger, Integer>, Integer> counts,
      Pair<AtomicInteger, Integer> contribution) {
    tree.add(contribution.key(), contribution.value());
    counts.merge(contribution, 1, Integer::sum);
  }

  private static void remove(
      ConsecutiveSetTree<AtomicInteger, Integer, Integer> tree,
      Map<Pair<AtomicInteger, Integer>, Integer> counts,
      Pair<AtomicInteger, Integer> contribution) {
    assertThat(tree.remove(contribution.key(), contribution.value())).isTrue();
    counts.compute(contribution, (key, count) -> count == 1 ? null : count - 1);
  }

  private static void assertPartition(
      ConsecutiveSetTree<AtomicInteger, Integer, Integer> tree,
      Map<Pair<AtomicInteger, Integer>, Integer> counts,
      int maxDifference) {
    var sorted = new ArrayList<>(counts.keySet());
    sorted.sort(
        Comparator.<Pair<AtomicInteger, Integer>, Integer>comparing(Pair::value)
            .thenComparingInt(pair -> System.identityHashCode(pair.key())));
    var partitions = new ArrayList<List<Pair<AtomicInteger, Integer>>>();
    Integer previousIndex = null;
    for (var entry : sorted) {
      if (previousIndex == null || entry.value() - previousIndex > maxDifference) {
        partitions.add(new ArrayList<>());
      }
      partitions.getLast().add(entry);
      previousIndex = entry.value();
    }
    var items = new ArrayList<List<AtomicInteger>>();
    var lengths = new ArrayList<Integer>();
    var breaks = new ArrayList<Integer>();
    previousIndex = null;
    for (var partition : partitions) {
      items.add(partition.stream().map(Pair::key).toList());
      lengths.add(partition.getLast().value() - partition.getFirst().value() + maxDifference);
      if (previousIndex != null) {
        breaks.add(partition.getFirst().value() - previousIndex);
      }
      previousIndex = partition.getLast().value();
    }
    assertChain(tree, items, lengths, breaks);
  }

  @Test
  void equalValuesAtTheSameIndexRemainReferenceCounted() {
    var first = new String("same value");
    var equal = new String("same value");
    var tree =
        new ConsecutiveSetTree<String, Integer, Integer>((a, b) -> b - a, Integer::sum, 1, 0);
    tree.add(first, 0);
    tree.add(equal, 0);
    tree.add(equal, 1);
    assertChain(tree, List.of(List.of(first, equal)), List.of(2), List.of());
    assertThat(tree.remove(first, 99)).isFalse();
    tree.remove(first, 0);
    assertChain(tree, List.of(List.of(first, equal)), List.of(2), List.of());
    tree.remove(equal, 0);
    assertChain(tree, List.of(List.of(equal)), List.of(1), List.of());
    tree.remove(equal, 1);
    assertChain(tree, List.of(), List.of(), List.of());
  }

  @Test
  void equalIndexOrderingStillUsesIdentityHashes() {
    var values = new ArrayList<Object>();
    for (int i = 0; i < 64; i++) {
      values.add(new Object());
    }
    var tree =
        new ConsecutiveSetTree<Object, Integer, Integer>((a, b) -> b - a, Integer::sum, 1, 0);
    values.forEach(value -> tree.add(value, 0));
    values.sort(Comparator.comparingInt(System::identityHashCode));
    assertChain(tree, List.of(values), List.of(1), List.of());
    for (var value : values) {
      tree.remove(value, 0);
    }
    assertChain(tree, List.of(), List.of(), List.of());
  }

  @Test
  void collidingIdentityHashes(@TempDir Path temporaryDirectory) throws Exception {
    var output = temporaryDirectory.resolve("collision-probe.log");
    var javaExecutable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    var process =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", javaExecutable).toString(),
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:hashCode=2",
                "-cp",
                System.getProperty(
                    "surefire.test.class.path", System.getProperty("java.class.path")),
                CollisionProbe.class.getName())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start();
    try {
      assertThat(process.waitFor(60, TimeUnit.SECONDS)).as("Collision probe completed").isTrue();
      assertThat(process.exitValue()).as(Files.readString(output)).isZero();
    } finally {
      process.destroyForcibly();
    }
  }

  /** Runs separately so forced collisions cannot affect the main test JVM. */
  public static final class CollisionProbe {

    public static void main(String[] args) {
      var a = new Object();
      var b = new Object();
      var c = new Object();
      var bridge = new Object();
      assertThat(System.identityHashCode(a)).isEqualTo(System.identityHashCode(b));
      var tree =
          new ConsecutiveSetTree<Object, Integer, Integer>((x, y) -> y - x, Integer::sum, 1, 0);
      tree.add(a, 1);
      tree.add(a, 1);
      tree.add(b, 1);
      tree.add(c, 3);
      assertChain(tree, List.of(List.of(a, b), List.of(c)), List.of(1, 1), List.of(2));
      tree.add(bridge, 2);
      assertChain(tree, List.of(List.of(a, b, bridge, c)), List.of(3), List.of());
      tree.remove(bridge, 2);
      assertChain(tree, List.of(List.of(a, b), List.of(c)), List.of(1, 1), List.of(2));
      tree.remove(a, 1);
      assertChain(tree, List.of(List.of(a, b), List.of(c)), List.of(1, 1), List.of(2));
      tree.remove(b, 1);
      assertChain(tree, List.of(List.of(a), List.of(c)), List.of(1, 1), List.of(2));
      tree.remove(a, 1);
      assertChain(tree, List.of(List.of(c)), List.of(1), List.of());
      tree.remove(c, 3);
      assertChain(tree, List.of(), List.of(), List.of());

      tree.add(b, 1);
      tree.add(a, 1);
      assertChain(tree, List.of(List.of(b, a)), List.of(1), List.of());
      tree.remove(b, 1); // Also exercise removing the first colliding endpoint.
      assertChain(tree, List.of(List.of(a)), List.of(1), List.of());
      tree.remove(a, 1);
      assertChain(tree, List.of(), List.of(), List.of());

      var zeroDifferenceTree =
          new ConsecutiveSetTree<Object, Integer, Integer>((x, y) -> y - x, Integer::sum, 0, 0);
      zeroDifferenceTree.add(a, 0);
      zeroDifferenceTree.add(b, 0);
      assertChain(zeroDifferenceTree, List.of(List.of(a, b)), List.of(0), List.of());
      zeroDifferenceTree.remove(a, 0);
      zeroDifferenceTree.remove(b, 0);
      assertChain(zeroDifferenceTree, List.of(), List.of(), List.of());

      new ConsecutiveSetTreeMutationTest().capturedContributionsMatchIndependentPartition(1);
    }
  }
}
