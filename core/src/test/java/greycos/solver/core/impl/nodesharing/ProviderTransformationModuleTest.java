package greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.reflect.InvocationTargetException;
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

import greycos.solver.core.api.score.stream.ConstraintProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests actual named application and core modules, even when Surefire uses the class path. */
class ProviderTransformationModuleTest {

  private static final String CORE_MODULE = "greycos.solver.core";
  private static final String PROVIDER_MODULE = "provider.fixture";
  private static final String IMPLEMENTATION_PACKAGE = "greycos.solver.core.impl.nodesharing";

  @TempDir Path temporaryDirectory;

  @Test
  void qualifiedOpenPackageSupportsTransformationInNamedModules() throws Exception {
    var fixture = moduleFixture(true);
    assertThat(fixture.provider().getModule().isNamed()).isTrue();
    assertThat(fixture.sharer().getClass().getModule().getName()).isEqualTo(CORE_MODULE);
    assertThat(fixture.provider().getModule().isOpen("providerfixture", getClass().getModule()))
        .as("The fixture opens its package only to the named core module")
        .isFalse();

    var generated = fixture.transform();
    assertThat(generated).isNotSameAs(fixture.provider());
    assertThat(generated.getSuperclass()).isSameAs(fixture.provider());
    assertThat(generated.isHidden()).isTrue();
    assertThat(generated.getModule()).isSameAs(fixture.provider().getModule());
    assertThat(generated.getClassLoader()).isSameAs(fixture.provider().getClassLoader());
    assertThat(fixture.transform()).isSameAs(generated);

    var instance = generated.getConstructor().newInstance();
    var constraints = ProviderLoaderTestSupport.collect(instance);
    assertThat(constraints.names()).containsExactly("A1", "A2");
    assertThat(constraints.predicates()).hasSize(2);
    assertThat(constraints.predicates().get(0)).isSameAs(constraints.predicates().get(1));
    assertThat(constraints.predicates().get(0).test("Apple")).isTrue();
    assertThat(constraints.predicates().get(0).test("Banana")).isFalse();

    fixture.provider().getField("prefix").set(null, "B");
    fixture.provider().getField("first").setBoolean(null, false);
    assertThat(constraints.predicates().get(0).test("Apple")).isFalse();
    assertThat(constraints.predicates().get(0).test("Banana")).isTrue();
    assertThat(ProviderLoaderTestSupport.collect(instance).names()).containsExactly("B1", "B2");
    assertThat(fixture.provider().getField("constructions").getInt(null)).isEqualTo(1);
  }

  @Test
  void closedPackageFailsWithProviderModuleAndOpeningInstructions() throws Exception {
    var fixture = moduleFixture(false);
    var coreModule = fixture.sharer().getClass().getModule();
    assertThat(fixture.provider().getModule().isOpen("providerfixture", coreModule)).isFalse();
    var exception = assertThrows(InvocationTargetException.class, fixture::transform);
    assertThat(exception.getCause())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(ProviderLoaderTestSupport.PROVIDER_NAME)
        .hasMessageContaining(PROVIDER_MODULE)
        .hasMessageContaining("providerfixture")
        .hasMessageContaining("--add-opens provider.fixture/providerfixture=greycos.solver.core")
        .hasMessageContaining("constraintStreamAutomaticNodeSharing");
    assertThat(exception.getCause().getCause()).isInstanceOf(IllegalAccessException.class);
  }

  private ModuleFixture moduleFixture(boolean openPackage) throws Exception {
    var modulePath = productionModulePath();
    var descriptor =
        "module "
            + PROVIDER_MODULE
            + " { requires "
            + CORE_MODULE
            + "; exports providerfixture; "
            + (openPackage ? "opens providerfixture to " + CORE_MODULE + "; " : "")
            + "}";
    var providerClasses =
        ProviderLoaderTestSupport.compile(
            temporaryDirectory,
            Map.of(
                "module-info.java",
                descriptor,
                "providerfixture/IsolatedProvider.java",
                ProviderLoaderTestSupport.PROVIDER_SOURCE),
            List.of(
                "--module-path",
                ProviderLoaderTestSupport.joinPaths(modulePath),
                "--class-path",
                Files.createDirectories(temporaryDirectory.resolve("empty-classpath")).toString()));
    var allModules = new ArrayList<>(modulePath);
    allModules.add(providerClasses);
    var finder = ModuleFinder.of(allModules.toArray(Path[]::new));
    var configuration =
        ModuleLayer.boot()
            .configuration()
            .resolve(finder, ModuleFinder.of(), Set.of(CORE_MODULE, PROVIDER_MODULE));
    var controller =
        ModuleLayer.defineModulesWithOneLoader(
            configuration, List.of(ModuleLayer.boot()), ClassLoader.getPlatformClassLoader());
    var layer = controller.layer();
    var coreModule = layer.findModule(CORE_MODULE).orElseThrow();
    // Only expose the sharer's implementation entry point to this test; provider access remains
    // governed by the compiled module descriptor and is never changed by the test controller.
    controller.addExports(coreModule, IMPLEMENTATION_PACKAGE, getClass().getModule());
    var sharerType =
        layer
            .findLoader(CORE_MODULE)
            .loadClass(IMPLEMENTATION_PACKAGE + ".DefaultConstraintProviderNodeSharer");
    var providerType =
        layer.findLoader(PROVIDER_MODULE).loadClass(ProviderLoaderTestSupport.PROVIDER_NAME);
    return new ModuleFixture(sharerType.getConstructor().newInstance(), providerType);
  }

  private static List<Path> productionModulePath() throws Exception {
    var coreLocation =
        Path.of(
            ConstraintProvider.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    var descriptors = new HashMap<String, ModuleDescriptor>();
    var locations = new HashMap<String, Path>();
    for (var location : ProviderLoaderTestSupport.classPathEntries()) {
      if (!location.equals(coreLocation)
          && !(Files.isRegularFile(location) && location.toString().endsWith(".jar"))) continue;
      for (var reference : ModuleFinder.of(location).findAll()) {
        descriptors.putIfAbsent(reference.descriptor().name(), reference.descriptor());
        locations.putIfAbsent(reference.descriptor().name(), location);
      }
    }
    var selected = new LinkedHashSet<Path>();
    var visited = new HashSet<String>();
    var pending = new ArrayDeque<>(List.of(CORE_MODULE));
    var systemModules = ModuleFinder.ofSystem();
    while (!pending.isEmpty()) {
      var name = pending.removeFirst();
      if (!visited.add(name) || systemModules.find(name).isPresent()) continue;
      var descriptor = descriptors.get(name);
      assertThat(descriptor)
          .as("Required production module %s is on the test runtime path", name)
          .isNotNull();
      selected.add(locations.get(name));
      for (var requirement : descriptor.requires()) {
        if (!requirement.modifiers().contains(ModuleDescriptor.Requires.Modifier.STATIC)) {
          pending.addLast(requirement.name());
        }
      }
    }
    return List.copyOf(selected);
  }

  private record ModuleFixture(Object sharer, Class<?> provider) {
    Class<?> transform() throws ReflectiveOperationException {
      return (Class<?>)
          sharer
              .getClass()
              .getMethod("buildNodeSharedConstraintProvider", Class.class)
              .invoke(sharer, provider);
    }
  }
}
