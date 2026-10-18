package greycos.solver.core.impl.score.analysis;

import static java.util.Comparator.comparing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.analysis.ConstraintAnalysis;
import greycos.solver.core.api.score.analysis.MatchAnalysis;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record DefaultConstraintAnalysis<Score_ extends Score<Score_>>(
    ConstraintRef constraintRef,
    Score_ weight,
    Score_ score,
    @Nullable List<MatchAnalysis<Score_>> matches,
    int matchCount,
    @Nullable Long rawMatchCount,
    boolean isMatchCountAvailable)
    implements ConstraintAnalysis<Score_> {

  public DefaultConstraintAnalysis(
      ConstraintRef constraintRef,
      Score_ weight,
      Score_ score,
      @Nullable List<MatchAnalysis<Score_>> matches) {
    this(constraintRef, weight, score, matches, matches == null ? -1 : matches.size());
  }

  public DefaultConstraintAnalysis(
      ConstraintRef constraintRef,
      Score_ weight,
      Score_ score,
      @Nullable List<MatchAnalysis<Score_>> matches,
      int matchCount) {
    this(constraintRef, weight, score, matches, matchCount, null);
  }

  public DefaultConstraintAnalysis(
      ConstraintRef constraintRef,
      Score_ weight,
      Score_ score,
      @Nullable List<MatchAnalysis<Score_>> matches,
      int matchCount,
      @Nullable Long rawMatchCount) {
    this(
        constraintRef,
        weight,
        score,
        matches,
        matchCount,
        rawMatchCount,
        matches != null || matchCount != -1);
  }

  public DefaultConstraintAnalysis {
    Objects.requireNonNull(constraintRef, "constraintRef");
    Objects.requireNonNull(weight, "weight");
    Objects.requireNonNull(score, "score");
    matches = matches == null ? null : List.copyOf(matches);
    if (!isMatchCountAvailable && (matches != null || matchCount != -1)) {
      throw new IllegalArgumentException(
          "The constraint (%s) has an unavailable match count, but matches (%s) must be null and matchCount (%d) must be -1."
              .formatted(constraintRef, matches, matchCount));
    }
  }

  DefaultConstraintAnalysis<Score_> negate() {
    var negatedMatchCount = isMatchCountAvailable ? Math.negateExact(matchCount) : -1;
    Long negatedRawMatchCount = rawMatchCount == null ? null : Math.negateExact(rawMatchCount);
    if (matches == null) {
      return new DefaultConstraintAnalysis<>(
          constraintRef,
          weight.negate(),
          score.negate(),
          null,
          negatedMatchCount,
          negatedRawMatchCount,
          isMatchCountAvailable);
    }
    var negatedMatches =
        matches.stream()
            .map(
                match ->
                    new DefaultMatchAnalysis<>(
                        match.constraintRef(), match.score().negate(), match.justification()))
            .map(match -> (MatchAnalysis<Score_>) match)
            .toList();
    return new DefaultConstraintAnalysis<>(
        constraintRef,
        weight.negate(),
        score.negate(),
        negatedMatches,
        negatedMatchCount,
        negatedRawMatchCount,
        isMatchCountAvailable);
  }

  static <Score_ extends Score<Score_>> DefaultConstraintAnalysis<Score_> diff(
      ConstraintRef constraintRef,
      @Nullable ConstraintAnalysis<Score_> analysis,
      @Nullable ConstraintAnalysis<Score_> otherAnalysis) {
    if (analysis == null) {
      if (otherAnalysis == null) {
        throw new IllegalStateException(
            "Impossible state: neither score analysis contains constraint (%s)."
                .formatted(constraintRef));
      }
      return negate(otherAnalysis);
    } else if (otherAnalysis == null) {
      return copyOf(analysis);
    }

    var matches = analysis.matches();
    var otherMatches = otherAnalysis.matches();
    if ((matches == null) != (otherMatches == null)) {
      throw new IllegalStateException(
          "One score analysis provides matches for constraint (%s), while the other does not."
              .formatted(constraintRef));
    }
    var hasCount = analysis.isMatchCountAvailable();
    if (hasCount != otherAnalysis.isMatchCountAvailable()) {
      throw new IllegalStateException(
          "One score analysis provides a match count for constraint (%s), while the other does not."
              .formatted(constraintRef));
    }
    var countDifference =
        hasCount ? Math.subtractExact(analysis.matchCount(), otherAnalysis.matchCount()) : -1;

    var weightDifference = analysis.weight().subtract(otherAnalysis.weight());
    var scoreDifference = analysis.score().subtract(otherAnalysis.score());
    Long rawCountDifference =
        analysis.rawMatchCount() == null || otherAnalysis.rawMatchCount() == null
            ? null
            : Math.subtractExact(analysis.rawMatchCount(), otherAnalysis.rawMatchCount());
    if (matches == null) {
      return new DefaultConstraintAnalysis<>(
          constraintRef,
          weightDifference,
          scoreDifference,
          null,
          countDifference,
          rawCountDifference,
          hasCount);
    }

    var matchMap = mapMatchesToJustifications(matches);
    var otherMatchMap = mapMatchesToJustifications(Objects.requireNonNull(otherMatches));
    var matchDifference =
        Stream.concat(matchMap.keySet().stream(), otherMatchMap.keySet().stream())
            .distinct()
            .flatMap(
                justification -> {
                  var match = matchMap.get(justification);
                  var otherMatch = otherMatchMap.get(justification);
                  if (match == null) {
                    return Stream.of(
                        new DefaultMatchAnalysis<>(
                            constraintRef,
                            Objects.requireNonNull(otherMatch).score().negate(),
                            justification));
                  } else if (otherMatch == null) {
                    return Stream.of(
                        new DefaultMatchAnalysis<>(constraintRef, match.score(), justification));
                  }
                  var scoreDiff = match.score().subtract(otherMatch.score());
                  return scoreDiff.isZero()
                      ? Stream.<DefaultMatchAnalysis<Score_>>empty()
                      : Stream.of(
                          new DefaultMatchAnalysis<>(constraintRef, scoreDiff, justification));
                })
            .map(match -> (MatchAnalysis<Score_>) match)
            .toList();
    return new DefaultConstraintAnalysis<>(
        constraintRef,
        weightDifference,
        scoreDifference,
        matchDifference,
        countDifference,
        rawCountDifference,
        hasCount);
  }

  @SuppressWarnings("unchecked")
  private static <Score_ extends Score<Score_>> DefaultConstraintAnalysis<Score_> copyOf(
      ConstraintAnalysis<Score_> analysis) {
    if (analysis instanceof DefaultConstraintAnalysis<?> defaultAnalysis) {
      return (DefaultConstraintAnalysis<Score_>) defaultAnalysis;
    }
    return new DefaultConstraintAnalysis<>(
        analysis.constraintRef(),
        analysis.weight(),
        analysis.score(),
        analysis.matches(),
        analysis.matchCount(),
        analysis.rawMatchCount(),
        analysis.isMatchCountAvailable());
  }

  private static <Score_ extends Score<Score_>> DefaultConstraintAnalysis<Score_> negate(
      ConstraintAnalysis<Score_> analysis) {
    return copyOf(analysis).negate();
  }

  private static <Score_ extends Score<Score_>>
      Map<ConstraintJustification, MatchAnalysis<Score_>> mapMatchesToJustifications(
          List<MatchAnalysis<Score_>> matchAnalyses) {
    var matchMap =
        new LinkedHashMap<ConstraintJustification, MatchAnalysis<Score_>>(matchAnalyses.size());
    for (var matchAnalysis : matchAnalyses) {
      var previous = matchMap.put(matchAnalysis.justification(), matchAnalysis);
      if (previous != null) {
        throw new IllegalStateException(
            "Multiple matches for constraint (%s) have the same justification (%s)."
                .formatted(matchAnalysis.constraintRef(), matchAnalysis.justification()));
      }
    }
    return matchMap;
  }

  @Override
  public String summarize() {
    return summarize(DefaultScoreAnalysis.DEFAULT_SUMMARY_MATCH_LIMIT);
  }

  @Override
  public String summarize(int topLimit) {
    if (topLimit < 1) {
      throw new IllegalArgumentException(
          "The topLimit (%d) must be at least 1.".formatted(topLimit));
    }
    var constraintMatches = matches;
    if (constraintMatches == null) {
      throw new IllegalStateException(
          "Constraint matches are unavailable. Request ScoreAnalysisFetchPolicy.FETCH_ALL.");
    }
    var summary = new StringBuilder();
    summary.append(
        """
            Explanation of score (%s):
                Constraint matches:
            """
            .formatted(score));
    if (constraintMatches.isEmpty()) {
      summary.append(
          "%8s%s: constraint (%s) has no matches.\n"
              .formatted(" ", score.toShortString(), constraintRef.id()));
    } else {
      summary.append(
          "%8s%s: constraint (%s) has %d matches:\n"
              .formatted(" ", score.toShortString(), constraintRef.id(), constraintMatches.size()));
    }
    constraintMatches.stream()
        .sorted(comparing(MatchAnalysis::score))
        .limit(topLimit)
        .forEach(
            match ->
                summary.append(
                    "%12s%s: justified with (%s)\n"
                        .formatted(" ", match.score().toShortString(), match.justification())));
    if (constraintMatches.size() > topLimit) {
      summary.append(
          "%12s... and %d more matches\n".formatted(" ", constraintMatches.size() - topLimit));
    }
    return summary.toString();
  }

  @Override
  public String toString() {
    if (matches == null) {
      return !isMatchCountAvailable
          ? "(%s at %s, constraint matching disabled)".formatted(score, weight)
          : "(%s at %s, %d matches, justifications disabled)".formatted(score, weight, matchCount);
    }
    return "(%s at %s, %d matches with justifications)".formatted(score, weight, matches.size());
  }
}
