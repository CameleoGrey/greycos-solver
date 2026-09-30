package greycos.solver.core.api.function;

/**
 * Represents a function that accepts three arguments and produces a float-valued result. This is
 * the {@code float}-producing primitive specialization for {@link TriFunction}.
 *
 * <p>This is a functional interface whose functional method is {@link #applyAsFloat(Object, Object,
 * Object)}.
 *
 * @param <A> the type of the first argument to the function
 * @param <B> the type of the second argument to the function
 * @param <C> the type of the third argument to the function
 */
@FunctionalInterface
public interface ToFloatTriFunction<A, B, C> {

  /**
   * Applies this function to the given arguments.
   *
   * @param a the first function argument
   * @param b the second function argument
   * @param c the third function argument
   * @return the function result
   */
  float applyAsFloat(A a, B b, C c);
}
