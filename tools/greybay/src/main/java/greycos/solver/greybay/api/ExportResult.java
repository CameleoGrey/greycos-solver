package greycos.solver.greybay.api;

import java.nio.file.Path;
import java.util.Map;

/** Files are installed atomically and committed by the provenance manifest written last. */
public record ExportResult(
    Path solverConfigPath,
    Path selectionManifestPath,
    Path evaluationsCsvPath,
    Path reportPath,
    Map<String, String> artifactDigests) {
  public ExportResult {
    artifactDigests = Map.copyOf(artifactDigests);
  }
}
