package greycos.solver.core.impl.score.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.SequencedMap;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.analysis.IndictmentAnalysis;
import greycos.solver.core.api.score.analysis.IndictmentMatchAnalysis;
import greycos.solver.core.impl.score.analysis.IndictmentAnalysisBuilder.MatchKey;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** A detached pair of independently available indictment views. */
@NullMarked
public record IndictmentMaps<Score_ extends Score<Score_>>(
    @Nullable SequencedMap<Object, IndictmentAnalysis<Score_>> terminal,
    @Nullable SequencedMap<Object, IndictmentAnalysis<Score_>> original) {

  private static final IndictmentMaps<?> UNAVAILABLE = new IndictmentMaps<>(null, null);

  public IndictmentMaps {
    terminal = copy(terminal);
    original = copy(original);
  }

  @SuppressWarnings("unchecked")
  public static <Score_ extends Score<Score_>> IndictmentMaps<Score_> unavailable() {
    // Contains no scores or objects, so all score types can share the disabled-path value.
    return (IndictmentMaps<Score_>) UNAVAILABLE;
  }

  private static <Score_ extends Score<Score_>>
      @Nullable SequencedMap<Object, IndictmentAnalysis<Score_>> copy(
          @Nullable SequencedMap<Object, IndictmentAnalysis<Score_>> source) {
    if (source == null) {
      return null;
    }
    var copy = new LinkedHashMap<Object, IndictmentAnalysis<Score_>>(source.size());
    source.forEach(
        (key, value) -> {
          Objects.requireNonNull(key, "indictedObject");
          Objects.requireNonNull(value, "indictmentAnalysis");
          if (!key.equals(value.indictedObject())) {
            throw new IllegalArgumentException(
                "The indictment key (%s) differs from its indicted object (%s)."
                    .formatted(key, value.indictedObject()));
          }
          copy.put(key, value);
        });
    return Collections.unmodifiableSequencedMap(copy);
  }

  public IndictmentMaps<Score_> diff(IndictmentMaps<Score_> other) {
    Objects.requireNonNull(other, "other");
    return new IndictmentMaps<>(
        diffView("terminal", terminal, other.terminal),
        diffView("original", original, other.original));
  }

  private static <Score_ extends Score<Score_>>
      @Nullable SequencedMap<Object, IndictmentAnalysis<Score_>> diffView(
          String view,
          @Nullable SequencedMap<Object, IndictmentAnalysis<Score_>> left,
          @Nullable SequencedMap<Object, IndictmentAnalysis<Score_>> right) {
    if ((left == null) != (right == null)) {
      throw new IllegalStateException(
          "One score analysis provides %s indictments, while the other does not.".formatted(view));
    }
    if (left == null) {
      return null;
    }
    Objects.requireNonNull(right);
    var keys = new LinkedHashSet<>(left.keySet());
    keys.addAll(right.keySet());
    var result = new LinkedHashMap<Object, IndictmentAnalysis<Score_>>();
    for (var object : keys) {
      var leftAnalysis = left.get(object);
      var rightAnalysis = right.get(object);
      var zero =
          leftAnalysis == null
              ? Objects.requireNonNull(rightAnalysis).score().zero()
              : leftAnalysis.score().zero();
      var score =
          (leftAnalysis == null ? zero : leftAnalysis.score())
              .subtract(rightAnalysis == null ? zero : rightAnalysis.score());
      var matchCount =
          Math.subtractExact(
              leftAnalysis == null ? 0 : leftAnalysis.matchCount(),
              rightAnalysis == null ? 0 : rightAnalysis.matchCount());
      var leftMatches = index(leftAnalysis);
      var rightMatches = index(rightAnalysis);
      var matchKeys = new LinkedHashSet<>(leftMatches.keySet());
      matchKeys.addAll(rightMatches.keySet());
      var matches = new ArrayList<IndictmentMatchAnalysis<Score_>>();
      for (var key : matchKeys) {
        var leftMatch = leftMatches.get(key);
        var rightMatch = rightMatches.get(key);
        var matchScore =
            (leftMatch == null ? zero : leftMatch.score())
                .subtract(rightMatch == null ? zero : rightMatch.score());
        var count =
            Math.subtractExact(
                leftMatch == null ? 0 : leftMatch.matchCount(),
                rightMatch == null ? 0 : rightMatch.matchCount());
        if (!matchScore.isZero() || count != 0) {
          matches.add(
              new DefaultIndictmentMatchAnalysis<>(
                  key.constraintRef(), matchScore, key.justification(), count));
        }
      }
      if (!score.isZero() || matchCount != 0 || !matches.isEmpty()) {
        result.put(object, new DefaultIndictmentAnalysis<>(object, score, matches, matchCount));
      }
    }
    return result;
  }

  private static <Score_ extends Score<Score_>>
      SequencedMap<MatchKey, IndictmentMatchAnalysis<Score_>> index(
          @Nullable IndictmentAnalysis<Score_> analysis) {
    var result = new LinkedHashMap<MatchKey, IndictmentMatchAnalysis<Score_>>();
    if (analysis != null) {
      for (var match : analysis.matches()) {
        var key = new MatchKey(match.constraintRef(), match.justification());
        if (result.put(key, match) != null) {
          throw new IllegalStateException(
              "Multiple indictment matches for object (%s) and constraint (%s) have the same justification (%s)."
                  .formatted(analysis.indictedObject(), key.constraintRef(), key.justification()));
        }
      }
    }
    return result;
  }
}
