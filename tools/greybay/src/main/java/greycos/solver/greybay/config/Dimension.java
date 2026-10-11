package greycos.solver.greybay.config;

/** A typed coordinate in a finite acyclic conditional space. */
public sealed interface Dimension
    permits CategoricalDimension, IntegerDimension, RealDimension, FixedDimension {
  String name();

  Activation activation();

  Dimension withActivation(Activation activation);

  void validateValue(Object value);
}
