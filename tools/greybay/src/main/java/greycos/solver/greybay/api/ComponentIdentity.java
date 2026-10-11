package greycos.solver.greybay.api;

import java.util.Objects;

/** Stable semantic and application artifact identity, checked when a study is resumed. */
public record ComponentIdentity(String id, String version, String artifactIdentity) {
  public ComponentIdentity {
    requireText(id, "id");
    requireText(version, "version");
    requireText(artifactIdentity, "artifactIdentity");
  }

  public ComponentIdentity(String id, String version) {
    this(id, version, id + ":" + version);
  }

  public static String requireText(String value, String name) {
    Objects.requireNonNull(value, name);
    if (value.isBlank()) {
      throw new IllegalArgumentException("The " + name + " (" + value + ") must not be blank.");
    }
    return value;
  }
}
