package greycos.solver.core.impl.bavet.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;

import greycos.solver.core.api.score.profile.ConstraintProfile;
import greycos.solver.core.testdata.profile.ConstraintProfileTestData;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class ConstraintProfileRendererTest {

  @Test
  void summaryReportsEachExactOwnerBucketOncePerSection() {
    var events = capture(ConstraintProfileTestData.populated(), Level.INFO);

    assertThat(events).hasSize(1);
    assertThat(events.getFirst().getLoggerName())
        .isEqualTo(DefaultConstraintProfiler.class.getName());
    assertThat(events.getFirst().getFormattedMessage())
        .isEqualTo(
            """
            Constraint stream profiling summary: outcome COMPLETED.
            Initialization score work: wall 0.000 ms; measured 0.000 ms; unmeasured 0.000 ms; coverage 50.00%; calls 5; failures 0.
              Recorded owners | Exclusive time | Share of measured work | Calls | Failures | Average | Partial invocations
              Shared: A's constraint, B constraint | 0.000 ms | 60.00% | 3 | 0 | 10.000 ns | false
              Unattributed | 0.000 ms | 20.00% | 1 | 0 | 10.000 ns | false
              A's constraint | 0.000 ms | 20.00% | 1 | 0 | 10.000 ns | false
            Search score work: wall 0.000 ms; measured 0.000 ms; unmeasured 0.000 ms; coverage 50.00%; calls 5; failures 0.
              Recorded owners | Exclusive time | Share of measured work | Calls | Failures | Average | Partial invocations
              Shared: A's constraint, B constraint | 0.000 ms | 60.00% | 3 | 0 | 10.000 ns | false
              A's constraint | 0.000 ms | 40.00% | 2 | 0 | 10.000 ns | false
            Combined score work: wall 0.000 ms; measured 0.000 ms; unmeasured 0.000 ms; coverage 50.00%; calls 10; failures 0.
              Recorded owners | Exclusive time | Share of measured work | Calls | Failures | Average | Partial invocations
              Shared: A's constraint, B constraint | 0.000 ms | 60.00% | 6 | 0 | 10.000 ns | false
              A's constraint | 0.000 ms | 30.00% | 3 | 0 | 10.000 ns | false
              Unattributed | 0.000 ms | 10.00% | 1 | 0 | 10.000 ns | false""");
  }

  @Test
  void metadataIncludesGenerationActivityRolesLocationsAndHistoricalOwnership() {
    var events = capture(ConstraintProfileTestData.populated(), Level.DEBUG);

    assertThat(events).extracting(ILoggingEvent::getLevel).containsExactly(Level.INFO, Level.DEBUG);
    assertThat(events.getLast().getFormattedMessage())
        .isEqualTo(
            """
            Constraint stream profiling metadata:
              Generation 4294967296: initialization completed true.
                Ownership epoch 0: ENABLED_CANDIDATES.
                Ownership epoch 1: ACTIVE.
                Constraint A's constraint: ACTIVE.
                Constraint B constraint: ACTIVE.
                Constraint Disabled: DISABLED_WEIGHT.
                Constraint Inactive: INACTIVE.
              Unit 8589934592 (generation 4294967296): kind Root "quoted"\\\\path\\nnext; role ROOT; locations example.Provider#known:42, example.Provider\\\\generated#unknown"\\n\\t:n/a.
                Ownership epoch 0: ENABLED_CANDIDATES; recorded owners Shared: A's constraint, B constraint.
                Ownership epoch 1: ACTIVE; recorded owners A's constraint.
              Unit 8589934593 (generation 4294967296): kind Deferred; role PREPARATION; locations n/a.
                Ownership epoch 1: ACTIVE; recorded owners A's constraint.
              Unit 8589934594 (generation 4294967296): kind Join; role PROPAGATOR; locations n/a.
                Ownership epoch 1: ACTIVE; recorded owners Shared: A's constraint, B constraint.
              Unit 8589934595 (generation 4294967296): kind Input; role LEFT_INPUT; locations n/a.
                Ownership epoch 1: ACTIVE; recorded owners Unattributed.""");
  }

  @Test
  void traceReportsOperationCountersAsNanosecondsWithoutDuplicatingCombinedRows() {
    var events = capture(ConstraintProfileTestData.populated(), Level.TRACE);

    assertThat(events)
        .extracting(ILoggingEvent::getLevel)
        .containsExactly(Level.INFO, Level.DEBUG, Level.TRACE);
    assertThat(events.getLast().getFormattedMessage())
        .isEqualTo(
            """
            Constraint stream profiling measurements:
              Generation | Unit | Section | Ownership epoch | Ownership state | Recorded owners | Operation | Exclusive time (ns) | Calls | Failures | Partial invocations
              4294967296 | 8589934592 | INITIALIZATION | 0 | ENABLED_CANDIDATES | Shared: A's constraint, B constraint | INSERT | 30 | 3 | 0 | false
              4294967296 | 8589934592 | SEARCH | 1 | ACTIVE | A's constraint | UPDATE | 20 | 2 | 0 | false
              4294967296 | 8589934593 | INITIALIZATION | 1 | ACTIVE | A's constraint | PREPARE | 10 | 1 | 0 | false
              4294967296 | 8589934594 | SEARCH | 1 | ACTIVE | Shared: A's constraint, B constraint | RETRACT | 30 | 3 | 0 | false
              4294967296 | 8589934595 | INITIALIZATION | 1 | ACTIVE | Unattributed | INSERT | 10 | 1 | 0 | false""");
  }

  @Test
  void partialInvocationsKeepSectionAveragesUnavailableAndCombinedAverageComplete() {
    var events = capture(ConstraintProfileTestData.failedPartial(), Level.TRACE);

    assertThat(events.getFirst().getFormattedMessage())
        .contains(
            "outcome FAILED.",
            "coverage 60.00%; calls 1; failures 1.",
            "A's constraint | 0.000 ms | 66.67% | 0 | 0 | n/a | true",
            "A's constraint | 0.000 ms | 100.00% | 1 | 1 | n/a | true",
            "A's constraint | 0.000 ms | 83.33% | 1 | 1 | 20.000 ns | false");
    assertThat(events.get(1).getFormattedMessage())
        .contains(
            "Generation 4294967297: initialization completed false.",
            "Ownership epoch 0: UNRESOLVED.",
            "Constraint A's constraint: UNRESOLVED.",
            "role RIGHT_INPUT; locations n/a.",
            "role LIFECYCLE; locations n/a.");
    assertThat(events.getLast().getFormattedMessage())
        .contains(
            "INITIALIZATION | 1 | ACTIVE | A's constraint | UPDATE | 8 | 0 | 0 | true",
            "SEARCH | 1 | ACTIVE | A's constraint | UPDATE | 12 | 1 | 1 | true",
            "INITIALIZATION | 0 | UNRESOLVED | Unattributed | INSERT | 4 | 1 | 1 | false");
  }

  @Test
  void emptyProfileReportsUnavailableCoverageAndEmptyTables() {
    var events = capture(ConstraintProfileTestData.empty(), Level.INFO);

    assertThat(events.getFirst().getFormattedMessage())
        .contains("outcome COMPLETED.")
        .doesNotContain("NaN", "Infinity")
        .containsPattern("Initialization score work: .* coverage n/a; calls 0; failures 0\\.")
        .containsPattern("Search score work: .* coverage n/a; calls 0; failures 0\\.")
        .containsPattern("Combined score work: .* coverage n/a; calls 0; failures 0\\.");
    assertThat(
            events
                .getFirst()
                .getFormattedMessage()
                .lines()
                .filter(line -> line.equals("  (no measured work)")))
        .hasSize(3);
  }

  @Test
  void zeroDurationRetainsLargeCountsAndUnavailableMeasuredShare() {
    var events = capture(ConstraintProfileTestData.zeroDuration(), Level.INFO);

    assertThat(events.getFirst().getFormattedMessage())
        .contains(
            "outcome TERMINATED_EARLY.",
            "coverage n/a; calls 3000000001; failures 0.",
            "A's constraint | 0.000 ms | n/a | 3000000001 | 0 | 0.000 ns | false")
        .doesNotContain("NaN", "Infinity");
  }

  @Test
  @ResourceLock(Resources.LOCALE)
  void renderingDoesNotDependOnDefaultLocale() {
    var profile = ConstraintProfileTestData.populated();
    var previousLocale = Locale.getDefault(Locale.Category.FORMAT);
    try {
      Locale.setDefault(Locale.Category.FORMAT, Locale.US);
      var english = capture(profile, Level.TRACE);
      Locale.setDefault(Locale.Category.FORMAT, Locale.GERMANY);
      var german = capture(profile, Level.TRACE);

      assertThat(german)
          .extracting(ILoggingEvent::getFormattedMessage)
          .containsExactlyElementsOf(
              english.stream().map(ILoggingEvent::getFormattedMessage).toList());
    } finally {
      Locale.setDefault(Locale.Category.FORMAT, previousLocale);
    }
  }

  @Test
  void disabledLevelsDoNotReadOrFormatTheProfile() {
    var profile = mock(ConstraintProfile.class);
    for (var level : List.of(Level.WARN, Level.ERROR, Level.OFF)) {
      assertThat(capture(profile, level)).isEmpty();
    }
    verifyNoInteractions(profile);
  }

  @Test
  void infoDoesNotReadMetadataOrMeasurementRows() {
    var profile = mock(ConstraintProfile.class);
    var empty = ConstraintProfileTestData.empty();
    when(profile.outcome()).thenReturn(empty.outcome());
    when(profile.initialization()).thenReturn(empty.initialization());
    when(profile.search()).thenReturn(empty.search());
    when(profile.combined()).thenReturn(empty.combined());

    assertThat(capture(profile, Level.INFO)).hasSize(1);

    verify(profile, never()).generations();
    verify(profile, never()).units();
  }

  private static List<ILoggingEvent> capture(ConstraintProfile profile, Level level) {
    try (var capture = new LogCapture(level)) {
      ConstraintProfileRenderer.report(profile, capture.logger);
      return List.copyOf(capture.appender.list);
    }
  }

  private static final class LogCapture implements AutoCloseable {
    private final LoggerContext context = new LoggerContext();
    private final Logger logger = context.getLogger(DefaultConstraintProfiler.class.getName());
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(Level level) {
      appender.setContext(context);
      appender.start();
      logger.setAdditive(false);
      logger.setLevel(level);
      logger.addAppender(appender);
    }

    @Override
    public void close() {
      context.stop();
    }
  }
}
