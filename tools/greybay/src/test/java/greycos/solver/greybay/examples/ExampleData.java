package greycos.solver.greybay.examples;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import greycos.solver.greybay.api.ComponentIdentity;

/** Shared immutable input catalog. Generated data is illustrative, not a performance corpus. */
final class ExampleData {
  static final List<String> TRAINING_IDS = List.of("train-small", "train-medium", "train-large");
  static final List<String> HELD_OUT_IDS =
      List.of("heldout-small", "heldout-medium", "heldout-large");

  private ExampleData() {}

  static int size(String id) {
    if (!TRAINING_IDS.contains(id) && !HELD_OUT_IDS.contains(id)) {
      throw new IllegalArgumentException("Unknown example problem ID (" + id + ").");
    }
    return id.endsWith("small") ? 6 : id.endsWith("medium") ? 18 : 54;
  }

  static ComponentIdentity identity(String id) {
    return new ComponentIdentity("greybay.example." + id, "1", "greybay-examples-v1");
  }

  static String fingerprint(String canonicalContent) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonicalContent.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java runtime.", exception);
    }
  }
}
