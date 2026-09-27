package greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import greycos.solver.core.api.score.stream.ConstraintProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProviderTransformationClassLoaderTest {

  @TempDir Path temporaryDirectory;

  @Test
  void childLoaderResolvesApplicationFrameTypesAndRetainsOriginalState() throws Exception {
    var classes = compileProvider();
    var parent = ConstraintProvider.class.getClassLoader();
    assertThatThrownBy(() -> parent.loadClass("providerfixture.OnlyA"))
        .isInstanceOf(ClassNotFoundException.class);
    try (var loader = new URLClassLoader(new URL[] {classes.toUri().toURL()}, parent)) {
      var original =
          loader
              .loadClass(ProviderLoaderTestSupport.PROVIDER_NAME)
              .asSubclass(ConstraintProvider.class);
      var originalInstance = original.getConstructor().newInstance();
      var unshared = ProviderLoaderTestSupport.collect(originalInstance);
      assertThat(unshared.predicates()).hasSize(2);
      assertThat(unshared.predicates().get(0)).isNotSameAs(unshared.predicates().get(1));

      var transformed =
          new DefaultConstraintProviderNodeSharer().buildNodeSharedConstraintProvider(original);
      assertThat(transformed).isNotSameAs(original);
      assertThat(transformed.getSuperclass()).isSameAs(original);
      assertThat(transformed.getClassLoader()).isSameAs(loader);
      assertThat(transformed.getModule()).isSameAs(original.getModule());
      assertThat(transformed.isHidden()).isTrue();
      var instance = transformed.getConstructor().newInstance();
      var shared = ProviderLoaderTestSupport.collect(instance);
      assertThat(shared.names()).containsExactly("A1", "A2");
      assertThat(shared.predicates()).hasSize(2);
      assertThat(shared.predicates().get(0)).isSameAs(shared.predicates().get(1));
      assertThat(shared.predicates().get(0).test("Apple")).isTrue();
      assertThat(shared.predicates().get(0).test("Banana")).isFalse();
      assertThat(original.getField("constructions").getInt(null)).isEqualTo(2);

      original.getField("prefix").set(null, "B");
      original.getField("first").setBoolean(null, false);
      assertThat(shared.predicates().get(0).test("Apple")).isFalse();
      assertThat(shared.predicates().get(0).test("Banana")).isTrue();
      assertThat(ProviderLoaderTestSupport.collect(instance).names()).containsExactly("B1", "B2");
    }
  }

  @Test
  void sameProviderNameInDifferentLoadersHasSeparateClassesAndCaches() throws Exception {
    var classes = compileProvider();
    var urls = new URL[] {classes.toUri().toURL()};
    var parent = ConstraintProvider.class.getClassLoader();
    try (var firstLoader = new URLClassLoader(urls, parent);
        var secondLoader = new URLClassLoader(urls, parent)) {
      var firstOriginal =
          firstLoader
              .loadClass(ProviderLoaderTestSupport.PROVIDER_NAME)
              .asSubclass(ConstraintProvider.class);
      var secondOriginal =
          secondLoader
              .loadClass(ProviderLoaderTestSupport.PROVIDER_NAME)
              .asSubclass(ConstraintProvider.class);
      firstOriginal.getField("prefix").set(null, "A");
      secondOriginal.getField("prefix").set(null, "B");
      var sharer = new DefaultConstraintProviderNodeSharer();
      var first = sharer.buildNodeSharedConstraintProvider(firstOriginal);
      var second = sharer.buildNodeSharedConstraintProvider(secondOriginal);
      assertThat(first).isNotSameAs(second);
      assertThat(first.getSuperclass()).isSameAs(firstOriginal);
      assertThat(second.getSuperclass()).isSameAs(secondOriginal);
      assertThat(first.getClassLoader()).isSameAs(firstLoader);
      assertThat(second.getClassLoader()).isSameAs(secondLoader);
      assertThat(
              new DefaultConstraintProviderNodeSharer()
                  .buildNodeSharedConstraintProvider(firstOriginal))
          .isSameAs(first);
      assertThat(
              new DefaultConstraintProviderNodeSharer()
                  .buildNodeSharedConstraintProvider(secondOriginal))
          .isSameAs(second);

      var firstPredicates =
          ProviderLoaderTestSupport.collect(first.getConstructor().newInstance()).predicates();
      var secondPredicates =
          ProviderLoaderTestSupport.collect(second.getConstructor().newInstance()).predicates();
      assertThat(firstPredicates.get(0)).isSameAs(firstPredicates.get(1));
      assertThat(secondPredicates.get(0)).isSameAs(secondPredicates.get(1));
      assertThat(firstPredicates.get(0)).isNotSameAs(secondPredicates.get(0));
      assertThat(firstPredicates.get(0).test("Apple")).isTrue();
      assertThat(firstPredicates.get(0).test("Banana")).isFalse();
      assertThat(secondPredicates.get(0).test("Apple")).isFalse();
      assertThat(secondPredicates.get(0).test("Banana")).isTrue();
    }
  }

  @Test
  void concurrentSharersDefineOnlyOneClassForEachOriginal() throws Exception {
    var classes = compileProvider();
    try (var loader =
        new URLClassLoader(
            new URL[] {classes.toUri().toURL()}, ConstraintProvider.class.getClassLoader())) {
      var original =
          loader
              .loadClass(ProviderLoaderTestSupport.PROVIDER_NAME)
              .asSubclass(ConstraintProvider.class);
      int workerCount = 12;
      var ready = new CountDownLatch(workerCount);
      var start = new CountDownLatch(1);
      var executor = Executors.newFixedThreadPool(workerCount);
      try {
        var results = new ArrayList<Future<Class<? extends ConstraintProvider>>>();
        for (int i = 0; i < workerCount; i++) {
          results.add(
              executor.submit(
                  () -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS))
                      throw new AssertionError("Concurrent start timed out");
                    return new DefaultConstraintProviderNodeSharer()
                        .buildNodeSharedConstraintProvider(original);
                  }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        var generated = results.getFirst().get(30, TimeUnit.SECONDS);
        assertThat(generated).isNotSameAs(original);
        assertThat(generated.getSuperclass()).isSameAs(original);
        for (var result : results) assertThat(result.get(30, TimeUnit.SECONDS)).isSameAs(generated);
        var predicates =
            ProviderLoaderTestSupport.collect(generated.getConstructor().newInstance())
                .predicates();
        assertThat(predicates).hasSize(2);
        assertThat(predicates.get(0)).isSameAs(predicates.get(1));
      } finally {
        start.countDown();
        executor.shutdownNow();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
      }
    }
  }

  private Path compileProvider() throws Exception {
    return ProviderLoaderTestSupport.compile(
        temporaryDirectory,
        Map.of("providerfixture/IsolatedProvider.java", ProviderLoaderTestSupport.PROVIDER_SOURCE),
        List.of(
            "--class-path",
            ProviderLoaderTestSupport.joinPaths(ProviderLoaderTestSupport.classPathEntries())));
  }
}
