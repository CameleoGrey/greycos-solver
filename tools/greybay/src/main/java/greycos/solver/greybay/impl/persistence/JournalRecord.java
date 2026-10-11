package greycos.solver.greybay.impl.persistence;

import java.util.Map;

/** A forced journal transition; its payload is recursively immutable. */
public record JournalRecord(
    long sequence, String kind, Map<String, Object> payload, String previousHash, String hash) {
  public JournalRecord {
    payload = StoreJson.immutableObject(payload);
  }
}
