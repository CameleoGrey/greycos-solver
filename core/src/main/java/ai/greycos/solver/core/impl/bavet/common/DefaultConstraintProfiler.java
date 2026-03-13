package ai.greycos.solver.core.impl.bavet.common;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

import ai.greycos.solver.core.api.score.constraint.ConstraintRef;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NullMarked
public final class DefaultConstraintProfiler implements InnerConstraintProfiler {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConstraintProfiler.class);

  private final Map<ConstraintNodeProfileId, Stats> statsByProfileId = new LinkedHashMap<>();
  private final Map<ConstraintRef, Set<ConstraintNodeProfileId>> profilesByConstraintRef =
      new LinkedHashMap<>();

  @Override
  public void register(ConstraintNodeProfileId profileId) {
    statsByProfileId.computeIfAbsent(profileId, ignored -> new Stats());
  }

  @Override
  public void registerConstraint(
      ConstraintRef constraintRef, Set<ConstraintNodeProfileId> profileIdSet) {
    profilesByConstraintRef.put(constraintRef, profileIdSet);
  }

  @Override
  public void measure(ConstraintNodeProfileId profileId, Operation operation, Runnable measurable) {
    var stats = statsByProfileId.computeIfAbsent(profileId, ignored -> new Stats());
    var start = System.nanoTime();
    try {
      measurable.run();
    } finally {
      stats.timeByOperation.get(operation).add(System.nanoTime() - start);
      stats.countByOperation.get(operation).increment();
    }
  }

  @Override
  public void summarize() {
    if (profilesByConstraintRef.isEmpty()) {
      return;
    }
    var summary = new StringBuilder("Constraint stream profiling summary:");
    profilesByConstraintRef.entrySet().stream()
        .sorted(
            Comparator.comparingLong(
                    (Map.Entry<ConstraintRef, Set<ConstraintNodeProfileId>> entry) ->
                        entry.getValue().stream()
                            .mapToLong(profileId -> totalNanos(statsByProfileId.get(profileId)))
                            .sum())
                .reversed())
        .forEach(
            entry -> {
              var totalNanos =
                  entry.getValue().stream()
                      .mapToLong(profileId -> totalNanos(statsByProfileId.get(profileId)))
                      .sum();
              summary
                  .append(System.lineSeparator())
                  .append("  ")
                  .append(entry.getKey())
                  .append(": ")
                  .append(formatMillis(totalNanos));
            });
    LOGGER.info(summary.toString());
  }

  private static long totalNanos(Stats stats) {
    if (stats == null) {
      return 0L;
    }
    return stats.timeByOperation.values().stream().mapToLong(LongAdder::sum).sum();
  }

  private static String formatMillis(long nanos) {
    return "%.3f ms".formatted(nanos / 1_000_000.0);
  }

  private static final class Stats {
    private final EnumMap<Operation, LongAdder> timeByOperation = new EnumMap<>(Operation.class);
    private final EnumMap<Operation, LongAdder> countByOperation = new EnumMap<>(Operation.class);

    private Stats() {
      for (var operation : Operation.values()) {
        timeByOperation.put(operation, new LongAdder());
        countByOperation.put(operation, new LongAdder());
      }
    }
  }
}
