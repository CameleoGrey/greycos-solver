package greycos.solver.core.impl.alns;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.alns.AlnsAssignment;
import greycos.solver.core.api.solver.alns.AlnsContext;
import greycos.solver.core.api.solver.alns.AlnsDestroyOperator;
import greycos.solver.core.api.solver.alns.AlnsEvaluation;
import greycos.solver.core.api.solver.alns.AlnsGrouping;
import greycos.solver.core.api.solver.alns.AlnsRanking;
import greycos.solver.core.api.solver.alns.AlnsRelatedness;
import greycos.solver.core.api.solver.alns.AlnsRepairOperator;
import greycos.solver.core.api.solver.alns.AlnsTarget;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsDestroyOperatorType;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.util.ConfigUtils;

/**
 * Generic operators; candidate eligibility, mutations and scratch rollback belong to the context.
 */
public final class BuiltinAlnsOperators {

  // Bound transient score results while the evaluator independently bounds in-flight work.
  private static final int SCORING_BATCH_SIZE = 256;

  private BuiltinAlnsOperators() {}

  @SuppressWarnings("unchecked")
  public static <S, Score_ extends Score<Score_>> AlnsDestroyOperator<S, Score_> destroy(
      AlnsDestroyOperatorConfig config) {
    var copied = config.copyConfig();
    if (copied.getCustomClass() != null
        && (copied.getType() != null
            || copied.getRelatednessClass() != null
            || copied.getRankingClass() != null
            || copied.getGroupingClass() != null)) {
      throw new IllegalArgumentException(
          "ALNS customClass cannot be combined with a built-in destroy type or callback.");
    }
    if (copied.getCustomClass() != null) {
      var operator = ConfigUtils.newInstance(copied, "customClass", copied.getCustomClass());
      configureProperties(operator, "customClass", copied.getCustomProperties());
      return (AlnsDestroyOperator<S, Score_>) operator;
    }
    var type = Objects.requireNonNullElse(copied.getType(), AlnsDestroyOperatorType.RANDOM);
    if (copied.getRelatednessClass() != null && type != AlnsDestroyOperatorType.RELATEDNESS) {
      throw new IllegalArgumentException("ALNS relatednessClass is only supported by RELATEDNESS.");
    }
    if (copied.getRankingClass() != null && type != AlnsDestroyOperatorType.WORST_REMOVAL) {
      throw new IllegalArgumentException("ALNS rankingClass is only supported by WORST_REMOVAL.");
    }
    if (copied.getGroupingClass() != null && type != AlnsDestroyOperatorType.GROUP_REMOVAL) {
      throw new IllegalArgumentException("ALNS groupingClass is only supported by GROUP_REMOVAL.");
    }
    double exponent = Objects.requireNonNullElse(copied.getRankExponent(), 6.0);
    if (!Double.isFinite(exponent) || exponent <= 0) {
      throw new IllegalArgumentException("ALNS rankExponent must be finite and positive.");
    }
    AlnsRelatedness<S> relatedness =
        type != AlnsDestroyOperatorType.RELATEDNESS || copied.getRelatednessClass() == null
            ? null
            : ConfigUtils.newInstance(copied, "relatednessClass", copied.getRelatednessClass());
    AlnsRanking<S> ranking =
        type != AlnsDestroyOperatorType.WORST_REMOVAL || copied.getRankingClass() == null
            ? null
            : ConfigUtils.newInstance(copied, "rankingClass", copied.getRankingClass());
    AlnsGrouping<S> grouping =
        copied.getGroupingClass() == null
            ? null
            : ConfigUtils.newInstance(copied, "groupingClass", copied.getGroupingClass());
    if (type == AlnsDestroyOperatorType.RELATEDNESS && relatedness == null) {
      throw new IllegalArgumentException("RELATEDNESS requires relatednessClass.");
    }
    if (relatedness != null)
      configureProperties(relatedness, "relatednessClass", copied.getCustomProperties());
    if (ranking != null) configureProperties(ranking, "rankingClass", copied.getCustomProperties());
    if (grouping != null)
      configureProperties(grouping, "groupingClass", copied.getCustomProperties());
    AlnsDestroyOperator<S, Score_> delegate =
        (context, size) -> {
          context.checkTermination();
          var pool = new ArrayList<>(scopedTargets(context, copied));
          int count = Math.min(Math.max(0, size), pool.size());
          if (count == 0) {
            return List.of();
          }
          return switch (type) {
            case RANDOM -> randomSelection(pool, count, context.random());
            case LIST_BLOCK -> listBlock(context, pool, count);
            case RELATEDNESS -> related(context, pool, count, relatedness, exponent);
            case WORST_REMOVAL -> worst(context, pool, count, ranking, exponent);
            case GROUP_REMOVAL -> group(context, pool, count, grouping);
          };
        };
    return new ManagedDestroy<>(
        delegate, relatedness != null ? relatedness : ranking != null ? ranking : grouping);
  }

  public static <S, Score_ extends Score<Score_>> List<AlnsTarget<S>> scopedTargets(
      AlnsContext<S, Score_> context, AlnsDestroyOperatorConfig config) {
    return context.targets().stream()
        .filter(target -> matches(target, config.getEntityClass(), config.getVariableName()))
        .filter(target -> config.getType() != AlnsDestroyOperatorType.LIST_BLOCK || target.isList())
        .distinct()
        .toList();
  }

  public static boolean matches(AlnsTarget<?> target, Class<?> entityClass, String variableName) {
    return (entityClass == null
            || ((entityClass.isAssignableFrom(target.variable().entityClass())
                    || target.variable().entityClass().isAssignableFrom(entityClass))
                && (target.entity() == null || entityClass.isInstance(target.entity()))))
        && (variableName == null || variableName.equals(target.variable().variableName()));
  }

  private static <T> List<T> randomSelection(ArrayList<T> pool, int count, RandomGenerator random) {
    for (int i = 0; i < count; i++) {
      int other = random.nextInt(i, pool.size());
      var temporary = pool.get(i);
      pool.set(i, pool.get(other));
      pool.set(other, temporary);
    }
    return List.copyOf(pool.subList(0, count));
  }

  private static <S, Score_ extends Score<Score_>> List<AlnsTarget<S>> listBlock(
      AlnsContext<S, Score_> context, List<AlnsTarget<S>> pool, int count) {
    var seed = pool.get(context.random().nextInt(pool.size()));
    var owner = context.currentAssignment(seed).entity();
    var sameList =
        pool.stream()
            .filter(
                target ->
                    target.variable().equals(seed.variable())
                        && context.currentAssignment(target).entity() == owner)
            .sorted(Comparator.comparingInt(target -> context.currentAssignment(target).index()))
            .toList();
    int seedIndex = sameList.indexOf(seed);
    int left = seedIndex;
    int right = seedIndex + 1;
    while (left > 0
        && context.currentAssignment(sameList.get(left - 1)).index() + 1
            == context.currentAssignment(sameList.get(left)).index()) {
      left--;
    }
    while (right < sameList.size()
        && context.currentAssignment(sameList.get(right - 1)).index() + 1
            == context.currentAssignment(sameList.get(right)).index()) {
      right++;
    }
    int length = Math.min(count, right - left);
    int earliestStart = Math.max(left, seedIndex - length + 1);
    int latestStart = Math.min(seedIndex, right - length);
    int start = context.random().nextInt(earliestStart, latestStart + 1);
    return List.copyOf(sameList.subList(start, start + length));
  }

  private static <S, Score_ extends Score<Score_>> List<AlnsTarget<S>> group(
      AlnsContext<S, Score_> context,
      List<AlnsTarget<S>> pool,
      int count,
      AlnsGrouping<S> grouping) {
    var groups = new LinkedHashMap<GroupKey, ArrayList<AlnsTarget<S>>>();
    for (var target : pool) {
      context.checkTermination();
      Object key;
      if (grouping != null) {
        key = grouping.groupKey(context.workingSolution(), target);
        if (key == null) {
          throw new IllegalArgumentException("ALNS grouping must return a nonnull group key.");
        }
      } else {
        var current = context.currentAssignment(target);
        key = target.isList() ? new IdentityKey(current.entity()) : current.value();
      }
      groups
          .computeIfAbsent(new GroupKey(target.variable(), key), ignored -> new ArrayList<>())
          .add(target);
    }
    context.checkTermination();
    var selected = new ArrayList<>(groups.values()).get(context.random().nextInt(groups.size()));
    return selected.size() <= count
        ? List.copyOf(selected)
        : randomSelection(selected, count, context.random());
  }

  private record GroupKey(Object variable, Object key) {}

  private record IdentityKey(Object value) {
    @Override
    public boolean equals(Object other) {
      return other instanceof IdentityKey key && value == key.value;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(value);
    }
  }

  private static <S, Score_ extends Score<Score_>> List<AlnsTarget<S>> related(
      AlnsContext<S, Score_> context,
      ArrayList<AlnsTarget<S>> pool,
      int count,
      AlnsRelatedness<S> relatedness,
      double exponent) {
    var selected = new ArrayList<AlnsTarget<S>>(count);
    selected.add(pool.remove(context.random().nextInt(pool.size())));
    while (selected.size() < count) {
      context.checkTermination();
      var reference = selected.get(context.random().nextInt(selected.size()));
      var ranked = new ArrayList<RankedTarget<S>>(pool.size());
      for (var target : pool) {
        context.checkTermination();
        double distance = relatedness.distance(context.workingSolution(), reference, target);
        if (!Double.isFinite(distance) || distance < 0) {
          throw new IllegalArgumentException("ALNS relatedness must be finite and nonnegative.");
        }
        ranked.add(new RankedTarget<>(target, distance));
      }
      ranked.sort(Comparator.comparingDouble(RankedTarget::rank));
      var target = ranked.get(rankIndex(context.random(), ranked.size(), exponent)).target();
      selected.add(target);
      pool.remove(target);
    }
    return List.copyOf(selected);
  }

  private static <S, Score_ extends Score<Score_>> List<AlnsTarget<S>> worst(
      AlnsContext<S, Score_> context,
      ArrayList<AlnsTarget<S>> pool,
      int count,
      AlnsRanking<S> ranking,
      double exponent) {
    var selected = new ArrayList<AlnsTarget<S>>(count);
    // Hold selected removals in one scratch state. Inner probes undo only their candidate removal,
    // avoiding repeated replay of the entire selected prefix for every remaining target.
    context.evaluate(
        scratch -> {
          while (selected.size() < count) {
            context.checkTermination();
            AlnsTarget<S> chosen;
            if (ranking != null) {
              var ranked = new ArrayList<RankedTarget<S>>(pool.size());
              for (var target : pool) {
                context.checkTermination();
                double rank = ranking.rank(context.workingSolution(), target);
                if (!Double.isFinite(rank)) {
                  throw new IllegalArgumentException("ALNS removal ranking must be finite.");
                }
                ranked.add(new RankedTarget<>(target, rank));
              }
              ranked.sort(Comparator.comparingDouble(RankedTarget<S>::rank).reversed());
              chosen = ranked.get(rankIndex(context.random(), ranked.size(), exponent)).target();
            } else {
              var baseline = context.score();
              var ranked = new ArrayList<MarginalTarget<S>>(pool.size());
              for (int start = 0; start < pool.size(); start += SCORING_BATCH_SIZE) {
                var batch = pool.subList(start, Math.min(pool.size(), start + SCORING_BATCH_SIZE));
                var evaluations = context.evaluateRemovals(batch);
                for (int i = 0; i < batch.size(); i++) {
                  ranked.add(
                      new MarginalTarget<>(
                          batch.get(i),
                          AlnsScoreMath.difference(evaluations.get(i).score(), baseline.score())));
                }
              }
              ranked.sort(
                  (left, right) -> AlnsScoreMath.compare(right.improvement(), left.improvement()));
              chosen = ranked.get(rankIndex(context.random(), ranked.size(), exponent)).target();
            }
            selected.add(chosen);
            pool.remove(chosen);
            scratch.destroy(chosen);
          }
        });
    return List.copyOf(selected);
  }

  private static int rankIndex(RandomGenerator random, int size, double exponent) {
    return Math.min(size - 1, (int) (Math.pow(random.nextDouble(), exponent) * size));
  }

  @SuppressWarnings("unchecked")
  public static <S, Score_ extends Score<Score_>> AlnsRepairOperator<S, Score_> repair(
      AlnsRepairOperatorConfig config) {
    var copied = config.copyConfig();
    if (copied.getRegretK() != null && copied.getType() != AlnsRepairOperatorType.REGRET_K) {
      throw new IllegalArgumentException("ALNS regretK is only supported by REGRET_K.");
    }
    if (copied.getCustomClass() != null && copied.getType() != null) {
      throw new IllegalArgumentException(
          "ALNS customClass cannot be combined with a built-in repair type.");
    }
    if (copied.getCustomClass() != null) {
      var operator = ConfigUtils.newInstance(copied, "customClass", copied.getCustomClass());
      configureProperties(operator, "customClass", copied.getCustomProperties());
      return (AlnsRepairOperator<S, Score_>) operator;
    }
    var type = Objects.requireNonNullElse(copied.getType(), AlnsRepairOperatorType.GREEDY);
    int topK = Objects.requireNonNullElse(copied.getTopK(), 3);
    if (topK < 1) {
      throw new IllegalArgumentException("ALNS repair topK must be positive.");
    }
    int regretK = Objects.requireNonNullElse(copied.getRegretK(), 4);
    if (regretK < 2) {
      throw new IllegalArgumentException("ALNS regretK must be at least 2.");
    }
    return (context, targets) -> {
      var pending = new ArrayList<>(new LinkedHashSet<>(targets));
      if (pending.stream()
          .anyMatch(
              target -> !matches(target, copied.getEntityClass(), copied.getVariableName()))) {
        return false;
      }
      if (type == AlnsRepairOperatorType.RANDOMIZED_GREEDY) {
        pending = new ArrayList<>(randomSelection(pending, pending.size(), context.random()));
      }
      pending.sort(Comparator.comparing(target -> target.variable().allowsUnassigned()));
      while (!pending.isEmpty()) {
        context.checkTermination();
        Choice<S, Score_> choice;
        if (type == AlnsRepairOperatorType.REGRET_2
            || type == AlnsRepairOperatorType.REGRET_3
            || type == AlnsRepairOperatorType.REGRET_K) {
          choice =
              chooseRegret(
                  context,
                  pending,
                  type == AlnsRepairOperatorType.REGRET_2
                      ? 2
                      : type == AlnsRepairOperatorType.REGRET_3 ? 3 : regretK);
        } else if (type == AlnsRepairOperatorType.CHEAPEST_INSERTION) {
          choice = chooseCheapest(context, pending);
        } else {
          var target = pending.get(0);
          var alternatives =
              alternatives(
                  context, target, type == AlnsRepairOperatorType.RANDOMIZED_GREEDY ? topK : 1);
          if (alternatives.isEmpty()) {
            return false;
          }
          int index =
              type == AlnsRepairOperatorType.RANDOMIZED_GREEDY
                  ? context.random().nextInt(Math.min(topK, alternatives.size()))
                  : 0;
          choice = new Choice<>(target, alternatives.get(index), null, false);
        }
        if (choice == null) {
          return false;
        }
        if (context instanceof DefaultAlnsContext<S, Score_> framework
            && choice.candidate().baselineRevision() >= 0) {
          framework.assignEvaluated(choice.candidate());
        } else {
          context.assign(choice.candidate().assignment());
        }
        pending.remove(choice.target());
      }
      return context.score().isComplete();
    };
  }

  private static <S, Score_ extends Score<Score_>> List<Candidate<S, Score_>> alternatives(
      AlnsContext<S, Score_> context, AlnsTarget<S> target, int retainedCount) {
    if (context instanceof DefaultAlnsContext<S, Score_> framework
        && framework.usesPreparedProbes()) {
      return framework.bestAssignments(List.of(target), retainedCount).getFirst();
    }
    Comparator<OrderedCandidate<S, Score_>> worstFirst =
        (left, right) -> {
          int score = left.candidate().evaluation().compareTo(right.candidate().evaluation());
          // Among equal scores, the later original assignment is the first one discarded.
          return score != 0 ? score : Integer.compare(right.index(), left.index());
        };
    var candidates =
        new PriorityQueue<OrderedCandidate<S, Score_>>(Math.min(retainedCount, 16), worstFirst);
    var assignments = context.assignments(target);
    for (int start = 0; start < assignments.size(); start += SCORING_BATCH_SIZE) {
      var batch =
          assignments.subList(start, Math.min(assignments.size(), start + SCORING_BATCH_SIZE));
      var evaluations = context.evaluateAssignments(batch);
      for (int i = 0; i < batch.size(); i++) {
        var evaluation = evaluations.get(i);
        if (candidates.size() == retainedCount) {
          if (evaluation.compareTo(candidates.peek().candidate().evaluation()) <= 0) {
            continue;
          }
          candidates.remove();
        }
        var candidate =
            new OrderedCandidate<>(start + i, new Candidate<>(batch.get(i), evaluation));
        candidates.add(candidate);
      }
    }
    return candidates.stream()
        .sorted(worstFirst.reversed())
        .map(OrderedCandidate::candidate)
        .toList();
  }

  private record OrderedCandidate<S, Score_ extends Score<Score_>>(
      int index, Candidate<S, Score_> candidate) {}

  private static <S> List<AlnsTarget<S>> eligiblePending(List<AlnsTarget<S>> pending) {
    // Mandatory and optional decisions are not interchangeable: complete mandatory repairs first.
    boolean mandatoryRemaining =
        pending.stream().anyMatch(target -> !target.variable().allowsUnassigned());
    return pending.stream()
        .filter(target -> !mandatoryRemaining || !target.variable().allowsUnassigned())
        .toList();
  }

  private static <S, Score_ extends Score<Score_>> Choice<S, Score_> chooseCheapest(
      AlnsContext<S, Score_> context, List<AlnsTarget<S>> pending) {
    Choice<S, Score_> best = null;
    var eligible = eligiblePending(pending);
    List<List<Candidate<S, Score_>>> prepared =
        context instanceof DefaultAlnsContext<S, Score_> framework && framework.usesPreparedProbes()
            ? framework.bestAssignments(eligible, 1)
            : null;
    for (int targetIndex = 0; targetIndex < eligible.size(); targetIndex++) {
      var target = eligible.get(targetIndex);
      var alternatives =
          prepared == null ? alternatives(context, target, 1) : prepared.get(targetIndex);
      if (alternatives.isEmpty()) {
        return null;
      }
      var candidate = alternatives.getFirst();
      if (best == null || candidate.evaluation().compareTo(best.candidate().evaluation()) > 0) {
        best = new Choice<>(target, candidate, null, false);
      }
    }
    return best;
  }

  private static <S, Score_ extends Score<Score_>> Choice<S, Score_> chooseRegret(
      AlnsContext<S, Score_> context, List<AlnsTarget<S>> pending, int k) {
    Choice<S, Score_> best = null;
    var eligible = eligiblePending(pending);
    List<List<Candidate<S, Score_>>> prepared =
        context instanceof DefaultAlnsContext<S, Score_> framework && framework.usesPreparedProbes()
            ? framework.bestAssignments(eligible, k)
            : null;
    for (int targetIndex = 0; targetIndex < eligible.size(); targetIndex++) {
      var target = eligible.get(targetIndex);
      var alternatives =
          prepared == null ? alternatives(context, target, k) : prepared.get(targetIndex);
      if (alternatives.isEmpty()) {
        return null;
      }
      var first = alternatives.get(0);
      BigDecimal[] regret =
          AlnsScoreMath.difference(first.evaluation().score(), first.evaluation().score());
      for (int rank = 1; rank < Math.min(k, alternatives.size()); rank++) {
        if (k > 3 && rank % SCORING_BATCH_SIZE == 0) context.checkTermination();
        var other = alternatives.get(rank);
        var difference =
            AlnsScoreMath.difference(first.evaluation().score(), other.evaluation().score());
        for (int level = 0; level < regret.length; level++) {
          regret[level] = regret[level].add(difference[level]);
        }
      }
      if (alternatives.size() < k) {
        var difference =
            AlnsScoreMath.difference(
                first.evaluation().score(), alternatives.getLast().evaluation().score());
        var repetitions = BigDecimal.valueOf(k - alternatives.size());
        for (int level = 0; level < regret.length; level++) {
          regret[level] = regret[level].add(difference[level].multiply(repetitions));
        }
      }
      var candidate = new Choice<>(target, first, regret, alternatives.size() == 1);
      if (best == null || compareChoices(candidate, best) > 0) {
        best = candidate;
      }
    }
    return best;
  }

  private static <S, Score_ extends Score<Score_>> int compareChoices(
      Choice<S, Score_> left, Choice<S, Score_> right) {
    int forced = Boolean.compare(left.forced(), right.forced());
    if (forced != 0) {
      return forced;
    }
    int regret = AlnsScoreMath.compare(left.regret(), right.regret());
    return regret != 0
        ? regret
        : left.candidate().evaluation().compareTo(right.candidate().evaluation());
  }

  private record RankedTarget<S>(AlnsTarget<S> target, double rank) {}

  private static void configureProperties(
      Object instance, String property, Map<String, String> properties) {
    try {
      ConfigUtils.applyCustomProperties(instance, property, properties, "customProperties");
    } catch (RuntimeException | Error failure) {
      if (instance instanceof AutoCloseable closeable) {
        try {
          closeable.close();
        } catch (Exception cleanupFailure) {
          failure.addSuppressed(cleanupFailure);
        }
      }
      throw failure;
    }
  }

  private record ManagedDestroy<S, Score_ extends Score<Score_>>(
      AlnsDestroyOperator<S, Score_> delegate, Object resource)
      implements AlnsDestroyOperator<S, Score_>, AutoCloseable {
    @Override
    public List<AlnsTarget<S>> select(AlnsContext<S, Score_> context, int size) {
      return delegate.select(context, size);
    }

    @Override
    public void close() throws Exception {
      if (resource instanceof AutoCloseable closeable) {
        closeable.close();
      }
    }
  }

  private record MarginalTarget<S>(AlnsTarget<S> target, BigDecimal[] improvement) {}

  record Candidate<S, Score_ extends Score<Score_>>(
      AlnsAssignment<S> assignment, AlnsEvaluation<Score_> evaluation, long baselineRevision) {
    Candidate(AlnsAssignment<S> assignment, AlnsEvaluation<Score_> evaluation) {
      this(assignment, evaluation, -1);
    }
  }

  private record Choice<S, Score_ extends Score<Score_>>(
      AlnsTarget<S> target, Candidate<S, Score_> candidate, BigDecimal[] regret, boolean forced) {}
}
