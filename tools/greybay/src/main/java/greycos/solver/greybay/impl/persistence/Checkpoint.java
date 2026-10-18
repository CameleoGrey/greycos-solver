package greycos.solver.greybay.impl.persistence;

import java.util.Map;

/** A cached projection anchored to a durable digest marker or legacy full checkpoint record. */
public record Checkpoint(long sequence, String hash, Map<String, Object> state) {
  public Checkpoint {
    state = StoreJson.immutableObject(state);
  }
}
