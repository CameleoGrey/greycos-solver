package greycos.solver.core.impl.alns;

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

import greycos.solver.core.api.solver.alns.AlnsAcceptancePolicy;
import greycos.solver.core.api.solver.alns.AlnsAssignment;
import greycos.solver.core.api.solver.alns.AlnsBasicVariable;
import greycos.solver.core.api.solver.alns.AlnsChange;
import greycos.solver.core.api.solver.alns.AlnsContext;
import greycos.solver.core.api.solver.alns.AlnsDestroyOperator;
import greycos.solver.core.api.solver.alns.AlnsEvaluation;
import greycos.solver.core.api.solver.alns.AlnsListVariable;
import greycos.solver.core.api.solver.alns.AlnsMutableSolutionView;
import greycos.solver.core.api.solver.alns.AlnsOperatorPair;
import greycos.solver.core.api.solver.alns.AlnsOutcome;
import greycos.solver.core.api.solver.alns.AlnsRanking;
import greycos.solver.core.api.solver.alns.AlnsRelatedness;
import greycos.solver.core.api.solver.alns.AlnsRepairOperator;
import greycos.solver.core.api.solver.alns.AlnsSelectionPolicy;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.api.solver.alns.AlnsTerminationException;
import greycos.solver.core.api.solver.alns.AlnsTrialResult;
import greycos.solver.core.api.solver.alns.AlnsVariable;
import greycos.solver.core.config.alns.AlnsAcceptanceType;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsDestroyOperatorType;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.alns.AlnsSelectionPolicyType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Compiles a real external consumer without access to test models or implementation packages. */
class AlnsPublicApiTest {
  @TempDir Path temporaryDirectory;

  @Test
  void standaloneCustomOperatorsAndConfigurationCompileAgainstThePublicApi() throws Exception {
    var compiler = ToolProvider.getSystemJavaCompiler();
    assertThat(compiler)
        .as("A JDK compiler is required for the public API contract test")
        .isNotNull();
    var coreLocation = coreLocation();
    var dependencies = dependencyLocations(coreLocation);
    var productionModulePath = productionModulePath(dependencies);
    var sourceDirectory = Files.createDirectories(temporaryDirectory.resolve("source/consumer"));
    var outputDirectory = Files.createDirectories(temporaryDirectory.resolve("classes"));
    var source = sourceDirectory.resolve("PublicAlnsConsumer.java");
    assertThat(CONSUMER).doesNotContain(".impl.", ".preview.", ".testcotwin.");
    Files.writeString(source, CONSUMER);
    var sources = new ArrayList<Path>();
    sources.add(source);
    var options =
        new ArrayList<>(
            List.of(
                "-proc:none",
                "--release",
                Integer.toString(Runtime.version().feature()),
                "-d",
                outputDirectory.toString()));
    if (!productionModulePath.isEmpty()) {
      var moduleSource = sourceDirectory.getParent().resolve("module-info.java");
      Files.writeString(
          moduleSource, "module alns.external.consumer { requires greycos.solver.core; }\n");
      sources.add(moduleSource);
      options.addAll(
          List.of(
              "--module-path",
              joinPaths(productionModulePath),
              "--class-path",
              Files.createDirectories(temporaryDirectory.resolve("empty-classpath")).toString()));
    } else {
      // Some IDE test launches expose only an incomplete module graph. Still exclude test-classes.
      options.addAll(List.of("--class-path", joinPaths(dependencies)));
    }
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (var fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
      var units = fileManager.getJavaFileObjectsFromPaths(sources);
      boolean compiled =
          compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
      assertThat(compiled)
          .withFailMessage(
              "Standalone ALNS %s consumer failed:\n%s",
              productionModulePath.isEmpty() ? "classpath" : "named-module",
              diagnostics.getDiagnostics().stream()
                  .map(Object::toString)
                  .collect(Collectors.joining("\n")))
          .isTrue();
    }
    assertThat(outputDirectory.resolve("consumer/PublicAlnsConsumer$Destroy.class")).exists();
    assertThat(outputDirectory.resolve("consumer/PublicAlnsConsumer$Repair.class")).exists();
    assertThat(outputDirectory.resolve("consumer/PublicAlnsConsumer$Selection.class")).exists();
    assertThat(outputDirectory.resolve("consumer/PublicAlnsConsumer$Acceptance.class")).exists();
    if (!productionModulePath.isEmpty())
      assertThat(outputDirectory.resolve("module-info.class")).exists();
  }

  @Test
  void publicAlnsSignaturesContainOnlyPublicExportedTypes() throws Exception {
    var descriptor =
        ModuleFinder.of(coreLocation())
            .find("greycos.solver.core")
            .orElseThrow(
                () -> new AssertionError("The compiled core module descriptor must be present."))
            .descriptor();
    var exported =
        descriptor.exports().stream()
            .filter(export -> !export.isQualified())
            .map(ModuleDescriptor.Exports::source)
            .collect(Collectors.toSet());
    var publicTypes =
        List.<Class<?>>of(
            AlnsAcceptancePolicy.class,
            AlnsAssignment.class,
            AlnsBasicVariable.class,
            AlnsChange.class,
            AlnsContext.class,
            AlnsDestroyOperator.class,
            AlnsEvaluation.class,
            AlnsListVariable.class,
            AlnsMutableSolutionView.class,
            AlnsOperatorPair.class,
            AlnsOutcome.class,
            AlnsRanking.class,
            AlnsRelatedness.class,
            AlnsRepairOperator.class,
            AlnsSelectionPolicy.class,
            AlnsTarget.class,
            AlnsTerminationException.class,
            AlnsTrialResult.class,
            AlnsVariable.class,
            AlnsPhaseConfig.class,
            AlnsDestroyOperatorConfig.class,
            AlnsRepairOperatorConfig.class,
            AlnsAcceptanceType.class,
            AlnsDestroyOperatorType.class,
            AlnsRepairOperatorType.class,
            AlnsSelectionPolicyType.class);
    for (var publicType : publicTypes) {
      var visited = new HashSet<Type>();
      assertPublicType(publicType, exported, visited);
      for (var type : publicType.getGenericInterfaces()) assertPublicType(type, exported, visited);
      for (var constructor : publicType.getConstructors()) {
        for (var type : constructor.getGenericParameterTypes())
          assertPublicType(type, exported, visited);
        for (var type : constructor.getGenericExceptionTypes())
          assertPublicType(type, exported, visited);
      }
      for (var method : publicType.getDeclaredMethods()) {
        if (!Modifier.isPublic(method.getModifiers())) continue;
        assertPublicType(method.getGenericReturnType(), exported, visited);
        for (var type : method.getGenericParameterTypes())
          assertPublicType(type, exported, visited);
        for (var type : method.getGenericExceptionTypes())
          assertPublicType(type, exported, visited);
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
    return Path.of(AlnsContext.class.getProtectionDomain().getCodeSource().getLocation().toURI());
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
      import greycos.solver.core.api.cotwin.entity.PlanningEntity;
      import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
      import greycos.solver.core.api.cotwin.solution.PlanningScore;
      import greycos.solver.core.api.cotwin.solution.PlanningSolution;
      import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
      import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
      import greycos.solver.core.api.cotwin.variable.PlanningVariable;
      import greycos.solver.core.api.score.SimpleScore;
      import greycos.solver.core.api.score.stream.Constraint;
      import greycos.solver.core.api.score.stream.ConstraintFactory;
      import greycos.solver.core.api.score.stream.ConstraintProvider;
      import greycos.solver.core.api.solver.alns.AlnsAcceptancePolicy;
      import greycos.solver.core.api.solver.alns.AlnsAssignment;
      import greycos.solver.core.api.solver.alns.AlnsContext;
      import greycos.solver.core.api.solver.alns.AlnsDestroyOperator;
      import greycos.solver.core.api.solver.alns.AlnsEvaluation;
      import greycos.solver.core.api.solver.alns.AlnsOperatorPair;
      import greycos.solver.core.api.solver.alns.AlnsOutcome;
      import greycos.solver.core.api.solver.alns.AlnsRepairOperator;
      import greycos.solver.core.api.solver.alns.AlnsSelectionPolicy;
      import greycos.solver.core.api.solver.alns.AlnsTarget;
      import greycos.solver.core.api.solver.alns.AlnsTrialResult;
      import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
      import greycos.solver.core.config.alns.AlnsPhaseConfig;
      import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
      import greycos.solver.core.config.solver.SolverConfig;
      import greycos.solver.core.config.solver.termination.TerminationConfig;

      public final class PublicAlnsConsumer {
        @PlanningSolution
        public static final class Problem {
          @PlanningEntityCollectionProperty public List<Job> jobs;
          @ValueRangeProvider(id="values") @ProblemFactCollectionProperty public List<Integer> values;
          @PlanningScore public SimpleScore score;
        }
        @PlanningEntity
        public static final class Job {
          @PlanningVariable(valueRangeProviderRefs="values") public Integer value;
        }
        public static final class Constraints implements ConstraintProvider {
          @Override public Constraint[] defineConstraints(ConstraintFactory factory) {
            return new Constraint[] { factory.forEach(Job.class)
                .reward(SimpleScore.ONE, job -> job.value.longValue()).asConstraint("Value") };
          }
        }
        public static final class Destroy implements AlnsDestroyOperator<Problem, SimpleScore> {
          @Override public List<AlnsTarget<Problem>> select(AlnsContext<Problem, SimpleScore> context, int size) {
            context.checkTermination();
            return context.targets().stream().limit(size).toList();
          }
        }
        public static final class Repair implements AlnsRepairOperator<Problem, SimpleScore> {
          @Override public boolean repair(AlnsContext<Problem, SimpleScore> context, List<AlnsTarget<Problem>> pending) {
            for (var target : pending) {
              context.checkTermination();
              AlnsAssignment<Problem> best = null;
              AlnsEvaluation<SimpleScore> bestScore = null;
              for (var assignment : context.assignments(target)) {
                var score = context.evaluate(view -> view.assign(assignment));
                if (bestScore == null || score.compareTo(bestScore) > 0) { best = assignment; bestScore = score; }
              }
              if (best == null) return false;
              context.assign(best);
            }
            return true;
          }
        }
        public static final class Selection implements AlnsSelectionPolicy<SimpleScore> {
          private AlnsOutcome lastOutcome;
          @Override public AlnsOperatorPair select(List<AlnsOperatorPair> eligible, RandomGenerator random) {
            return eligible.get(random.nextInt(eligible.size()));
          }
          @Override public void update(AlnsTrialResult<SimpleScore> result) { lastOutcome = result.outcome(); }
        }
        public static final class Acceptance implements AlnsAcceptancePolicy<SimpleScore> {
          @Override public boolean isAccepted(SimpleScore current, SimpleScore candidate, RandomGenerator random) {
            return candidate.compareTo(current) >= 0;
          }
        }
        public static SolverConfig configuration() {
          return new SolverConfig().withSolutionClass(Problem.class).withEntityClasses(Job.class)
              .withConstraintProviderClass(Constraints.class)
              .withPhases(new AlnsPhaseConfig()
                  .withDestroyOperators(new AlnsDestroyOperatorConfig().withId("customDestroy").withCustomClass(Destroy.class))
                  .withRepairOperators(new AlnsRepairOperatorConfig().withId("customRepair").withCustomClass(Repair.class))
                  .withSelectionPolicyClass(Selection.class).withAcceptancePolicyClass(Acceptance.class)
                  .withTerminationConfig(new TerminationConfig().withStepCountLimit(3)));
        }
      }
      """;
}
