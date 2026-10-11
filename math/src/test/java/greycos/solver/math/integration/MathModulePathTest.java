package greycos.solver.math.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises provider construction, generated accessors and cloning from a named application. */
class MathModulePathTest {

  @TempDir Path temporary;

  @Test
  void solveFromNamedModule() throws Exception {
    var source = temporary.resolve("source");
    Files.createDirectories(source.resolve("consumer"));
    Files.writeString(
        source.resolve("module-info.java"),
        "module math.consumer { requires greycos.solver.math; }\n");
    Files.writeString(
        source.resolve("consumer/Main.java"),
        """
        package consumer;
        import greycos.solver.math.api.MathModel;
        import greycos.solver.math.api.MathSolver;
        import greycos.solver.math.config.SolveOptions;
        import greycos.solver.core.config.solver.EnvironmentMode;
        public final class Main {
          public static void main(String[] args) {
            var builder = MathModel.builder();
            var x = builder.intVar("x", 0, 3);
            var y = builder.intVar("y", 0, 3);
            builder.initialValue(x, 0);
            builder.fix(y, 2);
            builder.addConstraint("capacity", x.plus(y).le(4));
            builder.maximize(x);
            var model = builder.build();
            var options = SolveOptions.moveEvaluationLimit(100)
                .withEnvironmentMode(EnvironmentMode.FULL_ASSERT);
            var result = MathSolver.solve(model, options);
            if (result.value(y) != 2 || result.value(x) != 2
                || result.objectiveValue() != 2) {
              throw new AssertionError("Named-module solve failed: " + result);
            }
            var second = MathSolver.solve(model, options);
            if (!result.values().equals(second.values())) {
              throw new AssertionError("Named-module repeated solve changed assignments");
            }
            System.out.println("MATH_MODULE_PATH_OK");
          }
        }
        """);
    var classes = temporary.resolve("classes");
    var classpath =
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    var modulePath =
        Arrays.stream(classpath.split(File.pathSeparator))
            .filter(entry -> !Path.of(entry).getFileName().toString().equals("test-classes"))
            .collect(Collectors.joining(File.pathSeparator));
    execute(
        "javac",
        "--module-path",
        modulePath,
        "-d",
        classes.toString(),
        source.resolve("module-info.java").toString(),
        source.resolve("consumer/Main.java").toString());
    var output =
        execute(
            "java",
            "--module-path",
            modulePath + File.pathSeparator + classes,
            "--module",
            "math.consumer/consumer.Main");
    assertThat(output).contains("MATH_MODULE_PATH_OK");
  }

  private String execute(String executable, String... arguments) throws Exception {
    var command = new ArrayList<String>();
    command.add(Path.of(System.getProperty("java.home"), "bin", executable).toString());
    command.addAll(Arrays.asList(arguments));
    var outputFile = temporary.resolve(executable + ".log");
    var process =
        new ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(outputFile.toFile())
            .start();
    try {
      assertThat(process.waitFor(60, TimeUnit.SECONDS)).as("Completion of %s", command).isTrue();
      var output = Files.readString(outputFile);
      assertThat(process.exitValue()).as("Command %s\n%s", command, output).isZero();
      return output;
    } finally {
      if (process.isAlive()) {
        process.destroyForcibly();
      }
    }
  }
}
