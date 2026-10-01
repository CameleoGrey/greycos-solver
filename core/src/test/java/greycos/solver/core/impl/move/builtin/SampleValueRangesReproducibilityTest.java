package greycos.solver.core.impl.move.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SampleValueRangesReproducibilityTest {

  @Test
  void equalSizeRangeSamplingIsReproducibleAcrossFreshJvms(@TempDir Path temporaryDirectory)
      throws Exception {
    var firstTrace = runProbe(temporaryDirectory.resolve("first.log"));
    var secondTrace = runProbe(temporaryDirectory.resolve("second.log"));

    assertThat(firstTrace).hasSize(2);
    assertThat(firstTrace.getFirst()).startsWith("TRACE AB ");
    assertThat(firstTrace.getLast()).startsWith("TRACE BA ");
    assertThat(secondTrace).containsExactlyElementsOf(firstTrace);
  }

  private static List<String> runProbe(Path output) throws Exception {
    var javaExecutable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    var process =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", javaExecutable).toString(),
                "-cp",
                System.getProperty(
                    "surefire.test.class.path", System.getProperty("java.class.path")),
                SamplingProbe.class.getName())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start();
    try {
      assertThat(process.waitFor(60, TimeUnit.SECONDS))
          .as("Sampling probe must finish; output: %s", Files.readString(output))
          .isTrue();
      assertThat(process.exitValue()).as(Files.readString(output)).isZero();
      return Files.readAllLines(output).stream().filter(line -> line.startsWith("TRACE ")).toList();
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        assertThat(process.waitFor(10, TimeUnit.SECONDS))
            .as("Sampling probe must terminate after forced cleanup")
            .isTrue();
      }
    }
  }

  /** Each process checks both encounter orders, regardless of its immutable-set iteration salt. */
  public static final class SamplingProbe {

    public static void main(String[] args) {
      try (var fixture = new SampleValueRangesTestSupport()) {
        var ab = fixture.ranges(fixture.entityA, fixture.entityB);
        var ba = fixture.ranges(fixture.entityB, fixture.entityA);
        assertThat(ab.smallestRange()).isSameAs(fixture.rangeOf(fixture.entityA));
        assertThat(ba.smallestRange()).isSameAs(fixture.rangeOf(fixture.entityB));
        System.out.println("TRACE AB " + trace(ab));
        System.out.println("TRACE BA " + trace(ba));
      }
    }

    private static List<String> trace(SampleValueRanges<TestdataValue> ranges) {
      var random = DefaultRandomSource.seeded(0).moveIteratorUsage();
      var trace = new ArrayList<String>();
      for (var i = 0; i < 32; i++) {
        var value = ranges.findTarget(random, null);
        assertThat(value).isNotNull();
        assertThat(value.getCode()).isIn("b", "c");
        trace.add(value.getCode());
      }
      return trace;
    }
  }
}
