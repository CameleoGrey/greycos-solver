package greycos.solver.core.impl.score.analysis;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.analysis.ConstraintAnalysis;
import greycos.solver.core.api.score.analysis.IndictmentAnalysis;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Shared diagnostic formatting for built-in and custom analysis implementations. */
@NullMarked
public final class ScoreAnalysisSummary {

  private ScoreAnalysisSummary() {}

  public static <Score_ extends Score<Score_>> String summarize(
      ScoreAnalysis<Score_> analysis, int matchLimit, int indictmentLimit) {
    validateLimit("matchLimit", matchLimit);
    validateLimit("indictmentLimit", indictmentLimit);
    var summary = new StringBuilder();
    summary.append(
        """
        Explanation of score (%s):
            Constraint matches:
        """
            .formatted(analysis.score()));
    var structural = analysis.structuralFlawAnalysis();
    if (structural != null) {
      summary.append("    Structural flaws:\n");
      structural
          .getVariableLoops()
          .forEach(loop -> summary.append("        ").append(loop).append('\n'));
    }
    var raw = analysis.rawAnalysis();
    analysis.constraintAnalyses().stream()
        .sorted(Comparator.comparing(ConstraintAnalysis::score))
        .forEach(
            constraint -> {
              var matches = constraint.matches();
              if (matches == null) {
                throw new IllegalStateException(
                    "Constraint matches are unavailable. Request ScoreAnalysisFetchPolicy.FETCH_ALL.");
              }
              if (matches.isEmpty()) {
                summary.append(
                    "%8s%s: constraint (%s) has no %smatches.\n"
                        .formatted(
                            " ",
                            constraint.score().toShortString(),
                            constraint.constraintId(),
                            raw == null ? "" : "grouped "));
              } else {
                summary.append(
                    "%8s%s: constraint (%s) has %d matches:\n"
                        .formatted(
                            " ",
                            constraint.score().toShortString(),
                            constraint.constraintId(),
                            matches.size()));
              }
              if (raw != null) {
                var occurrences =
                    raw.constraintMap().getOrDefault(constraint.constraintRef(), List.of());
                summary.append(
                    "%12s%d raw occurrences (net count %d).\n"
                        .formatted(
                            " ",
                            occurrences.size(),
                            occurrences.stream()
                                .mapToLong(occurrence -> occurrence.occurrenceSign())
                                .sum()));
              }
              matches.stream()
                  .sorted(Comparator.comparing(match -> match.score()))
                  .limit(matchLimit)
                  .forEach(
                      match ->
                          summary.append(
                              "%12s%s: justified with (%s)\n"
                                  .formatted(
                                      " ", match.score().toShortString(), match.justification())));
              if (matches.size() > matchLimit) {
                summary.append(
                    "%12s... and %d more matches\n".formatted(" ", matches.size() - matchLimit));
              }
            });
    appendIndictments(
        summary, "Terminal indictments", analysis.indictmentMap(), matchLimit, indictmentLimit);
    appendIndictments(
        summary,
        "Original indictments",
        analysis.originalIndictmentMap(),
        matchLimit,
        indictmentLimit);
    return summary.toString();
  }

  public static <Score_ extends Score<Score_>> String summarize(
      IndictmentAnalysis<Score_> indictment, int matchLimit) {
    validateLimit("matchLimit", matchLimit);
    var summary = new StringBuilder();
    appendIndictment(summary, indictment, matchLimit, "");
    return summary.toString();
  }

  private static <Score_ extends Score<Score_>> void appendIndictments(
      StringBuilder summary,
      String title,
      @Nullable Map<Object, IndictmentAnalysis<Score_>> indictments,
      int matchLimit,
      int indictmentLimit) {
    if (indictments == null) {
      return;
    }
    summary.append("    ").append(title).append(":\n");
    if (indictments.isEmpty()) {
      summary.append("        No indicted objects.\n");
      return;
    }
    indictments.values().stream()
        .sorted(Comparator.comparing(IndictmentAnalysis::score))
        .limit(indictmentLimit)
        .forEach(indictment -> appendIndictment(summary, indictment, matchLimit, "        "));
    if (indictments.size() > indictmentLimit) {
      summary.append(
          "        ... and %d more indicted objects\n"
              .formatted(indictments.size() - indictmentLimit));
    }
  }

  private static <Score_ extends Score<Score_>> void appendIndictment(
      StringBuilder summary, IndictmentAnalysis<Score_> indictment, int matchLimit, String indent) {
    summary.append(
        "%s%s: indicted object (%s) has %d contributing occurrences in %d match groups:\n"
            .formatted(
                indent,
                indictment.score().toShortString(),
                indictment.indictedObject(),
                indictment.matchCount(),
                indictment.matches().size()));
    indictment.matches().stream()
        .sorted(Comparator.comparing(match -> match.score()))
        .limit(matchLimit)
        .forEach(
            match ->
                summary.append(
                    "%s    %s: constraint (%s), %d occurrences, justified with (%s)\n"
                        .formatted(
                            indent,
                            match.score().toShortString(),
                            match.constraintRef().id(),
                            match.matchCount(),
                            match.justification())));
    if (indictment.matches().size() > matchLimit) {
      summary.append(
          "%s    ... and %d more match groups\n"
              .formatted(indent, indictment.matches().size() - matchLimit));
    }
  }

  private static void validateLimit(String name, int limit) {
    if (limit < 1) {
      throw new IllegalArgumentException("The %s (%d) must be at least 1.".formatted(name, limit));
    }
  }
}
