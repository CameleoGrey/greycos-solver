package greycos.solver.core.api.solver.multistage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListMultistageMoveSelectorConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Checks a real consumer compiled against production classes and the exported public surface. */
class MultistagePublicApiTest {
  @TempDir Path temporaryDirectory;

  @Test
  void externalBasicAndListProvidersCompileWithoutImplementationOrPreviewImports()
      throws Exception {
    var compiler = ToolProvider.getSystemJavaCompiler();
    assertThat(compiler).as("A JDK compiler is required for the public API test").isNotNull();
    var dependencies = dependencyLocations(coreLocation());
    var modulePath = productionModulePath(dependencies);
    var sourceDirectory = Files.createDirectories(temporaryDirectory.resolve("source/consumer"));
    var outputDirectory = Files.createDirectories(temporaryDirectory.resolve("classes"));
    var source = sourceDirectory.resolve("PublicMultistageConsumer.java");
    assertThat(CONSUMER).doesNotContain(".impl.", ".preview.", ".testcotwin.");
    Files.writeString(source, CONSUMER);
    var sources = new ArrayList<Path>(List.of(source));
    var options =
        new ArrayList<>(
            List.of(
                "-proc:none",
                "--release",
                Integer.toString(Runtime.version().feature()),
                "-d",
                outputDirectory.toString()));
    if (modulePath.isEmpty()) {
      options.addAll(List.of("--class-path", joinPaths(dependencies)));
    } else {
      var moduleSource = sourceDirectory.getParent().resolve("module-info.java");
      Files.writeString(
          moduleSource, "module multistage.external.consumer { requires greycos.solver.core; }\n");
      sources.add(moduleSource);
      options.addAll(
          List.of(
              "--module-path",
              joinPaths(modulePath),
              "--class-path",
              Files.createDirectories(temporaryDirectory.resolve("empty-classpath")).toString()));
    }
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
      boolean compiled =
          compiler
              .getTask(
                  null,
                  manager,
                  diagnostics,
                  options,
                  null,
                  manager.getJavaFileObjectsFromPaths(sources))
              .call();
      assertThat(compiled)
          .withFailMessage(
              "External multistage consumer failed:%n%s",
              diagnostics.getDiagnostics().stream()
                  .map(Object::toString)
                  .collect(Collectors.joining("\n")))
          .isTrue();
    }
    assertThat(outputDirectory.resolve("consumer/PublicMultistageConsumer$BasicProvider.class"))
        .exists();
    assertThat(outputDirectory.resolve("consumer/PublicMultistageConsumer$ListProvider.class"))
        .exists();
    if (!modulePath.isEmpty()) assertThat(outputDirectory.resolve("module-info.class")).exists();
  }

  @Test
  void stableSignaturesContainOnlyPublicExportedTypes() throws Exception {
    var descriptor =
        ModuleFinder.of(coreLocation())
            .find("greycos.solver.core")
            .orElseThrow(() -> new AssertionError("The core module descriptor must be present."))
            .descriptor();
    var exported =
        descriptor.exports().stream()
            .filter(export -> !export.isQualified())
            .map(ModuleDescriptor.Exports::source)
            .collect(Collectors.toSet());
    var types =
        List.<Class<?>>of(
            BasicVariableStageProvider.class,
            ListVariableStageProvider.class,
            BasicVariableCustomStage.class,
            ListVariableCustomStage.class,
            BasicVariableMoveEvaluator.class,
            ListVariableMoveEvaluator.class,
            MultistageMoveEvaluator.class,
            MultistageOperation.class,
            MultistageStageResult.class,
            MultistageStageResult.Kind.class,
            MultistagePosition.class,
            MultistageEvaluation.class,
            MultistageMoveSelectorConfig.class,
            ListMultistageMoveSelectorConfig.class);
    for (var type : types) {
      var visited = new HashSet<Type>();
      assertPublicType(type, exported, visited);
      for (var parent : type.getGenericInterfaces()) assertPublicType(parent, exported, visited);
      for (var constructor : type.getConstructors()) {
        for (var parameter : constructor.getGenericParameterTypes())
          assertPublicType(parameter, exported, visited);
        for (var exception : constructor.getGenericExceptionTypes())
          assertPublicType(exception, exported, visited);
      }
      for (var method : type.getDeclaredMethods()) {
        if (!Modifier.isPublic(method.getModifiers())) continue;
        assertPublicType(method.getGenericReturnType(), exported, visited);
        for (var parameter : method.getGenericParameterTypes())
          assertPublicType(parameter, exported, visited);
        for (var exception : method.getGenericExceptionTypes())
          assertPublicType(exception, exported, visited);
      }
    }
  }

  private static void assertPublicType(Type type, Set<String> exported, Set<Type> visited) {
    if (!visited.add(type)) return;
    assertThat(type.getTypeName())
        .doesNotContain("greycos.solver.core.impl.", "greycos.solver.core.preview.");
    if (type instanceof Class<?> clazz) {
      if (clazz.isArray()) {
        assertPublicType(clazz.getComponentType(), exported, visited);
      } else if (!clazz.isPrimitive()) {
        assertThat(Modifier.isPublic(clazz.getModifiers()))
            .as("Public signature type %s", clazz)
            .isTrue();
        if (clazz.getName().startsWith("greycos.solver.core.")) {
          assertThat(exported)
              .as("Unqualified export for %s", clazz)
              .contains(clazz.getPackageName());
        }
      }
    } else if (type instanceof ParameterizedType parameterized) {
      assertPublicType(parameterized.getRawType(), exported, visited);
      for (var argument : parameterized.getActualTypeArguments())
        assertPublicType(argument, exported, visited);
    } else if (type instanceof TypeVariable<?> variable) {
      for (var bound : variable.getBounds()) assertPublicType(bound, exported, visited);
    } else if (type instanceof WildcardType wildcard) {
      for (var bound : wildcard.getLowerBounds()) assertPublicType(bound, exported, visited);
      for (var bound : wildcard.getUpperBounds()) assertPublicType(bound, exported, visited);
    } else if (type instanceof GenericArrayType array) {
      assertPublicType(array.getGenericComponentType(), exported, visited);
    }
  }

  private static Path coreLocation() throws Exception {
    return Path.of(
        MultistageMoveEvaluator.class.getProtectionDomain().getCodeSource().getLocation().toURI());
  }

  private static List<Path> dependencyLocations(Path coreLocation) {
    var locations = new LinkedHashSet<Path>();
    locations.add(coreLocation);
    for (var property : List.of("java.class.path", "surefire.test.class.path", "jdk.module.path")) {
      for (var entry :
          System.getProperty(property, "")
              .split(java.util.regex.Pattern.quote(File.pathSeparator))) {
        if (entry.isBlank()) continue;
        var path = Path.of(entry);
        if (Files.isRegularFile(path) && entry.endsWith(".jar")) locations.add(path);
      }
    }
    return List.copyOf(locations);
  }

  /**
   * Resolve only the production module graph, keeping JUnit/Mockito/test classes out of the
   * consumer.
   */
  private static List<Path> productionModulePath(List<Path> locations) {
    Map<String, Path> moduleLocations = new HashMap<>();
    Map<String, ModuleDescriptor> descriptors = new HashMap<>();
    for (var location : locations) {
      for (var reference : ModuleFinder.of(location).findAll()) {
        moduleLocations.putIfAbsent(reference.descriptor().name(), location);
        descriptors.putIfAbsent(reference.descriptor().name(), reference.descriptor());
      }
    }
    var selected = new LinkedHashSet<Path>();
    var visited = new HashSet<String>();
    var pending = new ArrayDeque<>(List.of("greycos.solver.core"));
    var systemModules = ModuleFinder.ofSystem();
    while (!pending.isEmpty()) {
      var name = pending.removeFirst();
      if (!visited.add(name) || systemModules.find(name).isPresent()) continue;
      var descriptor = descriptors.get(name);
      if (descriptor == null) return List.of();
      selected.add(moduleLocations.get(name));
      for (var requirement : descriptor.requires()) pending.addLast(requirement.name());
    }
    return List.copyOf(selected);
  }

  private static String joinPaths(List<Path> paths) {
    return paths.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
  }

  private static final String CONSUMER =
      """
      package consumer;

      import java.util.List;
      import java.util.random.RandomGenerator;
      import greycos.solver.core.api.score.SimpleScore;
      import greycos.solver.core.api.solver.multistage.BasicVariableCustomStage;
      import greycos.solver.core.api.solver.multistage.BasicVariableStageProvider;
      import greycos.solver.core.api.solver.multistage.ListVariableCustomStage;
      import greycos.solver.core.api.solver.multistage.ListVariableStageProvider;
      import greycos.solver.core.api.solver.multistage.MultistageStageResult;
      import greycos.solver.core.config.heuristic.selector.move.generic.MultistageMoveSelectorConfig;
      import greycos.solver.core.config.heuristic.selector.move.generic.list.ListMultistageMoveSelectorConfig;

      public final class PublicMultistageConsumer {
        public static final class Problem { public List<Entity> entities; }
        public static final class Entity { public Integer value; public List<Integer> values; }
        public static final class BasicProvider
            implements BasicVariableStageProvider<Problem, Entity, Integer, SimpleScore> {
          private Problem solution;
          public void initialize(Problem solution) { this.solution = solution; }
          public long getCandidateCount() { return solution.entities.size(); }
          public List<BasicVariableCustomStage<Problem, Entity, Integer, SimpleScore>> createStages(
              long candidateIndex, RandomGenerator random) {
            var entity = solution.entities.get(Math.toIntExact(candidateIndex));
            return List.of(evaluator -> MultistageStageResult.apply(evaluator.unassign(entity)),
                evaluator -> evaluator.bestFit(evaluator.legalValues(entity).stream()
                    .map(value -> evaluator.assign(entity, value)).toList()));
          }
          public void phaseEnded() { solution = null; }
        }
        public static final class ListProvider
            implements ListVariableStageProvider<Problem, Entity, Integer, SimpleScore> {
          private Problem solution;
          public void initialize(Problem solution) { this.solution = solution; }
          public long getCandidateCount() { return solution.entities.size(); }
          public List<ListVariableCustomStage<Problem, Entity, Integer, SimpleScore>> createStages(
              long candidateIndex, RandomGenerator random) {
            var entity = solution.entities.get(Math.toIntExact(candidateIndex));
            if (entity.values.isEmpty()) return List.of();
            var value = entity.values.getFirst();
            return List.of(evaluator -> MultistageStageResult.apply(evaluator.unassign(value)),
                evaluator -> evaluator.bestFit(evaluator.legalPositions(value).stream()
                    .map(position -> position.isUnassigned() ? evaluator.unassign(value)
                        : evaluator.place(value, position.entity(), position.index())).toList()));
          }
          public void phaseEnded() { solution = null; }
        }
        public static MultistageMoveSelectorConfig basicConfig() {
          return new MultistageMoveSelectorConfig().withStageProviderClass(BasicProvider.class)
              .withEntityClass(Entity.class).withVariableName("value")
              .withCandidateCountLimit(5).withProbeCountLimit(100);
        }
        public static ListMultistageMoveSelectorConfig listConfig() {
          return new ListMultistageMoveSelectorConfig().withStageProviderClass(ListProvider.class)
              .withEntityClass(Entity.class).withVariableName("values")
              .withCandidateCountLimit(7).withProbeCountLimit(200);
        }
      }
      """;
}
