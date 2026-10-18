package greycos.solver.core.impl.score.stream.common.inliner;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import greycos.solver.core.api.function.PentaFunction;
import greycos.solver.core.api.function.QuadFunction;
import greycos.solver.core.api.function.TriFunction;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.impl.score.constraint.ConstraintMatch;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Allows creating {@link ConstraintMatch} instances lazily if and only if they are required by the
 * end user.
 *
 * <p>Lazy behavior is important for constraint matching performance. In order to create {@link
 * ConstraintMatch}, an entire {@link ConstraintJustification} object needs to be created. Creating
 * this structure every time a constraint is matched would be wasteful, as the same constraint match
 * can be undone almost immediately, resulting in a lot of pointless garbage. Therefore, {@link
 * ConstraintMatch} (along with all of its supporting data structures) is only created when actually
 * needed, and that is during score explanation. Until that point, this thin wrapper serves as a
 * placeholder which understands what to create when needed.
 */
@NullMarked
@FunctionalInterface
public interface ConstraintMatchSupplier<Score_ extends Score<Score_>>
    extends BiFunction<Constraint, Score_, ConstraintMatch<Score_>> {

  /**
   * @return the constraint match returned by the supplier will have its justification set to null.
   *     This is useful when the justifications are disabled, to save memory.
   * @param <Score_>
   */
  static <Score_ extends Score<Score_>> ConstraintMatchSupplier<Score_> empty() {
    return (constraint, impact) ->
        new ConstraintMatch<>(constraint.getConstraintRef(), null, impact);
  }

  static <A, Score_ extends Score<Score_>> ConstraintMatchSupplier<Score_> of(
      BiFunction<A, Score<?>, ConstraintJustification> justificationMapping, A a) {
    return (constraint, impact) -> {
      try {
        var justification = justificationMapping.apply(a, impact);
        return new ConstraintMatch<>(constraint.getConstraintRef(), justification, impact);
      } catch (Exception e) {
        throw createJustificationException(constraint, e, a);
      }
    };
  }

  private static RuntimeException createJustificationException(
      Constraint constraint, Exception cause, Object... facts) {
    throw new IllegalStateException(
        "Consequence of a constraint ("
            + constraint.getConstraintRef()
            + ") threw an exception creating constraint justification from a tuple ("
            + factsToString(facts)
            + ").",
        cause);
  }

  private static String factsToString(Object... facts) {
    return Arrays.stream(facts).map(Objects::toString).collect(Collectors.joining(", ", "{", "}"));
  }

  static <A, B, Score_ extends Score<Score_>> ConstraintMatchSupplier<Score_> of(
      TriFunction<A, B, Score_, ConstraintJustification> justificationMapping, A a, B b) {
    return (constraint, impact) -> {
      try {
        var justification = justificationMapping.apply(a, b, impact);
        return new ConstraintMatch<>(constraint.getConstraintRef(), justification, impact);
      } catch (Exception e) {
        throw createJustificationException(constraint, e, a, b);
      }
    };
  }

  static <A, B, C, Score_ extends Score<Score_>> ConstraintMatchSupplier<Score_> of(
      QuadFunction<A, B, C, Score_, ConstraintJustification> justificationMapping, A a, B b, C c) {
    return (constraint, impact) -> {
      try {
        var justification = justificationMapping.apply(a, b, c, impact);
        return new ConstraintMatch<>(constraint.getConstraintRef(), justification, impact);
      } catch (Exception e) {
        throw createJustificationException(constraint, e, a, b, c);
      }
    };
  }

  static <A, B, C, D, Score_ extends Score<Score_>> ConstraintMatchSupplier<Score_> of(
      PentaFunction<A, B, C, D, Score_, ConstraintJustification> justificationMapping,
      A a,
      B b,
      C c,
      D d) {
    return (constraint, impact) -> {
      try {
        var justification = justificationMapping.apply(a, b, c, d, impact);
        return new ConstraintMatch<>(constraint.getConstraintRef(), justification, impact);
      } catch (Exception e) {
        throw createJustificationException(constraint, e, a, b, c, d);
      }
    };
  }

  /** Available only for suppliers created for an explicit indictment request. */
  default Collection<?> getIndictedObjects(Constraint constraint) {
    throw new IllegalStateException(
        "Indictment metadata was not retained for " + constraint.getConstraintRef() + ".");
  }

  static <A, Score_ extends Score<Score_>> ConstraintMatchSupplier<Score_> withIndictments(
      BiFunction<A, Score<?>, ConstraintJustification> justificationMapping,
      @Nullable Function<A, ? extends Collection<?>> indictmentMapping,
      A a) {
    return withIndictments(
        ConstraintMatchSupplier.<A, Score_>of(justificationMapping, a),
        indictmentMapping == null ? null : () -> indictmentMapping.apply(a),
        a);
  }

  static <A, B, Score_ extends Score<Score_>> ConstraintMatchSupplier<Score_> withIndictments(
      TriFunction<A, B, Score_, ConstraintJustification> justificationMapping,
      @Nullable BiFunction<A, B, ? extends Collection<?>> indictmentMapping,
      A a,
      B b) {
    return withIndictments(
        of(justificationMapping, a, b),
        indictmentMapping == null ? null : () -> indictmentMapping.apply(a, b),
        a,
        b);
  }

  static <A, B, C, Score_ extends Score<Score_>> ConstraintMatchSupplier<Score_> withIndictments(
      QuadFunction<A, B, C, Score_, ConstraintJustification> justificationMapping,
      @Nullable TriFunction<A, B, C, ? extends Collection<?>> indictmentMapping,
      A a,
      B b,
      C c) {
    return withIndictments(
        of(justificationMapping, a, b, c),
        indictmentMapping == null ? null : () -> indictmentMapping.apply(a, b, c),
        a,
        b,
        c);
  }

  static <A, B, C, D, Score_ extends Score<Score_>> ConstraintMatchSupplier<Score_> withIndictments(
      PentaFunction<A, B, C, D, Score_, ConstraintJustification> justificationMapping,
      @Nullable QuadFunction<A, B, C, D, ? extends Collection<?>> indictmentMapping,
      A a,
      B b,
      C c,
      D d) {
    return withIndictments(
        of(justificationMapping, a, b, c, d),
        indictmentMapping == null ? null : () -> indictmentMapping.apply(a, b, c, d),
        a,
        b,
        c,
        d);
  }

  private static <Score_ extends Score<Score_>> ConstraintMatchSupplier<Score_> withIndictments(
      ConstraintMatchSupplier<Score_> delegate,
      @Nullable Supplier<? extends Collection<?>> mapping,
      Object... facts) {
    return new ConstraintMatchSupplier<>() {
      @Override
      public ConstraintMatch<Score_> apply(Constraint constraint, Score_ impact) {
        return delegate.apply(constraint, impact);
      }

      @Override
      public Collection<?> getIndictedObjects(Constraint constraint) {
        if (mapping == null) {
          return Arrays.stream(facts).filter(Objects::nonNull).toList();
        }
        try {
          var objects =
              Objects.requireNonNull(mapping.get(), "The indictment mapping returned null.");
          for (var object : objects) {
            Objects.requireNonNull(object, "The indictment mapping returned a null element.");
          }
          return List.copyOf(objects);
        } catch (Exception e) {
          throw new IllegalStateException(
              "Consequence of a constraint ("
                  + constraint.getConstraintRef()
                  + ") threw an exception selecting indicted objects from a tuple ("
                  + factsToString(facts)
                  + ").",
              e);
        }
      }
    };
  }
}
