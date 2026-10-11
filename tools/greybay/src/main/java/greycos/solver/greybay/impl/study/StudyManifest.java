package greycos.solver.greybay.impl.study;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.api.ProblemDescriptor;
import greycos.solver.greybay.api.objective.BalancedQualityObjective;
import greycos.solver.greybay.config.GreybayStudyDefinition;
import greycos.solver.greybay.config.ProblemPanel;
import greycos.solver.greybay.config.SearchSpace;
import greycos.solver.greybay.impl.config.FrozenSolverConfig;
import greycos.solver.greybay.impl.persistence.StoreJson;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.slf4j.LoggerFactory;

record StudyManifest(
    Map<String, Object> value,
    Map<String, ProblemDescriptor> descriptors,
    SearchSpace searchSpace) {
  static StudyManifest prepare(GreybayStudyDefinition<?, ?> definition) {
    var descriptors = new LinkedHashMap<String, ProblemDescriptor>();
    var fingerprints = new LinkedHashMap<String, String>();
    var encodedProblems = new ArrayList<Map<String, Object>>();
    for (var panel : List.of(definition.trainingPanel(), definition.heldOutPanel())) {
      for (String id : panel.problemIds()) {
        var descriptor = definition.problemAdapter().describe(id);
        if (!id.equals(descriptor.id())) {
          throw new IllegalArgumentException(
              "Problem descriptor ID ("
                  + descriptor.id()
                  + ") does not match requested ID ("
                  + id
                  + ").");
        }
        String duplicate = fingerprints.putIfAbsent(descriptor.contentFingerprint(), id);
        if (duplicate != null) {
          throw new IllegalArgumentException(
              "Duplicate problem fingerprint for ("
                  + duplicate
                  + ") and ("
                  + id
                  + "); training and held-out instances must be independent.");
        }
        if (!descriptors.isEmpty()) {
          var reference = descriptors.values().iterator().next().capabilities();
          if (!reference.scoreType().equals(descriptor.capabilities().scoreType())
              || !reference
                  .scoreLevelLabels()
                  .equals(descriptor.capabilities().scoreLevelLabels())) {
            throw new IllegalArgumentException(
                "All problems must declare one score type and level layout.");
          }
        }
        descriptors.put(id, descriptor);
        var capability = descriptor.capabilities();
        encodedProblems.add(
            Map.of(
                "id",
                id,
                "fingerprint",
                descriptor.contentFingerprint(),
                "capabilities",
                Map.of(
                    "basicVariables",
                    capability.basicVariables(),
                    "listVariables",
                    capability.listVariables(),
                    "allowsUnassigned",
                    capability.allowsUnassigned(),
                    "pinned",
                    capability.pinned(),
                    "scoreType",
                    capability.scoreType(),
                    "scoreLevelLabels",
                    capability.scoreLevelLabels(),
                    "features",
                    capability.features().stream().sorted().toList())));
      }
    }
    if (definition.objective() instanceof BalancedQualityObjective<?> balanced) {
      balanced.validateProblemIds(descriptors.keySet());
    }
    var spaces = new LinkedHashMap<String, SearchSpace>();
    var templates = new ArrayList<Map<String, Object>>();
    var code = new TreeMap<String, Object>();
    definition
        .templates()
        .forEach(
            template -> {
              template.searchSpace().validate(template.defaultAssignment());
              definition
                  .trainingPanel()
                  .problemIds()
                  .forEach(id -> template.validate(descriptors.get(id).capabilities()));
              spaces.put(template.identity().id(), template.searchSpace());
              templates.add(
                  Map.of(
                      "identity",
                      identity(template.identity()),
                      "schema",
                      template.searchSpace().toMap(),
                      "defaultAssignment",
                      template.defaultAssignment().toMap()));
              addClassIdentity(code, template.getClass());
            });
    var searchSpace = SearchSpace.combine("greybay-combined-space-v1", spaces);
    var base = definition.baseSolverConfig();
    base.visitReferencedClasses(
        type -> {
          if (type != null) addClassIdentity(code, type);
        });
    addClassIdentity(code, SolverConfig.class);
    addClassIdentity(code, NormalDistribution.class);
    addClassIdentity(code, LoggerFactory.getILoggerFactory().getClass());
    addClassIdentity(code, DefaultGreybayStudy.class);
    addClassIdentity(code, definition.problemAdapter().getClass());
    addClassIdentity(code, definition.objective().getClass());
    addClassIdentity(code, definition.objective().scoreAdapter().getClass());
    addClassIdentity(code, definition.sampler().getClass());
    if (definition.runtimeReconstructionProvider() != null) {
      addClassIdentity(code, definition.runtimeReconstructionProvider().getClass());
    }
    var manifest = new LinkedHashMap<String, Object>();
    manifest.put("schemaVersion", "1");
    manifest.put("protocolVersion", "greybay-on-time-synchronous-snapshot-v1");
    manifest.put(
        "baseConfigIdentity",
        FrozenSolverConfig.canonicalIdentity(
            base,
            definition.runtimeReconstructionProvider() == null
                ? null
                : definition.runtimeReconstructionProvider().identity()));
    manifest.put("templates", templates);
    manifest.put(
        "baselineCandidateAssignment",
        definition.baselineCandidateAssignment() == null
            ? null
            : definition.baselineCandidateAssignment().toMap());
    manifest.put("searchSpace", searchSpace.toMap());
    manifest.put("adapter", identity(definition.problemAdapter().identity()));
    manifest.put(
        "verifier",
        definition
            .problemAdapter()
            .verifier()
            .map(
                verifier -> {
                  addClassIdentity(code, verifier.getClass());
                  return identity(verifier.identity());
                })
            .orElse(null));
    manifest.put(
        "runtimeReconstruction",
        definition.runtimeReconstructionProvider() == null
            ? null
            : identity(definition.runtimeReconstructionProvider().identity()));
    manifest.put("objective", identity(definition.objective().identity()));
    manifest.put("scoreAdapter", identity(definition.objective().scoreAdapter().identity()));
    manifest.put("sampler", identity(definition.sampler().identity()));
    manifest.put("optimizerSeed", Long.toString(definition.optimizerSeed()));
    manifest.put("problems", encodedProblems);
    manifest.put("trainingPanel", panel(definition.trainingPanel()));
    manifest.put("heldOutPanel", panel(definition.heldOutPanel()));
    manifest.put("solveMillis", Long.toString(definition.solveDuration().toMillis()));
    manifest.put("graceMillis", Long.toString(definition.protocol().graceDuration().toMillis()));
    manifest.put("warmupMillis", Long.toString(definition.warmupDuration().toMillis()));
    manifest.put("preflightSeed", Long.toString(definition.preflightSeed()));
    manifest.put("warmupSeed", Long.toString(definition.warmupSeed()));
    manifest.put(
        "resourcePolicy",
        Map.of(
            "moveThreadCount",
            definition.resourcePolicy().moveThreadCount(),
            "allowInternalWorkers",
            definition.resourcePolicy().allowInternalWorkers(),
            "applicationEnvironment",
            definition.resourcePolicy().environmentIdentity()));
    manifest.put("environment", environment());
    manifest.put("applicationClassDigests", code);
    manifest.put("studyId", StoreJson.sha256(StoreJson.canonicalBytes(manifest)));
    return new StudyManifest(
        StoreJson.immutableObject(manifest), Map.copyOf(descriptors), searchSpace);
  }

  static Map<String, Object> identity(ComponentIdentity identity) {
    return Map.of(
        "id",
        identity.id(),
        "version",
        identity.version(),
        "artifactIdentity",
        identity.artifactIdentity());
  }

  static Map<String, Object> panel(ProblemPanel panel) {
    return Map.of(
        "problemIds",
        panel.problemIds(),
        "seeds",
        panel.seeds().stream().map(Object::toString).toList());
  }

  private static Map<String, Object> environment() {
    var values = new TreeMap<String, Object>();
    for (String name :
        List.of(
            "java.version",
            "java.vendor",
            "java.vm.name",
            "java.vm.version",
            "os.name",
            "os.version",
            "os.arch")) values.put(name, System.getProperty(name, "unknown"));
    values.put("availableProcessors", Integer.toString(Runtime.getRuntime().availableProcessors()));
    values.put("maximumHeapBytes", Long.toString(Runtime.getRuntime().maxMemory()));
    values.put("vmArguments", ManagementFactory.getRuntimeMXBean().getInputArguments());
    values.put("numericalImplementation", "StrictMath-SplitMix64-v1");
    values.put("cpuIdentity", cpuIdentity());
    var logging = new TreeMap<String, Object>();
    System.getProperties().stringPropertyNames().stream()
        .filter(
            name ->
                name.startsWith("org.slf4j.simpleLogger.")
                    || name.startsWith("logback.")
                    || name.startsWith("log4j")
                    || name.startsWith("java.util.logging."))
        .forEach(name -> logging.put(name, System.getProperty(name)));
    for (String resource :
        List.of("logback.xml", "logback-test.xml", "log4j2.xml", "logging.properties")) {
      try (var input =
          Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
        if (input != null)
          logging.put("resource:" + resource, StoreJson.sha256(input.readAllBytes()));
      } catch (IOException e) {
        throw new IllegalStateException("Cannot fingerprint logging configuration.", e);
      }
    }
    values.put("logging", logging);
    return values;
  }

  private static void addClassIdentity(Map<String, Object> identities, Class<?> type) {
    if (type.isArray()) type = type.getComponentType();
    if (type.isPrimitive()) return;
    String name = type.getName();
    if (identities.containsKey(name)) return;
    var source = type.getProtectionDomain().getCodeSource();
    if (source != null) {
      try {
        Path location = Path.of(source.getLocation().toURI()).toRealPath();
        String key = "artifact:" + location;
        if (!identities.containsKey(key)) identities.put(key, artifactDigest(location));
      } catch (Exception e) {
        throw new IllegalStateException(
            "Cannot fingerprint application artifact for (" + name + ").", e);
      }
    }
    try (var input = type.getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
      // Dynamically generated callbacks require their declared application artifact identity.
      identities.put(
          name,
          input == null
              ? "application-artifact-identity-required"
              : StoreJson.sha256(input.readAllBytes()));
    } catch (IOException e) {
      throw new IllegalStateException("Unable to fingerprint configured class (" + name + ").", e);
    }
  }

  private static String cpuIdentity() {
    Path cpuInfo = Path.of("/proc/cpuinfo");
    if (Files.isReadable(cpuInfo)) {
      try (var lines = Files.lines(cpuInfo)) {
        return lines
            .filter(
                line ->
                    line.startsWith("vendor_id")
                        || line.startsWith("model name")
                        || line.startsWith("cpu family")
                        || line.startsWith("model\t")
                        || line.startsWith("flags")
                        || line.startsWith("Hardware")
                        || line.startsWith("CPU implementer")
                        || line.startsWith("CPU part"))
            .map(String::trim)
            .distinct()
            .sorted()
            .reduce("", (left, right) -> left + "\n" + right);
      } catch (IOException e) {
        throw new IllegalStateException("Cannot read CPU identity.", e);
      }
    }
    return System.getProperty("os.arch")
        + ":"
        + System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "unavailable");
  }

  private static String artifactDigest(Path source) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    List<Path> files;
    if (Files.isDirectory(source)) {
      try (var stream = Files.walk(source)) {
        files = stream.filter(Files::isRegularFile).sorted().toList();
      }
    } else files = List.of(source);
    for (Path file : files) {
      String relative =
          Files.isDirectory(source)
              ? source.relativize(file).toString().replace('\\', '/')
              : source.getFileName().toString();
      byte[] name = relative.getBytes(StandardCharsets.UTF_8);
      digest.update(Integer.toString(name.length).getBytes(StandardCharsets.UTF_8));
      digest.update((byte) ':');
      digest.update(name);
      digest.update(Long.toString(Files.size(file)).getBytes(StandardCharsets.UTF_8));
      digest.update((byte) ':');
      try (var input = Files.newInputStream(file)) {
        byte[] buffer = new byte[16384];
        int read;
        while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
      }
    }
    return "sha256:" + HexFormat.of().formatHex(digest.digest());
  }
}
