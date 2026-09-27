package greycos.solver.core.impl.nodesharing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.function.Predicate;
import java.util.function.Supplier;

import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;

import org.junit.jupiter.api.Test;

class NodeSharingSerializableProviderTest {

  @Test
  void requiredTransformationFailsBeforeBreakingCapturedProviderSerialization() throws Exception {
    assertThat(roundTrip(new SharingProvider().supplier()).get()).isEqualTo("preserved");
    assertThatThrownBy(
            () ->
                new DefaultConstraintProviderNodeSharer()
                    .buildNodeSharedConstraintProvider(SharingProvider.class))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(SharingProvider.class.getName())
        .hasMessageContaining("java.io.Serializable")
        .hasMessageContaining("cannot be deserialized")
        .hasMessageContaining("disable constraintStreamAutomaticNodeSharing");
  }

  @Test
  void successfulNoOpPreservesSerializableProviders() throws Exception {
    var type =
        new DefaultConstraintProviderNodeSharer()
            .buildNodeSharedConstraintProvider(NoSharingProvider.class);
    assertThat(type).isSameAs(NoSharingProvider.class);
    assertThat(roundTrip(type.getConstructor().newInstance().supplier()).get())
        .isEqualTo("preserved");
  }

  @SuppressWarnings("unchecked")
  private static Supplier<String> roundTrip(Supplier<String> supplier) throws Exception {
    var bytes = new ByteArrayOutputStream();
    try (var output = new ObjectOutputStream(bytes)) {
      output.writeObject(supplier);
    }
    try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (Supplier<String>) input.readObject();
    }
  }

  public static class SharingProvider implements ConstraintProvider, Serializable {
    private final String value = "preserved";

    public Supplier<String> supplier() {
      Predicate<String> first = text -> !text.isEmpty();
      Predicate<String> second = text -> !text.isEmpty();
      if (first.test(value) && second.test(value)) {
        return (Supplier<String> & Serializable) this::secret;
      }
      throw new IllegalStateException("Invalid test predicate");
    }

    private String secret() {
      return value;
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }
  }

  public static class NoSharingProvider implements ConstraintProvider, Serializable {
    public Supplier<String> supplier() {
      return (Supplier<String> & Serializable) this::secret;
    }

    private String secret() {
      return "preserved";
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[0];
    }
  }
}
