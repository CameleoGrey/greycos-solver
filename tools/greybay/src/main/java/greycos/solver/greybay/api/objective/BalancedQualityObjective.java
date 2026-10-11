package greycos.solver.greybay.api.objective;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import greycos.solver.core.api.score.Score;
import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.impl.objective.BalancedObjectiveCodec;

/**
 * Maximizes feasibility rate, then each normalized native level lexicographically. Instances have
 * equal weight; seeds are averaged within each instance. Exact ties remain ties. The coordinator
 * retains its incumbent on a tie, including the baseline entered first.
 */
public final class BalancedQualityObjective<Score_ extends Score<Score_>>
    implements StudyObjective<Score_> {
  private final ExactScoreAdapter<Score_> scoreAdapter;
  private final Map<String, List<ExactRational>> explicitScales;
  private final ComponentIdentity identity;

  public BalancedQualityObjective() {
    this(Map.of());
  }

  public BalancedQualityObjective(Map<String, List<ExactRational>> explicitScales) {
    this(ExactScoreAdapter.standard(), explicitScales);
  }

  public BalancedQualityObjective(ExactScoreAdapter<Score_> scoreAdapter) {
    this(scoreAdapter, Map.of());
  }

  /**
   * Scale overrides replace every level of the specified instance; unspecified instances use
   * defaults.
   */
  public BalancedQualityObjective(
      ExactScoreAdapter<Score_> scoreAdapter, Map<String, List<ExactRational>> explicitScales) {
    this.scoreAdapter = Objects.requireNonNull(scoreAdapter, "scoreAdapter");
    Objects.requireNonNull(scoreAdapter.identity(), "scoreAdapter.identity");
    var copy = new TreeMap<String, List<ExactRational>>();
    explicitScales.forEach(
        (problemId, scales) -> {
          var vector = List.copyOf(scales);
          if (problemId.isBlank()
              || vector.isEmpty()
              || vector.stream().anyMatch(value -> value.signum() <= 0)) {
            throw new IllegalArgumentException(
                "Explicit scales for problem ("
                    + problemId
                    + ") must be nonempty and positive: "
                    + vector
                    + ".");
          }
          copy.put(problemId, vector);
        });
    this.explicitScales = Collections.unmodifiableMap(copy);
    identity = new ComponentIdentity("greybay.balanced-quality", "1", configurationIdentity());
  }

  private String configurationIdentity() {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var adapterIdentity = scoreAdapter.identity();
      for (var value :
          List.of(
              adapterIdentity.id(),
              adapterIdentity.version(),
              adapterIdentity.artifactIdentity())) {
        addIdentityPart(digest, value);
      }
      explicitScales.forEach(
          (problemId, scales) -> {
            addIdentityPart(digest, problemId);
            addIdentityPart(digest, Integer.toString(scales.size()));
            scales.forEach(scale -> addIdentityPart(digest, scale.toString()));
          });
      return "greycos-solver-greybay:" + HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("The JVM must support SHA-256.", exception);
    }
  }

  private static void addIdentityPart(MessageDigest digest, String value) {
    var bytes = value.getBytes(StandardCharsets.UTF_8);
    digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
    digest.update((byte) ':');
    digest.update(bytes);
  }

  @Override
  public ComponentIdentity identity() {
    return identity;
  }

  @Override
  public ExactScoreAdapter<Score_> scoreAdapter() {
    return scoreAdapter;
  }

  public Map<String, List<ExactRational>> explicitScales() {
    return explicitScales;
  }

  /**
   * Validates override IDs against all declared study instances, including training and held-out
   * panels. Call before calibration; each individual panel uses only its own matching overrides.
   */
  public void validateProblemIds(Collection<String> problemIds) {
    Objects.requireNonNull(problemIds, "problemIds");
    var unknown =
        explicitScales.keySet().stream()
            .filter(problemId -> !problemIds.contains(problemId))
            .toList();
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException(
          "Explicit scales refer to unknown study problem IDs: " + unknown + ".");
    }
  }

  @Override
  public BalancedObjectiveCalibration calibrate(CompletedPanel<Score_> baseline) {
    var firstScore = baseline.observations().getFirst().score();
    var layout = scoreAdapter.layout(firstScore);
    var anchors = new LinkedHashMap<String, List<ExactRational>>();
    var scales = new LinkedHashMap<String, List<ExactRational>>();
    for (var problemId : baseline.problemIds()) {
      var sums = zeroVector(layout.levelCount());
      for (var seed : baseline.seeds()) {
        var levels = levels(baseline.observation(problemId, seed), layout);
        for (int level = 0; level < sums.size(); level++) {
          sums.set(level, sums.get(level).add(levels.get(level)));
        }
      }
      var anchor = sums.stream().map(sum -> sum.divide(baseline.seeds().size())).toList();
      anchors.put(problemId, anchor);
      var scale = explicitScales.get(problemId);
      if (scale == null) {
        scale = anchor.stream().map(BalancedQualityObjective::defaultScale).toList();
      } else if (scale.size() != layout.levelCount()) {
        throw new IllegalArgumentException(
            "Explicit scales for problem ("
                + problemId
                + ") have "
                + scale.size()
                + " levels; expected "
                + layout.levelCount()
                + ".");
      }
      scales.put(problemId, scale);
    }
    return new BalancedObjectiveCalibration(
        layout, scoreAdapter.identity(), baseline.problemIds(), baseline.seeds(), anchors, scales);
  }

  private static ExactRational defaultScale(ExactRational anchor) {
    var absolute = anchor.abs();
    return absolute.compareTo(ExactRational.ONE) > 0 ? absolute : ExactRational.ONE;
  }

  @Override
  public BalancedObjectiveValue evaluate(
      CompletedPanel<Score_> panel, ObjectiveCalibration calibration) {
    if (!(calibration instanceof BalancedObjectiveCalibration reference)) {
      throw new IllegalArgumentException(
          "Balanced quality requires a BalancedObjectiveCalibration.");
    }
    if (!reference.scoreAdapterIdentity().equals(scoreAdapter.identity())
        || !panel.problemIds().equals(reference.problemIds())
        || !panel.seeds().equals(reference.seeds())) {
      throw new IllegalArgumentException(
          "The panel or exact score adapter differs from the frozen calibration.");
    }
    var gains = zeroVector(reference.layout().levelCount());
    var feasible = ExactRational.ZERO;
    for (var problemId : panel.problemIds()) {
      var instanceGains = zeroVector(gains.size());
      long feasibleSeeds = 0;
      for (var seed : panel.seeds()) {
        var observation = panel.observation(problemId, seed);
        var levels = levels(observation, reference.layout());
        if (observation.score().isFeasible()) {
          feasibleSeeds++;
        }
        for (int level = 0; level < gains.size(); level++) {
          var gain =
              levels
                  .get(level)
                  .subtract(reference.anchors().get(problemId).get(level))
                  .divide(reference.scales().get(problemId).get(level));
          instanceGains.set(level, instanceGains.get(level).add(gain));
        }
      }
      feasible = feasible.add(ExactRational.of(feasibleSeeds, panel.seeds().size()));
      for (int level = 0; level < gains.size(); level++) {
        gains.set(
            level, gains.get(level).add(instanceGains.get(level).divide(panel.seeds().size())));
      }
    }
    return new BalancedObjectiveValue(
        feasible.divide(panel.problemIds().size()),
        gains.stream().map(gain -> gain.divide(panel.problemIds().size())).toList());
  }

  private List<ExactRational> levels(ScoredObservation<Score_> observation, ScoreLayout expected) {
    var score = observation.score();
    if (!expected.equals(scoreAdapter.layout(score))) {
      throw new IllegalArgumentException(
          "Score layout differs from calibration for problem ("
              + observation.problemId()
              + "), seed ("
              + observation.seed()
              + ").");
    }
    var levels = List.copyOf(scoreAdapter.levels(score));
    if (levels.size() != expected.levelCount()) {
      throw new IllegalArgumentException(
          "Exact score adapter returned "
              + levels.size()
              + " levels; expected "
              + expected.levelCount()
              + ".");
    }
    return levels;
  }

  private static ArrayList<ExactRational> zeroVector(int size) {
    return new ArrayList<>(Collections.nCopies(size, ExactRational.ZERO));
  }

  @Override
  public int compare(ObjectiveValue left, ObjectiveValue right) {
    if (!(left instanceof BalancedObjectiveValue leftValue)
        || !(right instanceof BalancedObjectiveValue rightValue)) {
      throw new IllegalArgumentException(
          "Balanced quality requires BalancedObjectiveValue operands.");
    }
    if (leftValue.normalizedGains().size() != rightValue.normalizedGains().size()) {
      throw new IllegalArgumentException(
          "Cannot compare objective values with different score-level counts.");
    }
    int comparison = leftValue.feasibilityRate().compareTo(rightValue.feasibilityRate());
    for (int level = 0; comparison == 0 && level < leftValue.normalizedGains().size(); level++) {
      comparison =
          leftValue.normalizedGains().get(level).compareTo(rightValue.normalizedGains().get(level));
    }
    return comparison;
  }

  @Override
  public ObjectiveCodec codec() {
    return BalancedObjectiveCodec.INSTANCE;
  }
}
