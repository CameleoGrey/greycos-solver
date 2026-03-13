package ai.greycos.solver.core.impl.score.stream;

import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Function;

import ai.greycos.solver.core.api.function.QuadFunction;
import ai.greycos.solver.core.api.function.TriFunction;
import ai.greycos.solver.core.api.score.stream.bi.BiJoiner;
import ai.greycos.solver.core.api.score.stream.penta.PentaJoiner;
import ai.greycos.solver.core.api.score.stream.quad.QuadJoiner;
import ai.greycos.solver.core.api.score.stream.tri.TriJoiner;
import ai.greycos.solver.core.impl.bavet.bi.joiner.DefaultBiJoiner;
import ai.greycos.solver.core.impl.bavet.common.joiner.JoinerType;
import ai.greycos.solver.core.impl.bavet.penta.joiner.DefaultPentaJoiner;
import ai.greycos.solver.core.impl.bavet.quad.joiner.DefaultQuadJoiner;
import ai.greycos.solver.core.impl.bavet.tri.joiner.DefaultTriJoiner;

import org.jspecify.annotations.NonNull;

/**
 * These joiners are not finished because they show score corruptions when used. They are kept in an
 * internal namespace until the public API is ready.
 */
public final class UnfinishedJoiners {

  public static <A, B, Property_> @NonNull BiJoiner<A, B> containing(
      @NonNull Function<A, Collection<Property_>> leftMapping,
      @NonNull Function<B, Property_> rightMapping) {
    return new DefaultBiJoiner<>(leftMapping, JoinerType.CONTAINING, rightMapping);
  }

  public static <A, B, Property_> @NonNull BiJoiner<A, B> containedIn(
      @NonNull Function<A, Property_> leftMapping,
      @NonNull Function<B, Collection<Property_>> rightMapping) {
    return new DefaultBiJoiner<>(leftMapping, JoinerType.CONTAINED_IN, rightMapping);
  }

  public static <A, Property_> @NonNull BiJoiner<A, A> containingAnyOf(
      @NonNull Function<A, Collection<Property_>> mapping) {
    return containingAnyOf(mapping, mapping);
  }

  public static <A, B, Property_> @NonNull BiJoiner<A, B> containingAnyOf(
      @NonNull Function<A, Collection<Property_>> leftMapping,
      @NonNull Function<B, Collection<Property_>> rightMapping) {
    return new DefaultBiJoiner<>(leftMapping, JoinerType.CONTAINING_ANY_OF, rightMapping);
  }

  public static <A, B, C, Property_> @NonNull TriJoiner<A, B, C> containing(
      @NonNull BiFunction<A, B, Collection<Property_>> leftMapping,
      @NonNull Function<C, Property_> rightMapping) {
    return new DefaultTriJoiner<>(leftMapping, JoinerType.CONTAINING, rightMapping);
  }

  public static <A, B, C, Property_> @NonNull TriJoiner<A, B, C> containedIn(
      @NonNull BiFunction<A, B, Property_> leftMapping,
      @NonNull Function<C, Collection<Property_>> rightMapping) {
    return new DefaultTriJoiner<>(leftMapping, JoinerType.CONTAINED_IN, rightMapping);
  }

  public static <A, B, C, Property_> @NonNull TriJoiner<A, B, C> containingAnyOf(
      @NonNull BiFunction<A, B, Collection<Property_>> leftMapping,
      @NonNull Function<C, Collection<Property_>> rightMapping) {
    return new DefaultTriJoiner<>(leftMapping, JoinerType.CONTAINING_ANY_OF, rightMapping);
  }

  public static <A, B, C, D, Property_> @NonNull QuadJoiner<A, B, C, D> containing(
      @NonNull TriFunction<A, B, C, Collection<Property_>> leftMapping,
      @NonNull Function<D, Property_> rightMapping) {
    return new DefaultQuadJoiner<>(leftMapping, JoinerType.CONTAINING, rightMapping);
  }

  public static <A, B, C, D, Property_> @NonNull QuadJoiner<A, B, C, D> containedIn(
      @NonNull TriFunction<A, B, C, Property_> leftMapping,
      @NonNull Function<D, Collection<Property_>> rightMapping) {
    return new DefaultQuadJoiner<>(leftMapping, JoinerType.CONTAINED_IN, rightMapping);
  }

  public static <A, B, C, D, Property_> @NonNull QuadJoiner<A, B, C, D> containingAnyOf(
      @NonNull TriFunction<A, B, C, Collection<Property_>> leftMapping,
      @NonNull Function<D, Collection<Property_>> rightMapping) {
    return new DefaultQuadJoiner<>(leftMapping, JoinerType.CONTAINING_ANY_OF, rightMapping);
  }

  public static <A, B, C, D, E, Property_> @NonNull PentaJoiner<A, B, C, D, E> containing(
      @NonNull QuadFunction<A, B, C, D, Collection<Property_>> leftMapping,
      @NonNull Function<E, Property_> rightMapping) {
    return new DefaultPentaJoiner<>(leftMapping, JoinerType.CONTAINING, rightMapping);
  }

  public static <A, B, C, D, E, Property_> @NonNull PentaJoiner<A, B, C, D, E> containedIn(
      @NonNull QuadFunction<A, B, C, D, Property_> leftMapping,
      @NonNull Function<E, Collection<Property_>> rightMapping) {
    return new DefaultPentaJoiner<>(leftMapping, JoinerType.CONTAINED_IN, rightMapping);
  }

  public static <A, B, C, D, E, Property_> @NonNull PentaJoiner<A, B, C, D, E> containingAnyOf(
      @NonNull QuadFunction<A, B, C, D, Collection<Property_>> leftMapping,
      @NonNull Function<E, Collection<Property_>> rightMapping) {
    return new DefaultPentaJoiner<>(leftMapping, JoinerType.CONTAINING_ANY_OF, rightMapping);
  }

  private UnfinishedJoiners() {}
}
