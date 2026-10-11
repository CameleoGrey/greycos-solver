package greycos.solver.greybay.impl.persistence;

import java.util.Map;

/** A checkpoint anchored to its own durable STORE_CHECKPOINT journal record. */
public record Checkpoint(long sequence, String hash, Map<String, Object> state) {
  public Checkpoint {
    state = StoreJson.immutableObject(state);
  }
}
