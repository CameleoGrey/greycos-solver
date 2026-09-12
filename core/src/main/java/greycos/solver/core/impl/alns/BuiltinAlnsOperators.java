package greycos.solver.core.impl.alns;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.alns.AlnsAssignment;
import greycos.solver.core.api.solver.alns.AlnsContext;
import greycos.solver.core.api.solver.alns.AlnsDestroyOperator;
import greycos.solver.core.api.solver.alns.AlnsEvaluation;
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

  private BuiltinAlnsOperators() {}

  @SuppressWarnings("unchecked")
  public static <S, Score_ extends Score<Score_>> AlnsDestroyOperator<S, Score_> destroy(
      AlnsDestroyOperatorConfig config) {
    var copied = config.copyConfig();
    if (copied.getCustomClass() != null
        && (copied.getType() != null
            || copied.getRelatednessClass() != null
            || copied.getRankingClass() != null)) {
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
    if (type == AlnsDestroyOperatorType.RELATEDNESS && relatedness == null) {
      throw new IllegalArgumentException("RELATEDNESS requires relatednessClass.");
    }
    if (relatedness != null)
      configureProperties(relatedness, "relatednessClass", copied.getCustomProperties());
    if (ranking != null) configureProperties(ranking, "rankingClass", copied.getCustomProperties());
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
          };
        };
    return new ManagedDestroy<>(delegate, relatedness != null ? relatedness : ranking);
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
              for (var target : pool) {
                context.checkTermination();
                var removed = context.evaluate(candidate -> candidate.destroy(target));
                ranked.add(
                    new MarginalTarget<>(
                        target, AlnsScoreMath.difference(removed.score(), baseline.score())));
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
        if (type == AlnsRepairOperatorType.REGRET_2 || type == AlnsRepairOperatorType.REGRET_3) {
          choice = chooseRegret(context, pending, type == AlnsRepairOperatorType.REGRET_2 ? 2 : 3);
        } else {
          var target = pending.get(0);
          var alternatives = alternatives(context, target);
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
        context.assign(choice.candidate().assignment());
        pending.remove(choice.target());
      }
      return context.score().isComplete();
    };
  }

  private static <S, Score_ extends Score<Score_>> List<Candidate<S, Score_>> alternatives(
      AlnsContext<S, Score_> context, AlnsTarget<S> target) {
    var candidates = new ArrayList<Candidate<S, Score_>>();
    for (var assignment : context.assignments(target)) {
      context.checkTermination();
      candidates.add(new Candidate<>(assignment, context.evaluate(assignment)));
    }
    candidates.sort((left, right) -> right.evaluation().compareTo(left.evaluation()));
    return candidates;
  }

  private static <S, Score_ extends Score<Score_>> Choice<S, Score_> chooseRegret(
      AlnsContext<S, Score_> context, List<AlnsTarget<S>> pending, int k) {
    Choice<S, Score_> best = null;
    // Mandatory and optional decisions are not interchangeable: complete mandatory repairs first.
    boolean mandatoryRemaining =
        pending.stream().anyMatch(target -> !target.variable().allowsUnassigned());
    for (var target : pending) {
      if (mandatoryRemaining && target.variable().allowsUnassigned()) {
        continue;
      }
      var alternatives = alternatives(context, target);
      if (alternatives.isEmpty()) {
        return null;
      }
      var first = alternatives.get(0);
      BigDecimal[] regret =
          AlnsScoreMath.difference(first.evaluation().score(), first.evaluation().score());
      for (int rank = 1; rank < k; rank++) {
        var other = alternatives.get(Math.min(rank, alternatives.size() - 1));
        var difference =
            AlnsScoreMath.difference(first.evaluation().score(), other.evaluation().score());
        for (int level = 0; level < regret.length; level++) {
          regret[level] = regret[level].add(difference[level]);
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

  private record Candidate<S, Score_ extends Score<Score_>>(
      AlnsAssignment<S> assignment, AlnsEvaluation<Score_> evaluation) {}

  private record Choice<S, Score_ extends Score<Score_>>(
      AlnsTarget<S> target, Candidate<S, Score_> candidate, BigDecimal[] regret, boolean forced) {}
}
