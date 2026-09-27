package greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import greycos.solver.core.api.score.stream.ConstraintProvider;

/** Builds application fixtures which are deliberately absent from the test class loader. */
final class ProviderLoaderTestSupport {

  static final String PROVIDER_NAME = "providerfixture.IsolatedProvider";

  static final String PROVIDER_SOURCE =
      """
      package providerfixture;

      import java.util.function.BiFunction;
      import greycos.solver.core.api.score.SimpleScore;
      import greycos.solver.core.api.score.stream.Constraint;
      import greycos.solver.core.api.score.stream.ConstraintFactory;
      import greycos.solver.core.api.score.stream.ConstraintProvider;

      public class IsolatedProvider implements ConstraintProvider {
        public static String prefix = "A";
        public static boolean first = true;
        public static int constructions;

        public IsolatedProvider() {
          constructions++;
        }

        public Constraint[] defineConstraints(ConstraintFactory factory) {
          // Computing frames must resolve both application types through the provider loader.
          Base selected = first ? new OnlyA() : new OnlyB();
          BiFunction<ConstraintFactory, String, Constraint> firstConstraint = this::firstConstraint;
          BiFunction<ConstraintFactory, String, Constraint> secondConstraint = IsolatedProvider::secondConstraint;
          return new Constraint[] {
            firstConstraint.apply(factory, selected.name() + "1"),
            secondConstraint.apply(factory, selected.name() + "2")
          };
        }

        private Constraint firstConstraint(ConstraintFactory factory, String name) {
          return factory.forEach(String.class).filter(value -> value.startsWith(prefix))
              .penalize(SimpleScore.ONE).asConstraint(name);
        }

        private static Constraint secondConstraint(ConstraintFactory factory, String name) {
          return factory.forEach(String.class).filter(value -> value.startsWith(prefix))
              .penalize(SimpleScore.ONE).asConstraint(name);
        }
      }

      abstract class Base {
        abstract String name();
      }

      class OnlyA extends Base {
        String name() { return "A"; }
      }

      class OnlyB extends Base {
        String name() { return "B"; }
      }
      """;

  private ProviderLoaderTestSupport() {}

  static Path compile(Path directory, Map<String, String> sources, List<String> pathOptions)
      throws Exception {
    var compiler = ToolProvider.getSystemJavaCompiler();
    assertThat(compiler).as("Provider isolation tests require a JDK compiler").isNotNull();
    var sourceDirectory = Files.createDirectories(directory.resolve("sources"));
    var classesDirectory = Files.createDirectories(directory.resolve("classes"));
    var sourceFiles = new ArrayList<Path>();
    for (var source : sources.entrySet()) {
      var file = sourceDirectory.resolve(source.getKey());
      Files.createDirectories(file.getParent());
      Files.writeString(file, source.getValue());
      sourceFiles.add(file);
    }
    var options =
        new ArrayList<>(
            List.of(
                "-proc:none",
                "--release",
                Integer.toString(Runtime.version().feature()),
                "-d",
                classesDirectory.toString()));
    options.addAll(pathOptions);
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (var fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
      var units = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
      assertThat(compiler.getTask(null, fileManager, diagnostics, options, null, units).call())
          .withFailMessage(
              "Provider fixture compilation failed:%n%s",
              diagnostics.getDiagnostics().stream()
                  .map(Object::toString)
                  .collect(Collectors.joining("\n")))
          .isTrue();
    }
    return classesDirectory;
  }

  static List<Path> classPathEntries() throws Exception {
    var locations = new LinkedHashSet<Path>();
    locations.add(
        Path.of(
            ConstraintProvider.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
    for (var property : List.of("java.class.path", "surefire.test.class.path", "jdk.module.path")) {
      for (var entry : System.getProperty(property, "").split(Pattern.quote(File.pathSeparator))) {
        if (!entry.isBlank()) locations.add(Path.of(entry));
      }
    }
    return List.copyOf(locations);
  }

  static String joinPaths(List<Path> paths) {
    return paths.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
  }

  @SuppressWarnings("unchecked")
  static CollectedConstraints collect(Object provider) throws Exception {
    var loader = provider.getClass().getClassLoader();
    var constraintType = loader.loadClass("greycos.solver.core.api.score.stream.Constraint");
    var factoryType = loader.loadClass("greycos.solver.core.api.score.stream.ConstraintFactory");
    var providerType = loader.loadClass("greycos.solver.core.api.score.stream.ConstraintProvider");
    var builderType =
        loader.loadClass("greycos.solver.core.api.score.stream.uni.UniConstraintBuilder");
    var streamType =
        loader.loadClass("greycos.solver.core.api.score.stream.uni.UniConstraintStream");
    var predicates = new ArrayList<Predicate<String>>();
    var names = new ArrayList<String>();
    var builder =
        Proxy.newProxyInstance(
            loader,
            new Class<?>[] {builderType},
            (proxy, method, args) -> {
              if (method.getName().equals("asConstraint")) {
                names.add((String) args[0]);
                return Proxy.newProxyInstance(
                    loader, new Class<?>[] {constraintType}, (ignored, called, values) -> null);
              }
              throw new AssertionError("Unexpected builder call: " + method);
            });
    var stream =
        Proxy.newProxyInstance(
            loader,
            new Class<?>[] {streamType},
            (proxy, method, args) -> {
              if (method.getName().equals("filter")) {
                predicates.add((Predicate<String>) args[0]);
                return proxy;
              }
              if (method.getName().equals("penalize")) return builder;
              throw new AssertionError("Unexpected stream call: " + method);
            });
    var factory =
        Proxy.newProxyInstance(
            loader,
            new Class<?>[] {factoryType},
            (proxy, method, args) -> {
              if (method.getName().equals("forEach")) return stream;
              throw new AssertionError("Unexpected factory call: " + method);
            });
    providerType.getMethod("defineConstraints", factoryType).invoke(provider, factory);
    return new CollectedConstraints(List.copyOf(predicates), List.copyOf(names));
  }

  record CollectedConstraints(List<Predicate<String>> predicates, List<String> names) {}
}
