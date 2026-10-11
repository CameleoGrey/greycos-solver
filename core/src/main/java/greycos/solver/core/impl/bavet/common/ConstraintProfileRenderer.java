package greycos.solver.core.impl.bavet.common;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import greycos.solver.core.api.score.profile.ConstraintProfile;
import greycos.solver.core.api.score.profile.ConstraintProfileSection;
import greycos.solver.core.api.score.profile.ConstraintProfileSourceLocation;
import greycos.solver.core.api.score.stream.ConstraintRef;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** Renders a frozen run without revisiting its live scoring graph. */
@NullMarked
final class ConstraintProfileRenderer {

  private ConstraintProfileRenderer() {}

  static void report(ConstraintProfile profile, Logger logger) {
    if (logger.isInfoEnabled()) {
      logger.info(renderSummary(profile));
    }
    if (logger.isDebugEnabled()) {
      logger.debug(renderMetadata(profile));
    }
    if (logger.isTraceEnabled()) {
      logger.trace(renderMeasurements(profile));
    }
  }

  private static String renderSummary(ConstraintProfile profile) {
    var report = new StringBuilder("Constraint stream profiling summary: outcome ");
    report.append(profile.outcome()).append('.');
    appendSection(report, "Initialization", profile.initialization());
    appendSection(report, "Search", profile.search());
    appendSection(report, "Combined", profile.combined());
    return report.toString();
  }

  private static void appendSection(
      StringBuilder report, String label, ConstraintProfileSection section) {
    report
        .append('\n')
        .append(label)
        .append(" score work: wall ")
        .append(formatMillis(section.scoringWallNanos()))
        .append("; measured ")
        .append(formatMillis(section.measuredNanos()))
        .append("; unmeasured ")
        .append(formatMillis(section.unmeasuredNanos()))
        .append("; coverage ")
        .append(formatPercentage(section.coverage()))
        .append("; calls ")
        .append(section.eventCount())
        .append("; failures ")
        .append(section.failureCount())
        .append('.');
    report.append(
        "\n  Recorded owners | Exclusive time | Share of measured work | Calls | Failures | Average | Partial invocations");
    if (section.buckets().isEmpty()) {
      report.append("\n  (no measured work)");
    }
    for (var bucket : section.buckets()) {
      report
          .append("\n  ")
          .append(formatOwners(bucket.owners()))
          .append(" | ")
          .append(formatMillis(bucket.exclusiveNanos()))
          .append(" | ")
          .append(formatPercentage(bucket.measuredShare()))
          .append(" | ")
          .append(bucket.eventCount())
          .append(" | ")
          .append(bucket.failureCount())
          .append(" | ")
          .append(formatAverage(bucket.averageNanosPerCall()))
          .append(" | ")
          .append(bucket.partialInvocations());
    }
  }

  private static String renderMetadata(ConstraintProfile profile) {
    var report = new StringBuilder("Constraint stream profiling metadata:");
    for (var generation : profile.generations()) {
      report
          .append("\n  Generation ")
          .append(generation.generationId())
          .append(": initialization completed ")
          .append(generation.initializationCompleted())
          .append('.');
      for (var epoch : generation.ownershipEpochs()) {
        report
            .append("\n    Ownership epoch ")
            .append(epoch.ownershipEpoch())
            .append(": ")
            .append(epoch.ownershipState())
            .append('.');
      }
      for (var constraint : generation.constraints()) {
        report
            .append("\n    Constraint ")
            .append(escapeLabel(constraint.constraintRef().id()))
            .append(": ")
            .append(constraint.activity())
            .append('.');
      }
    }
    for (var unit : profile.units()) {
      report
          .append("\n  Unit ")
          .append(unit.unitId())
          .append(" (generation ")
          .append(unit.generationId())
          .append("): kind ")
          .append(escapeLabel(unit.kind()))
          .append("; role ")
          .append(unit.role())
          .append("; locations ")
          .append(formatLocations(unit.sourceLocations()))
          .append('.');
      var ownershipDescriptions = new LinkedHashSet<String>();
      for (var measurement : unit.measurements()) {
        ownershipDescriptions.add(
            "\n    Ownership epoch "
                + measurement.ownershipEpoch()
                + ": "
                + measurement.ownershipState()
                + "; recorded owners "
                + formatOwners(measurement.owners())
                + '.');
      }
      ownershipDescriptions.forEach(report::append);
    }
    return report.toString();
  }

  private static String renderMeasurements(ConstraintProfile profile) {
    var report = new StringBuilder("Constraint stream profiling measurements:");
    report.append(
        "\n  Generation | Unit | Section | Ownership epoch | Ownership state | Recorded owners | Operation | Exclusive time (ns) | Calls | Failures | Partial invocations");
    for (var unit : profile.units()) {
      for (var measurement : unit.measurements()) {
        report
            .append("\n  ")
            .append(unit.generationId())
            .append(" | ")
            .append(unit.unitId())
            .append(" | ")
            .append(measurement.stage())
            .append(" | ")
            .append(measurement.ownershipEpoch())
            .append(" | ")
            .append(measurement.ownershipState())
            .append(" | ")
            .append(formatOwners(measurement.owners()))
            .append(" | ")
            .append(measurement.operation())
            .append(" | ")
            .append(measurement.exclusiveNanos())
            .append(" | ")
            .append(measurement.eventCount())
            .append(" | ")
            .append(measurement.failureCount())
            .append(" | ")
            .append(measurement.partialInvocations());
      }
    }
    return report.toString();
  }

  private static String formatOwners(List<ConstraintRef> owners) {
    if (owners.isEmpty()) {
      return "Unattributed";
    }
    var labels =
        owners.stream().map(owner -> escapeLabel(owner.id())).collect(Collectors.joining(", "));
    return owners.size() == 1 ? labels : "Shared: " + labels;
  }

  private static String formatLocations(List<ConstraintProfileSourceLocation> locations) {
    if (locations.isEmpty()) {
      return "n/a";
    }
    return locations.stream()
        .map(
            location ->
                escapeLabel(location.className())
                    + '#'
                    + escapeLabel(location.methodName())
                    + ':'
                    + (location.lineNumber() == null ? "n/a" : location.lineNumber()))
        .collect(Collectors.joining(", "));
  }

  private static String escapeLabel(String label) {
    return label
        .replace("\\", "\\\\")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
        .replace("|", "\\|");
  }

  private static String formatMillis(long nanos) {
    return String.format(Locale.ROOT, "%.3f ms", nanos / 1_000_000.0);
  }

  private static String formatPercentage(@Nullable Double fraction) {
    return fraction == null ? "n/a" : String.format(Locale.ROOT, "%.2f%%", fraction * 100.0);
  }

  private static String formatAverage(@Nullable Double nanos) {
    return nanos == null ? "n/a" : String.format(Locale.ROOT, "%.3f ns", nanos);
  }
}
