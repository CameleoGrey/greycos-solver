package greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NodeSharingHelperReferenceTest {

  private static int calls;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void boundUnboundAndStaticHelpersShareFilterWork(boolean sharingEnabled) throws Exception {
    for (Class<? extends ConstraintProvider> providerClass :
        java.util.List.of(BoundProvider.class, UnboundProvider.class, StaticProvider.class)) {
      if (sharingEnabled) {
        var generated =
            new DefaultConstraintProviderNodeSharer()
                .buildNodeSharedConstraintProvider(providerClass);
        assertThat(generated.getDeclaredMethod("defineConstraints", ConstraintFactory.class))
            .isNotNull();
      }
      var factory =
          BavetConstraintStreamScoreDirectorFactory
              .<TestdataSolution, SimpleScore>buildScoreDirectorFactory(
                  TestdataSolution.buildSolutionDescriptor(),
                  new ScoreDirectorFactoryConfig()
                      .withConstraintProviderClass(providerClass)
                      .withConstraintStreamAutomaticNodeSharing(sharingEnabled),
                  EnvironmentMode.NO_ASSERT);
      calls = 0;
      assertThat(
              factory
                  .fireAndForget(new TestdataEntity("abc", new TestdataValue("assigned")))
                  .extractScore())
          .isEqualTo(SimpleScore.of(-2));
      assertThat(calls)
          .as("filter calls for %s", providerClass.getSimpleName())
          .isEqualTo(sharingEnabled ? 1 : 2);
    }
  }

  @Test
  void helperBridgesPreserveOriginalReceiversNullChecksAndMonitors() throws Exception {
    var generated =
        new DefaultConstraintProviderNodeSharer()
            .buildNodeSharedConstraintProvider(StateProvider.class);
    var provider = generated.getConstructor(int.class).newInstance(3);
    var originalReceiver = new StateProvider(7);

    assertThat(provider.bound(provider).getAsInt()).isEqualTo(3);
    assertThat(provider.bound(originalReceiver).getAsInt()).isEqualTo(7);
    assertThat(provider.unbound().apply(originalReceiver)).isEqualTo(7);
    assertThatThrownBy(() -> provider.bound(null)).isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> provider.unbound().apply(null))
        .isInstanceOf(NullPointerException.class);
    assertThat(provider.staticMonitorReference().getAsBoolean()).isTrue();
    assertThat(Thread.holdsLock(provider)).isFalse();
    assertThat(Thread.holdsLock(originalReceiver)).isFalse();
    assertThat(Thread.holdsLock(StateProvider.class)).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void extendedReferencesRetainOriginalSerializationMetadata() throws Exception {
    var generated =
        new DefaultConstraintProviderNodeSharer()
            .buildNodeSharedConstraintProvider(StateProvider.class);
    var provider = generated.getConstructor(int.class).newInstance(3);
    var supplier = provider.serializableReference();
    assertThat(supplier).isInstanceOf(Marker.class);
    var writeReplace = supplier.getClass().getDeclaredMethod("writeReplace");
    writeReplace.setAccessible(true);
    var serialized = (SerializedLambda) writeReplace.invoke(supplier);
    assertThat(serialized.getCapturingClass())
        .isEqualTo(StateProvider.class.getName().replace('.', '/'));
    assertThat(serialized.getImplClass())
        .isEqualTo(StateProvider.class.getName().replace('.', '/'));
    assertThat(serialized.getImplMethodName()).isEqualTo("staticPredicate");

    var bytes = new ByteArrayOutputStream();
    try (var output = new ObjectOutputStream(bytes)) {
      output.writeObject(supplier);
    }
    Supplier<Predicate<String>> restored;
    try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      restored = (Supplier<Predicate<String>>) input.readObject();
    }
    assertThat(restored).isInstanceOf(Marker.class);
    assertThat(restored.get().test("x")).isTrue();
    assertThat(restored.get().test("")).isFalse();
    assertThat(provider.ordinaryStaticReference().get()).isSameAs(provider.marker());
  }

  private static boolean count(TestdataEntity entity) {
    calls++;
    return !entity.getCode().isEmpty();
  }

  public static class BoundProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      Function<ConstraintFactory, Constraint> first = this::first;
      Function<ConstraintFactory, Constraint> second = this::second;
      return new Constraint[] {first.apply(factory), second.apply(factory)};
    }

    private Constraint first(ConstraintFactory factory) {
      return factory
          .forEach(TestdataEntity.class)
          .filter(entity -> count(entity))
          .penalize(SimpleScore.ONE)
          .asConstraint("first");
    }

    private Constraint second(ConstraintFactory factory) {
      return factory
          .forEach(TestdataEntity.class)
          .filter(entity -> count(entity))
          .penalize(SimpleScore.ONE)
          .asConstraint("second");
    }
  }

  public static class UnboundProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      BiFunction<UnboundProvider, ConstraintFactory, Constraint> first = UnboundProvider::first;
      BiFunction<UnboundProvider, ConstraintFactory, Constraint> second = UnboundProvider::second;
      return new Constraint[] {first.apply(this, factory), second.apply(this, factory)};
    }

    private Constraint first(ConstraintFactory factory) {
      return factory
          .forEach(TestdataEntity.class)
          .filter(entity -> count(entity))
          .penalize(SimpleScore.ONE)
          .asConstraint("first");
    }

    private Constraint second(ConstraintFactory factory) {
      return factory
          .forEach(TestdataEntity.class)
          .filter(entity -> count(entity))
          .penalize(SimpleScore.ONE)
          .asConstraint("second");
    }
  }

  public static class StaticProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      Function<ConstraintFactory, Constraint> first = StaticProvider::first;
      Function<ConstraintFactory, Constraint> second = StaticProvider::second;
      return new Constraint[] {first.apply(factory), second.apply(factory)};
    }

    private static Constraint first(ConstraintFactory factory) {
      return factory
          .forEach(TestdataEntity.class)
          .filter(entity -> count(entity))
          .penalize(SimpleScore.ONE)
          .asConstraint("first");
    }

    private static Constraint second(ConstraintFactory factory) {
      return factory
          .forEach(TestdataEntity.class)
          .filter(entity -> count(entity))
          .penalize(SimpleScore.ONE)
          .asConstraint("second");
    }
  }

  public interface Marker {}

  public static class StateProvider implements ConstraintProvider {
    private final int minimum;

    public StateProvider(int minimum) {
      this.minimum = minimum;
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }

    public IntSupplier bound(StateProvider target) {
      return target::lockedValue;
    }

    public Function<StateProvider, Integer> unbound() {
      return StateProvider::lockedValue;
    }

    public BooleanSupplier staticMonitorReference() {
      return StateProvider::staticMonitor;
    }

    public Supplier<Predicate<String>> ordinaryStaticReference() {
      return StateProvider::staticPredicate;
    }

    public Supplier<Predicate<String>> serializableReference() {
      Predicate<String> marker = value -> !value.isEmpty();
      if (!marker.test("x")) {
        throw new IllegalStateException("Invalid marker");
      }
      return (Supplier<Predicate<String>> & Serializable & Marker) StateProvider::staticPredicate;
    }

    private synchronized int lockedValue() {
      Predicate<String> marker = value -> !value.isEmpty();
      if (!marker.test("x") || !Thread.holdsLock(this)) {
        throw new IllegalStateException("Original receiver monitor is not held");
      }
      return minimum;
    }

    private static synchronized boolean staticMonitor() {
      Predicate<String> marker = value -> !value.isEmpty();
      return marker.test("x") && Thread.holdsLock(StateProvider.class);
    }

    private static Predicate<String> staticPredicate() {
      return value -> !value.isEmpty();
    }

    public Predicate<String> marker() {
      return value -> !value.isEmpty();
    }
  }
}
