package greycos.solver.greybay.qualification;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import greycos.solver.core.api.score.Score;
import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.api.QualificationResult;
import greycos.solver.greybay.api.StudyResult;
import greycos.solver.greybay.config.GreybayStudyDefinition;
import greycos.solver.greybay.config.ProblemPanel;
import greycos.solver.greybay.impl.persistence.StoreJson;

/**
 * Explicitly invoked Stage D campaign tooling, kept in test sources without a public runtime API
 * commitment. Constructing or freezing a campaign performs no solve. Ordinary unit tests use a
 * controlled runner.
 */
public final class CampaignHarness {
  public enum Method {
    TPE,
    RANDOM
  }

  public enum Model {
    BASIC_SCHEDULING,
    LIST_ROUTING,
    MIXED
  }

  public enum Size {
    SMALL,
    MEDIUM,
    LARGE
  }

  /** No solve horizon or active-time allocation has a universal default. */
  public record Protocol(
      String family,
      Model model,
      Map<String, Size> instanceSizes,
      List<Long> optimizerSeeds,
      Duration searchAllocation,
      Duration qualificationAllocation,
      long maximumTrials,
      long bootstrapSeed,
      String reportingCriteria) {
    public Protocol {
      ComponentIdentity.requireText(family, "family");
      Objects.requireNonNull(model, "model");
      instanceSizes = Map.copyOf(instanceSizes);
      optimizerSeeds = List.copyOf(optimizerSeeds);
      ComponentIdentity.requireText(reportingCriteria, "reportingCriteria");
      if (optimizerSeeds.isEmpty()
          || new HashSet<>(optimizerSeeds).size() != optimizerSeeds.size()) {
        throw new IllegalArgumentException("optimizerSeeds must be nonempty and unique.");
      }
      if (maximumTrials <= 0) {
        throw new IllegalArgumentException(
            "maximumTrials must be positive for a sampler campaign.");
      }
      positiveNanos(searchAllocation, "searchAllocation");
      positiveNanos(qualificationAllocation, "qualificationAllocation");
      Math.addExact(searchAllocation.toNanos(), qualificationAllocation.toNanos());
    }

    public static List<Long> referenceOptimizerSeeds() {
      return List.of(101L, 211L, 307L, 401L, 503L, 601L, 701L, 809L, 907L, 1009L);
    }
  }

  @FunctionalInterface
  public interface DefinitionFactory<Solution_, Score_ extends Score<Score_>> {
    GreybayStudyDefinition<Solution_, Score_> create(Method method, long optimizerSeed);
  }

  public record Request<Solution_, Score_ extends Score<Score_>>(
      Method method, long optimizerSeed, GreybayStudyDefinition<Solution_, Score_> definition) {
    public String directoryName() {
      return method.name().toLowerCase(java.util.Locale.ROOT) + "-" + optimizerSeed;
    }
  }

  /**
   * Cost is the study coordinator's measured/lower-bound active cost, never a fabricated solve-time
   * total.
   */
  public record Evidence<Score_ extends Score<Score_>>(
      StudyResult<Score_> search,
      StudyResult<Score_> finished,
      QualificationResult<Score_> qualification,
      String detail) {
    public Evidence {
      Objects.requireNonNull(search, "search");
      Objects.requireNonNull(finished, "finished");
      Objects.requireNonNull(detail, "detail");
      if (search.measuredActiveNanos() < 0
          || finished.measuredActiveNanos() < search.measuredActiveNanos()
          || search.admissionChargedNanos() < search.measuredActiveNanos()
          || finished.admissionChargedNanos() < search.admissionChargedNanos()
          || finished.admissionChargedNanos() < finished.measuredActiveNanos()) {
        throw new IllegalArgumentException(
            "Campaign accounting must be nonnegative and cumulative.");
      }
    }
  }

  @FunctionalInterface
  public interface Runner<Solution_, Score_ extends Score<Score_>> {
    Evidence<Score_> run(Request<Solution_, Score_> request, Protocol protocol, Path directory);
  }

  public record Run<Score_ extends Score<Score_>>(
      Method method, long optimizerSeed, Evidence<Score_> evidence) {}

  public record Frozen<Solution_, Score_ extends Score<Score_>>(
      Protocol protocol, Map<String, Object> manifest, List<Request<Solution_, Score_>> requests) {
    public Frozen {
      manifest = StoreJson.immutableObject(manifest);
      requests = List.copyOf(requests);
    }

    public byte[] manifestBytes() {
      return StoreJson.canonicalBytes(manifest);
    }

    public String fingerprint() {
      return StoreJson.sha256(manifestBytes());
    }

    /** A new directory is required; the protocol is durable before the first runner invocation. */
    public List<Run<Score_>> execute(Path directory, Runner<Solution_, Score_> runner)
        throws IOException {
      // Recheck all definitions before any measurements, including callback-declared identities.
      for (var request : requests) {
        if (!commonIdentity(request.definition()).equals(manifest.get("study"))) {
          throw new IllegalStateException(
              "Frozen study identity changed before execution: " + request.directoryName());
        }
      }
      Files.createDirectory(directory);
      Files.write(
          directory.resolve("campaign-manifest.json"),
          manifestBytes(),
          StandardOpenOption.CREATE_NEW);
      var runs = new ArrayList<Run<Score_>>();
      for (var request : requests) {
        var evidence = runner.run(request, protocol, directory.resolve(request.directoryName()));
        runs.add(new Run<>(request.method(), request.optimizerSeed(), evidence));
        CampaignReport.write(this, runs, directory);
      }
      return List.copyOf(runs);
    }
  }

  private CampaignHarness() {}

  public static <Solution_, Score_ extends Score<Score_>> Frozen<Solution_, Score_> freeze(
      Protocol protocol, DefinitionFactory<Solution_, Score_> factory) {
    var requests = new ArrayList<Request<Solution_, Score_>>();
    Map<String, Object> common = null;
    var samplerIdentities = new LinkedHashMap<String, Object>();
    int seedIndex = 0;
    for (long seed : protocol.optimizerSeeds()) {
      // Predeclared alternating order balances first/second execution position without
      // outcome-driven changes.
      var order =
          seedIndex++ % 2 == 0
              ? List.of(Method.TPE, Method.RANDOM)
              : List.of(Method.RANDOM, Method.TPE);
      for (var method : order) {
        var definition = factory.create(method, seed);
        if (definition.optimizerSeed() != seed
            || definition.runLimits().maximumCandidateTrials() != protocol.maximumTrials()
            || !definition.runLimits().activeTimeLimit().equals(protocol.searchAllocation())) {
          throw new IllegalArgumentException(
              "Definition optimizer seed and search limits must match the frozen protocol.");
        }
        var identity = commonIdentity(definition);
        if (common == null) {
          common = identity;
          var ids = new HashSet<>(definition.trainingPanel().problemIds());
          ids.addAll(definition.heldOutPanel().problemIds());
          if (!ids.equals(protocol.instanceSizes().keySet())) {
            throw new IllegalArgumentException(
                "instanceSizes must label every training and held-out instance exactly once.");
          }
        } else if (!common.equals(identity)) {
          throw new IllegalArgumentException(
              "TPE/random and every optimizer seed must share identical frozen study inputs.");
        }
        var sampler = component(definition.sampler().identity());
        var previous = samplerIdentities.putIfAbsent(method.name(), sampler);
        if (previous != null && !previous.equals(sampler)) {
          throw new IllegalArgumentException(
              "Sampler identity changed across optimizer seeds for " + method);
        }
        requests.add(new Request<>(method, seed, definition));
      }
    }
    if (samplerIdentities
        .get(Method.TPE.name())
        .equals(samplerIdentities.get(Method.RANDOM.name()))) {
      throw new IllegalArgumentException(
          "TPE and random require distinct, explicitly identified samplers.");
    }
    var manifest = new LinkedHashMap<String, Object>();
    manifest.put("schema", "greybay-campaign-v1");
    manifest.put("family", protocol.family());
    manifest.put("model", protocol.model().name());
    var sizes = new LinkedHashMap<String, Object>();
    protocol.instanceSizes().forEach((id, size) -> sizes.put(id, size.name()));
    manifest.put("instanceSizes", sizes);
    manifest.put("study", common);
    manifest.put("samplers", samplerIdentities);
    manifest.put(
        "optimizerSeeds", protocol.optimizerSeeds().stream().map(Object::toString).toList());
    manifest.put("referenceOptimizerSeedCount", "10");
    manifest.put("searchActiveNanos", Long.toString(protocol.searchAllocation().toNanos()));
    manifest.put(
        "qualificationActiveNanos", Long.toString(protocol.qualificationAllocation().toNanos()));
    manifest.put("maximumTrials", Long.toString(protocol.maximumTrials()));
    manifest.put("bootstrapSeed", Long.toString(protocol.bootstrapSeed()));
    manifest.put("bootstrapResamples", Integer.toString(InstancePairedBootstrap.RESAMPLES));
    manifest.put(
        "bootstrapEstimator",
        "equal-instance mean of paired seed means; 95% percentile inverse empirical CDF");
    manifest.put("reportingCriteria", protocol.reportingCriteria());
    manifest.put("executionOrder", requests.stream().map(Request::directoryName).toList());
    return new Frozen<>(protocol, manifest, requests);
  }

  static Map<String, Object> commonIdentity(GreybayStudyDefinition<?, ?> definition) {
    var result = new LinkedHashMap<String, Object>();
    var writer = new StringWriter();
    definition.baseSolverConfig().writeXml(writer);
    result.put("baselineXml", writer.toString());
    result.put("adapter", component(definition.problemAdapter().identity()));
    result.put(
        "verifier",
        definition
            .problemAdapter()
            .verifier()
            .map(verifier -> component(verifier.identity()))
            .orElse(null));
    result.put("objective", component(definition.objective().identity()));
    result.put(
        "reconstruction",
        definition.runtimeReconstructionProvider() == null
            ? null
            : component(definition.runtimeReconstructionProvider().identity()));
    result.put("training", panel(definition.trainingPanel()));
    result.put("heldOut", panel(definition.heldOutPanel()));
    var descriptors = new ArrayList<Map<String, Object>>();
    var ids = new ArrayList<>(definition.trainingPanel().problemIds());
    ids.addAll(definition.heldOutPanel().problemIds());
    for (var id : ids) {
      var descriptor = definition.problemAdapter().describe(id);
      if (!descriptor.id().equals(id)) {
        throw new IllegalArgumentException(
            "Problem descriptor ID differs from requested ID: " + id);
      }
      var capabilities = descriptor.capabilities();
      descriptors.add(
          Map.of(
              "id",
              descriptor.id(),
              "contentFingerprint",
              descriptor.contentFingerprint(),
              "basicVariables",
              capabilities.basicVariables(),
              "listVariables",
              capabilities.listVariables(),
              "allowsUnassigned",
              capabilities.allowsUnassigned(),
              "pinned",
              capabilities.pinned(),
              "scoreType",
              capabilities.scoreType(),
              "scoreLevels",
              capabilities.scoreLevelLabels(),
              "features",
              capabilities.features().stream().sorted().toList()));
    }
    result.put("instances", descriptors);
    result.put(
        "templates",
        definition.templates().stream()
            .map(
                template ->
                    Map.<String, Object>of(
                        "identity",
                        component(template.identity()),
                        "space",
                        template.searchSpace().toMap(),
                        "defaultAssignment",
                        template.defaultAssignment().toMap()))
            .toList());
    result.put("solveNanos", Long.toString(definition.solveDuration().toNanos()));
    result.put("warmupNanos", Long.toString(definition.warmupDuration().toNanos()));
    result.put("preflightSeed", Long.toString(definition.preflightSeed()));
    result.put("warmupSeed", Long.toString(definition.warmupSeed()));
    var resources = definition.resourcePolicy();
    result.put(
        "resources",
        Map.of(
            "moveThreadCount",
            resources.moveThreadCount(),
            "allowInternalWorkers",
            resources.allowInternalWorkers(),
            "environmentIdentity",
            resources.environmentIdentity()));
    return StoreJson.immutableObject(result);
  }

  private static Map<String, Object> panel(ProblemPanel panel) {
    return Map.of(
        "instances",
        panel.problemIds(),
        "solverSeeds",
        panel.seeds().stream().map(Object::toString).toList());
  }

  private static Map<String, Object> component(ComponentIdentity identity) {
    return Map.of(
        "id",
        identity.id(),
        "version",
        identity.version(),
        "artifact",
        identity.artifactIdentity());
  }

  private static void positiveNanos(Duration duration, String name) {
    Objects.requireNonNull(duration, name);
    if (duration.isNegative() || duration.isZero()) {
      throw new IllegalArgumentException(name + " must be positive.");
    }
    duration.toNanos();
  }
}
