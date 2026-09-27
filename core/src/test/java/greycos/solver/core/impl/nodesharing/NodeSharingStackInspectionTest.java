package greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Predicate;
import java.util.function.Supplier;

import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;

import org.junit.jupiter.api.Test;

class NodeSharingStackInspectionTest {

  @Test
  void inheritedThrowableStackInspectionFailsBeforeRelocation() {
    assertThat(new DirectProvider().observed()).isEqualTo(DirectProvider.class.getName());
    assertThatThrownBy(
            () ->
                new DefaultConstraintProviderNodeSharer()
                    .buildNodeSharedConstraintProvider(DirectProvider.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(DirectProvider.class.getName())
        .hasMessageContaining("observed")
        .hasMessageContaining("stack inspection");
  }

  @Test
  void stackInspectingMethodReferenceFailsBeforeRelocation() {
    assertThatThrownBy(
            () ->
                new DefaultConstraintProviderNodeSharer()
                    .buildNodeSharedConstraintProvider(ReferenceProvider.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(ReferenceProvider.class.getName())
        .hasMessageContaining("observed")
        .hasMessageContaining("stack-inspecting method reference");
  }

  @Test
  void inheritedStaticThreadOperationsDoNotEstablishBodyEquivalence() {
    assertThat(new ConstraintProviderAnalyzer(ThreadProvider.class).analyze().hasShareableLambdas())
        .isFalse();
    assertThat(
            new DefaultConstraintProviderNodeSharer()
                .buildNodeSharedConstraintProvider(ThreadProvider.class))
        .isSameAs(ThreadProvider.class);
  }

  public static class DirectProvider implements ConstraintProvider {
    public String observed() {
      Predicate<String> first = value -> !value.isEmpty();
      Predicate<String> second = value -> !value.isEmpty();
      if (first.test("x") && second.test("x")) {
        return new RuntimeException().getStackTrace()[0].getClassName();
      }
      throw new IllegalStateException("Invalid test predicate");
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }
  }

  public static class ReferenceProvider implements ConstraintProvider {
    public Supplier<StackTraceElement[]> observed() {
      Predicate<String> first = value -> !value.isEmpty();
      Predicate<String> second = value -> !value.isEmpty();
      if (first.test("x") && second.test("x")) {
        return new RuntimeException()::getStackTrace;
      }
      throw new IllegalStateException("Invalid test predicate");
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }
  }

  public static class ChildThread extends Thread {}

  public static class ThreadProvider implements ConstraintProvider {
    public Supplier<StackTraceElement[]> first() {
      return () -> ChildThread.getAllStackTraces().get(Thread.currentThread());
    }

    public Supplier<StackTraceElement[]> second() {
      return () -> ChildThread.getAllStackTraces().get(Thread.currentThread());
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }
  }
}
