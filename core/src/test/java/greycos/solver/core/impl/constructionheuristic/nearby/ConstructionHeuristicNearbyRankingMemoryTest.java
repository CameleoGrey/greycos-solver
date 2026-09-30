package greycos.solver.core.impl.constructionheuristic.nearby;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import greycos.solver.core.impl.heuristic.selector.move.generic.SelectorBasedChangeMove;
import greycos.solver.core.impl.score.director.ScoreDirector;
import greycos.solver.core.preview.api.move.Move;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConstructionHeuristicNearbyRankingMemoryTest {

  @Test
  void queuedValueEntityRankingFitsConstrainedHeap(@TempDir Path temporaryDirectory)
      throws Exception {
    var output = temporaryDirectory.resolve("queued-value-ranking.log");
    var javaExecutable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    var process =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", javaExecutable).toString(),
                "-Xmx96m",
                "-cp",
                System.getProperty(
                    "surefire.test.class.path", System.getProperty("java.class.path")),
                HeapProbe.class.getName())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start();
    try {
      assertThat(process.waitFor(60, TimeUnit.SECONDS))
          .as(
              "Queued-value ranking must finish under a constrained heap; output: %s",
              Files.readString(output))
          .isTrue();
      assertThat(process.exitValue()).as(Files.readString(output)).isZero();
      assertThat(Files.readString(output))
          .contains("candidates=2000 anchors=2000 calls=4000000 assignments=2000");
    } finally {
      process.destroyForcibly();
    }
  }

  /** The old implementation retained four million anchor groups before emitting any assignment. */
  public static final class HeapProbe {

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
      var count = 2000;
      var descriptor = TestdataEntity.buildVariableDescriptorForValue();
      var solution = TestdataSolution.generateUninitializedSolution(1, count * 2);
      var value = solution.getValueList().getFirst();
      var candidates = new ArrayList<Move<TestdataSolution>>(count);
      for (var i = 0; i < count; i++) {
        solution.getEntityList().get(i).setValue(value);
        candidates.add(
            new SelectorBasedChangeMove<>(
                descriptor, solution.getEntityList().get(i + count), value));
      }
      // Only the working solution is needed; avoid mocking-agent overhead in the bounded JVM.
      var director =
          (ScoreDirector<TestdataSolution>)
              Proxy.newProxyInstance(
                  ScoreDirector.class.getClassLoader(),
                  new Class<?>[] {ScoreDirector.class},
                  (proxy, method, arguments) -> {
                    if (method.getName().equals("getWorkingSolution")) {
                      return solution;
                    }
                    throw new UnsupportedOperationException(method.toString());
                  });
      var meter = new CountingMeter();
      var profile =
          new ConstructionHeuristicNearbyRanking.MeterProfile(
              new ConstructionHeuristicNearbyProfile(
                  descriptor,
                  CountingMeter.class,
                  ConstructionHeuristicNearbyProfile.ArgumentShape.ENTITY_ENTITY,
                  "constrained-heap regression"),
              meter);
      var ranking =
          ConstructionHeuristicNearbyRanking.rank(
              candidates, descriptor, List.of(profile), true, director);
      if (meter.calls != (long) count * count) {
        throw new AssertionError("Expected every exact candidate/anchor distance to be evaluated.");
      }
      var assignments = 0;
      while (ranking.rankedMoves().hasNext()) {
        var move = (ChangeMove<?>) ranking.rankedMoves().next();
        if (move.getEntity() != solution.getEntityList().get(assignments + count)
            || move.getToPlanningValue() != value) {
          throw new AssertionError("Equal distances must retain candidate source order.");
        }
        assignments++;
      }
      if (assignments != count || ranking.tailMoves().hasNext()) {
        throw new AssertionError("Every admitted assignment must appear exactly once.");
      }
      System.out.printf(
          "candidates=%d anchors=%d calls=%d assignments=%d%n",
          count, count, meter.calls, assignments);
    }
  }

  public static final class CountingMeter implements NearbyDistanceMeter<Object, Object> {
    private long calls;

    @Override
    public double getNearbyDistance(Object origin, Object destination) {
      calls++;
      return 0;
    }
  }
}
