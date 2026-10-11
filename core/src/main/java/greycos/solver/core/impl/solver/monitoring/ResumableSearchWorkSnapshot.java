package greycos.solver.core.impl.solver.monitoring;

import java.util.EnumMap;
import java.util.Map;

/** Immutable cumulative physical score work, separate from logical search-budget accounting. */
public record ResumableSearchWorkSnapshot(
    long totalScoreCalculationCount,
    Map<ResumableSearchWorkCategory, Long> scoreCalculationCountsByCategory) {

  public static final ResumableSearchWorkSnapshot ZERO =
      new ResumableSearchWorkSnapshot(0L, Map.of());

  public ResumableSearchWorkSnapshot {
    scoreCalculationCountsByCategory = Map.copyOf(scoreCalculationCountsByCategory);
    long categoryTotal = 0L;
    for (var count : scoreCalculationCountsByCategory.values()) {
      if (count < 0L) {
        throw new IllegalArgumentException(
            "Physical score calculation counts must not be negative.");
      }
      categoryTotal = Math.addExact(categoryTotal, count);
    }
    if (categoryTotal != totalScoreCalculationCount) {
      throw new IllegalArgumentException(
          "The total physical score calculation count (%d) differs from its category total (%d)."
              .formatted(totalScoreCalculationCount, categoryTotal));
    }
  }

  public long scoreCalculationCount(ResumableSearchWorkCategory category) {
    return scoreCalculationCountsByCategory.getOrDefault(category, 0L);
  }

  public ResumableSearchWorkSnapshot plus(ResumableSearchWorkSnapshot other) {
    var counts = new EnumMap<ResumableSearchWorkCategory, Long>(ResumableSearchWorkCategory.class);
    counts.putAll(scoreCalculationCountsByCategory);
    other.scoreCalculationCountsByCategory.forEach(
        (category, count) -> counts.merge(category, count, Math::addExact));
    return new ResumableSearchWorkSnapshot(
        Math.addExact(totalScoreCalculationCount, other.totalScoreCalculationCount), counts);
  }
}
