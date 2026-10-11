package greycos.solver.greybay.api;

import java.util.Objects;

/** A problem label is metadata; the fingerprint identifies immutable problem content. */
public record ProblemDescriptor(
    String id, String contentFingerprint, ProblemCapabilities capabilities) {
  public ProblemDescriptor {
    ComponentIdentity.requireText(id, "problemId");
    ComponentIdentity.requireText(contentFingerprint, "contentFingerprint");
    Objects.requireNonNull(capabilities, "capabilities");
  }
}
