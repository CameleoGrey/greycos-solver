package greycos.solver.greybay.config;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.greybay.api.ConfigurationTemplate;
import greycos.solver.greybay.api.ProblemAdapter;
import greycos.solver.greybay.api.RuntimeReconstructionProvider;
import greycos.solver.greybay.api.objective.StudyObjective;
import greycos.solver.greybay.api.sampling.Sampler;
import greycos.solver.greybay.impl.config.FrozenSolverConfig;

/**
 * Immutable Java-first study definition. Component implementations must keep their declared
 * identity and behavior stable. Operational admission limits are excluded from the immutable
 * experiment identity and may increase on resume.
 */
public final class GreybayStudyDefinition<Solution_, Score_ extends Score<Score_>> {
  private final FrozenSolverConfig base;
  private final ProblemAdapter<Solution_> problemAdapter;
  private final ProblemPanel trainingPanel;
  private final ProblemPanel heldOutPanel;
  private final List<ConfigurationTemplate> templates;
  private final StudyObjective<Score_> objective;
  private final Sampler sampler;
  private final long optimizerSeed;
  private final EvaluationProtocol protocol;
  private final StudyRunLimits runLimits;
  private final RuntimeReconstructionProvider runtimeReconstructionProvider;
  private final ParameterAssignment baselineCandidateAssignment;

  private GreybayStudyDefinition(Builder<Solution_, Score_> builder) {
    problemAdapter = Objects.requireNonNull(builder.problemAdapter, "problemAdapter");
    trainingPanel = Objects.requireNonNull(builder.trainingPanel, "trainingPanel");
    heldOutPanel = Objects.requireNonNull(builder.heldOutPanel, "heldOutPanel");
    templates = List.copyOf(Objects.requireNonNull(builder.templates, "templates"));
    if (templates.isEmpty()
        || templates.stream().map(t -> t.identity().id()).distinct().count() != templates.size()) {
      throw new IllegalArgumentException(
          "templates must be nonempty and have unique identity IDs.");
    }
    objective = Objects.requireNonNull(builder.objective, "objective");
    sampler = Objects.requireNonNull(builder.sampler, "sampler");
    optimizerSeed = Objects.requireNonNull(builder.optimizerSeed, "optimizerSeed");
    protocol =
        new EvaluationProtocol(
            builder.solveDuration,
            builder.warmupDuration,
            builder.preflightSeed,
            builder.warmupSeed,
            builder.resourcePolicy);
    runLimits =
        new StudyRunLimits(
            Objects.requireNonNull(builder.maxTrials, "maxTrials"), builder.activeTimeLimit);
    var trainingSeeds = new HashSet<>(trainingPanel.seeds());
    if (heldOutPanel.seeds().stream().anyMatch(trainingSeeds::contains)
        || trainingSeeds.contains(protocol.preflightSeed())
        || trainingSeeds.contains(protocol.warmupSeed())
        || heldOutPanel.seeds().contains(protocol.preflightSeed())
        || heldOutPanel.seeds().contains(protocol.warmupSeed())) {
      throw new IllegalArgumentException(
          "Training, held-out, warmup and preflight seeds must be disjoint.");
    }
    if (trainingPanel.problemIds().stream().anyMatch(heldOutPanel.problemIds()::contains)) {
      throw new IllegalArgumentException("Training and held-out problem IDs must be disjoint.");
    }
    baselineCandidateAssignment = builder.baselineCandidateAssignment;
    runtimeReconstructionProvider = builder.runtimeReconstructionProvider;
    base =
        FrozenSolverConfig.capture(
            Objects.requireNonNull(builder.baseSolverConfig, "baseSolverConfig"),
            runtimeReconstructionProvider);
  }

  public static <Solution_, Score_ extends Score<Score_>> Builder<Solution_, Score_> builder() {
    return new Builder<>();
  }

  public SolverConfig baseSolverConfig() {
    return base.freshConfig();
  }

  public ProblemAdapter<Solution_> problemAdapter() {
    return problemAdapter;
  }

  public ProblemPanel trainingPanel() {
    return trainingPanel;
  }

  public ProblemPanel heldOutPanel() {
    return heldOutPanel;
  }

  public List<ConfigurationTemplate> templates() {
    return templates;
  }

  public StudyObjective<Score_> objective() {
    return objective;
  }

  public Sampler sampler() {
    return sampler;
  }

  public long optimizerSeed() {
    return optimizerSeed;
  }

  public EvaluationProtocol protocol() {
    return protocol;
  }

  public StudyRunLimits runLimits() {
    return runLimits;
  }

  public Duration solveDuration() {
    return protocol.solveDuration();
  }

  public Duration warmupDuration() {
    return protocol.warmupDuration();
  }

  public long preflightSeed() {
    return protocol.preflightSeed();
  }

  public long warmupSeed() {
    return protocol.warmupSeed();
  }

  public ResourcePolicy resourcePolicy() {
    return protocol.resourcePolicy();
  }

  public ParameterAssignment baselineCandidateAssignment() {
    return baselineCandidateAssignment;
  }

  public RuntimeReconstructionProvider runtimeReconstructionProvider() {
    return runtimeReconstructionProvider;
  }

  public static final class Builder<Solution_, Score_ extends Score<Score_>> {
    private SolverConfig baseSolverConfig;
    private ProblemAdapter<Solution_> problemAdapter;
    private ProblemPanel trainingPanel;
    private ProblemPanel heldOutPanel;
    private List<ConfigurationTemplate> templates;
    private StudyObjective<Score_> objective;
    private Sampler sampler;
    private Long optimizerSeed;
    private Duration solveDuration;
    private Duration warmupDuration = Duration.ofSeconds(30);
    private long preflightSeed = Long.MIN_VALUE;
    private long warmupSeed = Long.MIN_VALUE + 1;
    private ResourcePolicy resourcePolicy = ResourcePolicy.singleThreaded();
    private Long maxTrials;
    private Duration activeTimeLimit;
    private RuntimeReconstructionProvider runtimeReconstructionProvider;
    private ParameterAssignment baselineCandidateAssignment;

    public Builder<Solution_, Score_> baseSolverConfig(SolverConfig value) {
      baseSolverConfig = value;
      return this;
    }

    public Builder<Solution_, Score_> problemAdapter(ProblemAdapter<Solution_> value) {
      problemAdapter = value;
      return this;
    }

    public Builder<Solution_, Score_> trainingPanel(ProblemPanel value) {
      trainingPanel = value;
      return this;
    }

    public Builder<Solution_, Score_> heldOutPanel(ProblemPanel value) {
      heldOutPanel = value;
      return this;
    }

    public Builder<Solution_, Score_> templates(List<ConfigurationTemplate> value) {
      templates = value;
      return this;
    }

    public Builder<Solution_, Score_> objective(StudyObjective<Score_> value) {
      objective = value;
      return this;
    }

    public Builder<Solution_, Score_> sampler(SamplerConfig value) {
      sampler = value.buildSampler();
      return this;
    }

    public Builder<Solution_, Score_> sampler(Sampler value) {
      sampler = value;
      return this;
    }

    public Builder<Solution_, Score_> optimizerSeed(long value) {
      optimizerSeed = value;
      return this;
    }

    public Builder<Solution_, Score_> solveDuration(Duration value) {
      solveDuration = value;
      return this;
    }

    public Builder<Solution_, Score_> warmupDuration(Duration value) {
      warmupDuration = value;
      return this;
    }

    public Builder<Solution_, Score_> preflightSeed(long value) {
      preflightSeed = value;
      return this;
    }

    public Builder<Solution_, Score_> warmupSeed(long value) {
      warmupSeed = value;
      return this;
    }

    public Builder<Solution_, Score_> resourcePolicy(ResourcePolicy value) {
      resourcePolicy = value;
      return this;
    }

    public Builder<Solution_, Score_> maxTrials(long value) {
      maxTrials = value;
      return this;
    }

    public Builder<Solution_, Score_> activeTimeLimit(Duration value) {
      activeTimeLimit = value;
      return this;
    }

    public Builder<Solution_, Score_> baselineCandidateAssignment(ParameterAssignment value) {
      baselineCandidateAssignment = value;
      return this;
    }

    public Builder<Solution_, Score_> runtimeReconstructionProvider(
        RuntimeReconstructionProvider value) {
      runtimeReconstructionProvider = value;
      return this;
    }

    public GreybayStudyDefinition<Solution_, Score_> build() {
      return new GreybayStudyDefinition<>(this);
    }
  }
}
