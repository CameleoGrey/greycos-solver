package greycos.solver.core.impl.nodesharing.protectedaccess;

/** Lives outside the provider's package so JVM protected-receiver checks apply. */
public class ProviderTransformationProtectedBase {
  protected long number = 7;
  protected String label = "base";

  protected long value() {
    return number;
  }

  protected long value(long increment) {
    return number + increment;
  }

  public long number() {
    return number;
  }

  public String label() {
    return label;
  }
}
