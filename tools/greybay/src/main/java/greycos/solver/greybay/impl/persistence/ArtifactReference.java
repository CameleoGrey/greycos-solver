package greycos.solver.greybay.impl.persistence;

import java.util.Map;
import java.util.Objects;

/** A content-addressed artifact inside a study directory, independent of any caller path. */
public record ArtifactReference(String digest, String suffix) {
  public ArtifactReference {
    Objects.requireNonNull(digest, "digest");
    Objects.requireNonNull(suffix, "suffix");
    if (!digest.matches("sha256:[0-9a-f]{64}")) {
      throw new IllegalArgumentException("Invalid artifact digest (" + digest + ").");
    }
    if (suffix.startsWith(".")) {
      suffix = suffix.substring(1);
    }
    if (!suffix.matches("[a-z][a-z0-9]{0,15}")) {
      throw new IllegalArgumentException("Invalid artifact suffix (" + suffix + ").");
    }
  }

  public Map<String, Object> toMap() {
    return Map.of("digest", digest, "suffix", suffix);
  }

  public String fileName() {
    return digest.substring("sha256:".length()) + "." + suffix;
  }
}
