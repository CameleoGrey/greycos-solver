package greycos.solver.greybay.impl.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    }
    try (var resumed = StudyStore.resume(path, MANIFEST)) {
      assertThat(resumed.records()).hasSize(1);
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
  void corruptedCheckpointIsPreservedAndRecoveredFromJournaledState() throws Exception {
    var path = temporaryDirectory.resolve("study");
    try (var store = StudyStore.create(path, MANIFEST)) {
      store.append("PROPOSED", Map.of("rng", "123"));
      store.checkpoint(Map.of("rng", "123", "reserved", List.of("1")));
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
  void crashAfterCheckpointJournalCommitBeforeReplacementUsesNewestState() throws Exception {
    var path = temporaryDirectory.resolve("study");
    try (var store = StudyStore.create(path, MANIFEST)) {
      store.checkpoint(Map.of("rng", "old"));
      store.append(StudyStore.CHECKPOINT_KIND, Map.of("rng", "new"));
    }
    try (var resumed = StudyStore.resume(path, MANIFEST)) {
      assertThat(resumed.checkpoint().orElseThrow().state()).containsEntry("rng", "new");
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
}
