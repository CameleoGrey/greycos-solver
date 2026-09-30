package greycos.solver.core.api.function;

/**
 * Represents a function that accepts one argument and produces a float-valued result. This is the
 * {@code float}-producing primitive specialization for {@link java.util.function.Function}.
 *
 * <p>This is a functional interface whose functional method is {@link #applyAsFloat(Object)}.
 *
 * @param <A> the type of the first argument to the function
 */
@FunctionalInterface
public interface ToFloatFunction<A> {

  /**
   * Applies this function to the given arguments.
   *
   * @param a the first function argument
   * @return the function result
   */
  float applyAsFloat(A a);
}
