package greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.nodesharing.external.ExternalStackObservationHelper;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class TransitiveStackObservationTest {

  @Test
  void localHelperKeepsScoresWhenUnrelatedLambdasAreShared() {
    assertScores(LocalScoreProvider.class);
  }

  @Test
  void externalHelperChainKeepsScoresWhenUnrelatedLambdasAreShared() {
    assertScores(ExternalScoreProvider.class);
  }

  private static void assertScores(Class<? extends ConstraintProvider> providerClass) {
    assertThat(
            new ConstraintProviderAnalyzer(providerClass).analyze().getShareableLambdaGroupCount())
        .isEqualTo(1);
    assertThat(
            new DefaultConstraintProviderNodeSharer()
                .buildNodeSharedConstraintProvider(providerClass))
        .isNotSameAs(providerClass);
    for (boolean sharing : new boolean[] {false, true}) {
      var factory =
          BavetConstraintStreamScoreDirectorFactory.buildScoreDirectorFactory(
              TestdataSolution.buildSolutionDescriptor(),
              new ScoreDirectorFactoryConfig()
                  .withConstraintProviderClass(providerClass)
                  .withConstraintStreamAutomaticNodeSharing(sharing),
              EnvironmentMode.NO_ASSERT);
      assertThat(factory.fireAndForget(entity("first")).extractScore())
          .isEqualTo(SimpleScore.of(-1));
      assertThat(factory.fireAndForget(entity("second")).extractScore())
          .isEqualTo(SimpleScore.of(-2));
    }
  }

  @Test
  void returnedThrowablesRetainTheirOriginalImplementationFrames() throws Exception {
    var provider = shared(ThrowableProvider.class);
    assertThat(provider.getClass()).isNotSameAs(ThrowableProvider.class);
    assertThat(lambdaFrame(provider.first().apply("x"))).contains("$first$");
    assertThat(lambdaFrame(provider.second().apply("x"))).contains("$second$");
    assertThat(lambdaFrame(provider.firstSubclass().apply("x"))).contains("$firstSubclass$");
    assertThat(lambdaFrame(provider.secondSubclass().apply("x"))).contains("$secondSubclass$");
  }

  private static String lambdaFrame(Throwable throwable) {
    return Arrays.stream(throwable.getStackTrace())
        .map(StackTraceElement::getMethodName)
        .filter(name -> name.startsWith("lambda$"))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void everyEntryIntoARecursiveHelperCycleSeesItsStackObserver() {
    var analyzer = analyzer(ExternalStackObservationHelper.class);
    for (String method : List.of("recursiveObservationSecond", "recursiveObservationFirst")) {
      var result =
          analyzer.inspectMethod(
              Type.getInternalName(ExternalStackObservationHelper.class), method, "(I)Z");
      assertThat(result.observationPath()).contains("recursiveObservationFirst", "getStackTrace");
      assertThat(result.permitsStructuralIdentity()).isFalse();
    }
  }

  @Test
  void clearRecursiveHelpersAndOrdinaryGettersStillShare() throws Exception {
    var provider = shared(ClearHelperProvider.class);
    assertThat(provider.getClass()).isNotSameAs(ClearHelperProvider.class);
    assertThat(provider.first()).isSameAs(provider.second());
    assertThat(provider.first().test(entity("ab"))).isTrue();
    assertThat(provider.first().test(entity("abc"))).isFalse();
  }

  @Test
  void inheritedPlatformMethodsRemainTerminalContracts() {
    var result =
        analyzer(ClearHelperProvider.class)
            .inspectMethod(
                Type.getInternalName(ClearHelperProvider.class),
                "toString",
                "()Ljava/lang/String;");
    assertThat(result.permitsStructuralIdentity()).isTrue();
  }

  @Test
  void analysisDoesNotInitializeAnExternalHelper() {
    assertThat(ExternalStackObservationHelper.initializationCount).isZero();
    assertThat(
            new ConstraintProviderAnalyzer(UninitializedHelperProvider.class)
                .analyze()
                .getShareableLambdaGroupCount())
        .isEqualTo(1);
    assertThat(ExternalStackObservationHelper.initializationCount).isZero();
  }

  @Test
  void relocationRejectsAnActualObservationInAnExternalHelper() {
    assertThatThrownBy(() -> shared(RelocatedObserverProvider.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(RelocatedObserverProvider.class.getName())
        .hasMessageContaining("observed")
        .hasMessageContaining(ExternalStackObservationHelper.class.getName())
        .hasMessageContaining("getStackTrace")
        .hasMessageContaining("\nMaybe ");
  }

  @Test
  void ordinaryErrorConstructionDoesNotRejectRelocation() throws Exception {
    var provider = shared(OrdinaryErrorProvider.class);
    assertThat(provider.getClass()).isNotSameAs(OrdinaryErrorProvider.class);
    assertThat(provider.checked(false)).isEqualTo("ok");
    assertThatThrownBy(() -> provider.checked(true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("expected");
  }

  @Test
  void unavailableHelperBytecodeOnlyPreventsStructuralMerging(@TempDir Path directory)
      throws Exception {
    String source =
        """
        package stackfixture;
        import java.util.function.Predicate;
        import greycos.solver.core.api.score.stream.Constraint;
        import greycos.solver.core.api.score.stream.ConstraintFactory;
        import greycos.solver.core.api.score.stream.ConstraintProvider;
        public class HiddenHelperProvider implements ConstraintProvider {
          public Predicate<String> first() {
            Predicate<String> safe = value -> !value.isEmpty();
            HiddenHelper.touch();
            return value -> HiddenHelper.matches(value);
          }
          public Predicate<String> second() {
            Predicate<String> safe = value -> !value.isEmpty();
            HiddenHelper.touch();
            return value -> HiddenHelper.matches(value);
          }
          public Constraint[] defineConstraints(ConstraintFactory factory) { return new Constraint[0]; }
        }
        class HiddenHelper {
          static void touch() {}
          static boolean matches(String value) { return !value.isEmpty(); }
        }
        """;
    Path classes =
        ProviderLoaderTestSupport.compile(
            directory,
            Map.of("stackfixture/HiddenHelperProvider.java", source),
            List.of(
                "-classpath",
                ProviderLoaderTestSupport.joinPaths(ProviderLoaderTestSupport.classPathEntries())));
    try (var loader =
        new URLClassLoader(new URL[] {classes.toUri().toURL()}, getClass().getClassLoader()) {
          @Override
          public InputStream getResourceAsStream(String name) {
            return name.equals("stackfixture/HiddenHelper.class")
                ? null
                : super.getResourceAsStream(name);
          }
        }) {
      var providerClass =
          loader
              .loadClass("stackfixture.HiddenHelperProvider")
              .asSubclass(ConstraintProvider.class);
      assertThat(
              new ConstraintProviderAnalyzer(providerClass)
                  .analyze()
                  .getShareableLambdaGroupCount())
          .isEqualTo(1);
      var provider = shared(providerClass);
      assertThat(provider.getClass()).isNotSameAs(providerClass);
      var first = providerClass.getMethod("first").invoke(provider);
      var second = providerClass.getMethod("second").invoke(provider);
      assertThat(first).isNotSameAs(second);
      @SuppressWarnings("unchecked")
      var predicate = (Predicate<String>) second;
      assertThat(predicate.test("x")).isTrue();
      assertThat(predicate.test("")).isFalse();
    }
  }

  @Test
  void analysisBudgetExhaustionPreventsAClaimOfStructuralEquivalence() {
    var writer = new ClassWriter(0);
    String owner = "stackfixture/LargeHelperGraph";
    writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
    for (int index = 0; index < 3000; index++) {
      var method =
          writer.visitMethod(
              Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "method" + index, "()Z", null, null);
      method.visitCode();
      if (index == 2999) {
        method.visitInsn(Opcodes.ICONST_1);
      } else {
        method.visitMethodInsn(Opcodes.INVOKESTATIC, owner, "method" + (index + 1), "()Z", false);
      }
      method.visitInsn(Opcodes.IRETURN);
      method.visitMaxs(1, 0);
      method.visitEnd();
    }
    writer.visitEnd();
    byte[] bytes = writer.toByteArray();
    Class<?> helper =
        new ClassLoader(getClass().getClassLoader()) {
          Class<?> define() {
            return defineClass(owner.replace('/', '.'), bytes, 0, bytes.length);
          }
        }.define();
    var result = new StackObservationAnalyzer(helper, bytes).inspectMethod(owner, "method0", "()Z");
    assertThat(result.unknown()).isTrue();
    assertThat(result.permitsStructuralIdentity()).isFalse();
  }

  private static StackObservationAnalyzer analyzer(Class<?> type) {
    return new StackObservationAnalyzer(type, NodeSharingTransformer.readClassFile(type));
  }

  private static <T extends ConstraintProvider> T shared(Class<T> type) throws Exception {
    return new DefaultConstraintProviderNodeSharer()
        .buildNodeSharedConstraintProvider(type)
        .getDeclaredConstructor()
        .newInstance();
  }

  private static TestdataEntity entity(String code) {
    return new TestdataEntity(code, new TestdataValue("value"));
  }

  public static class LocalScoreProvider implements ConstraintProvider {
    public Constraint first(ConstraintFactory factory) {
      return factory
          .forEach(TestdataEntity.class)
          .filter(entity -> !entity.getCode().isEmpty())
          .filter(entity -> selected(entity))
          .penalize(SimpleScore.ONE)
          .asConstraint("first");
    }

    public Constraint second(ConstraintFactory factory) {
      return factory
          .forEach(TestdataEntity.class)
          .filter(entity -> !entity.getCode().isEmpty())
          .filter(entity -> selected(entity))
          .penalize(SimpleScore.of(2))
          .asConstraint("second");
    }

    private static boolean selected(TestdataEntity entity) {
      return new Throwable()
          .getStackTrace()[1]
          .getMethodName()
          .contains("$" + entity.getCode() + "$");
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {first(factory), second(factory)};
    }
  }

  public static class ExternalScoreProvider implements ConstraintProvider {
    public Constraint first(ConstraintFactory factory) {
      return factory
          .forEach(TestdataEntity.class)
          .filter(entity -> !entity.getCode().isEmpty())
          .filter(entity -> ExternalStackObservationHelper.selected(entity))
          .penalize(SimpleScore.ONE)
          .asConstraint("first");
    }

    public Constraint second(ConstraintFactory factory) {
      return factory
          .forEach(TestdataEntity.class)
          .filter(entity -> !entity.getCode().isEmpty())
          .filter(entity -> ExternalStackObservationHelper.selected(entity))
          .penalize(SimpleScore.of(2))
          .asConstraint("second");
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {first(factory), second(factory)};
    }
  }

  public static class ThrowableProvider implements ConstraintProvider {
    public Function<String, Throwable> first() {
      Predicate<String> safe = value -> !value.isEmpty();
      return value -> new Throwable();
    }

    public Function<String, Throwable> second() {
      Predicate<String> safe = value -> !value.isEmpty();
      return value -> new Throwable();
    }

    public Function<String, Throwable> firstSubclass() {
      Predicate<String> safe = value -> !value.isEmpty();
      return value -> new ApplicationException();
    }

    public Function<String, Throwable> secondSubclass() {
      Predicate<String> safe = value -> !value.isEmpty();
      return value -> new ApplicationException();
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }
  }

  public static class ApplicationException extends IllegalArgumentException {}

  public static class ClearHelperProvider implements ConstraintProvider {
    public Predicate<TestdataEntity> first() {
      return entity -> ExternalStackObservationHelper.even(entity.getCode().length());
    }

    public Predicate<TestdataEntity> second() {
      return entity -> ExternalStackObservationHelper.even(entity.getCode().length());
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }
  }

  public static class UninitializedHelperProvider implements ConstraintProvider {
    public Predicate<String> first() {
      return value -> ExternalStackObservationHelper.UninitializedHelper.matches(value);
    }

    public Predicate<String> second() {
      return value -> ExternalStackObservationHelper.UninitializedHelper.matches(value);
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }
  }

  public static class RelocatedObserverProvider implements ConstraintProvider {
    public String observed() {
      Predicate<String> first = value -> !value.isEmpty();
      Predicate<String> second = value -> !value.isEmpty();
      return ExternalStackObservationHelper.observedCaller();
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }
  }

  public static class OrdinaryErrorProvider implements ConstraintProvider {
    public String checked(boolean fail) {
      Predicate<String> first = value -> !value.isEmpty();
      Predicate<String> second = value -> !value.isEmpty();
      if (fail) {
        throw new IllegalArgumentException("expected");
      }
      return "ok";
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }
  }
}
