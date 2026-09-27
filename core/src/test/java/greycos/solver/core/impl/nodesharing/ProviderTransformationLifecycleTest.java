package greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.invoke.MethodHandles;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.impl.nodesharing.protectedaccess.ProviderTransformationProtectedBase;

import org.junit.jupiter.api.Test;

class ProviderTransformationLifecycleTest {

  private static int initializerCalls;

  @Test
  void originalStateConstructorAndStaticInitializationArePreserved() throws Exception {
    StatefulProvider.prefix = "configured";
    int before = initializerCalls;
    Class<? extends StatefulProvider> transformed = transform(StatefulProvider.class);
    StatefulProvider provider = transformed.getConstructor(int.class).newInstance(3);

    assertThat(initializerCalls).isEqualTo(before);
    assertThat(provider.first()).isSameAs(provider.second());
    assertThat(provider.constructorPredicate).isSameAs(provider.first());
    assertThat(provider.first().test("configured value")).isTrue();
    assertThat(provider.captured().test("1234")).isTrue();
    assertThat(provider.captured().test("123")).isFalse();
    StatefulProvider.prefix = "changed";
    assertThat(provider.first().test("configured value")).isFalse();
    assertThat(provider.second().test("changed value")).isTrue();
    assertThat(provider.providerLiteral()).isSameAs(StatefulProvider.class);
    assertThat(provider.originalLookupClass()).isSameAs(StatefulProvider.class);
  }

  @Test
  void staticInitializerCallsOriginalHelpersBeforeGeneratedCachesExist() throws Exception {
    StaticHelperProvider provider =
        transform(StaticHelperProvider.class).getConstructor().newInstance();

    assertThat(StaticHelperProvider.INITIAL_PREDICATE).isNotNull();
    assertThat(StaticHelperProvider.INITIAL_PREDICATE.test("value")).isTrue();
    assertThat(provider.first()).isSameAs(provider.second());
    assertThat(provider.first().test("value")).isTrue();
  }

  @Test
  void privateHelpersAcceptOriginalReceiversAndRejectNull() throws Exception {
    StatefulProvider provider =
        transform(StatefulProvider.class).getConstructor(int.class).newInstance(3);
    StatefulProvider other = new StatefulProvider(7);

    assertThat(provider.readOther(other)).isEqualTo(7);
    assertThat(provider.readOther(provider)).isEqualTo(3);
    assertThat(provider.callConstant(other)).isTrue();
    assertThatThrownBy(() -> provider.callConstant(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void synchronizedPrivateHelpersKeepOriginalMonitorsAndReleaseOnExceptions() throws Exception {
    StatefulProvider provider =
        transform(StatefulProvider.class).getConstructor(int.class).newInstance(3);

    assertThat(provider.monitorsHeld()).isTrue();
    assertThat(provider.lockedLong(true)).isEqualTo(Long.MAX_VALUE);
    assertThat(provider.lockedLong(false)).isEqualTo(Long.MIN_VALUE);
    assertThatThrownBy(provider::throwWhileLocked)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("expected");
    assertThat(Thread.holdsLock(provider)).isFalse();
    assertThat(Thread.holdsLock(StatefulProvider.class)).isFalse();
  }

  @Test
  void originalSuperclassAndInterfaceSuperDispatchIsPreserved() throws Exception {
    SuperProvider provider = transform(SuperProvider.class).getConstructor().newInstance();
    InterfaceProvider interfaceProvider =
        transform(InterfaceProvider.class).getConstructor().newInstance();

    assertThat(provider.text()).isEqualTo("base provider");
    assertThat(provider.privateSuper()).isEqualTo("base");
    assertThat(interfaceProvider.text()).isEqualTo("interface provider");
    assertThat(provider.first()).isSameAs(provider.second());
    assertThat(interfaceProvider.first()).isSameAs(interfaceProvider.second());
  }

  @Test
  void protectedFieldsAndMethodsKeepOriginalReceiverAccessAcrossPackages() throws Exception {
    ProtectedProvider provider = transform(ProtectedProvider.class).getConstructor().newInstance();
    ProtectedProvider other = new ProtectedProvider();

    assertThat(provider.readAndUpdateOther(other)).isEqualTo(26L);
    assertThat(other.number()).isEqualTo(9L);
    assertThat(other.label()).isEqualTo("base updated");
    assertThat(provider.number()).isEqualTo(7L);
    assertThat(provider.referenceTo(other).getAsLong()).isEqualTo(9L);
    assertThat(provider.referenceTo(provider).getAsLong()).isEqualTo(7L);
    assertThat(provider.first()).isSameAs(provider.second());
  }

  @Test
  void generatedHelperNamesDoNotCollideWithProviderMethods() throws Exception {
    CollisionProvider provider = transform(CollisionProvider.class).getConstructor().newInstance();

    assertThat(provider.first()).isSameAs(provider.second());
    assertThat(provider.$greycos$helper$helper$0(provider)).isEqualTo("user method");
  }

  @Test
  void callerSensitiveCopiedMethodFailsWithContextInsteadOfFallingBack() {
    assertThatThrownBy(() -> transform(CallerSensitiveProvider.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(CallerSensitiveProvider.class.getName())
        .hasMessageContaining("first")
        .hasMessageContaining("caller-sensitive")
        .hasMessageContaining("disable constraintStreamAutomaticNodeSharing");
  }

  @Test
  void directLookupFailsInsteadOfReturningWeakerLookupPrivileges() {
    assertThatThrownBy(() -> transform(LookupProvider.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(LookupProvider.class.getName())
        .hasMessageContaining("caller-sensitive")
        .hasMessageContaining("lookup");
  }

  private static <T extends ConstraintProvider> Class<T> transform(Class<T> provider) {
    return new DefaultConstraintProviderNodeSharer().buildNodeSharedConstraintProvider(provider);
  }

  public static class StatefulProvider implements ConstraintProvider {
    private static String prefix = "initial";
    private final int minimum;
    private final Predicate<String> constructorPredicate;

    static {
      initializerCalls++;
    }

    public StatefulProvider(int minimum) {
      this.minimum = minimum;
      constructorPredicate = first();
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }

    public Predicate<String> first() {
      return value -> value.startsWith(prefix);
    }

    public Predicate<String> second() {
      return value -> value.startsWith(prefix);
    }

    public Predicate<String> captured() {
      return value -> value.length() > minimum;
    }

    public Class<?> originalLookupClass() {
      return MethodHandles.lookup().lookupClass();
    }

    public Class<?> providerLiteral() {
      return StatefulProvider.class;
    }

    public int readOther(StatefulProvider other) {
      return other.privateMinimum();
    }

    private int privateMinimum() {
      Predicate<String> marker = value -> !value.isEmpty();
      return marker.test("x") ? minimum : 0;
    }

    public boolean callConstant(StatefulProvider other) {
      return other.constant();
    }

    private boolean constant() {
      Predicate<String> marker = value -> !value.isEmpty();
      return marker.test("x");
    }

    public boolean monitorsHeld() {
      return instanceMonitorHeld() && staticMonitorHeld();
    }

    private synchronized boolean instanceMonitorHeld() {
      Predicate<String> marker = value -> !value.isEmpty();
      return marker.test("x") && Thread.holdsLock(this);
    }

    private static synchronized boolean staticMonitorHeld() {
      Predicate<String> marker = value -> !value.isEmpty();
      return marker.test("x") && Thread.holdsLock(StatefulProvider.class);
    }

    public long lockedLong(boolean positive) {
      return privateLockedLong(positive);
    }

    private synchronized long privateLockedLong(boolean positive) {
      Predicate<String> marker = value -> !value.isEmpty();
      if (!marker.test("x") || !Thread.holdsLock(this)) {
        throw new IllegalStateException("receiver is not locked");
      }
      if (positive) {
        return Long.MAX_VALUE;
      }
      return Long.MIN_VALUE;
    }

    public void throwWhileLocked() {
      privateThrowWhileLocked();
    }

    private synchronized void privateThrowWhileLocked() {
      Predicate<String> marker = value -> !value.isEmpty();
      if (marker.test("x")) {
        throw new IllegalArgumentException("expected");
      }
    }
  }

  public static class StaticHelperProvider implements ConstraintProvider {
    public static final Predicate<String> INITIAL_PREDICATE = firstHelper();

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }

    public Predicate<String> first() {
      return firstHelper();
    }

    public Predicate<String> second() {
      return secondHelper();
    }

    private static Predicate<String> firstHelper() {
      return value -> !value.isEmpty();
    }

    private static Predicate<String> secondHelper() {
      return value -> !value.isEmpty();
    }
  }

  public static class BaseProvider {
    public String text() {
      return "base";
    }
  }

  public static class SuperProvider extends BaseProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }

    @Override
    public String text() {
      Predicate<String> marker = value -> !value.isEmpty();
      return marker.test("x") ? super.text() + " provider" : "invalid";
    }

    public String privateSuper() {
      return privateText();
    }

    private String privateText() {
      Predicate<String> marker = value -> !value.isEmpty();
      return marker.test("x") ? super.text() : "invalid";
    }

    public Predicate<String> first() {
      return value -> !value.isEmpty();
    }

    public Predicate<String> second() {
      return value -> !value.isEmpty();
    }
  }

  public interface TextProvider {
    default String text() {
      return "interface";
    }
  }

  public static class InterfaceProvider implements TextProvider, ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }

    @Override
    public String text() {
      Predicate<String> marker = value -> !value.isEmpty();
      return marker.test("x") ? TextProvider.super.text() + " provider" : "invalid";
    }

    public Predicate<String> first() {
      return value -> !value.isEmpty();
    }

    public Predicate<String> second() {
      return value -> !value.isEmpty();
    }
  }

  public static class ProtectedProvider extends ProviderTransformationProtectedBase
      implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }

    public long readAndUpdateOther(ProtectedProvider other) {
      return other.readAndUpdate();
    }

    private long readAndUpdate() {
      Predicate<String> marker = value -> !value.isEmpty();
      long before = number + value();
      number += 2;
      label = marker.test("x") ? label + " updated" : label;
      return before + value(3);
    }

    public LongSupplier referenceTo(ProtectedProvider other) {
      Predicate<String> marker = value -> !value.isEmpty();
      if (!marker.test("x")) {
        throw new IllegalStateException("invalid predicate");
      }
      return other::value;
    }

    public Predicate<String> first() {
      return value -> !value.isEmpty();
    }

    public Predicate<String> second() {
      return value -> !value.isEmpty();
    }
  }

  public static class CollisionProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }

    private Predicate<String> helper() {
      return value -> !value.isEmpty();
    }

    public String $greycos$helper$helper$0(CollisionProvider ignored) {
      return "user method";
    }

    public Predicate<String> first() {
      return helper();
    }

    public Predicate<String> second() {
      return value -> !value.isEmpty();
    }
  }

  public static class LookupProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }

    public int lookupModes() {
      return MethodHandles.lookup().lookupModes();
    }

    public Predicate<String> first() {
      MethodHandles.lookup();
      return value -> !value.isEmpty();
    }

    public Predicate<String> second() {
      return value -> !value.isEmpty();
    }
  }

  public static class CallerSensitiveProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }

    public Class<?> loadClass(String name) throws ClassNotFoundException {
      return Class.forName(name);
    }

    public Predicate<String> first() {
      try {
        Class.forName("java.lang.String");
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException(e);
      }
      return value -> !value.isEmpty();
    }

    public Predicate<String> second() {
      return value -> !value.isEmpty();
    }
  }
}
