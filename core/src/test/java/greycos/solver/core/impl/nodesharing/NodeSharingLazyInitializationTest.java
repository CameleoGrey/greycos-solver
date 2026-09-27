package greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NodeSharingLazyInitializationTest {

  private static CountDownLatch interfaceInitializationStarted;
  private static CountDownLatch allowInitializationDependency;
  private static InitializationDependencyProvider dependencyProvider;

  private static final String PROVIDER_NAME = "lazyfixture.LazyProvider";
  private static final String PROVIDER_SOURCE =
      """
      package lazyfixture;

      import java.util.function.Predicate;
      import greycos.solver.core.api.score.SimpleScore;
      import greycos.solver.core.api.score.stream.Constraint;
      import greycos.solver.core.api.score.stream.ConstraintFactory;
      import greycos.solver.core.api.score.stream.ConstraintProvider;
      import greycos.solver.core.testcotwin.TestdataEntity;

      public class LazyProvider implements ConstraintProvider {
        public static int configuration;
        public static int thresholdInitializations;
        public static int unusedInitializations;
        public final Predicate<TestdataEntity> constructorPredicate;

        public interface ThresholdPredicate extends Predicate<TestdataEntity> {
          int THRESHOLD = initializeThreshold();
          default int threshold() { return THRESHOLD; }
        }

        public interface UnusedPredicate extends Predicate<String> {
          int INITIALIZED = initializeUnused();
          default int initialized() { return INITIALIZED; }
        }

        private static int initializeThreshold() {
          thresholdInitializations++;
          return configuration;
        }

        private static int initializeUnused() {
          return ++unusedInitializations;
        }

        public LazyProvider() {
          configuration = 5;
          constructorPredicate = first();
        }

        public ThresholdPredicate first() {
          return entity -> entity.getCode().length() > ThresholdPredicate.THRESHOLD;
        }

        public ThresholdPredicate second() {
          return entity -> entity.getCode().length() > ThresholdPredicate.THRESHOLD;
        }

        public UnusedPredicate unusedFirst() { return value -> !value.isEmpty(); }
        public UnusedPredicate unusedSecond() { return value -> !value.isEmpty(); }

        public Constraint[] defineConstraints(ConstraintFactory factory) {
          return new Constraint[] {
            factory.forEach(TestdataEntity.class).filter(first())
                .penalize(SimpleScore.ONE).asConstraint("first"),
            factory.forEach(TestdataEntity.class).filter(second())
                .penalize(SimpleScore.ONE).asConstraint("second")
          };
        }
      }
      """;

  @TempDir Path temporaryDirectory;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void constructorDispatchAndScoresPreserveInterfaceInitializationOrder(boolean sharingEnabled)
      throws Exception {
    // Separate defining loaders prevent the first test case from initializing the second's SAM.
    try (var loader = newProviderLoader()) {
      var original = loader.loadClass(PROVIDER_NAME).asSubclass(ConstraintProvider.class);
      var type =
          sharingEnabled
              ? new DefaultConstraintProviderNodeSharer()
                  .buildNodeSharedConstraintProvider(original)
              : original;
      assertThat(original.getField("thresholdInitializations").getInt(null)).isZero();
      assertThat(original.getField("unusedInitializations").getInt(null)).isZero();

      var provider = type.getConstructor().newInstance();
      Object first = original.getMethod("first").invoke(provider);
      assertThat(original.getField("constructorPredicate").get(provider)).isSameAs(first);
      if (sharingEnabled) {
        assertThat(original.getMethod("second").invoke(provider)).isSameAs(first);
      }
      assertThat(original.getField("thresholdInitializations").getInt(null)).isEqualTo(1);
      assertThat(original.getField("unusedInitializations").getInt(null)).isZero();

      var thread = Thread.currentThread();
      var previousLoader = thread.getContextClassLoader();
      try {
        // The configuration resolves its stored provider name through the context class loader.
        thread.setContextClassLoader(loader);
        var factory =
            BavetConstraintStreamScoreDirectorFactory
                .<TestdataSolution, SimpleScore>buildScoreDirectorFactory(
                    TestdataSolution.buildSolutionDescriptor(),
                    new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(original)
                        .withConstraintStreamAutomaticNodeSharing(sharingEnabled),
                    EnvironmentMode.NO_ASSERT);
        assertThat(
                factory
                    .fireAndForget(new TestdataEntity("abc", new TestdataValue("assigned")))
                    .extractScore())
            .isEqualTo(SimpleScore.ZERO);
      } finally {
        thread.setContextClassLoader(previousLoader);
      }
      assertThat(original.getField("unusedInitializations").getInt(null)).isZero();
    }
  }

  @Test
  void concurrentFirstUsePublishesOneSharedLambda() throws Exception {
    try (var loader = newProviderLoader()) {
      var original = loader.loadClass(PROVIDER_NAME).asSubclass(ConstraintProvider.class);
      var type =
          new DefaultConstraintProviderNodeSharer().buildNodeSharedConstraintProvider(original);
      var provider = type.getConstructor().newInstance();
      assertThat(original.getField("unusedInitializations").getInt(null)).isZero();
      var first = original.getMethod("unusedFirst");
      var second = original.getMethod("unusedSecond");
      int workerCount = 12;
      var ready = new CountDownLatch(workerCount);
      var start = new CountDownLatch(1);
      var executor = Executors.newFixedThreadPool(workerCount);
      try {
        var results = new ArrayList<Future<Object>>();
        for (int i = 0; i < workerCount; i++) {
          var method = i % 2 == 0 ? first : second;
          results.add(
              executor.submit(
                  () -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                      throw new AssertionError("Concurrent first use timed out");
                    }
                    return method.invoke(provider);
                  }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        Object predicate = results.getFirst().get(30, TimeUnit.SECONDS);
        assertThat(predicate).isInstanceOf(Predicate.class);
        for (var result : results) {
          assertThat(result.get(30, TimeUnit.SECONDS)).isSameAs(predicate);
        }
        assertThat(original.getField("unusedInitializations").getInt(null)).isEqualTo(1);
      } finally {
        start.countDown();
        executor.shutdownNow();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
      }
    }
  }

  @Test
  void initializingOneFunctionalInterfaceCanUseADifferentSharedGroup() throws Exception {
    interfaceInitializationStarted = new CountDownLatch(1);
    allowInitializationDependency = new CountDownLatch(1);
    dependencyProvider =
        new DefaultConstraintProviderNodeSharer()
            .buildNodeSharedConstraintProvider(InitializationDependencyProvider.class)
            .getConstructor()
            .newInstance();
    var initializing = new FutureTask<>(() -> Class.forName(InitializingPredicate.class.getName()));
    var initializerThread = Thread.ofPlatform().daemon().start(initializing);
    var using = new FutureTask<>(dependencyProvider::blockedFirst);
    var userThread = Thread.ofPlatform().daemon().unstarted(using);
    try {
      assertThat(interfaceInitializationStarted.await(10, TimeUnit.SECONDS)).isTrue();
      userThread.start();
      // Wait until the getter enters its lambda bootstrap and therefore holds its cache lock.
      // The other thread still owns the SAM's initialization lock at this point. Its dependency
      // must be able to use another shared group without waiting for the first getter.
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      boolean enteredBootstrap = false;
      while (!enteredBootstrap && System.nanoTime() < deadline) {
        enteredBootstrap =
            Arrays.stream(userThread.getStackTrace())
                .anyMatch(
                    frame ->
                        frame.getClassName().contains("$GreycosNodeSharingLookup$")
                            && frame.getMethodName().equals("bootstrap"));
        if (!enteredBootstrap) {
          Thread.sleep(1);
        }
      }
      assertThat(enteredBootstrap).as("The shared getter entered its lambda bootstrap").isTrue();
      allowInitializationDependency.countDown();
      assertThat(initializing.get(10, TimeUnit.SECONDS)).isSameAs(InitializingPredicate.class);
      assertThat(using.get(10, TimeUnit.SECONDS).test("x")).isTrue();
      assertThat(dependencyProvider.first()).isSameAs(dependencyProvider.second());
    } finally {
      allowInitializationDependency.countDown();
      // Daemon workers keep a deadlock regression from hanging the entire test process.
      initializerThread.interrupt();
      userThread.interrupt();
    }
  }

  private static int initializeDependency() {
    interfaceInitializationStarted.countDown();
    try {
      if (!allowInitializationDependency.await(10, TimeUnit.SECONDS)) {
        throw new AssertionError("Interface initialization dependency timed out");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interface initialization was interrupted", e);
    }
    return dependencyProvider.first().test("x") ? 1 : 0;
  }

  public interface InitializingPredicate extends Predicate<String> {
    int INITIALIZED = initializeDependency();

    default int initialized() {
      return INITIALIZED;
    }
  }

  public static class InitializationDependencyProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }

    public Predicate<String> first() {
      return value -> !value.isEmpty();
    }

    public Predicate<String> second() {
      return value -> !value.isEmpty();
    }

    public InitializingPredicate blockedFirst() {
      return value -> !value.isEmpty();
    }

    public InitializingPredicate blockedSecond() {
      return value -> !value.isEmpty();
    }
  }

  private URLClassLoader newProviderLoader() throws Exception {
    Path classes =
        ProviderLoaderTestSupport.compile(
            temporaryDirectory,
            Map.of("lazyfixture/LazyProvider.java", PROVIDER_SOURCE),
            List.of(
                "--class-path",
                ProviderLoaderTestSupport.joinPaths(ProviderLoaderTestSupport.classPathEntries())));
    return new URLClassLoader(
        new URL[] {classes.toUri().toURL()}, ConstraintProvider.class.getClassLoader());
  }
}
