package greycos.solver.core.impl.score.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.SequencedMap;

import greycos.solver.core.api.score.FloatingScoreAccumulator;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.analysis.IndictmentAnalysis;
import greycos.solver.core.api.score.analysis.IndictmentMatchAnalysis;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintRef;
import greycos.solver.core.impl.score.FloatingScoreSupport;
import greycos.solver.core.impl.score.constraint.ConstraintMatch;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Builds detached object-centric analyses from original matching occurrences. */
@NullMarked
public final class IndictmentAnalysisBuilder<Score_ extends Score<Score_>> {

  private final Score_ zero;
  private final SequencedMap<Object, ObjectTotal> matchesByObject = new LinkedHashMap<>();

  public IndictmentAnalysisBuilder(Score_ zero) {
    this.zero = Objects.requireNonNull(zero, "zero");
    if (!zero.isZero()) {
      throw new IllegalArgumentException("The initial score (%s) must be zero.".formatted(zero));
    }
  }

  public void add(ConstraintMatch<Score_> match, Collection<?> selectedObjects) {
    Objects.requireNonNull(match, "match");
    Objects.requireNonNull(selectedObjects, "selectedObjects");
    var distinctObjects = new LinkedHashSet<Object>();
    for (var object : selectedObjects) {
      distinctObjects.add(Objects.requireNonNull(object, "An indicted object must not be null."));
    }
    ConstraintJustification justification =
        Objects.requireNonNull(
            match.getJustification(), "The indicted match must have a justification.");
    var key = new MatchKey(match.getConstraintRef(), justification);
    for (var object : distinctObjects) {
      matchesByObject
          .computeIfAbsent(object, ignored -> new ObjectTotal())
          .add(key, match.getScore());
    }
  }

  public SequencedMap<Object, IndictmentAnalysis<Score_>> build() {
    var result = new LinkedHashMap<Object, IndictmentAnalysis<Score_>>(matchesByObject.size());
    matchesByObject.forEach(
        (object, total) -> {
          var matches = new ArrayList<IndictmentMatchAnalysis<Score_>>(total.groups.size());
          total.groups.forEach(
              (key, group) ->
                  matches.add(
                      new DefaultIndictmentMatchAnalysis<>(
                          key.constraintRef(), group.score(), key.justification(), group.count)));
          result.put(
              object,
              new DefaultIndictmentAnalysis<>(
                  object, total.total.score(), matches, total.total.count));
        });
    return Collections.unmodifiableSequencedMap(result);
  }

  record MatchKey(ConstraintRef constraintRef, ConstraintJustification justification) {}

  private final class ObjectTotal {

    private final ScoreTotal total = new ScoreTotal();
    private final SequencedMap<MatchKey, ScoreTotal> groups = new LinkedHashMap<>();

    private void add(MatchKey key, Score_ contribution) {
      total.add(contribution);
      groups.computeIfAbsent(key, ignored -> new ScoreTotal()).add(contribution);
    }
  }

  /** The same raw-contribution arithmetic as ScoreArithmetic.sum, without retaining occurrences. */
  private final class ScoreTotal {

    private Score_ total = zero;
    private final @Nullable FloatingScoreAccumulator<Score_> floating =
        FloatingScoreSupport.isFloatingScore(zero) ? FloatingScoreAccumulator.create(zero) : null;
    private long count;

    private void add(Score_ contribution) {
      if (floating == null) {
        total = total.add(contribution);
      } else {
        floating.add(contribution);
      }
      count = Math.incrementExact(count);
    }

    private Score_ score() {
      return floating == null ? total : floating.extractScore();
    }
  }
}
