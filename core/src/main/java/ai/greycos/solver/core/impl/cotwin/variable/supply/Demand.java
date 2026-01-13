package ai.greycos.solver.core.impl.cotwin.variable.supply;

/**
 * A subsystem submits a demand for a {@link Supply}. Implementations must overwrite {@link
 * Object#equals(Object)} and {@link Object#hashCode()}.
 *
 * @param <Supply_> Subclass of {@link Supply}
 * @see Supply
 * @see SupplyManager
 */
public interface Demand<Supply_ extends Supply> {

  /**
   * Only called if the cotwin model doesn't already support the demand (through a shadow variable
   * usually). Equal demands share the same {@link Supply}.
   *
   * @param supplyManager never null
   * @return never null
   */
  Supply_ createExternalizedSupply(SupplyManager supplyManager);
}
