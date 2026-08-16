package greycos.solver.core.impl.score.analysis;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.stream.Stream;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.analysis.ConstraintAnalysis;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.score.stream.ConstraintRef;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class DefaultScoreAnalysis<Score_ extends Score<Score_>>
    implements ScoreAnalysis<Score_> {

  static final int DEFAULT_SUMMARY_MATCH_LIMIT = 3;

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static final Comparator<ConstraintAnalysis<?>> REVERSED_WEIGHT_COMPARATOR =
      Comparator.<ConstraintAnalysis<?>, Score>comparing(ConstraintAnalysis::weight).reversed();

  private static final Comparator<ConstraintAnalysis<?>> CONSTRAINT_COMPARATOR =
      REVERSED_WEIGHT_COMPARATOR.thenComparing(ConstraintAnalysis::constraintRef);

  private final Score_ score;
  private final SequencedMap<ConstraintRef, ConstraintAnalysis<Score_>> constraintMap;
  private final boolean solutionInitialized;

  public DefaultScoreAnalysis(
      Score_ score, Map<ConstraintRef, ConstraintAnalysis<Score_>> constraintMap) {
    this(score, constraintMap, true);
  }

  public DefaultScoreAnalysis(
      Score_ score,
      Map<ConstraintRef, ConstraintAnalysis<Score_>> constraintMap,
      boolean solutionInitialized) {
    this.score = Objects.requireNonNull(score, "score");
    Objects.requireNonNull(constraintMap, "constraintMap");
    var sortedMap =
        new LinkedHashMap<ConstraintRef, ConstraintAnalysis<Score_>>(constraintMap.size());
    constraintMap.values().stream()
        .sorted(CONSTRAINT_COMPARATOR)
        .forEach(analysis -> sortedMap.put(analysis.constraintRef(), analysis));
    this.constraintMap = Collections.unmodifiableSequencedMap(sortedMap);
    this.solutionInitialized = solutionInitialized;
  }

  @Override
  public Score_ score() {
    return score;
  }

  @Override
  public SequencedMap<ConstraintRef, ConstraintAnalysis<Score_>> constraintMap() {
    return constraintMap;
  }

  @Override
  public boolean isSolutionInitialized() {
    return solutionInitialized;
  }

  @Override
  public @Nullable ConstraintAnalysis<Score_> getConstraintAnalysis(ConstraintRef constraintRef) {
    return constraintMap.get(constraintRef);
  }

  @Override
  public @Nullable ConstraintAnalysis<Score_> getConstraintAnalysis(String constraintId) {
    return constraintMap.get(ConstraintRef.of(constraintId));
  }

  @Override
  public ScoreAnalysis<Score_> diff(ScoreAnalysis<Score_> other) {
    Objects.requireNonNull(other, "other");
    var result = new LinkedHashMap<ConstraintRef, ConstraintAnalysis<Score_>>();
    Stream.concat(constraintMap.keySet().stream(), other.constraintMap().keySet().stream())
        .distinct()
        .forEach(
            constraintRef -> {
              var difference =
                  DefaultConstraintAnalysis.diff(
                      constraintRef,
                      getConstraintAnalysis(constraintRef),
                      other.getConstraintAnalysis(constraintRef));
              if (!difference.weight().isZero() || !difference.score().isZero()) {
                result.put(constraintRef, difference);
              } else if (difference.matches() == null) {
                if (difference.matchCount() != 0) {
                  result.put(constraintRef, difference);
                }
              } else if (!difference.matches().isEmpty()) {
                result.put(constraintRef, difference);
              }
            });
    return new DefaultScoreAnalysis<>(score.subtract(other.score()), result, solutionInitialized);
  }

  @Override
  public Collection<ConstraintAnalysis<Score_>> constraintAnalyses() {
    return constraintMap.values();
  }

  @Override
  public String summarize() {
    return summarize(DEFAULT_SUMMARY_MATCH_LIMIT);
  }

  @Override
  public String summarize(int topLimit) {
    if (topLimit < 1) {
      throw new IllegalArgumentException(
          "The topLimit (%d) must be at least 1.".formatted(topLimit));
    }
    var summary = new StringBuilder();
    summary.append(
        """
            Explanation of score (%s):
                Constraint matches:
            """
            .formatted(score));
    constraintAnalyses().stream()
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
                    "%8s%s: constraint (%s) has no matches.\n"
                        .formatted(
                            " ", constraint.score().toShortString(), constraint.constraintId()));
              } else {
                summary.append(
                    "%8s%s: constraint (%s) has %d matches:\n"
                        .formatted(
                            " ",
                            constraint.score().toShortString(),
                            constraint.constraintId(),
                            matches.size()));
              }
              matches.stream()
                  .sorted(Comparator.comparing(match -> match.score()))
                  .limit(topLimit)
                  .forEach(
                      match ->
                          summary.append(
                              "%12s%s: justified with (%s)\n"
                                  .formatted(
                                      " ", match.score().toShortString(), match.justification())));
              if (matches.size() > topLimit) {
                summary.append(
                    "%12s... and %d more matches\n".formatted(" ", matches.size() - topLimit));
              }
            });
    return summary.toString();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof DefaultScoreAnalysis<?> analysis
        && score.equals(analysis.score)
        && constraintMap.equals(analysis.constraintMap)
        && solutionInitialized == analysis.solutionInitialized;
  }

  @Override
  public int hashCode() {
    return Objects.hash(score, constraintMap, solutionInitialized);
  }

  @Override
  public String toString() {
    return "Score analysis of score %s with %d constraints.".formatted(score, constraintMap.size());
  }
}
