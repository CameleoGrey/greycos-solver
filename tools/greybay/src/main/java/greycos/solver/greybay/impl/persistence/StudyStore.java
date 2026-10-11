package greycos.solver.greybay.impl.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Single-writer local study journal. All state-changing writes are forced before returning.
 *
 * <p>Checkpoints are caches: their complete state is also journaled as {@code STORE_CHECKPOINT}.
 * Replay clients must ignore that reserved record kind when reducing application transitions.
 * Artifact references must use {@link ArtifactReference#toMap()} so append and recovery validate
 * their existence and content. Process-crash recovery requires a local filesystem providing locks
 * and atomic replacement. No universal power-loss guarantee is made.
 */
public final class StudyStore implements AutoCloseable {
  public static final String CHECKPOINT_KIND = "STORE_CHECKPOINT";
  private static final int MAGIC = 0x47424a31;
  private static final int HEADER_BYTES = Integer.BYTES * 3;
  private static final int DIGEST_BYTES = 32;
  private static final String GENESIS = "sha256:" + "0".repeat(64);
  private static final String FORMAT_VERSION = "1";

  private final Path directory;
  private final Map<String, Object> manifest;
  private final FileChannel lockChannel;
  private final FileLock lock;
  private final FileChannel journal;
  private final List<JournalRecord> records = new ArrayList<>();
  private Checkpoint checkpoint;
  private boolean closed;
  private boolean poisoned;

  private StudyStore(Path directory, Map<String, Object> manifest, boolean create)
      throws IOException {
    this.directory = directory;
    this.manifest = StoreJson.immutableObject(manifest);
    var lockPath = directory.resolve("study.lock");
    rejectSymbolicLink(lockPath);
    lockChannel =
        create
            ? FileChannel.open(lockPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            : FileChannel.open(lockPath, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
    FileLock acquired = null;
    FileChannel openedJournal = null;
    try {
      try {
        acquired = lockChannel.tryLock();
      } catch (OverlappingFileLockException failure) {
        throw new IOException("Study directory is already locked (" + directory + ").", failure);
      }
      if (acquired == null) {
        throw new IOException("Study directory is already locked (" + directory + ").");
      }
      var journalPath = directory.resolve("journal.gb");
      rejectSymbolicLink(journalPath);
      openedJournal =
          create
              ? FileChannel.open(
                  journalPath,
                  StandardOpenOption.CREATE_NEW,
                  StandardOpenOption.READ,
                  StandardOpenOption.WRITE)
              : FileChannel.open(
                  journalPath,
                  StandardOpenOption.READ,
                  StandardOpenOption.WRITE,
                  LinkOption.NOFOLLOW_LINKS);
    } catch (IOException | RuntimeException | Error failure) {
      closeAfterFailure(openedJournal, failure);
      closeAfterFailure(acquired, failure);
      closeAfterFailure(lockChannel, failure);
      throw failure;
    }
    lock = acquired;
    journal = openedJournal;
  }

  public static StudyStore create(Path directory, Map<String, Object> manifest) throws IOException {
    Objects.requireNonNull(directory, "directory");
    var identity = StoreJson.immutableObject(manifest);
    var absolute = directory.toAbsolutePath().normalize();
    Files.createDirectories(absolute.getParent());
    Files.createDirectory(absolute);
    var store = new StudyStore(absolute.toRealPath(), identity, true);
    try {
      Files.createDirectory(store.directory.resolve("artifacts"));
      Files.createDirectory(store.directory.resolve("diagnostics"));
      store.preflight();
      var document =
          Map.<String, Object>of(
              "formatVersion",
              FORMAT_VERSION,
              "identity",
              identity,
              "digest",
              StoreJson.sha256(StoreJson.canonicalBytes(identity)));
      store.atomicWrite(
          store.directory.resolve("manifest.json"), StoreJson.canonicalBytes(document));
      store.journal.force(true);
      forceDirectory(store.directory);
      return store;
    } catch (IOException | RuntimeException | Error failure) {
      closeAfterFailure(store, failure);
      throw failure;
    }
  }

  public static StudyStore resume(Path directory, Map<String, Object> manifest) throws IOException {
    Objects.requireNonNull(directory, "directory");
    var identity = StoreJson.immutableObject(manifest);
    var store = new StudyStore(directory.toRealPath(), identity, false);
    try {
      store.verifyDirectory("artifacts");
      store.verifyDirectory("diagnostics");
      store.preflight();
      store.verifyManifest(identity);
      store.replay();
      store.restoreCheckpoint();
      return store;
    } catch (IOException | RuntimeException | Error failure) {
      closeAfterFailure(store, failure);
      throw failure;
    }
  }

  public Path directory() {
    return directory;
  }

  public Map<String, Object> manifest() {
    return manifest;
  }

  public synchronized List<JournalRecord> records() {
    return List.copyOf(records);
  }

  public synchronized Optional<Checkpoint> checkpoint() {
    return Optional.ofNullable(checkpoint);
  }

  public synchronized JournalRecord append(String kind, Map<String, Object> payload)
      throws IOException {
    requireOpen();
    if (kind == null || !kind.matches("[A-Z][A-Z0-9_]*")) {
      throw new IllegalArgumentException("Invalid journal record kind (" + kind + ").");
    }
    var immutablePayload = StoreJson.immutableObject(payload);
    validateReferences(immutablePayload);
    long sequence = records.size() + 1L;
    var previousHash = records.isEmpty() ? GENESIS : records.getLast().hash();
    var envelope =
        Map.<String, Object>of(
            "formatVersion",
            FORMAT_VERSION,
            "sequence",
            Long.toString(sequence),
            "kind",
            kind,
            "previousHash",
            previousHash,
            "payload",
            immutablePayload);
    var bytes = StoreJson.canonicalBytes(envelope);
    var hash = StoreJson.sha256(bytes);
    var header =
        ByteBuffer.allocate(HEADER_BYTES).putInt(MAGIC).putInt(bytes.length).putInt(~bytes.length);
    header.flip();
    try {
      journal.position(journal.size());
      writeFully(journal, header);
      writeFully(journal, ByteBuffer.wrap(bytes));
      writeFully(journal, ByteBuffer.wrap(HexFormat.of().parseHex(hash.substring(7))));
      journal.force(true);
    } catch (IOException failure) {
      poisoned = true;
      throw new IOException(
          "Journal append failed; close and recover study (" + directory + ").", failure);
    }
    var record = new JournalRecord(sequence, kind, immutablePayload, previousHash, hash);
    records.add(record);
    return record;
  }

  public synchronized ArtifactReference writeArtifact(byte[] bytes, String suffix)
      throws IOException {
    requireOpen();
    Objects.requireNonNull(bytes, "bytes");
    verifyDirectory("artifacts");
    var ownedBytes = bytes.clone();
    var reference = new ArtifactReference(StoreJson.sha256(ownedBytes), suffix);
    var path = artifactPath(reference);
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      requireArtifact(reference);
    } else {
      atomicWrite(path, ownedBytes);
    }
    return reference;
  }

  public Path artifactPath(ArtifactReference reference) {
    return directory.resolve("artifacts").resolve(reference.fileName());
  }

  public synchronized byte[] readArtifact(ArtifactReference reference) throws IOException {
    requireOpen();
    verifyDirectory("artifacts");
    var path = artifactPath(reference);
    var bytes = readRegularFile(path);
    if (!reference.digest().equals(StoreJson.sha256(bytes))) {
      throw new IOException("Artifact digest mismatch (" + path + ").");
    }
    return bytes;
  }

  public synchronized void requireArtifact(ArtifactReference reference) throws IOException {
    readArtifact(reference);
  }

  public synchronized void checkpoint(Map<String, Object> state) throws IOException {
    requireOpen();
    var record = append(CHECKPOINT_KIND, state);
    checkpoint = new Checkpoint(record.sequence(), record.hash(), record.payload());
    writeCheckpoint(checkpoint);
  }

  private void verifyManifest(Map<String, Object> expected) throws IOException {
    var document = StoreJson.readObject(readRegularFile(directory.resolve("manifest.json")));
    if (!FORMAT_VERSION.equals(document.get("formatVersion"))) {
      throw new IOException(
          "Unsupported manifest formatVersion (" + document.get("formatVersion") + ").");
    }
    var actual = object(document.get("identity"), "manifest identity");
    if (!StoreJson.sha256(StoreJson.canonicalBytes(actual)).equals(document.get("digest"))) {
      throw new IOException("Manifest digest mismatch (" + directory + ").");
    }
    var differences = new ArrayList<String>();
    compare("$", actual, expected, differences);
    if (!differences.isEmpty()) {
      throw new IOException("Immutable study identity differs:\n" + String.join("\n", differences));
    }
    validateReferences(actual);
  }

  private void replay() throws IOException {
    long position = 0;
    long size = journal.size();
    while (position < size) {
      if (size - position < HEADER_BYTES) {
        preserveTail(position);
        break;
      }
      journal.position(position);
      var header = ByteBuffer.allocate(HEADER_BYTES);
      readFully(journal, header);
      header.flip();
      int magic = header.getInt();
      int length = header.getInt();
      int lengthCheck = header.getInt();
      if (magic != MAGIC || length <= 0 || lengthCheck != ~length) {
        throw new IOException("Corrupt complete journal header at byte (" + position + ").");
      }
      long frameLength = HEADER_BYTES + (long) length + DIGEST_BYTES;
      if (frameLength > size - position) {
        preserveTail(position);
        break;
      }
      var payloadBytes = ByteBuffer.allocate(length);
      readFully(journal, payloadBytes);
      var digestBytes = ByteBuffer.allocate(DIGEST_BYTES);
      readFully(journal, digestBytes);
      var hash = "sha256:" + HexFormat.of().formatHex(digestBytes.array());
      if (!hash.equals(StoreJson.sha256(payloadBytes.array()))) {
        throw new IOException("Journal checksum mismatch at byte (" + position + ").");
      }
      var envelope = StoreJson.readObject(payloadBytes.array());
      long sequence;
      try {
        sequence = Long.parseLong(string(envelope, "sequence"));
      } catch (NumberFormatException failure) {
        throw new IOException("Invalid journal sequence at byte (" + position + ").", failure);
      }
      var previous = records.isEmpty() ? GENESIS : records.getLast().hash();
      if (!FORMAT_VERSION.equals(envelope.get("formatVersion"))
          || sequence != records.size() + 1L
          || !previous.equals(envelope.get("previousHash"))) {
        throw new IOException(
            "Journal version, sequence or hash chain mismatch at byte (" + position + ").");
      }
      var kind = string(envelope, "kind");
      if (!kind.matches("[A-Z][A-Z0-9_]*")) {
        throw new IOException("Invalid journal record kind (" + kind + ").");
      }
      var payload = object(envelope.get("payload"), "journal payload");
      validateReferences(payload);
      records.add(new JournalRecord(sequence, kind, payload, previous, hash));
      position += frameLength;
    }
    journal.position(journal.size());
  }

  private void restoreCheckpoint() throws IOException {
    for (var record : records) {
      if (CHECKPOINT_KIND.equals(record.kind())) {
        checkpoint = new Checkpoint(record.sequence(), record.hash(), record.payload());
      }
    }
    var path = directory.resolve("checkpoint.json");
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      var bytes = readRegularFile(path);
      boolean matches = false;
      try {
        var document = StoreJson.readObject(bytes);
        if (checkpoint != null) {
          matches = checkpointDocument(checkpoint).equals(document);
        }
      } catch (IOException ignored) {
        // The full checkpoint state must be recovered from a valid journal record below.
      }
      if (matches) {
        return;
      }
      if (checkpoint == null) {
        throw new IOException(
            "Checkpoint has no independently recoverable journal state (" + path + ").");
      }
      preserveDiagnostic("checkpoint", bytes);
    }
    if (checkpoint != null) {
      writeCheckpoint(checkpoint);
    }
  }

  private static Map<String, Object> checkpointDocument(Checkpoint checkpoint) {
    var content =
        Map.<String, Object>of(
            "formatVersion",
            FORMAT_VERSION,
            "sequence",
            Long.toString(checkpoint.sequence()),
            "journalHash",
            checkpoint.hash(),
            "state",
            checkpoint.state());
    return Map.of(
        "content", content, "digest", StoreJson.sha256(StoreJson.canonicalBytes(content)));
  }

  private void writeCheckpoint(Checkpoint checkpoint) throws IOException {
    atomicWrite(
        directory.resolve("checkpoint.json"),
        StoreJson.canonicalBytes(checkpointDocument(checkpoint)));
  }

  private void preserveTail(long position) throws IOException {
    long remaining = journal.size() - position;
    if (remaining > Integer.MAX_VALUE) {
      throw new IOException(
          "Torn journal tail is too large to preserve (" + remaining + " bytes).");
    }
    var tail = ByteBuffer.allocate((int) remaining);
    journal.position(position);
    readFully(journal, tail);
    preserveDiagnostic("torn-journal", tail.array());
    journal.truncate(position);
    journal.force(true);
  }

  private void preserveDiagnostic(String name, byte[] bytes) throws IOException {
    verifyDirectory("diagnostics");
    atomicWrite(
        directory.resolve("diagnostics").resolve(name + "-" + UUID.randomUUID() + ".bin"), bytes);
  }

  private void validateReferences(Object value) throws IOException {
    if (value instanceof Map<?, ?> map) {
      if (map.keySet().equals(Set.of("digest", "suffix"))) {
        try {
          if (!(map.get("digest") instanceof String digest)
              || !(map.get("suffix") instanceof String suffix)) {
            throw new IllegalArgumentException("Artifact digest and suffix must be strings.");
          }
          requireArtifact(new ArtifactReference(digest, suffix));
        } catch (IllegalArgumentException failure) {
          throw new IOException("Invalid artifact reference (" + map + ").", failure);
        }
      } else {
        for (var item : map.values()) {
          validateReferences(item);
        }
      }
    } else if (value instanceof List<?> list) {
      for (var item : list) {
        validateReferences(item);
      }
    }
  }

  private void preflight() throws IOException {
    // Acquiring the study lock has already established exclusive ownership.
    var path = directory.resolve("atomic-preflight-" + UUID.randomUUID());
    try {
      atomicWrite(path, new byte[] {1});
      atomicWrite(path, new byte[] {2});
      if (!Arrays.equals(readRegularFile(path), new byte[] {2})) {
        throw new IOException(
            "Filesystem atomic replacement preflight failed (" + directory + ").");
      }
    } finally {
      Files.deleteIfExists(path);
      forceDirectory(directory);
    }
  }

  private void atomicWrite(Path destination, byte[] bytes) throws IOException {
    rejectSymbolicLink(destination);
    var temporary =
        destination.resolveSibling(
            "." + destination.getFileName() + "." + UUID.randomUUID() + ".tmp");
    try {
      try (var channel =
          FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
        writeFully(channel, ByteBuffer.wrap(bytes));
        channel.force(true);
      }
      Files.move(
          temporary,
          destination,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
      forceDirectory(destination.getParent());
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private void verifyDirectory(String name) throws IOException {
    var path = directory.resolve(name);
    if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException(
          "Study directory is missing or is not an ordinary directory (" + path + ").");
    }
  }

  private static byte[] readRegularFile(Path path) throws IOException {
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException(
          "Required study artifact is missing or is not a regular file (" + path + ").");
    }
    try (var channel = FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
      if (channel.size() > Integer.MAX_VALUE) {
        throw new IOException("Study artifact exceeds supported byte-array size (" + path + ").");
      }
      var buffer = ByteBuffer.allocate((int) channel.size());
      readFully(channel, buffer);
      return buffer.array();
    }
  }

  private static void rejectSymbolicLink(Path path) throws IOException {
    if (Files.isSymbolicLink(path)) {
      throw new IOException("Study control paths must not be symbolic links (" + path + ").");
    }
  }

  private static void forceDirectory(Path directory) throws IOException {
    try (var channel = FileChannel.open(directory, StandardOpenOption.READ)) {
      channel.force(true);
    }
  }

  private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
    while (buffer.hasRemaining()) {
      channel.write(buffer);
    }
  }

  private static void readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
    while (buffer.hasRemaining()) {
      if (channel.read(buffer) < 0) {
        throw new IOException("Unexpected end of study file.");
      }
    }
  }

  private static String string(Map<String, Object> object, String key) throws IOException {
    if (object.get(key) instanceof String value) {
      return value;
    }
    throw new IOException("Expected string field (" + key + ").");
  }

  private static Map<String, Object> object(Object value, String description) throws IOException {
    if (!(value instanceof Map<?, ?> map)) {
      throw new IOException("Expected object for " + description + ".");
    }
    @SuppressWarnings("unchecked")
    var result = (Map<String, Object>) map;
    return result;
  }

  private static void compare(
      String path, Object actual, Object expected, List<String> differences) {
    if (actual instanceof Map<?, ?> left && expected instanceof Map<?, ?> right) {
      var keys = new TreeSet<String>();
      left.keySet().forEach(key -> keys.add((String) key));
      right.keySet().forEach(key -> keys.add((String) key));
      for (var key : keys) {
        if (!left.containsKey(key) || !right.containsKey(key)) {
          differences.add(
              path
                  + "."
                  + key
                  + " differs (stored present="
                  + left.containsKey(key)
                  + ", requested present="
                  + right.containsKey(key)
                  + ").");
        } else {
          compare(path + "." + key, left.get(key), right.get(key), differences);
        }
      }
    } else if (actual instanceof List<?> left
        && expected instanceof List<?> right
        && left.size() == right.size()) {
      for (int i = 0; i < left.size(); i++) {
        compare(path + "[" + i + "]", left.get(i), right.get(i), differences);
      }
    } else if (!Objects.equals(actual, expected)) {
      differences.add(path + " differs (stored=" + actual + ", requested=" + expected + ").");
    }
  }

  private void requireOpen() throws IOException {
    if (closed || poisoned) {
      throw new IOException(
          "Study store is " + (closed ? "closed" : "unusable after an append failure") + ".");
    }
  }

  private static void closeAfterFailure(AutoCloseable resource, Throwable failure) {
    if (resource != null) {
      try {
        resource.close();
      } catch (Exception | Error cleanupFailure) {
        if (cleanupFailure != failure) {
          failure.addSuppressed(cleanupFailure);
        }
      }
    }
  }

  @Override
  public synchronized void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    IOException failure = null;
    for (var resource : List.<AutoCloseable>of(journal, lock, lockChannel)) {
      try {
        resource.close();
      } catch (Exception closeFailure) {
        if (failure == null) {
          failure =
              new IOException("Failed to release study store (" + directory + ").", closeFailure);
        } else {
          failure.addSuppressed(closeFailure);
        }
      }
    }
    if (failure != null) {
      throw failure;
    }
  }
}
