package greycos.solver.greybay.impl.study;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import greycos.solver.greybay.api.ExportResult;
import greycos.solver.greybay.impl.persistence.ArtifactReference;
import greycos.solver.greybay.impl.persistence.StoreJson;
import greycos.solver.greybay.impl.persistence.StudyStore;

/** Journal-backed export installation. The provenance manifest is the final commit marker. */
final class ExportFiles {
  static final String XML = "selected-solver.xml";
  static final String CSV = "evaluations.csv";
  static final String REPORT = "report.md";
  static final String MANIFEST = "selection.json";
  private static final List<String> FILES = List.of(XML, CSV, REPORT, MANIFEST);
  private static final String PREPARED = "EXPORT_PREPARED";

  private ExportFiles() {}

  static Path destination(Path requested, StudyStore store, List<Path> inputs) throws IOException {
    Path destination = resolve(requested);
    Path study = store.directory().toRealPath();
    if (study.startsWith(destination)
        || destination.startsWith(study.resolve("artifacts"))
        || destination.startsWith(study.resolve("diagnostics"))) {
      throw new IOException(
          "Export destination aliases study control storage (" + requested + ").");
    }
    for (Path input : inputs) {
      Path identity = input.toRealPath();
      if (identity.equals(destination) || destination.startsWith(identity)) {
        throw new IOException("Export destination aliases an input (" + input + ").");
      }
    }
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
        && !Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException(
          "Export destination is not an ordinary directory (" + destination + ").");
    }
    protectFiles(destination, store, inputs);
    return destination;
  }

  private static Path resolve(Path requested) throws IOException {
    // Resolve existing ancestors before appending missing components. This covers relative and
    // symlink aliases.
    Path absolute = requested.toAbsolutePath().normalize();
    Path existing = absolute;
    var missing = new ArrayList<Path>();
    while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
      missing.add(existing.getFileName());
      existing = existing.getParent();
    }
    Path result = existing.toRealPath();
    for (int i = missing.size() - 1; i >= 0; i--) result = result.resolve(missing.get(i));
    return result;
  }

  static Map<String, byte[]> recover(Path destination, StudyStore store, String selectionIdentity)
      throws IOException {
    Map<String, Object> transaction = null;
    for (var record : store.records()) {
      if (record.kind().equals(PREPARED)
          && destination.toString().equals(record.payload().get("destination"))) {
        transaction = record.payload();
      }
    }
    if (transaction == null) {
      if (Files.isDirectory(destination)) {
        try (var children = Files.list(destination)) {
          if (children.findAny().isPresent()) {
            throw new IOException(
                "Refusing to overwrite pre-existing unrelated export files (" + destination + ").");
          }
        }
      }
      return null;
    }
    if (!selectionIdentity.equals(transaction.get("selectionIdentity"))) {
      throw new IOException(
          "Export destination belongs to another frozen selection (" + destination + ").");
    }
    var references = object(transaction.get("artifacts"));
    if (!references.keySet().equals(Set.copyOf(FILES))) {
      throw new IOException("Malformed export artifact inventory (" + destination + ").");
    }
    var artifacts = new LinkedHashMap<String, byte[]>();
    for (String name : FILES) {
      var reference = object(references.get(name));
      artifacts.put(
          name,
          store.readArtifact(
              new ArtifactReference(
                  (String) reference.get("digest"), (String) reference.get("suffix"))));
    }
    removeInterruptedTemporaries(destination, artifacts);
    verifyExisting(destination, artifacts);
    return artifacts;
  }

  static ExportResult install(
      Path destination,
      StudyStore store,
      List<Path> inputs,
      String selectionIdentity,
      Map<String, byte[]> artifacts,
      boolean prepared)
      throws IOException {
    if (!artifacts.keySet().equals(Set.copyOf(FILES)))
      throw new IOException("Incomplete export artifact inventory.");
    protectFiles(destination, store, inputs);
    verifyExisting(destination, artifacts);
    if (!prepared) {
      var references = new LinkedHashMap<String, Object>();
      for (String name : FILES) {
        String suffix = name.substring(name.lastIndexOf('.') + 1);
        references.put(name, store.writeArtifact(artifacts.get(name), suffix).toMap());
      }
      // All bytes are durable before any user-facing file appears. Recovery never needs to rerun a
      // solve.
      store.append(
          PREPARED,
          Map.of(
              "schemaVersion",
              "1",
              "destination",
              destination.toString(),
              "selectionIdentity",
              selectionIdentity,
              "artifacts",
              references));
    }
    Files.createDirectories(destination);
    for (String name : FILES) {
      Path target = destination.resolve(name);
      if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS))
        atomicWrite(target, artifacts.get(name));
    }
    verifyExisting(destination, artifacts);
    var digests = new LinkedHashMap<String, String>();
    artifacts.forEach((name, bytes) -> digests.put(name, StoreJson.sha256(bytes)));
    return new ExportResult(
        destination.resolve(XML),
        destination.resolve(MANIFEST),
        destination.resolve(CSV),
        destination.resolve(REPORT),
        digests);
  }

  private static void verifyExisting(Path destination, Map<String, byte[]> artifacts)
      throws IOException {
    if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) return;
    try (var children = Files.list(destination)) {
      for (Path path : children.toList()) {
        String name = path.getFileName().toString();
        if (!artifacts.containsKey(name)
            || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
            || !StoreJson.sha256(Files.readAllBytes(path))
                .equals(StoreJson.sha256(artifacts.get(name)))) {
          throw new IOException(
              "Refusing to overwrite unrelated or modified export artifact (" + path + ").");
        }
      }
    }
    // A committed export must contain every artifact. A missing final manifest denotes an
    // incomplete export.
    if (Files.exists(destination.resolve(MANIFEST), LinkOption.NOFOLLOW_LINKS)) {
      for (String name : FILES) {
        if (!Files.isRegularFile(destination.resolve(name), LinkOption.NOFOLLOW_LINKS)) {
          throw new IOException(
              "Committed export artifact is missing (" + destination.resolve(name) + ").");
        }
      }
    }
  }

  private static void protectFiles(Path destination, StudyStore store, List<Path> inputs)
      throws IOException {
    var protectedFiles = new ArrayList<Path>(inputs);
    for (String name : List.of("study.lock", "manifest.json", "journal.gb", "checkpoint.json")) {
      protectedFiles.add(store.directory().resolve(name));
    }
    for (String directory : List.of("artifacts", "diagnostics")) {
      try (var files = Files.walk(store.directory().resolve(directory))) {
        protectedFiles.addAll(files.filter(Files::isRegularFile).toList());
      }
    }
    for (String name : FILES) {
      Path output = destination.resolve(name);
      if (!Files.exists(output, LinkOption.NOFOLLOW_LINKS)) continue;
      if (!Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("Export artifact must be an ordinary file (" + output + ").");
      }
      for (Path input : protectedFiles) {
        if (Files.exists(input) && Files.isSameFile(output, input)) {
          throw new IOException(
              "Export artifact aliases a protected input or study file (" + output + ").");
        }
      }
      // Reject other studies' control files and unrelated hard-link aliases even when their bytes
      // match.
      try {
        if (((Number) Files.getAttribute(output, "unix:nlink", LinkOption.NOFOLLOW_LINKS))
                .longValue()
            > 1) {
          throw new IOException(
              "Export artifact has an existing hard-link alias (" + output + ").");
        }
      } catch (UnsupportedOperationException ignored) {
        // isSameFile above remains the portable protection for declared inputs and this study.
      }
    }
  }

  private static void atomicWrite(Path destination, byte[] bytes) throws IOException {
    Path temporary = temporary(destination, bytes);
    try {
      try (var channel =
          FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
        var buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) channel.write(buffer);
        channel.force(true);
      }
      Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
      try (var directory = FileChannel.open(destination.getParent(), StandardOpenOption.READ)) {
        directory.force(true);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static Path temporary(Path destination, byte[] bytes) {
    return destination.resolveSibling(
        "." + destination.getFileName() + "." + StoreJson.sha256(bytes).substring(7) + ".tmp");
  }

  private static void removeInterruptedTemporaries(Path destination, Map<String, byte[]> artifacts)
      throws IOException {
    // These names were reserved by EXPORT_PREPARED in an empty/previously verified destination.
    // A process crash can leave any prefix of the file. Never treat that prefix as committed
    // evidence.
    for (String name : FILES) {
      Path path = temporary(destination.resolve(name), artifacts.get(name));
      if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) continue;
      if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException(
            "Interrupted export temporary is not an ordinary file (" + path + ").");
      }
      try {
        if (((Number) Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS)).longValue()
            != 1) {
          throw new IOException(
              "Interrupted export temporary has a hard-link alias (" + path + ").");
        }
      } catch (UnsupportedOperationException ignored) {
        // Deleting this directory entry cannot modify the contents of another hard-link alias.
      }
      Files.delete(path);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> object(Object value) throws IOException {
    if (!(value instanceof Map<?, ?>))
      throw new IOException("Expected an export transaction object.");
    return (Map<String, Object>) value;
  }
}
