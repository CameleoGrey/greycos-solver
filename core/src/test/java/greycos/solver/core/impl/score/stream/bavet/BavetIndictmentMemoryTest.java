package greycos.solver.core.impl.score.stream.bavet;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BavetIndictmentMemoryTest {

  @ParameterizedTest
  @EnumSource(
      value = ConstraintMatchPolicy.class,
      names = {"ENABLED_WITH_ORIGINAL_INDICTMENTS", "ENABLED_WITH_RAW_ORIGINAL_INDICTMENTS"})
  void groupedExistenceSourcesFitConstrainedHeap(
      ConstraintMatchPolicy policy, @TempDir Path temporaryDirectory) throws Exception {
    var output = temporaryDirectory.resolve(policy.name() + ".log");
    var javaExecutable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
    var process =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", javaExecutable).toString(),
                "-Xmx128m",
                "-cp",
                System.getProperty(
                    "surefire.test.class.path", System.getProperty("java.class.path")),
                HeapProbe.class.getName(),
                policy.name())
            .redirectErrorStream(true)
            .redirectOutput(output.toFile())
            .start();
    try {
      assertThat(process.waitFor(60, TimeUnit.SECONDS))
          .as("Grouped existence analysis must finish; output: %s", Files.readString(output))
          .isTrue();
      assertThat(process.exitValue()).as(Files.readString(output)).isZero();
      assertThat(Files.readString(output))
          .contains("entities=4000 occurrences=1 originals=4000 policy=" + policy);
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
        assertThat(process.waitFor(10, TimeUnit.SECONDS))
            .as("Grouped existence child JVM cleanup")
            .isTrue();
      }
    }
  }

  /**
   * The graph and final attribution are linear; caching every intermediate union previously
   * retained more than 32 million source references for this single scoring occurrence.
   */
  public static final class HeapProbe {

    public static void main(String[] args) {
      var count = 4_000;
      var policy = ConstraintMatchPolicy.valueOf(args[0]);
      var solution = TestdataSolution.generateSolution(1, count);
      ConstraintProvider provider =
          factory ->
              new Constraint[] {
                factory
                    .forEach(TestdataEntity.class)
                    .ifExists(TestdataEntity.class)
                    .groupBy(ConstraintCollectors.count())
                    .penalize(SimpleScore.ONE)
                    .asConstraint("grouped-existence")
              };
      var factory =
          new BavetConstraintStreamScoreDirectorFactory<TestdataSolution, SimpleScore>(
              TestdataSolution.buildSolutionDescriptor(), provider, EnvironmentMode.NO_ASSERT);
      try (var director =
          factory.createScoreDirectorBuilder().withConstraintMatchPolicy(policy).build()) {
        director.setWorkingSolution(solution);
        check(
            director.calculateScore().raw().equals(SimpleScore.of(-1)),
            "All entities must produce exactly one grouped penalty.");
        var details = director.getAnalysisDetails();
        var terminal = details.indictments().terminal();
        check(
            terminal != null && terminal.size() == 1 && terminal.containsKey((long) count),
            "The terminal indictment must contain only the group count.");
        var terminalIndictment = terminal.get((long) count);
        check(
            terminalIndictment.score().equals(SimpleScore.of(-1))
                && terminalIndictment.matchCount() == 1,
            "The group count must receive exactly one penalty.");
        var original = details.indictments().original();
        check(original != null && original.size() == count, "Every entity must be indicted.");
        var expectedIdentities = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        expectedIdentities.addAll(solution.getEntityList());
        for (var entry : original.entrySet()) {
          check(expectedIdentities.remove(entry.getKey()), "Unexpected original source identity.");
          var indictment = entry.getValue();
          check(indictment.indictedObject() == entry.getKey(), "Indictment identity changed.");
          check(
              indictment.score().equals(SimpleScore.of(-1))
                  && indictment.matchCount() == 1
                  && indictment.matches().size() == 1
                  && indictment.matches().getFirst().score().equals(SimpleScore.of(-1))
                  && indictment.matches().getFirst().matchCount() == 1,
              "Each original must receive the group's one penalty, without duplicate witnesses.");
        }
        check(expectedIdentities.isEmpty(), "An original entity was omitted.");

        var raw = details.rawAnalysis();
        if (policy.isMatchOccurrenceEnabled()) {
          check(raw != null && raw.matchOccurrences().size() == 1, "Expected one raw occurrence.");
          var occurrence = raw.matchOccurrences().getFirst();
          check(
              occurrence.matchScore().equals(SimpleScore.of(-1))
                  && occurrence.occurrenceSign() == 1
                  && occurrence.indictedObjects().equals(List.of((long) count)),
              "The raw group penalty or terminal selection changed.");
          var sources = occurrence.originalIndictedObjects();
          check(
              sources != null && sources.size() == count,
              "Raw originals must include every entity.");
          expectedIdentities.addAll(solution.getEntityList());
          for (var source : sources) {
            check(
                expectedIdentities.remove(source), "Raw originals contain an unexpected identity.");
          }
          check(expectedIdentities.isEmpty(), "A raw original entity was omitted.");
          var originalIndex = raw.originalIndictmentMap();
          check(
              originalIndex != null && originalIndex.size() == count,
              "Raw original index is incomplete.");
          for (var entity : solution.getEntityList()) {
            var indexed = originalIndex.get(entity);
            check(
                indexed != null && indexed.size() == 1 && indexed.getFirst() == occurrence,
                "Each raw original index entry must refer to the canonical occurrence.");
          }
        } else {
          check(raw == null, "Grouped analysis must not collect raw occurrences.");
        }
        System.out.printf(
            "entities=%d occurrences=1 originals=%d policy=%s%n", count, original.size(), policy);
      }
    }

    private static void check(boolean condition, String message) {
      if (!condition) {
        throw new AssertionError(message);
      }
    }
  }
}
