package greycos.solver.core.impl.alns;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.alns.AlnsOperatorPair;
import greycos.solver.core.api.solver.alns.AlnsOutcome;
import greycos.solver.core.api.solver.alns.AlnsSelectionPolicy;
import greycos.solver.core.api.solver.alns.AlnsTrialResult;
import greycos.solver.core.config.alns.AlnsSelectionPolicyType;

/** Learns from completed proposals, never from scratch insertion evaluations. */
public final class DefaultAlnsSelection<Score_ extends Score<Score_>>
    implements AlnsSelectionPolicy<Score_> {
  private final AlnsSelectionPolicyType type;
  private final Map<String, Arm> destroyArms = new LinkedHashMap<>();
  private final Map<String, Arm> repairArms = new LinkedHashMap<>();
  private final Map<AlnsOperatorPair, Arm> pairArms = new LinkedHashMap<>();
  private final int segmentLength;
  private final double reaction;
  private final double floor;
  private final double exploration;
  private final double newBestReward;
  private final double improvedReward;
  private final double acceptedReward;
  private final double rewardScale;
  private long samples;

  public DefaultAlnsSelection(
      AlnsSelectionPolicyType type,
      Map<String, Double> destroyWeights,
      Map<String, Double> repairWeights,
      int segmentLength,
      double reaction,
      double floor,
      double exploration,
      double newBestReward,
      double improvedReward,
      double acceptedReward) {
    if (segmentLength < 1
        || !Double.isFinite(reaction)
        || reaction <= 0
        || reaction > 1
        || !Double.isFinite(floor)
        || floor <= 0
        || !Double.isFinite(exploration)
        || exploration < 0) {
      throw new IllegalArgumentException("Invalid ALNS adaptation parameters.");
    }
    for (double reward : new double[] {newBestReward, improvedReward, acceptedReward}) {
      if (!Double.isFinite(reward) || reward < 0) {
        throw new IllegalArgumentException("ALNS rewards must be finite and nonnegative.");
      }
    }
    this.type = type;
    this.segmentLength = segmentLength;
    this.reaction = reaction;
    this.floor = floor;
    this.exploration = exploration;
    this.newBestReward = newBestReward;
    this.improvedReward = improvedReward;
    this.acceptedReward = acceptedReward;
    rewardScale = Math.max(1, Math.max(newBestReward, Math.max(improvedReward, acceptedReward)));
    destroyWeights.forEach((id, weight) -> destroyArms.put(id, new Arm(weight)));
    repairWeights.forEach((id, weight) -> repairArms.put(id, new Arm(weight)));
  }

  @Override
  public AlnsOperatorPair select(List<AlnsOperatorPair> eligiblePairs, RandomGenerator random) {
    if (eligiblePairs.isEmpty()) {
      throw new IllegalArgumentException("ALNS selection requires an eligible operator pair.");
    }
    if (type == AlnsSelectionPolicyType.UCB) {
      return selectUcb(eligiblePairs, random);
    }
    var destroyIds = eligiblePairs.stream().map(AlnsOperatorPair::destroyId).distinct().toList();
    var destroyId = roulette(destroyIds, destroyArms, random);
    var repairIds =
        eligiblePairs.stream()
            .filter(pair -> pair.destroyId().equals(destroyId))
            .map(AlnsOperatorPair::repairId)
            .distinct()
            .toList();
    return new AlnsOperatorPair(destroyId, roulette(repairIds, repairArms, random));
  }

  private String roulette(List<String> ids, Map<String, Arm> arms, RandomGenerator random) {
    double maximum = ids.stream().mapToDouble(id -> arms.get(id).weight).max().orElseThrow();
    double sum = ids.stream().mapToDouble(id -> arms.get(id).weight / maximum).sum();
    double offset = random.nextDouble() * sum;
    for (var id : ids) {
      offset -= arms.get(id).weight / maximum;
      if (offset < 0) {
        return id;
      }
    }
    return ids.getLast();
  }

  private AlnsOperatorPair selectUcb(List<AlnsOperatorPair> pairs, RandomGenerator random) {
    var finalists = new ArrayList<AlnsOperatorPair>();
    double best = Double.NEGATIVE_INFINITY;
    for (var pair : pairs) {
      var arm = pairArms.computeIfAbsent(pair, ignored -> new Arm(1));
      double value =
          arm.uses == 0
              ? Double.POSITIVE_INFINITY
              : arm.meanReward + exploration * Math.sqrt(Math.log(Math.max(1, samples)) / arm.uses);
      if (value > best) {
        best = value;
        finalists.clear();
      }
      if (value == best) {
        finalists.add(pair);
      }
    }
    return finalists.get(random.nextInt(finalists.size()));
  }

  @Override
  public void update(AlnsTrialResult<Score_> result) {
    if (result.outcome() == AlnsOutcome.CANCELLED) {
      return;
    }
    double reward =
        switch (result.outcome()) {
          case NEW_BEST -> newBestReward;
          case IMPROVED -> improvedReward;
          case ACCEPTED -> acceptedReward;
          default -> 0.0;
        };
    samples++;
    if (type == AlnsSelectionPolicyType.UCB) {
      pairArms
          .computeIfAbsent(
              new AlnsOperatorPair(result.destroyId(), result.repairId()), ignored -> new Arm(1))
          .record(reward / rewardScale);
    } else {
      destroyArms.get(result.destroyId()).record(reward);
      repairArms.get(result.repairId()).record(reward);
      if (samples % segmentLength == 0) {
        destroyArms.values().forEach(this::updateWeight);
        repairArms.values().forEach(this::updateWeight);
      }
    }
  }

  private void updateWeight(Arm arm) {
    if (arm.uses > 0) {
      arm.weight =
          Math.max(
              floor,
              Math.min(Double.MAX_VALUE, (1 - reaction) * arm.weight + reaction * arm.meanReward));
      arm.uses = 0;
      arm.meanReward = 0;
    }
  }

  @Override
  public Map<String, Double> weights() {
    var result = new LinkedHashMap<String, Double>();
    if (type == AlnsSelectionPolicyType.UCB) {
      pairArms.forEach(
          (pair, arm) ->
              result.put(
                  AlnsStepScope.pairId(pair.destroyId(), pair.repairId()),
                  arm.uses == 0 ? 0.0 : arm.meanReward));
    } else {
      destroyArms.forEach((id, arm) -> result.put("destroy/" + id, arm.weight));
      repairArms.forEach((id, arm) -> result.put("repair/" + id, arm.weight));
    }
    return Map.copyOf(result);
  }

  private static final class Arm {
    private double weight;
    private long uses;
    private double meanReward;

    private Arm(double weight) {
      if (!Double.isFinite(weight) || weight <= 0) {
        throw new IllegalArgumentException("ALNS initial weights must be finite and positive.");
      }
      this.weight = weight;
    }

    private void record(double reward) {
      uses++;
      meanReward += (reward - meanReward) / uses;
    }
  }
}
