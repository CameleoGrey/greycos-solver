package greycos.solver.greybay.impl.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyStoreTest {
  private static final Map<String, Object> MANIFEST =
      Map.of(
          "schemaVersion",
          "1",
          "protocol",
          Map.of("seed", "9223372036854775807", "budgetNanos", "60000000000"));

  @TempDir Path temporaryDirectory;

  @Test
  void artifactsRecordsAndCheckpointsRoundTripWithoutChangingExactValues() throws Exception {
    var path = temporaryDirectory.resolve("study");
    ArtifactReference artifact;
    List<JournalRecord> committed;
    try (var store = StudyStore.create(path, MANIFEST)) {
      artifact =
          store.writeArtifact("solver configuration".getBytes(StandardCharsets.UTF_8), ".xml");
      var payload = new LinkedHashMap<String, Object>();
      payload.put("rng", "18446744073709551615");
      payload.put("configuration", artifact.toMap());
      payload.put("score", null);
      store.append("PROPOSED", payload);
      payload.put("rng", "mutated by caller");
      store.checkpoint(Map.of("nextTrial", "2"));
      committed = store.records();
      assertThat(committed.getFirst().payload().get("rng")).isEqualTo("18446744073709551615");
      assertThat(store.checkpoint().orElseThrow().sequence()).isEqualTo(2L);
      assertThat(committed.getLast().kind()).isEqualTo(StudyStore.CHECKPOINT_MARKER_KIND);
      assertThat(committed.getLast().payload())
          .containsOnlyKeys("stateDigest")
          .containsEntry(
              "stateDigest", StoreJson.sha256(StoreJson.canonicalBytes(Map.of("nextTrial", "2"))));
    }
    try (var resumed = StudyStore.resume(path, MANIFEST)) {
      assertThat(resumed.records()).isEqualTo(committed);
      assertThat(resumed.readArtifact(artifact))
          .isEqualTo("solver configuration".getBytes(StandardCharsets.UTF_8));
      assertThat(resumed.checkpoint().orElseThrow().state()).containsEntry("nextTrial", "2");
      var next = resumed.append("STARTED", Map.of("attempt", "0"));
      assertThat(next.sequence()).isEqualTo(3L);
      assertThat(next.previousHash()).isEqualTo(committed.getLast().hash());
    }
  }

  @Test
  void createRefusesExistingDirectoryAndResumeRequiresExclusiveOwnership() throws Exception {
    var path = temporaryDirectory.resolve("study");
    try (var store = StudyStore.create(path, MANIFEST)) {
      assertThat(store.manifest()).isEqualTo(MANIFEST);
      assertThatThrownBy(() -> StudyStore.create(path, MANIFEST)).isInstanceOf(IOException.class);
      assertThatThrownBy(() -> StudyStore.resume(path, MANIFEST))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("already locked");
    }
    try (var resumed = StudyStore.resume(path, MANIFEST)) {
      assertThat(resumed.records()).isEmpty();
    }
  }

  @Test
  void identityMismatchExplainsFieldAndReleasesLock() throws Exception {
    var path = temporaryDirectory.resolve("study");
    try (var store = StudyStore.create(path, MANIFEST)) {
      store.append("CREATED", Map.of());
    }
    var changed =
        Map.<String, Object>of(
            "schemaVersion", "1", "protocol", Map.of("seed", "4", "budgetNanos", "60000000000"));
    assertThatThrownBy(() -> StudyStore.resume(path, changed))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("$.protocol.seed");
    try (var resumed = StudyStore.resume(path, MANIFEST)) {
      assertThat(resumed.records()).hasSize(1);
    }
  }

  @Test
  void tornHeaderPayloadAndDigestPreserveEarlierCommitsAndOriginalTail() throws Exception {
    for (int tailBytes : List.of(1, 5, 11, 12, 30, 100)) {
      var path = temporaryDirectory.resolve("study-" + tailBytes);
      long firstLength;
      byte[] secondFrame;
      try (var store = StudyStore.create(path, MANIFEST)) {
        store.append("FIRST", Map.of("value", "1"));
        firstLength = Files.size(path.resolve("journal.gb"));
        store.append("SECOND", Map.of("value", "2"));
        var complete = Files.readAllBytes(path.resolve("journal.gb"));
        secondFrame = Arrays.copyOfRange(complete, (int) firstLength, complete.length);
      }
      try (var channel = FileChannel.open(path.resolve("journal.gb"), StandardOpenOption.WRITE)) {
        channel.truncate(firstLength + tailBytes);
      }
      try (var resumed = StudyStore.resume(path, MANIFEST)) {
        assertThat(resumed.records()).extracting(JournalRecord::kind).containsExactly("FIRST");
        assertThat(Files.size(path.resolve("journal.gb"))).isEqualTo(firstLength);
        try (var diagnostics = Files.list(path.resolve("diagnostics"))) {
          var preserved = diagnostics.toList();
          assertThat(preserved).hasSize(1);
          assertThat(Files.readAllBytes(preserved.getFirst()))
              .isEqualTo(Arrays.copyOf(secondFrame, tailBytes));
        }
        assertThat(resumed.append("RECOVERED", Map.of()).sequence()).isEqualTo(2L);
      }
    }
  }

  @Test
  void missingLastDigestBytesAreTornButCompleteChecksumMismatchIsCorruption() throws Exception {
    var torn = temporaryDirectory.resolve("torn");
    try (var store = StudyStore.create(torn, MANIFEST)) {
      store.append("PROPOSED", Map.of("state", "next"));
    }
    var journal = torn.resolve("journal.gb");
    try (var channel = FileChannel.open(journal, StandardOpenOption.WRITE)) {
      channel.truncate(channel.size() - 1);
    }
    try (var resumed = StudyStore.resume(torn, MANIFEST)) {
      assertThat(resumed.records()).isEmpty();
    }

    var corrupt = temporaryDirectory.resolve("corrupt");
    try (var store = StudyStore.create(corrupt, MANIFEST)) {
      store.append("PROPOSED", Map.of("state", "next"));
    }
    journal = corrupt.resolve("journal.gb");
    var bytes = Files.readAllBytes(journal);
    bytes[bytes.length - 1] ^= 1;
    Files.write(journal, bytes);
    assertThatThrownBy(() -> StudyStore.resume(corrupt, MANIFEST))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("checksum mismatch");
    assertThat(Files.readAllBytes(journal)).isEqualTo(bytes);
  }

  @Test
  void completeHeaderCorruptionIsNotSilentlyTruncated() throws Exception {
    var path = temporaryDirectory.resolve("study");
    try (var store = StudyStore.create(path, MANIFEST)) {
      store.append("PROPOSED", Map.of());
    }
    var bytes = Files.readAllBytes(path.resolve("journal.gb"));
    ByteBuffer.wrap(bytes).putInt(4, Integer.MAX_VALUE);
    Files.write(path.resolve("journal.gb"), bytes);
    assertThatThrownBy(() -> StudyStore.resume(path, MANIFEST))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Corrupt complete journal header");
    assertThat(Files.readAllBytes(path.resolve("journal.gb"))).isEqualTo(bytes);
  }

  @Test
  void duplicateCommittedRecordBreaksSequenceAndHashChain() throws Exception {
    var path = temporaryDirectory.resolve("study");
    try (var store = StudyStore.create(path, MANIFEST)) {
      store.append("PROPOSED", Map.of());
    }
    var journal = path.resolve("journal.gb");
    Files.write(journal, Files.readAllBytes(journal), StandardOpenOption.APPEND);
    assertThatThrownBy(() -> StudyStore.resume(path, MANIFEST))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("sequence or hash chain mismatch");
  }

  @Test
  void committedMissingArtifactFailsRecoveryAndUncommittedArtifactIsHarmless() throws Exception {
    var path = temporaryDirectory.resolve("study");
    ArtifactReference committed;
    try (var store = StudyStore.create(path, MANIFEST)) {
      committed = store.writeArtifact(new byte[] {1}, "json");
      store.writeArtifact(new byte[] {2}, "json");
      store.append("OBSERVED", Map.of("observation", committed.toMap()));
      store.checkpoint(Map.of("observations", List.of(committed.toMap())));
    }
    try (var resumed = StudyStore.resume(path, MANIFEST)) {
      assertThat(resumed.records()).hasSize(2);
      Files.delete(resumed.artifactPath(committed));
    }
    assertThatThrownBy(() -> StudyStore.resume(path, MANIFEST))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("missing");
  }

  @Test
  void appendRejectsMissingArtifactWithoutCommittingRecord() throws Exception {
    try (var store = StudyStore.create(temporaryDirectory.resolve("study"), MANIFEST)) {
      var reference = new ArtifactReference("sha256:" + "a".repeat(64), "json");
      assertThatThrownBy(() -> store.append("OBSERVED", Map.of("artifact", reference.toMap())))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("missing");
      assertThatThrownBy(() -> store.checkpoint(Map.of("artifact", reference.toMap())))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("missing");
      assertThat(store.records()).isEmpty();
      assertThat(store.append("STARTED", Map.of()).sequence()).isEqualTo(1L);
    }
  }

  @Test
  void corruptedCommittedArtifactCannotBeReplacedOrResumed() throws Exception {
    var path = temporaryDirectory.resolve("study");
    try (var store = StudyStore.create(path, MANIFEST)) {
      var reference = store.writeArtifact(new byte[] {1, 2, 3}, "bin");
      store.append("OBSERVED", Map.of("artifact", reference.toMap()));
      Files.write(store.artifactPath(reference), new byte[] {4, 5, 6});
      assertThatThrownBy(() -> store.writeArtifact(new byte[] {1, 2, 3}, "bin"))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("digest mismatch");
    }
    assertThatThrownBy(() -> StudyStore.resume(path, MANIFEST))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("digest mismatch");
  }

  @Test
  void manifestChecksumMismatchIsNotTreatedAsCompatibleIdentity() throws Exception {
    var path = temporaryDirectory.resolve("study");
    try (var store = StudyStore.create(path, MANIFEST)) {
      store.append("CREATED", Map.of());
    }
    var manifestPath = path.resolve("manifest.json");
    var document = new LinkedHashMap<>(StoreJson.readObject(Files.readAllBytes(manifestPath)));
    document.put("digest", "sha256:" + "0".repeat(64));
    Files.write(manifestPath, StoreJson.canonicalBytes(document));
    assertThatThrownBy(() -> StudyStore.resume(path, MANIFEST))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Manifest digest mismatch");
  }

  @Test
  void corruptedLegacyCheckpointIsPreservedAndRecoveredFromJournaledState() throws Exception {
    var path = temporaryDirectory.resolve("study");
    try (var store = StudyStore.create(path, MANIFEST)) {
      store.append("PROPOSED", Map.of("rng", "123"));
      store.append(StudyStore.CHECKPOINT_KIND, Map.of("rng", "123", "reserved", List.of("1")));
    }
    Files.writeString(path.resolve("checkpoint.json"), "broken checkpoint");
    try (var resumed = StudyStore.resume(path, MANIFEST)) {
      assertThat(resumed.checkpoint().orElseThrow().state()).containsEntry("rng", "123");
      assertThat(resumed.records()).hasSize(2);
      try (var diagnostics = Files.list(path.resolve("diagnostics"))) {
        assertThat(diagnostics.toList()).hasSize(1);
      }
    }
  }

  @Test
  void crashAfterLegacyCheckpointCommitBeforeReplacementUsesNewestState() throws Exception {
    var path = temporaryDirectory.resolve("study");
    try (var store = StudyStore.create(path, MANIFEST)) {
      store.append(StudyStore.CHECKPOINT_KIND, Map.of("rng", "old"));
    }
    try (var store = StudyStore.resume(path, MANIFEST)) {
      assertThat(store.checkpoint().orElseThrow().state()).containsEntry("rng", "old");
      store.append(StudyStore.CHECKPOINT_KIND, Map.of("rng", "new"));
    }
    try (var resumed = StudyStore.resume(path, MANIFEST)) {
      assertThat(resumed.checkpoint().orElseThrow().state()).containsEntry("rng", "new");
    }
  }

  @Test
  void checkpointJournalGrowthDoesNotDependOnProjectionSize() throws Exception {
    var path = temporaryDirectory.resolve("study");
    var state = Map.<String, Object>of("observations", List.of("x".repeat(100_000)));
    try (var store = StudyStore.create(path, MANIFEST)) {
      store.checkpoint(Map.of("observations", List.of()));
      long firstLength = Files.size(path.resolve("journal.gb"));
      store.checkpoint(state);
      long secondLength = Files.size(path.resolve("journal.gb")) - firstLength;
      assertThat(secondLength).isEqualTo(firstLength).isLessThan(1_000);
      assertThat(store.records())
          .allSatisfy(
              record -> {
                assertThat(record.kind()).isEqualTo(StudyStore.CHECKPOINT_MARKER_KIND);
                assertThat(record.payload()).containsOnlyKeys("stateDigest");
              });
      assertThat(store.checkpoint().orElseThrow().state()).isEqualTo(state);
    }
    try (var resumed = StudyStore.resume(path, MANIFEST)) {
      assertThat(resumed.checkpoint().orElseThrow().state()).isEqualTo(state);
    }
  }

  @Test
  void laterTransitionsDoNotInvalidateAnAnchoredCheckpoint() throws Exception {
    var path = temporaryDirectory.resolve("study");
    Checkpoint checkpoint;
    try (var store = StudyStore.create(path, MANIFEST)) {
      store.checkpoint(Map.of("rng", "123"));
      checkpoint = store.checkpoint().orElseThrow();
      store.append("ACTIVE_COMPLETED", Map.of("elapsedNanos", "456"));
    }
    var journal = Files.readAllBytes(path.resolve("journal.gb"));
    try (var resumed = StudyStore.resume(path, MANIFEST)) {
      assertThat(resumed.checkpoint()).contains(checkpoint);
      assertThat(resumed.records()).hasSize(2);
      assertThat(Files.readAllBytes(path.resolve("journal.gb"))).isEqualTo(journal);
      assertThat(diagnostics(path)).isEmpty();
    }
  }

  @Test
  void missingMarkerCacheLeavesJournalIntactAndCanBeRebuilt() throws Exception {
    var path = temporaryDirectory.resolve("study");
    var state = Map.<String, Object>of("rng", "123");
    try (var store = StudyStore.create(path, MANIFEST)) {
      store.append("PROPOSED", state);
      store.checkpoint(state);
    }
    var journal = Files.readAllBytes(path.resolve("journal.gb"));
    Files.delete(path.resolve("checkpoint.json"));
    try (var resumed = StudyStore.resume(path, MANIFEST)) {
      assertThat(resumed.checkpoint()).isEmpty();
      assertThat(Files.readAllBytes(path.resolve("journal.gb"))).isEqualTo(journal);
      assertThat(diagnostics(path)).isEmpty();
      resumed.checkpoint(state);
      assertThat(resumed.checkpoint().orElseThrow().state()).isEqualTo(state);
      assertThat(Arrays.copyOf(Files.readAllBytes(path.resolve("journal.gb")), journal.length))
          .isEqualTo(journal);
    }
    try (var resumed = StudyStore.resume(path, MANIFEST)) {
      assertThat(resumed.checkpoint().orElseThrow().state()).isEqualTo(state);
    }
  }

  @Test
  void corruptOrTamperedMarkerCacheIsPreservedThenDiscarded() throws Exception {
    for (var alteration :
        List.of(
            "invalidJson", "documentDigest", "state", "sequence", "journalHash", "formatVersion")) {
      var path = temporaryDirectory.resolve(alteration);
      try (var store = StudyStore.create(path, MANIFEST)) {
        store.checkpoint(Map.of("rng", "123"));
      }
      var cache = path.resolve("checkpoint.json");
      var document = StoreJson.readObject(Files.readAllBytes(cache));
      var content = new LinkedHashMap<>(object(document.get("content")));
      byte[] invalid;
      if (alteration.equals("invalidJson")) {
        invalid = "broken checkpoint".getBytes(StandardCharsets.UTF_8);
      } else if (alteration.equals("documentDigest")) {
        invalid = StoreJson.canonicalBytes(Map.of("content", content, "digest", "incorrect"));
      } else {
        content.put(
            alteration, alteration.equals("state") ? Map.of("rng", "tampered") : "incorrect");
        // Recomputing the side-file checksum must not bypass the journal anchor or state digest.
        invalid = checkpointBytes(content);
      }
      Files.write(cache, invalid);
      var journal = Files.readAllBytes(path.resolve("journal.gb"));
      try (var resumed = StudyStore.resume(path, MANIFEST)) {
        assertThat(resumed.checkpoint()).isEmpty();
        assertThat(Files.exists(cache)).isFalse();
        assertThat(Files.readAllBytes(path.resolve("journal.gb"))).isEqualTo(journal);
        var preserved = diagnostics(path);
        assertThat(preserved).hasSize(1);
        assertThat(Files.readAllBytes(preserved.getFirst())).isEqualTo(invalid);
      }
      try (var resumed = StudyStore.resume(path, MANIFEST)) {
        assertThat(resumed.checkpoint()).isEmpty();
        assertThat(diagnostics(path)).hasSize(1);
      }
    }
  }

  @Test
  void cacheWithoutAnyCheckpointRecordIsDisposable() throws Exception {
    var path = temporaryDirectory.resolve("study");
    try (var store = StudyStore.create(path, MANIFEST)) {
      store.append("PROPOSED", Map.of("rng", "123"));
    }
    Files.writeString(path.resolve("checkpoint.json"), "unanchored cache");
    try (var resumed = StudyStore.resume(path, MANIFEST)) {
      assertThat(resumed.checkpoint()).isEmpty();
      assertThat(resumed.records()).hasSize(1);
      assertThat(Files.exists(path.resolve("checkpoint.json"))).isFalse();
      assertThat(diagnostics(path)).hasSize(1);
    }
  }

  @Test
  void latestMarkerDoesNotFallBackToAnOlderLegacyProjection() throws Exception {
    var path = temporaryDirectory.resolve("study");
    var nextState = Map.<String, Object>of("rng", "new");
    try (var store = StudyStore.create(path, MANIFEST)) {
      store.append(StudyStore.CHECKPOINT_KIND, Map.of("rng", "old"));
    }
    try (var store = StudyStore.resume(path, MANIFEST)) {
      assertThat(store.checkpoint().orElseThrow().state()).containsEntry("rng", "old");
      store.append(
          StudyStore.CHECKPOINT_MARKER_KIND,
          Map.of("stateDigest", StoreJson.sha256(StoreJson.canonicalBytes(nextState))));
      assertThat(store.checkpoint()).isEmpty();
    }
    var stale = Files.readAllBytes(path.resolve("checkpoint.json"));
    var journal = Files.readAllBytes(path.resolve("journal.gb"));
    try (var resumed = StudyStore.resume(path, MANIFEST)) {
      assertThat(resumed.checkpoint()).isEmpty();
      assertThat(Files.exists(path.resolve("checkpoint.json"))).isFalse();
      assertThat(Files.readAllBytes(path.resolve("journal.gb"))).isEqualTo(journal);
      assertThat(Files.readAllBytes(diagnostics(path).getFirst())).isEqualTo(stale);
      resumed.checkpoint(nextState);
    }
    try (var resumed = StudyStore.resume(path, MANIFEST)) {
      assertThat(resumed.checkpoint().orElseThrow().state()).isEqualTo(nextState);
      assertThat(Arrays.copyOf(Files.readAllBytes(path.resolve("journal.gb")), journal.length))
          .isEqualTo(journal);
    }
  }

  @Test
  void unsafeCheckpointPathsFailWithoutReadingOrDeletingThem() throws Exception {
    for (boolean symbolicLink : List.of(false, true)) {
      var path = temporaryDirectory.resolve("study-" + symbolicLink);
      try (var store = StudyStore.create(path, MANIFEST)) {
        store.checkpoint(Map.of("rng", "123"));
      }
      var cache = path.resolve("checkpoint.json");
      var external = temporaryDirectory.resolve("external-" + symbolicLink);
      Files.writeString(external, "protected contents");
      Files.delete(cache);
      if (symbolicLink) Files.createSymbolicLink(cache, external);
      else Files.createDirectory(cache);
      assertThatThrownBy(() -> StudyStore.resume(path, MANIFEST))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("ordinary file");
      assertThat(Files.readString(external)).isEqualTo("protected contents");
      assertThat(diagnostics(path)).isEmpty();
      Files.delete(cache);
      try (var resumed = StudyStore.resume(path, MANIFEST)) {
        assertThat(resumed.checkpoint()).isEmpty();
      }
    }
  }

  @Test
  void inaccessibleCheckpointIsNotDiscardedAsCorruptCache() throws Exception {
    assumeTrue(Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"));
    var path = temporaryDirectory.resolve("study");
    try (var store = StudyStore.create(path, MANIFEST)) {
      store.checkpoint(Map.of("rng", "123"));
    }
    var cache = path.resolve("checkpoint.json");
    var permissions = Files.getPosixFilePermissions(cache);
    try {
      Files.setPosixFilePermissions(cache, Set.of());
      assumeTrue(!Files.isReadable(cache), "Privileged processes can bypass file permissions.");
      assertThatThrownBy(() -> StudyStore.resume(path, MANIFEST)).isInstanceOf(IOException.class);
      assertThat(Files.exists(cache)).isTrue();
      assertThat(diagnostics(path)).isEmpty();
    } finally {
      Files.setPosixFilePermissions(cache, permissions);
    }
    try (var resumed = StudyStore.resume(path, MANIFEST)) {
      assertThat(resumed.checkpoint().orElseThrow().state()).containsEntry("rng", "123");
    }
  }

  @Test
  void failedCacheReplacementDoesNotPoisonJournalOrRetainOlderProjection() throws Exception {
    var path = temporaryDirectory.resolve("study");
    try (var store = StudyStore.create(path, MANIFEST)) {
      store.checkpoint(Map.of("rng", "old"));
      var cache = path.resolve("checkpoint.json");
      Files.delete(cache);
      Files.createDirectory(cache);
      assertThatThrownBy(() -> store.checkpoint(Map.of("rng", "new")))
          .isInstanceOf(IOException.class);
      assertThat(store.checkpoint()).isEmpty();
      assertThat(store.records()).hasSize(2);
      assertThat(store.append("ACTIVE_COMPLETED", Map.of("elapsedNanos", "123")).sequence())
          .isEqualTo(3);
      Files.delete(cache);
    }
    try (var resumed = StudyStore.resume(path, MANIFEST)) {
      assertThat(resumed.checkpoint()).isEmpty();
      assertThat(resumed.records()).hasSize(3);
    }
  }

  @Test
  void malformedCheckpointMarkersAreRejectedOnAppendAndReplay() throws Exception {
    var invalidMarkers =
        List.<Map<String, Object>>of(
            Map.of(),
            Map.of("stateDigest", "incorrect"),
            Map.of("stateDigest", "sha256:" + "a".repeat(64), "state", Map.of()));
    for (int index = 0; index < invalidMarkers.size(); index++) {
      var path = temporaryDirectory.resolve("study-" + index);
      var invalid = invalidMarkers.get(index);
      try (var store = StudyStore.create(path, MANIFEST)) {
        assertThatThrownBy(() -> store.append(StudyStore.CHECKPOINT_MARKER_KIND, invalid))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("Malformed checkpoint marker");
        assertThat(store.records()).isEmpty();
      }
      var bytes =
          StoreJson.canonicalBytes(
              Map.of(
                  "formatVersion",
                  "1",
                  "sequence",
                  "1",
                  "kind",
                  StudyStore.CHECKPOINT_MARKER_KIND,
                  "previousHash",
                  "sha256:" + "0".repeat(64),
                  "payload",
                  invalid));
      var frame = ByteBuffer.allocate(12 + bytes.length + 32);
      frame.putInt(0x47424a31).putInt(bytes.length).putInt(~bytes.length).put(bytes);
      frame.put(HexFormat.of().parseHex(StoreJson.sha256(bytes).substring(7)));
      Files.write(path.resolve("journal.gb"), frame.array());
      assertThatThrownBy(() -> StudyStore.resume(path, MANIFEST))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("Malformed checkpoint marker");
      assertThat(Files.readAllBytes(path.resolve("journal.gb"))).isEqualTo(frame.array());
    }
  }

  @Test
  void concurrentHeartbeatsAndObservationsHaveOneContiguousJournal() throws Exception {
    var path = temporaryDirectory.resolve("study");
    try (var store = StudyStore.create(path, MANIFEST);
        var executor = Executors.newFixedThreadPool(2)) {
      var tasks = new ArrayList<java.util.concurrent.Future<?>>();
      for (int i = 0; i < 20; i++) {
        var sequence = Integer.toString(i);
        tasks.add(executor.submit(() -> store.append("HEARTBEAT", Map.of("elapsed", sequence))));
      }
      for (var task : tasks) {
        task.get();
      }
      assertThat(store.records()).hasSize(20);
    }
    try (var resumed = StudyStore.resume(path, MANIFEST)) {
      assertThat(resumed.records()).hasSize(20);
      for (int i = 0; i < 20; i++) {
        assertThat(resumed.records().get(i).sequence()).isEqualTo(i + 1L);
      }
    }
  }

  @Test
  void symbolicLinkArtifactDoesNotReadAnExternalFile() throws Exception {
    try (var store = StudyStore.create(temporaryDirectory.resolve("study"), MANIFEST)) {
      var bytes = new byte[] {1, 2, 3};
      var reference = new ArtifactReference(StoreJson.sha256(bytes), "bin");
      var external = temporaryDirectory.resolve("external");
      Files.write(external, bytes);
      Files.createSymbolicLink(store.artifactPath(reference), external);
      assertThatThrownBy(() -> store.readArtifact(reference)).isInstanceOf(IOException.class);
      assertThat(Files.readAllBytes(external)).isEqualTo(bytes);
    }
  }

  private static List<Path> diagnostics(Path study) throws IOException {
    try (var files = Files.list(study.resolve("diagnostics"))) {
      return files.toList();
    }
  }

  private static byte[] checkpointBytes(Map<String, Object> content) {
    return StoreJson.canonicalBytes(
        Map.of("content", content, "digest", StoreJson.sha256(StoreJson.canonicalBytes(content))));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> object(Object value) {
    return (Map<String, Object>) value;
  }
}
