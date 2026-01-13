package ai.greycos.solver.core.impl.cotwin.variable.supply;

/**
 * Supplies something for 1 or multiple subsystems. Subsystems need to submit a {@link Demand} to
 * get a supply, so the supply can be shared or reused from the cotwin model.
 *
 * @see Demand
 * @see SupplyManager
 */
public interface Supply {}
