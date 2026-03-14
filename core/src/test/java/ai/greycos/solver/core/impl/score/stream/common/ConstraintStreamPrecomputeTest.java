package ai.greycos.solver.core.impl.score.stream.common;

public interface ConstraintStreamPrecomputeTest {
  void filter_0_changed();

  default void filter_1_changed() {
    // requires two elements, so Bi, Tri and Quad
  }

  default void filter_2_changed() {
    // requires three elements, so Tri and Quad
  }

  default void filter_3_changed() {
    // requires four elements, Quad
  }

  void ifExists();

  void ifNotExists();

  void groupBy();

  default void flatten() {
    // Flatten is currently covered only on Bi stream precompute tests in GreyCOS.
  }

  default void flattenNewInstances() {
    // Flatten is currently covered only on Bi stream precompute tests in GreyCOS.
  }

  void flattenLast();

  void flattenLastNewInstances();

  void map();

  void concat();

  void distinct();

  void complement();
}
