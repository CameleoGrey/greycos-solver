package greycos.solver.core.impl.score.analysis;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.stream.Stream;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.analysis.ConstraintAnalysis;
import greycos.solver.core.api.score.analysis.IndictmentAnalysis;
import greycos.solver.core.api.score.analysis.RawScoreAnalysis;
import greycos.solver.core.api.score.analysis.ScoreAnalysis;
import greycos.solver.core.api.score.analysis.StructuralFlawAnalysis;
import greycos.solver.core.api.score.analysis.VariableLoop;
import greycos.solver.core.api.score.stream.ConstraintRef;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class DefaultScoreAnalysis<Score_ extends Score<Score_>>
    implements ScoreAnalysis<Score_> {

  static final int DEFAULT_SUMMARY_MATCH_LIMIT = 3;
  private static final int DEFAULT_SUMMARY_INDICTMENT_LIMIT = 5;

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static final Comparator<ConstraintAnalysis<?>> REVERSED_WEIGHT_COMPARATOR =
      Comparator.<ConstraintAnalysis<?>, Score>comparing(ConstraintAnalysis::weight).reversed();

  private static final Comparator<ConstraintAnalysis<?>> CONSTRAINT_COMPARATOR =
      REVERSED_WEIGHT_COMPARATOR.thenComparing(ConstraintAnalysis::constraintRef);

  private final Score_ score;
  private final SequencedMap<ConstraintRef, ConstraintAnalysis<Score_>> constraintMap;
  private final boolean solutionInitialized;
  private final @Nullable StructuralFlawAnalysis structuralFlawAnalysis;
  private final IndictmentMaps<Score_> indictmentMaps;
  private final @Nullable RawScoreAnalysis<Score_> rawAnalysis;

  public DefaultScoreAnalysis(
      Score_ score, Map<ConstraintRef, ConstraintAnalysis<Score_>> constraintMap) {
    this(score, constraintMap, true);
  }

  public DefaultScoreAnalysis(
      Score_ score,
      Map<ConstraintRef, ConstraintAnalysis<Score_>> constraintMap,
      boolean solutionInitialized) {
    this(score, constraintMap, solutionInitialized, List.of());
  }

  public DefaultScoreAnalysis(
      Score_ score,
      Map<ConstraintRef, ConstraintAnalysis<Score_>> constraintMap,
      boolean solutionInitialized,
      List<VariableLoop> variableLoops) {
    this(score, constraintMap, solutionInitialized, variableLoops, IndictmentMaps.unavailable());
  }

  public DefaultScoreAnalysis(
      Score_ score,
      Map<ConstraintRef, ConstraintAnalysis<Score_>> constraintMap,
      boolean solutionInitialized,
      List<VariableLoop> variableLoops,
      IndictmentMaps<Score_> indictmentMaps) {
    this(score, constraintMap, solutionInitialized, variableLoops, indictmentMaps, null);
  }

  public DefaultScoreAnalysis(
      Score_ score,
      Map<ConstraintRef, ConstraintAnalysis<Score_>> constraintMap,
      boolean solutionInitialized,
      List<VariableLoop> variableLoops,
      IndictmentMaps<Score_> indictmentMaps,
      @Nullable RawScoreAnalysis<Score_> rawAnalysis) {
    this.score = Objects.requireNonNull(score, "score");
    this.indictmentMaps = Objects.requireNonNull(indictmentMaps, "indictmentMaps");
    this.rawAnalysis = rawAnalysis;
    var loops = List.copyOf(variableLoops);
    this.structuralFlawAnalysis =
        score.structuralScore() < 0 || !loops.isEmpty()
            ? new DefaultStructuralFlawAnalysis(loops)
            : null;
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
  public @Nullable RawScoreAnalysis<Score_> rawAnalysis() {
    return rawAnalysis;
  }

  @Override
  public @Nullable SequencedMap<Object, IndictmentAnalysis<Score_>> indictmentMap() {
    return indictmentMaps.terminal();
  }

  @Override
  public @Nullable SequencedMap<Object, IndictmentAnalysis<Score_>> originalIndictmentMap() {
    return indictmentMaps.original();
  }

  @Override
  public boolean isSolutionInitialized() {
    return solutionInitialized;
  }

  @Override
  public boolean isSolutionStructurallyFlawed() {
    return structuralFlawAnalysis != null;
  }

  @Override
  public @Nullable StructuralFlawAnalysis structuralFlawAnalysis() {
    return structuralFlawAnalysis;
  }

  private record DefaultStructuralFlawAnalysis(List<VariableLoop> getVariableLoops)
      implements StructuralFlawAnalysis {}

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
    var rawDifference = DefaultRawScoreAnalysis.diff(rawAnalysis, other.rawAnalysis());
    var indictmentDifference =
        indictmentMaps.diff(
            new IndictmentMaps<>(other.indictmentMap(), other.originalIndictmentMap()));
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
              if (rawDifference != null) {
                long rawCount =
                    rawDifference.constraintMap().getOrDefault(constraintRef, List.of()).stream()
                        .mapToLong(occurrence -> occurrence.occurrenceSign())
                        .sum();
                difference =
                    new DefaultConstraintAnalysis<>(
                        difference.constraintRef(),
                        difference.weight(),
                        difference.score(),
                        difference.matches(),
                        difference.matchCount(),
                        rawCount,
                        difference.isMatchCountAvailable());
              }
              if (!difference.weight().isZero() || !difference.score().isZero()) {
                result.put(constraintRef, difference);
              } else if (difference.matches() == null) {
                if (difference.isMatchCountAvailable() && difference.matchCount() != 0) {
                  result.put(constraintRef, difference);
                }
              } else if (!difference.matches().isEmpty()) {
                result.put(constraintRef, difference);
              } else if (rawDifference != null
                  && rawDifference.constraintMap().containsKey(constraintRef)) {
                result.put(constraintRef, difference);
              }
            });
    return new DefaultScoreAnalysis<>(
        score.subtract(other.score()),
        result,
        solutionInitialized,
        structuralFlawAnalysis == null ? List.of() : structuralFlawAnalysis.getVariableLoops(),
        indictmentDifference,
        rawDifference);
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
    return summarize(topLimit, DEFAULT_SUMMARY_INDICTMENT_LIMIT);
  }

  @Override
  public String summarize(int matchLimit, int indictmentLimit) {
    return ScoreAnalysisSummary.summarize(this, matchLimit, indictmentLimit);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof DefaultScoreAnalysis<?> analysis
        && score.equals(analysis.score)
        && constraintMap.equals(analysis.constraintMap)
        && solutionInitialized == analysis.solutionInitialized
        && Objects.equals(structuralFlawAnalysis, analysis.structuralFlawAnalysis)
        && indictmentMaps.equals(analysis.indictmentMaps)
        && Objects.equals(rawAnalysis, analysis.rawAnalysis);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        score,
        constraintMap,
        solutionInitialized,
        structuralFlawAnalysis,
        indictmentMaps,
        rawAnalysis);
  }

  @Override
  public String toString() {
    return "Score analysis of score %s with %d constraints.".formatted(score, constraintMap.size());
  }
}
