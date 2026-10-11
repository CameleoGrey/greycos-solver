package greycos.solver.greybay.impl.template;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.config.alns.AlnsAcceptanceType;
import greycos.solver.core.config.alns.AlnsMoveThreadingMode;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.alns.AlnsSelectionPolicyType;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchConfig;
import greycos.solver.core.config.localsearch.GuidedLocalSearchFeatureComposition;
import greycos.solver.core.config.localsearch.GuidedLocalSearchGuidanceMode;
import greycos.solver.core.config.localsearch.GuidedLocalSearchSearchMode;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.phase.PhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.api.ConfigurationTemplate;
import greycos.solver.greybay.api.ProblemCapabilities;
import greycos.solver.greybay.config.Activation;
import greycos.solver.greybay.config.CategoricalDimension;
import greycos.solver.greybay.config.Dimension;
import greycos.solver.greybay.config.FixedDimension;
import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.config.SearchSpace;
import greycos.solver.greybay.config.template.AlnsPortfolio;
import greycos.solver.greybay.config.template.LocalSearchVariant;
import greycos.solver.greybay.config.template.MovePortfolio;
import greycos.solver.greybay.config.template.TemplateParameters;
import greycos.solver.greybay.config.template.TemplateRequirements;
import greycos.solver.greybay.impl.config.FrozenSolverConfig;
import greycos.solver.greybay.impl.objective.ScoreCopies;
import greycos.solver.greybay.impl.persistence.StoreJson;

/**
 * Implementation of the fixed reference portfolio; arbitrary mappings belong in custom templates.
 */
public final class ReferenceTemplate implements ConfigurationTemplate {
  private enum Kind {
    LS,
    GLS,
    ALNS,
    PIPELINE
  }

  private final TemplateParameters parameters;
  private final Kind kind;
  private final LocalSearchVariant variant;
  private final Map<String, MovePortfolio> moves;
  private final Map<String, AlnsPortfolio> alnsPortfolios;
  private final Score<?> referenceScore;
  private final ReferenceTemplate first;
  private final ReferenceTemplate last;
  private final ComponentIdentity derivedIdentity;

  private ReferenceTemplate(
      TemplateParameters parameters,
      Kind kind,
      LocalSearchVariant variant,
      Map<String, MovePortfolio> moves,
      Map<String, AlnsPortfolio> alnsPortfolios,
      Score<?> referenceScore,
      ReferenceTemplate first,
      ReferenceTemplate last) {
    this.parameters = Objects.requireNonNull(parameters, "parameters");
    this.kind = kind;
    this.variant = variant;
    this.moves = Map.copyOf(moves);
    this.alnsPortfolios = Map.copyOf(alnsPortfolios);
    this.referenceScore = snapshotScore(referenceScore);
    this.first = first;
    this.last = last;
    if (this.referenceScore != null)
      requirePositiveScore(this.referenceScore, variant != LocalSearchVariant.GREAT_DELUGE_SCORE);
    validateControlActivation();
    // This catches missing controls, inactive fields and unknown portfolio names before preflight.
    phases(defaultAssignment());
    this.derivedIdentity = deriveIdentity();
  }

  public static ReferenceTemplate localSearch(
      TemplateParameters parameters,
      LocalSearchVariant variant,
      Map<String, MovePortfolio> moves,
      Score<?> referenceScore) {
    Objects.requireNonNull(variant, "variant");
    if ((variant == LocalSearchVariant.SIMULATED_ANNEALING
            || variant == LocalSearchVariant.GREAT_DELUGE_SCORE)
        != (referenceScore != null)) {
      throw new IllegalArgumentException(
          "Variant ("
              + variant
              + ") requires a score vector only for SA or Great Deluge score increments.");
    }
    validatePortfolioChoices(parameters, "ls.moves", moves.keySet());
    return new ReferenceTemplate(
        parameters, Kind.LS, variant, moves, Map.of(), referenceScore, null, null);
  }

  public static ReferenceTemplate guidedLocalSearch(
      TemplateParameters parameters, Map<String, MovePortfolio> moves) {
    validatePortfolioChoices(parameters, "gls.moves", moves.keySet());
    return new ReferenceTemplate(parameters, Kind.GLS, null, moves, Map.of(), null, null, null);
  }

  public static ReferenceTemplate alns(
      TemplateParameters parameters, Map<String, AlnsPortfolio> portfolios, Score<?> temperature) {
    validatePortfolioChoices(parameters, "alns.portfolio", portfolios.keySet());
    if (choices(parameters, "alns.acceptance")
            .contains(AlnsAcceptanceType.SIMULATED_ANNEALING.name())
        != (temperature != null)) {
      throw new IllegalArgumentException(
          "An ALNS temperature vector is required exactly when SA acceptance is offered.");
    }
    return new ReferenceTemplate(
        parameters, Kind.ALNS, null, Map.of(), portfolios, temperature, null, null);
  }

  public static ReferenceTemplate pipeline(
      ComponentIdentity identity,
      ConfigurationTemplate first,
      ConfigurationTemplate last,
      Dimension firstFraction,
      Object defaultFraction) {
    if (!(first instanceof ReferenceTemplate head)
        || (head.kind != Kind.ALNS && head.kind != Kind.GLS)
        || !(last instanceof ReferenceTemplate tail)
        || tail.kind != Kind.LS) {
      throw new IllegalArgumentException(
          "Reference pipelines require ALNS or GLS followed by an ordinary reference LS template.");
    }
    if (!firstFraction.name().equals("pipeline.firstFraction")) {
      throw new IllegalArgumentException(
          "Pipeline fraction dimension must be named pipeline.firstFraction.");
    }
    var dimensions = new ArrayList<>(first.searchSpace().dimensions());
    dimensions.addAll(last.searchSpace().dimensions());
    dimensions.add(firstFraction);
    var defaults = new LinkedHashMap<>(first.defaultAssignment().values());
    defaults.putAll(last.defaultAssignment().values());
    defaults.put(firstFraction.name(), defaultFraction);
    var parameters =
        new TemplateParameters(
            identity,
            new SearchSpace(identity.version(), dimensions),
            ParameterAssignment.of(defaults));
    return new ReferenceTemplate(
        parameters, Kind.PIPELINE, null, Map.of(), Map.of(), null, head, tail);
  }

  @Override
  public ComponentIdentity identity() {
    return derivedIdentity;
  }

  private ComponentIdentity deriveIdentity() {
    var descriptor = new LinkedHashMap<String, Object>();
    descriptor.put("identityFormat", "greybay-reference-template-v1");
    descriptor.put("declaredIdentity", identityDescriptor(parameters.identity()));
    descriptor.put("kind", kind.name());
    descriptor.put("variant", variant == null ? null : variant.name());
    descriptor.put("searchSpace", searchSpace().canonicalForm());
    descriptor.put("defaultAssignment", defaultAssignment().canonicalForm());
    descriptor.put(
        "referenceScore",
        referenceScore == null
            ? null
            : Map.of(
                "class", referenceScore.getClass().getName(), "value", referenceScore.toString()));
    var moveDescriptors = new LinkedHashMap<String, Object>();
    moves.forEach(
        (name, portfolio) ->
            moveDescriptors.put(
                name,
                Map.of(
                    "identity", identityDescriptor(portfolio.identity()),
                    "requirements", requirementsDescriptor(portfolio.requirements()),
                    "configuration",
                        FrozenSolverConfig.canonicalIdentity(
                            new SolverConfig()
                                .withPhases(
                                    new LocalSearchPhaseConfig()
                                        .withMoveSelectorConfig(portfolio.selector())),
                            null))));
    descriptor.put("movePortfolios", moveDescriptors);
    var alnsDescriptors = new LinkedHashMap<String, Object>();
    alnsPortfolios.forEach(
        (name, portfolio) -> {
          var callbacks = new LinkedHashMap<String, Object>();
          portfolio
              .callbacks()
              .forEach(
                  (callback, identity) -> {
                    if (callbacks.putIfAbsent(callback.getName(), identityDescriptor(identity))
                        != null) {
                      throw new IllegalArgumentException(
                          "ALNS callback class name ("
                              + callback.getName()
                              + ") is registered more than once across class loaders.");
                    }
                  });
          alnsDescriptors.put(
              name,
              Map.of(
                  "identity", identityDescriptor(portfolio.identity()),
                  "requirements", requirementsDescriptor(portfolio.requirements()),
                  "eligibilityFeature", portfolio.eligibilityFeature(),
                  "callbacks", callbacks,
                  "configuration",
                      FrozenSolverConfig.canonicalIdentity(
                          new SolverConfig()
                              .withPhases(
                                  new AlnsPhaseConfig()
                                      .withDestroyOperatorConfigList(portfolio.destroys())
                                      .withRepairOperatorConfigList(portfolio.repairs())),
                          null)));
        });
    descriptor.put("alnsPortfolios", alnsDescriptors);
    descriptor.put(
        "pipelineChildren",
        first == null
            ? List.of()
            : List.of(identityDescriptor(first.identity()), identityDescriptor(last.identity())));
    return new ComponentIdentity(
        parameters.identity().id(),
        parameters.identity().version(),
        StoreJson.sha256(StoreJson.canonicalBytes(descriptor)));
  }

  private static Map<String, Object> identityDescriptor(ComponentIdentity identity) {
    return Map.of(
        "id",
        identity.id(),
        "version",
        identity.version(),
        "artifactIdentity",
        identity.artifactIdentity());
  }

  private static Map<String, Object> requirementsDescriptor(TemplateRequirements requirements) {
    return Map.of(
        "models",
        requirements.models().stream().map(Enum::name).sorted().toList(),
        "features",
        requirements.features().stream().sorted().toList());
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Score<?> snapshotScore(Score<?> score) {
    return score == null ? null : ScoreCopies.copy((Score) score);
  }

  @Override
  public SearchSpace searchSpace() {
    return parameters.searchSpace();
  }

  @Override
  public ParameterAssignment defaultAssignment() {
    return parameters.defaultAssignment();
  }

  @Override
  public void validate(ProblemCapabilities capabilities) {
    Objects.requireNonNull(capabilities, "capabilities");
    if (kind == Kind.PIPELINE) {
      first.validate(capabilities);
      last.validate(capabilities);
      return;
    }
    if (kind == Kind.ALNS) {
      for (String choice : choices(parameters, "alns.portfolio"))
        alnsPortfolios.get(choice).validate(capabilities);
    } else {
      for (String choice : choices(parameters, kind == Kind.GLS ? "gls.moves" : "ls.moves")) {
        moves.get(choice).requirements().validate(capabilities);
      }
    }
    if (referenceScore != null) {
      if (!capabilities.scoreType().equals(referenceScore.getClass().getName())
          && !capabilities.scoreType().equals(referenceScore.getClass().getSimpleName())) {
        throw new IllegalArgumentException(
            "Score vector type ("
                + referenceScore.getClass().getName()
                + ") does not match declared problem score type ("
                + capabilities.scoreType()
                + ").");
      }
      if (capabilities.scoreLevelLabels().size() != referenceScore.toLevelNumbers().length) {
        throw new IllegalArgumentException(
            "Score vector level count does not match declared problem score layout.");
      }
    }
  }

  @Override
  public SolverConfig materialize(SolverConfig baseCopy, ParameterAssignment assignment) {
    var result = Objects.requireNonNull(baseCopy, "baseCopy").copyConfig();
    if (result.getMoveThreadCount() != null
        && !SolverConfig.MOVE_THREAD_COUNT_NONE.equals(result.getMoveThreadCount())) {
      throw new IllegalArgumentException(
          "Reference templates require moveThreadCount NONE; configured value is ("
              + result.getMoveThreadCount()
              + "). Register a custom template for a fixed worker envelope.");
    }
    var phases = new ArrayList<PhaseConfig>();
    boolean searchSeen = false;
    if (result.getPhaseConfigList() != null) {
      for (var phase : result.getPhaseConfigList()) {
        if (isInitialization(phase)) {
          if (searchSeen)
            throw new IllegalArgumentException(
                "Initialization phases must precede search phases in a reference template base.");
          if (phase instanceof CustomPhaseConfig custom && custom.getCustomProperties() != null) {
            custom.setCustomProperties(new LinkedHashMap<>(custom.getCustomProperties()));
          }
          phases.add(phase);
        } else {
          searchSeen = true;
          if (!(phase instanceof LocalSearchPhaseConfig) && !(phase instanceof AlnsPhaseConfig)) {
            throw new IllegalArgumentException(
                "Reference template base contains an unsupported phase (" + phase + ").");
          }
        }
      }
    }
    phases.addAll(phases(assignment));
    result.setPhaseConfigList(phases);
    return result;
  }

  @Override
  public Map<Integer, Duration> approvedSearchPhaseLimits(
      SolverConfig materialized, Duration horizon, ParameterAssignment assignment) {
    searchSpace().validate(assignment);
    if (kind != Kind.PIPELINE) return Map.of();
    if (horizon.isNegative() || horizon.isZero() || horizon.getNano() % 1_000_000 != 0) {
      throw new IllegalArgumentException(
          "Pipeline horizon (" + horizon + ") must be positive whole milliseconds.");
    }
    double fraction = new Values(assignment).fraction("pipeline.firstFraction", false);
    long cap =
        BigDecimal.valueOf(fraction)
            .multiply(BigDecimal.valueOf(horizon.toMillis()))
            .setScale(0, RoundingMode.FLOOR)
            .longValueExact();
    if (cap == 0)
      throw new IllegalArgumentException(
          "pipeline.firstFraction (" + fraction + ") produces a zero-millisecond phase cap.");
    int index = 0;
    while (index < materialized.getPhaseConfigList().size()
        && isInitialization(materialized.getPhaseConfigList().get(index))) index++;
    if (materialized.getPhaseConfigList().size() != index + 2) {
      throw new IllegalArgumentException(
          "Pipeline materialization must contain exactly two search phases.");
    }
    return Map.of(index, Duration.ofMillis(cap));
  }

  private List<PhaseConfig> phases(ParameterAssignment assignment) {
    searchSpace().validate(assignment);
    if (kind == Kind.PIPELINE) {
      var values = new Values(assignment);
      values.fraction("pipeline.firstFraction", false);
      var result = new ArrayList<PhaseConfig>();
      result.addAll(first.phases(subAssignment(first, assignment)));
      result.addAll(last.phases(subAssignment(last, assignment)));
      return result;
    }
    var values = new Values(assignment);
    PhaseConfig phase =
        switch (kind) {
          case LS -> localSearch(values);
          case GLS -> guidedLocalSearch(values);
          case ALNS -> alns(values);
          case PIPELINE -> throw new IllegalStateException("Pipeline is handled above.");
        };
    values.finish();
    return List.of(phase);
  }

  private static boolean isInitialization(PhaseConfig<?> phase) {
    return phase instanceof ConstructionHeuristicPhaseConfig || phase instanceof CustomPhaseConfig;
  }

  private void validateControlActivation() {
    if (kind == Kind.PIPELINE) {
      var fraction = searchSpace().dimensions().getLast();
      if (!fraction.activation().equals(Activation.always())) {
        throw new IllegalArgumentException("pipeline.firstFraction must be unconditional.");
      }
      return;
    }
    if (kind == Kind.LS) {
      var control =
          switch (variant) {
            case LATE_ACCEPTANCE -> "ls.la.history";
            case TABU_SIZE -> "ls.tabu.size";
            case TABU_RATIO -> "ls.tabu.ratio";
            case SIMULATED_ANNEALING -> "ls.sa.temperatureScale";
            case GREAT_DELUGE_RATIO -> "ls.gd.incrementRatio";
            case GREAT_DELUGE_SCORE -> "ls.gd.incrementScale";
          };
      for (String move : choices(parameters, "ls.moves")) {
        requireActiveControls(
            Map.of("ls.moves", move), Set.of("ls.moves", "ls.acceptedCount", control), Set.of());
      }
    } else if (kind == Kind.GLS) {
      for (String move : choices(parameters, "gls.moves")) {
        requireActiveControls(
            Map.of("gls.moves", move),
            Set.of(
                "gls.moves",
                "gls.penaltyFactor",
                "gls.sampleSize",
                "gls.focusStepLimit",
                "gls.focusPenaltyUpdateLimit",
                "gls.maxPenaltyUpdatesPerStep",
                "gls.excursionStepLimit",
                "gls.excursionRepairStepLimit",
                "gls.maxUnproductiveRounds"),
            Set.of());
      }
    } else {
      for (String portfolio : choices(parameters, "alns.portfolio")) {
        for (String acceptance : choices(parameters, "alns.acceptance")) {
          for (String selection : choices(parameters, "alns.selection")) {
            for (String mode : choices(parameters, "alns.destroy.mode")) {
              var required =
                  new HashSet<>(
                      Set.of(
                          "alns.portfolio",
                          "alns.acceptance",
                          "alns.selection",
                          "alns.destroy.mode",
                          "alns.repair.calculationLimit"));
              switch (mode) {
                case "COUNT" ->
                    required.addAll(
                        Set.of("alns.destroy.minimumCount", "alns.destroy.maximumCount"));
                case "PERCENTAGE" ->
                    required.addAll(
                        Set.of("alns.destroy.minimumPercentage", "alns.destroy.maximumPercentage"));
                default ->
                    throw new IllegalArgumentException(
                        "Unknown ALNS destruction mode (" + mode + ").");
              }
              switch (AlnsAcceptanceType.valueOf(acceptance)) {
                case LATE_ACCEPTANCE -> required.add("alns.la.history");
                case SIMULATED_ANNEALING ->
                    required.addAll(Set.of("alns.sa.temperatureScale", "alns.sa.coolingRate"));
                case HILL_CLIMBING -> {}
              }
              switch (AlnsSelectionPolicyType.valueOf(selection)) {
                case UCB -> required.add("alns.ucb.exploration");
                case SEGMENTED_ROULETTE ->
                    required.addAll(
                        Set.of(
                            "alns.roulette.segmentLength",
                            "alns.roulette.reactionFactor",
                            "alns.roulette.minimumWeight"));
              }
              var repairs = alnsPortfolios.get(portfolio).repairs();
              if (repairs.stream()
                  .anyMatch(repair -> repair.getType() == AlnsRepairOperatorType.REGRET_K))
                required.add("alns.repair.regretK");
              if (repairs.stream()
                  .anyMatch(repair -> repair.getType() == AlnsRepairOperatorType.RANDOMIZED_GREEDY))
                required.add("alns.repair.topK");
              requireActiveControls(
                  Map.of(
                      "alns.portfolio",
                      portfolio,
                      "alns.acceptance",
                      acceptance,
                      "alns.selection",
                      selection,
                      "alns.destroy.mode",
                      mode),
                  required,
                  Set.of(
                      "alns.newBestReward",
                      "alns.improvedReward",
                      "alns.acceptedReward",
                      "alns.recoveryCount"));
            }
          }
        }
      }
    }
  }

  private void requireActiveControls(
      Map<String, String> branch, Set<String> required, Set<String> optional) {
    var active = new HashSet<String>();
    for (var dimension : searchSpace().dimensions()) {
      if (dimension.activation().isActive(branch)) active.add(dimension.name());
    }
    var unexpected = new HashSet<>(active);
    unexpected.removeAll(required);
    unexpected.removeAll(optional);
    var missing = new HashSet<>(required);
    missing.removeAll(active);
    if (!missing.isEmpty() || !unexpected.isEmpty()) {
      throw new IllegalArgumentException(
          "Template branch ("
              + branch
              + ") has missing controls ("
              + missing
              + ") or inactive/unknown controls ("
              + unexpected
              + "). Define matching categorical activation predicates.");
    }
  }

  private static ParameterAssignment subAssignment(
      ReferenceTemplate template, ParameterAssignment source) {
    var values = new LinkedHashMap<String, Object>();
    for (var dimension : template.searchSpace().dimensions()) {
      if (source.contains(dimension.name()))
        values.put(dimension.name(), source.get(dimension.name()));
    }
    return ParameterAssignment.of(values);
  }

  private LocalSearchPhaseConfig localSearch(Values values) {
    var phase = new LocalSearchPhaseConfig();
    phase.setMoveSelectorConfig(moves.get(values.string("ls.moves")).selector());
    phase.setForagerConfig(
        new LocalSearchForagerConfig()
            .withAcceptedCountLimit(values.positiveInt("ls.acceptedCount")));
    var acceptor = new LocalSearchAcceptorConfig();
    switch (variant) {
      case LATE_ACCEPTANCE -> acceptor.setLateAcceptanceSize(values.positiveInt("ls.la.history"));
      case TABU_SIZE -> acceptor.setEntityTabuSize(values.positiveInt("ls.tabu.size"));
      case TABU_RATIO -> acceptor.setEntityTabuRatio(values.fraction("ls.tabu.ratio", true));
      case SIMULATED_ANNEALING ->
          acceptor.setSimulatedAnnealingStartingTemperature(
              scaledScore(values.positiveReal("ls.sa.temperatureScale")));
      case GREAT_DELUGE_RATIO ->
          acceptor.setGreatDelugeWaterLevelIncrementRatio(
              values.positiveReal("ls.gd.incrementRatio"));
      case GREAT_DELUGE_SCORE ->
          acceptor.setGreatDelugeWaterLevelIncrementScore(
              scaledScore(values.positiveReal("ls.gd.incrementScale")));
    }
    phase.setAcceptorConfig(acceptor);
    return phase;
  }

  private LocalSearchPhaseConfig guidedLocalSearch(Values values) {
    var gls =
        new GuidedLocalSearchConfig()
            .withFeatureComposition(GuidedLocalSearchFeatureComposition.AUTOMATIC)
            .withGuidanceMode(GuidedLocalSearchGuidanceMode.ALL_LEVELS)
            .withSearchMode(GuidedLocalSearchSearchMode.SAMPLED)
            .withPenaltyFactor(values.positiveDecimal("gls.penaltyFactor"))
            .withSampleSize(values.positiveInt("gls.sampleSize"))
            .withFocusStepLimit(values.positiveInt("gls.focusStepLimit"))
            .withFocusPenaltyUpdateLimit(values.positiveInt("gls.focusPenaltyUpdateLimit"))
            .withMaxPenaltyUpdatesPerStep(values.positiveInt("gls.maxPenaltyUpdatesPerStep"))
            .withExcursionStepLimit(values.positiveInt("gls.excursionStepLimit"))
            .withExcursionRepairStepLimit(values.positiveInt("gls.excursionRepairStepLimit"))
            .withMaxUnproductiveRounds(values.positiveInt("gls.maxUnproductiveRounds"));
    return new LocalSearchPhaseConfig()
        .withLocalSearchType(LocalSearchType.GUIDED_LOCAL_SEARCH)
        .withGuidedLocalSearchConfig(gls)
        .withMoveSelectorConfig(moves.get(values.string("gls.moves")).selector());
  }

  private AlnsPhaseConfig alns(Values values) {
    var portfolio = alnsPortfolios.get(values.string("alns.portfolio"));
    var destroys = portfolio.destroys();
    var repairs = portfolio.repairs();
    switch (values.string("alns.destroy.mode")) {
      case "COUNT" -> {
        int minimum = values.positiveInt("alns.destroy.minimumCount");
        int maximum = values.positiveInt("alns.destroy.maximumCount");
        if (minimum > maximum)
          throw new IllegalArgumentException("ALNS minimum count exceeds maximum count.");
        destroys.forEach(
            destroy -> {
              destroy.setMinimumDestroyedPercentage(null);
              destroy.setMaximumDestroyedPercentage(null);
              destroy.setMinimumDestroyedCount(minimum);
              destroy.setMaximumDestroyedCount(maximum);
            });
      }
      case "PERCENTAGE" -> {
        double minimum = values.fraction("alns.destroy.minimumPercentage", true);
        double maximum = values.fraction("alns.destroy.maximumPercentage", true);
        if (minimum > maximum)
          throw new IllegalArgumentException("ALNS minimum percentage exceeds maximum percentage.");
        destroys.forEach(
            destroy -> {
              destroy.setMinimumDestroyedCount(null);
              destroy.setMaximumDestroyedCount(null);
              destroy.setMinimumDestroyedPercentage(minimum);
              destroy.setMaximumDestroyedPercentage(maximum);
            });
      }
      default ->
          throw new IllegalArgumentException("ALNS destruction mode must be COUNT or PERCENTAGE.");
    }
    if (repairs.stream().anyMatch(repair -> repair.getType() == AlnsRepairOperatorType.REGRET_K)) {
      int regretK = values.positiveInt("alns.repair.regretK");
      if (regretK < 2)
        throw new IllegalArgumentException("alns.repair.regretK must be at least two.");
      repairs.stream()
          .filter(repair -> repair.getType() == AlnsRepairOperatorType.REGRET_K)
          .forEach(repair -> repair.setRegretK(regretK));
    }
    if (repairs.stream()
        .anyMatch(repair -> repair.getType() == AlnsRepairOperatorType.RANDOMIZED_GREEDY)) {
      int topK = values.positiveInt("alns.repair.topK");
      repairs.stream()
          .filter(repair -> repair.getType() == AlnsRepairOperatorType.RANDOMIZED_GREEDY)
          .forEach(repair -> repair.setTopK(topK));
    }
    var phase =
        new AlnsPhaseConfig()
            .withMoveThreadCount(SolverConfig.MOVE_THREAD_COUNT_NONE)
            .withMoveThreadingMode(AlnsMoveThreadingMode.PROBES)
            .withDestroyOperatorConfigList(destroys)
            .withRepairOperatorConfigList(repairs)
            .withRepairScoreCalculationLimit(values.positiveLong("alns.repair.calculationLimit"));
    var acceptance = AlnsAcceptanceType.valueOf(values.string("alns.acceptance"));
    phase.setAcceptanceType(acceptance);
    switch (acceptance) {
      case LATE_ACCEPTANCE -> phase.setLateAcceptanceSize(values.positiveInt("alns.la.history"));
      case SIMULATED_ANNEALING -> {
        phase.setStartingTemperature(scaledScore(values.positiveReal("alns.sa.temperatureScale")));
        phase.setCoolingRate(values.fraction("alns.sa.coolingRate", true));
      }
      case HILL_CLIMBING -> {}
    }
    var selection = AlnsSelectionPolicyType.valueOf(values.string("alns.selection"));
    phase.setSelectionPolicyType(selection);
    switch (selection) {
      case SEGMENTED_ROULETTE -> {
        phase.setSegmentLength(values.positiveInt("alns.roulette.segmentLength"));
        phase.setReactionFactor(values.fraction("alns.roulette.reactionFactor", true));
        phase.setMinimumWeight(values.positiveReal("alns.roulette.minimumWeight"));
      }
      case UCB -> phase.setUcbExploration(values.nonnegativeReal("alns.ucb.exploration"));
    }
    if (values.has("alns.newBestReward"))
      phase.setNewBestReward(values.nonnegativeReal("alns.newBestReward"));
    if (values.has("alns.improvedReward"))
      phase.setImprovedReward(values.nonnegativeReal("alns.improvedReward"));
    if (values.has("alns.acceptedReward"))
      phase.setAcceptedReward(values.nonnegativeReal("alns.acceptedReward"));
    if (values.has("alns.recoveryCount"))
      phase.setRecoveryCount(values.nonnegativeInt("alns.recoveryCount"));
    return phase;
  }

  private String scaledScore(double multiplier) {
    if (referenceScore == null)
      throw new IllegalArgumentException(
          "A positive reference score vector is required for score scaling.");
    var score = referenceScore.multiply(multiplier);
    requirePositiveScore(score, variant != LocalSearchVariant.GREAT_DELUGE_SCORE);
    return score.toString();
  }

  private static void requirePositiveScore(Score<?> score, boolean everyLevelPositive) {
    if (score.structuralScore() != 0 || score.toLevelNumbers().length == 0) {
      throw new IllegalArgumentException(
          "Reference score (" + score + ") must have zero structural score and positive levels.");
    }
    int firstSign = 0;
    for (var level : score.toLevelNumbers()) {
      int sign = new BigDecimal(level.toString()).signum();
      if (firstSign == 0) firstSign = sign;
      if (everyLevelPositive && sign <= 0) {
        throw new IllegalArgumentException(
            "Reference score (" + score + ") must have finite positive levels.");
      }
    }
    if (firstSign <= 0)
      throw new IllegalArgumentException("Reference score (" + score + ") must be positive.");
  }

  private static void validatePortfolioChoices(
      TemplateParameters parameters, String name, Set<String> available) {
    if (!available.containsAll(choices(parameters, name))) {
      throw new IllegalArgumentException(
          "Parameter ("
              + name
              + ") contains an unregistered portfolio; registered IDs: "
              + available);
    }
  }

  private static List<String> choices(TemplateParameters parameters, String name) {
    var dimension =
        parameters.searchSpace().dimensions().stream()
            .filter(item -> item.name().equals(name))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException("Missing categorical parameter (" + name + ")."));
    if (dimension instanceof CategoricalDimension categorical) return categorical.choices();
    if (dimension instanceof FixedDimension fixed && fixed.value() instanceof String text)
      return List.of(text);
    throw new IllegalArgumentException(
        "Parameter (" + name + ") must be categorical or a fixed string.");
  }

  private static final class Values {
    private final ParameterAssignment assignment;
    private final Set<String> used = new HashSet<>();

    private Values(ParameterAssignment assignment) {
      this.assignment = assignment;
    }

    private boolean has(String name) {
      return assignment.contains(name);
    }

    private Object get(String name) {
      used.add(name);
      return assignment.get(name);
    }

    private String string(String name) {
      if (get(name) instanceof String value) return value;
      throw new IllegalArgumentException("Parameter (" + name + ") must be categorical.");
    }

    private long positiveLong(String name) {
      if (get(name) instanceof Long value && value > 0) return value;
      throw new IllegalArgumentException("Parameter (" + name + ") must be a positive integer.");
    }

    private int positiveInt(String name) {
      return Math.toIntExact(positiveLong(name));
    }

    private int nonnegativeInt(String name) {
      if (get(name) instanceof Long value && value >= 0) return Math.toIntExact(value);
      throw new IllegalArgumentException("Parameter (" + name + ") must be a nonnegative integer.");
    }

    private double nonnegativeReal(String name) {
      if (get(name) instanceof Double value && value >= 0 && Double.isFinite(value)) return value;
      throw new IllegalArgumentException(
          "Parameter (" + name + ") must be a finite nonnegative real.");
    }

    private double positiveReal(String name) {
      double value = nonnegativeReal(name);
      if (value == 0)
        throw new IllegalArgumentException("Parameter (" + name + ") must be positive.");
      return value;
    }

    private double fraction(String name, boolean inclusiveUpper) {
      double value = positiveReal(name);
      if (value > 1 || (!inclusiveUpper && value == 1)) {
        throw new IllegalArgumentException(
            "Parameter (" + name + ") must be in (0, " + (inclusiveUpper ? "1]." : "1)."));
      }
      return value;
    }

    private BigDecimal positiveDecimal(String name) {
      Object value = get(name);
      BigDecimal decimal;
      if (value instanceof Double number) decimal = BigDecimal.valueOf(number);
      else if (value instanceof String text) decimal = new BigDecimal(text);
      else
        throw new IllegalArgumentException(
            "Parameter (" + name + ") requires a real or exact decimal category.");
      if (decimal.signum() <= 0)
        throw new IllegalArgumentException("Parameter (" + name + ") must be positive.");
      return decimal;
    }

    private void finish() {
      if (!used.equals(assignment.values().keySet())) {
        var inactive = new HashSet<>(assignment.values().keySet());
        inactive.removeAll(used);
        throw new IllegalArgumentException(
            "Reference template assignment contains inactive or unknown controls: " + inactive);
      }
    }
  }
}
