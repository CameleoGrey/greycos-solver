package greycos.solver.greybay.impl.study;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.LongSupplier;

import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.solver.DefaultSolverFactory;
import greycos.solver.greybay.api.ConfigurationTemplate;
import greycos.solver.greybay.api.EvaluationRecord;
import greycos.solver.greybay.api.EvaluationStatus;
import greycos.solver.greybay.api.ExportResult;
import greycos.solver.greybay.api.GreybayStudy;
import greycos.solver.greybay.api.QualificationComparison;
import greycos.solver.greybay.api.QualificationResult;
import greycos.solver.greybay.api.QualificationStatus;
import greycos.solver.greybay.api.StudyResult;
import greycos.solver.greybay.api.StudyState;
import greycos.solver.greybay.api.TrainingStatus;
import greycos.solver.greybay.api.TrialResult;
import greycos.solver.greybay.api.TrialStatus;
import greycos.solver.greybay.api.VerificationResult;
import greycos.solver.greybay.api.objective.CompletedPanel;
import greycos.solver.greybay.api.objective.ExactRational;
import greycos.solver.greybay.api.objective.ObjectiveCalibration;
import greycos.solver.greybay.api.objective.ObjectiveValue;
import greycos.solver.greybay.api.objective.ScoredObservation;
import greycos.solver.greybay.api.sampling.SamplerState;
import greycos.solver.greybay.api.sampling.SamplingContext;
import greycos.solver.greybay.api.sampling.SamplingObservation;
import greycos.solver.greybay.config.GreybayStudyDefinition;
import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.config.ProblemPanel;
import greycos.solver.greybay.config.StudyRunLimits;
import greycos.solver.greybay.impl.config.FrozenSolverConfig;
import greycos.solver.greybay.impl.evaluation.EvaluationOutcome;
import greycos.solver.greybay.impl.evaluation.TrialEvaluator;
import greycos.solver.greybay.impl.persistence.ArtifactReference;
import greycos.solver.greybay.impl.persistence.JournalRecord;
import greycos.solver.greybay.impl.persistence.StoreJson;
import greycos.solver.greybay.impl.persistence.StudyStore;

/** Serial coordinator. Journal commits precede every sampler-visible state mutation. */
public final class DefaultGreybayStudy<Solution_, Score_ extends Score<Score_>>
    implements GreybayStudy<Solution_, Score_> {
  private final GreybayStudyDefinition<Solution_, Score_> definition;
  private final StudyManifest manifest;
  private final StudyStore store;
  private final StudyRunLimits limits;
  private final EvaluationRunner<Solution_, Score_> evaluator;
  private final LongSupplier nanoClock;
  private final Function<String, Score_> scoreParser;
  private final Object lifecycle = new Object();
  private final Object controlState = new Object();
  private final AtomicBoolean stopRequested = new AtomicBoolean();
  private final ScheduledExecutorService heartbeatExecutor;
  private final Map<Long, Trial> trials = new LinkedHashMap<>();
  private final Map<String, EvaluationRecord<Score_>> terminalCells = new LinkedHashMap<>();
  private final List<EvaluationRecord<Score_>> observations = new ArrayList<>();
  private final Map<String, Map<String, Object>> startedAttempts = new LinkedHashMap<>();
  private final Map<String, Long> nextAttempts = new HashMap<>();
  private final Map<String, Map<String, Object>> operations = new LinkedHashMap<>();
  private final Set<String> reservedIdentities = new HashSet<>();
  private final Map<String, ConfigurationTemplate> templates = new LinkedHashMap<>();
  private final List<Map<String, Object>> progression = new ArrayList<>();
  private volatile StudyResult<Score_> committedSnapshot;
  private volatile StudyState state = StudyState.CREATED;
  private volatile ActiveEvaluation activeEvaluation;
  private volatile String activeOperationId;
  private volatile long activeOperationStarted;
  private volatile RuntimeException heartbeatFailure;
  private volatile boolean closed;
  private volatile boolean operating;
  private final Map<String, Long> committedOperationCosts = new HashMap<>();
  private Thread operationThread;
  private Throwable operationFailure;
  private boolean closeCompleted;
  private long session;
  private long operationSequence;
  private long measuredNanos;
  private long admissionNanos;
  private boolean unknownCost;
  private boolean preflightComplete;
  private boolean warmed;
  private String stopReason;
  private SamplerState samplerState;
  private ObjectiveCalibration calibration;
  private ObjectiveCalibration heldOutCalibration;
  private Long selectedTrialId;
  private QualificationResult<Score_> qualification;
  private boolean qualificationTerminal;

  public static <Solution_, Score_ extends Score<Score_>>
      DefaultGreybayStudy<Solution_, Score_> open(
          Path directory,
          GreybayStudyDefinition<Solution_, Score_> definition,
          StudyRunLimits limits,
          boolean resume) {
    var evaluator = new TrialEvaluator<Solution_, Score_>();
    return open(directory, definition, limits, resume, evaluator::evaluate, System::nanoTime);
  }

  static <Solution_, Score_ extends Score<Score_>> DefaultGreybayStudy<Solution_, Score_> open(
      Path directory,
      GreybayStudyDefinition<Solution_, Score_> definition,
      StudyRunLimits limits,
      boolean resume,
      EvaluationRunner<Solution_, Score_> evaluator,
      LongSupplier nanoClock) {
    long setupStart = nanoClock.getAsLong();
    var manifest = StudyManifest.prepare(definition);
    StudyStore store = null;
    DefaultGreybayStudy<Solution_, Score_> result = null;
    OpeningHeartbeat opening = null;
    try {
      store =
          resume
              ? StudyStore.resume(directory, manifest.value())
              : StudyStore.create(directory, manifest.value());
      long previousSession =
          store.records().stream()
              .mapToLong(
                  record ->
                      record.kind().equals("PROCESS_STARTED")
                          ? number(record.payload(), "session")
                          : operationSession((String) record.payload().get("operationId")))
              .max()
              .orElse(0);
      opening =
          new OpeningHeartbeat(
              store, "session-" + (previousSession + 1) + "/setup-1", setupStart, nanoClock);
      result = new DefaultGreybayStudy<>(definition, manifest, store, limits, evaluator, nanoClock);
      result.initialize(resume, setupStart, opening);
      return result;
    } catch (IOException e) {
      if (opening != null) opening.close();
      closeFailedStudy(result, store, e);
      throw new UncheckedIOException(e);
    } catch (RuntimeException | Error e) {
      if (opening != null) opening.close();
      closeFailedStudy(result, store, e);
      throw e;
    } finally {
      if (opening != null) opening.close();
    }
  }

  private static long operationSession(String operationId) {
    if (operationId == null || !operationId.startsWith("session-")) return 0;
    int separator = operationId.indexOf('/', "session-".length());
    if (separator < 0) return 0;
    return Long.parseLong(operationId.substring("session-".length(), separator));
  }

  private static void closeFailedStudy(
      DefaultGreybayStudy<?, ?> study, StudyStore store, Throwable failure) {
    if (study != null) {
      try {
        study.close();
      } catch (RuntimeException | Error cleanup) {
        failure.addSuppressed(cleanup);
      }
      return;
    }
    closeFailedStore(store, failure);
  }

  private static void closeFailedStore(StudyStore store, Throwable failure) {
    if (store != null) {
      try {
        store.close();
      } catch (IOException cleanup) {
        failure.addSuppressed(cleanup);
      }
    }
  }

  private DefaultGreybayStudy(
      GreybayStudyDefinition<Solution_, Score_> definition,
      StudyManifest manifest,
      StudyStore store,
      StudyRunLimits limits,
      EvaluationRunner<Solution_, Score_> evaluator,
      LongSupplier nanoClock) {
    this.definition = definition;
    this.manifest = manifest;
    this.store = store;
    this.limits = Objects.requireNonNull(limits, "limits");
    this.evaluator = evaluator;
    this.nanoClock = nanoClock;
    var factory =
        (DefaultSolverFactory<Solution_>)
            SolverFactory.<Solution_>create(definition.baseSolverConfig());
    var scoreDefinition = factory.<Score_>getScoreDirectorFactory().getScoreDefinition();
    scoreParser = scoreDefinition::parseScore;
    definition.templates().forEach(template -> templates.put(template.identity().id(), template));
    samplerState = definition.sampler().initialState(definition.optimizerSeed());
    heartbeatExecutor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "greybay-study-heartbeat");
              thread.setDaemon(true);
              return thread;
            });
  }

  private void initialize(boolean resumed, long setupStart, OpeningHeartbeat opening)
      throws IOException {
    for (var record : store.records()) replay(record);
    if (resumed) recoverInterruptedWork(opening.operationId);
    session = Math.max(session + 1, operationSession(opening.operationId));
    append(
        "PROCESS_STARTED",
        Map.of(
            "session",
            Long.toString(session),
            "resumed",
            resumed,
            "timestamp",
            Instant.now().toString()));
    append(
        "LIMITS",
        Map.of(
            "maximumCandidateTrials",
            Long.toString(limits.maximumCandidateTrials()),
            "activeTimeNanos",
            Long.toString(limits.activeTimeLimit().toNanos())));
    // OpeningHeartbeat has covered factory construction and recovery since the store lock was
    // acquired.
    // The elapsed origin also includes manifest inspection before the store became available.
    operating = true;
    operationThread = Thread.currentThread();
    operationSequence = 1;
    activeOperationId = opening.operationId;
    activeOperationStarted = setupStart;
    try {
      if (!trials.containsKey(0L)) reserveBaseline();
    } catch (RuntimeException | Error failure) {
      operationFailure = failure;
      fail(failure);
      throw failure;
    } finally {
      opening.close();
      if (opening.failure != null) {
        if (operationFailure != null) operationFailure.addSuppressed(opening.failure);
        else operationFailure = opening.failure;
      }
      endOperation();
    }
    if (opening.failure != null) throw opening.failure;
    heartbeatExecutor.scheduleAtFixedRate(this::heartbeat, 1, 1, TimeUnit.SECONDS);
    updateSnapshot();
  }

  @Override
  public StudyResult<Score_> run() {
    if (selectedTrialId != null)
      throw new IllegalStateException("Training cannot resume after selection is frozen.");
    beginOperation("search");
    try {
      checkAdmission();
      preflight();
      warmup();
      calibrate();
      setState(StudyState.SEARCHING, null);
      while (true) {
        checkAdmission();
        if (consecutiveCandidateFailures() >= 3) {
          setState(
              StudyState.FAILED,
              "Three consecutive candidate-specific failures; inspect their diagnostics.");
          break;
        }
        Trial pending =
            trials.values().stream()
                .filter(trial -> trial.id != 0 && !trial.terminal())
                .findFirst()
                .orElse(null);
        if (pending == null) {
          if (trials.size() - 1 >= limits.maximumCandidateTrials()) {
            setState(StudyState.SEARCH_FINISHED, "MAXIMUM_CANDIDATE_TRIALS");
            break;
          }
          pending = propose();
          if (pending == null) break;
        }
        runTrial(pending);
      }
    } catch (PausedWork pause) {
      pause();
    } catch (RuntimeException | Error failure) {
      operationFailure = failure;
      fail(failure);
      throw failure;
    } finally {
      endOperation();
    }
    return snapshot();
  }

  private void reserveBaseline() {
    var config =
        StudyProtocol.apply(
            definition,
            definition.baseSolverConfig(),
            null,
            null,
            definition.trainingPanel().seeds().getFirst(),
            definition.solveDuration());
    var frozen = FrozenSolverConfig.capture(config, definition.runtimeReconstructionProvider());
    var artifact = writeArtifact(frozen.xml().getBytes(StandardCharsets.UTF_8), "xml");
    var payload = new LinkedHashMap<String, Object>();
    payload.put("trialId", "0");
    payload.put("candidateIdentity", frozen.identity());
    payload.put("config", artifact.toMap());
    var assignment = definition.baselineCandidateAssignment();
    if (assignment != null) {
      var candidate = materialize(assignment, definition.solveDuration());
      if (candidate.failure != null || !candidate.identity.equals(frozen.identity())) {
        throw new IllegalArgumentException(
            "baselineCandidateAssignment does not materialize exactly the effective baseline.");
      }
      payload.put("assignment", assignment.toMap());
    }
    append("BASELINE_RESERVED", payload);
  }

  private void preflight() {
    setState(StudyState.PREFLIGHT, null);
    if (preflightComplete) return;
    for (var template : definition.templates()) {
      var candidate =
          materialize(
              withTemplate(template, template.defaultAssignment()), definition.solveDuration());
      if (candidate.failure != null)
        throw new IllegalStateException(
            "Default assignment failed preflight for template ("
                + template.identity().id()
                + "): "
                + candidate.failure);
      var artifact = storeConfiguration(candidate.config);
      for (String problem : definition.trainingPanel().problemIds()) {
        checkAdmission();
        var record =
            evaluateCell(
                "PREFLIGHT:" + template.identity().id(),
                -1,
                candidate.identity,
                artifact,
                problem,
                definition.preflightSeed(),
                definition.solveDuration());
        if (record.status() == EvaluationStatus.FAILED
            || record.status() == EvaluationStatus.INVALID_RESULT
            || record.status() == EvaluationStatus.BUDGET_EXCEEDED) {
          throw new IllegalStateException(
              "Template preflight failed on ("
                  + problem
                  + "): "
                  + record.status()
                  + "; "
                  + record.failureMessage());
        }
      }
    }
    append("PREFLIGHT_COMPLETE", Map.of());
  }

  private void warmup() {
    if (warmed) return;
    if (!definition.warmupDuration().isZero()) {
      for (var template : definition.templates()) {
        checkAdmission();
        var candidate =
            materialize(
                withTemplate(template, template.defaultAssignment()), definition.warmupDuration());
        if (candidate.failure != null)
          throw new IllegalStateException("Warmup materialization failed: " + candidate.failure);
        var record =
            evaluateCell(
                "WARMUP:" + session + ":" + template.identity().id(),
                -1,
                candidate.identity,
                storeConfiguration(candidate.config),
                definition.trainingPanel().problemIds().getFirst(),
                definition.warmupSeed(),
                definition.warmupDuration());
        if (record.status() == EvaluationStatus.FAILED
            || record.status() == EvaluationStatus.INVALID_RESULT
            || record.status() == EvaluationStatus.BUDGET_EXCEEDED) {
          throw new IllegalStateException("Template warmup failed: " + record.failureMessage());
        }
      }
    }
    append(
        "WARMUP_COMPLETE",
        Map.of(
            "session", Long.toString(session), "disabled", definition.warmupDuration().isZero()));
  }

  private void calibrate() {
    if (calibration != null) {
      Trial baseline = trials.get(0L);
      if (!baseline.terminal())
        completeTrial(
            baseline,
            definition
                .objective()
                .evaluate(
                    completedPanel("TRAINING", baseline, definition.trainingPanel()), calibration));
      return;
    }
    setState(StudyState.CALIBRATING, null);
    var baseline = trials.get(0L);
    setTrialRunning(baseline);
    for (String problem : definition.trainingPanel().problemIds()) {
      for (long seed : definition.trainingPanel().seeds()) {
        var cell =
            evaluateCell(
                "TRAINING",
                0,
                baseline.identity,
                baseline.config,
                problem,
                seed,
                definition.solveDuration());
        if (!cell.eligible()) {
          terminateTrial(
              baseline,
              cell.status() == EvaluationStatus.FAILED
                  ? TrialStatus.FAILED
                  : TrialStatus.INELIGIBLE,
              "Calibration failed for problem ("
                  + problem
                  + "), seed ("
                  + seed
                  + "), status ("
                  + cell.status()
                  + "). Correct initialization, solve budget or baseline before creating another study.");
          throw new IllegalStateException(baseline.failure);
        }
      }
    }
    var panel = completedPanel("TRAINING", baseline, definition.trainingPanel());
    var result = definition.objective().calibrate(panel);
    append(
        "CALIBRATION", Map.of("value", definition.objective().codec().encodeCalibration(result)));
    completeTrial(baseline, definition.objective().evaluate(panel, calibration));
  }

  private Trial propose() {
    var cache = new HashMap<ParameterAssignment, Materialized>();
    Function<ParameterAssignment, String> resolver =
        assignment ->
            cache.computeIfAbsent(assignment, key -> materialize(key, definition.solveDuration()))
                .identity;
    List<SamplingObservation> history =
        trials.values().stream()
            .filter(trial -> trial.assignment != null && trial.terminal())
            .map(
                trial ->
                    new SamplingObservation(
                        trial.id,
                        trial.assignment,
                        trial.identity,
                        trial.status == TrialStatus.COMPLETE,
                        trial.id == 0,
                        trial.objective == null
                            ? null
                            : definition.objective().codec().encodeValue(trial.objective)))
            .toList();
    var context =
        new SamplingContext(
            manifest.searchSpace(),
            history,
            Set.copyOf(reservedIdentities),
            (left, right) ->
                definition
                    .objective()
                    .compare(
                        definition.objective().codec().decodeValue(left.qualityKey()),
                        definition.objective().codec().decodeValue(right.qualityKey())),
            resolver);
    var proposal = definition.sampler().propose(context, samplerState);
    checkAdmission();
    if (proposal.stopped()) {
      append(
          "SAMPLING_STOP",
          Map.of(
              "reason",
              proposal.stopReason().name(),
              "samplerState",
              proposal.nextState().toMap()));
      setState(StudyState.SEARCH_FINISHED, proposal.stopReason().name());
      return null;
    }
    manifest.searchSpace().validate(proposal.assignment());
    var candidate =
        cache.computeIfAbsent(
            proposal.assignment(), key -> materialize(key, definition.solveDuration()));
    if (!candidate.identity.equals(proposal.candidateIdentity())
        || reservedIdentities.contains(candidate.identity)) {
      throw new StudyProtocolException(
          "Sampler proposed a duplicate or incorrectly resolved candidate identity.");
    }
    long id = trials.size();
    var payload = new LinkedHashMap<String, Object>();
    payload.put("trialId", Long.toString(id));
    payload.put("candidateIdentity", candidate.identity);
    payload.put("assignment", proposal.assignment().toMap());
    payload.put(
        "config", candidate.config == null ? null : storeConfiguration(candidate.config).toMap());
    payload.put("materializationFailure", candidate.failure);
    payload.put("proposalSequence", Long.toString(proposal.proposalSequence()));
    payload.put("reason", proposal.reason().name());
    payload.put("goodObservationCount", Integer.toString(proposal.goodObservationCount()));
    payload.put("otherObservationCount", Integer.toString(proposal.otherObservationCount()));
    payload.put("historySize", Integer.toString(history.size()));
    payload.put("samplerState", proposal.nextState().toMap());
    append("PROPOSED", payload);
    return trials.get(id);
  }

  private Materialized materialize(ParameterAssignment assignment, Duration horizon) {
    manifest.searchSpace().validate(assignment);
    var template = templates.get(assignment.getString("template"));
    var activeValues = new LinkedHashMap<>(assignment.values());
    activeValues.remove("template");
    var active = ParameterAssignment.of(activeValues);
    template.searchSpace().validate(active);
    SolverConfig config;
    try {
      config =
          Objects.requireNonNull(
              template.materialize(definition.baseSolverConfig(), active), "materialized config");
    } catch (RuntimeException error) {
      return new Materialized(
          null, failedIdentity(assignment), error.getClass().getName() + ": " + error.getMessage());
    }
    try {
      config =
          StudyProtocol.apply(
              definition,
              config,
              template,
              active,
              definition.trainingPanel().seeds().getFirst(),
              horizon);
    } catch (CandidateMaterializationException error) {
      return new Materialized(
          null, failedIdentity(assignment), error.getClass().getName() + ": " + error.getMessage());
    }
    var frozen = FrozenSolverConfig.capture(config, definition.runtimeReconstructionProvider());
    return new Materialized(frozen.freshConfig(), frozen.identity(), null);
  }

  private String failedIdentity(ParameterAssignment assignment) {
    return StoreJson.sha256(
        StoreJson.canonicalBytes(
            Map.of(
                "manifest",
                manifest.value().get("studyId"),
                "failedAssignment",
                assignment.toMap())));
  }

  private ParameterAssignment withTemplate(
      ConfigurationTemplate template, ParameterAssignment active) {
    var values = new LinkedHashMap<>(active.values());
    values.put("template", template.identity().id());
    return ParameterAssignment.of(values);
  }

  private void runTrial(Trial trial) {
    setTrialRunning(trial);
    if (trial.failure != null && trial.config == null) {
      terminateTrial(trial, TrialStatus.FAILED, trial.failure);
      return;
    }
    for (String problem : definition.trainingPanel().problemIds()) {
      for (long seed : definition.trainingPanel().seeds()) {
        var record =
            evaluateCell(
                "TRAINING",
                trial.id,
                trial.identity,
                trial.config,
                problem,
                seed,
                definition.solveDuration());
        if (!record.eligible()) {
          terminateTrial(
              trial,
              record.status() == EvaluationStatus.FAILED
                  ? TrialStatus.FAILED
                  : TrialStatus.INELIGIBLE,
              record.status() + ": " + record.failureMessage());
          return;
        }
      }
    }
    var objective =
        definition
            .objective()
            .evaluate(completedPanel("TRAINING", trial, definition.trainingPanel()), calibration);
    completeTrial(trial, objective);
  }

  private void setTrialRunning(Trial trial) {
    if (trial.status != TrialStatus.RUNNING)
      append("TRIAL_RUNNING", Map.of("trialId", Long.toString(trial.id)));
  }

  private void completeTrial(Trial trial, ObjectiveValue objective) {
    var previous = incumbent();
    // Validate comparison before committing the result to sampler-visible history.
    if (previous != null) definition.objective().compare(objective, previous.objective);
    append(
        "TRIAL_TERMINAL",
        Map.of(
            "trialId",
            Long.toString(trial.id),
            "status",
            TrialStatus.COMPLETE.name(),
            "objective",
            definition.objective().codec().encodeValue(objective),
            "measuredActiveNanos",
            Long.toString(currentMeasuredNanos()),
            "admissionChargedNanos",
            Long.toString(currentAdmissionNanos())));
    checkpointTerminalProgress();
  }

  private void terminateTrial(Trial trial, TrialStatus status, String failure) {
    append(
        "TRIAL_TERMINAL",
        Map.of(
            "trialId",
            Long.toString(trial.id),
            "status",
            status.name(),
            "failure",
            Objects.toString(failure, ""),
            "notRunCellCount",
            Long.toString(
                definition.trainingPanel().size()
                    - trialEvaluations(trial.id).stream()
                        .filter(record -> record.status() != EvaluationStatus.INTERRUPTED)
                        .count()),
            "measuredActiveNanos",
            Long.toString(currentMeasuredNanos()),
            "admissionChargedNanos",
            Long.toString(currentAdmissionNanos())));
    checkpointTerminalProgress();
  }

  private EvaluationRecord<Score_> evaluateCell(
      String stage,
      long trialId,
      String identity,
      ArtifactReference configReference,
      String problem,
      long seed,
      Duration horizon) {
    String key = evaluationKey(stage, identity, problem, seed, horizon);
    var existing = terminalCells.get(key);
    if (existing != null) return existing;
    checkAdmission();
    long attempt = nextAttempts.getOrDefault(key, 0L);
    long reservation = Math.addExact(horizon.toNanos(), graceNanos(horizon));
    var payload = new LinkedHashMap<String, Object>();
    payload.put("evaluationId", key);
    payload.put("stage", stage);
    payload.put("trialId", Long.toString(trialId));
    payload.put("candidateIdentity", identity);
    payload.put("problemId", problem);
    payload.put("problemFingerprint", manifest.descriptors().get(problem).contentFingerprint());
    payload.put("solverSeed", Long.toString(seed));
    payload.put("attempt", Long.toString(attempt));
    payload.put("horizonNanos", Long.toString(horizon.toNanos()));
    payload.put("reservedNanos", Long.toString(reservation));
    payload.put("config", configReference.toMap());
    payload.put("operationId", activeOperationId);
    payload.put("startedAt", Instant.now().toString());
    append("EVALUATION_STARTED", payload);
    var evaluation = new ActiveEvaluation(key, nanoClock.getAsLong(), reservation);
    activeEvaluation = evaluation;
    updateSnapshot();
    StudyState evaluationStage = state;
    try {
      EvaluationOutcome<Score_> outcome;
      if (shouldStop()) {
        outcome = interruptedBeforeExecution();
      } else {
        SolverConfig config = readConfiguration(configReference);
        config.setRandomSeed(seed);
        outcome =
            shouldStop()
                ? interruptedBeforeExecution()
                : evaluator.evaluate(
                    config,
                    definition.problemAdapter(),
                    manifest.descriptors().get(problem),
                    seed,
                    horizon,
                    definition.problemAdapter().verifier().orElse(null),
                    this::shouldStop,
                    this::markStopping);
      }
      var observation = new LinkedHashMap<>(payload);
      observation.put("outcome", outcome.toMap());
      observation.put("completedAt", Instant.now().toString());
      observation.put(
          "attemptElapsedNanos",
          Long.toString(Math.max(0, nanoClock.getAsLong() - evaluation.startedNanos())));
      var artifact = writeArtifact(StoreJson.canonicalBytes(observation), "json");
      append(
          "EVALUATION_COMPLETED",
          Map.of(
              "evaluationId",
              key,
              "attempt",
              Long.toString(attempt),
              "observation",
              artifact.toMap()));
      if (outcome.status() == EvaluationStatus.INTERRUPTED) throw new PausedWork();
      return terminalCells.get(key);
    } catch (PausedWork pause) {
      throw pause;
    } catch (RuntimeException | Error failure) {
      try {
        append(
            "EVALUATION_MEASUREMENT_FAILED",
            Map.of(
                "evaluationId",
                key,
                "attempt",
                Long.toString(attempt),
                "operationId",
                activeOperationId,
                "attemptElapsedNanos",
                Long.toString(Math.max(0, nanoClock.getAsLong() - evaluation.startedNanos())),
                "type",
                failure.getClass().getName(),
                "message",
                Objects.toString(failure.getMessage(), "")));
      } catch (RuntimeException persistenceFailure) {
        failure.addSuppressed(persistenceFailure);
      }
      throw failure;
    } finally {
      activeEvaluation = null;
      synchronized (controlState) {
        if (state == StudyState.STOPPING && !stopRequested.get()) state = evaluationStage;
      }
      updateSnapshot();
    }
  }

  private EvaluationOutcome<Score_> interruptedBeforeExecution() {
    return new EvaluationOutcome<>(
        EvaluationStatus.INTERRUPTED,
        null,
        -1,
        -1,
        -1,
        0,
        0,
        0,
        VerificationResult.notConfigured(),
        null,
        "Stopped before evaluation execution.",
        List.of());
  }

  private static long graceNanos(Duration horizon) {
    long millis = horizon.toMillis();
    return Math.multiplyExact(
        Math.max(1_000L, millis / 20 + (millis % 20 == 0 ? 0 : 1)), 1_000_000L);
  }

  private String evaluationKey(
      String stage, String identity, String problem, long seed, Duration horizon) {
    return StoreJson.sha256(
        StoreJson.canonicalBytes(
            Map.of(
                "stage",
                stage,
                "candidateIdentity",
                identity,
                "problemFingerprint",
                manifest.descriptors().get(problem).contentFingerprint(),
                "solverSeed",
                Long.toString(seed),
                "horizonNanos",
                Long.toString(horizon.toNanos()),
                "studyId",
                manifest.value().get("studyId"))));
  }

  private CompletedPanel<Score_> completedPanel(String stage, Trial trial, ProblemPanel panel) {
    var scores = new ArrayList<ScoredObservation<Score_>>();
    for (String problem : panel.problemIds()) {
      for (long seed : panel.seeds()) {
        var record =
            terminalCells.get(
                evaluationKey(stage, trial.identity, problem, seed, definition.solveDuration()));
        if (record == null || !record.eligible())
          throw new IllegalStateException("Panel is incomplete: " + problem + "/" + seed);
        scores.add(new ScoredObservation<>(problem, seed, record.score()));
      }
    }
    return new CompletedPanel<>(panel.problemIds(), panel.seeds(), scores);
  }

  @Override
  public QualificationResult<Score_> qualify() {
    if (qualificationTerminal) return qualification;
    if (incumbent() == null)
      throw new IllegalStateException("Qualification requires an eligible training incumbent.");
    if (shouldStop())
      throw new IllegalStateException(
          "Qualification needs remaining active time; explicitly resume with increased limits.");
    beginOperation("qualification");
    try {
      if (selectedTrialId == null) {
        var incumbent = incumbent();
        append(
            "SELECTION_FROZEN",
            Map.of(
                "trialId",
                Long.toString(incumbent.id),
                "candidateIdentity",
                incumbent.identity,
                "trainingPanel",
                StudyManifest.panel(definition.trainingPanel()),
                "reason",
                "Best complete training objective; baseline and earlier incumbent retain exact ties."));
      }
      setState(StudyState.QUALIFYING, null);
      warmup();
      var selected = trials.get(selectedTrialId);
      if (selected.assignment != null) {
        var template = templates.get(selected.assignment.getString("template"));
        definition
            .heldOutPanel()
            .problemIds()
            .forEach(id -> template.validate(manifest.descriptors().get(id).capabilities()));
      }
      Trial baseline = trials.get(0L);
      var baselineFailure = evaluateQualificationPanel("HELD_OUT_BASELINE", baseline);
      if (baselineFailure != null) {
        finishQualification(
            baselineFailure == EvaluationStatus.FAILED
                ? QualificationStatus.FAILED
                : QualificationStatus.INCONCLUSIVE,
            QualificationComparison.UNAVAILABLE,
            null,
            null,
            null,
            "Held-out baseline could not calibrate the objective: " + baselineFailure,
            true);
      } else {
        var baselinePanel =
            completedPanel("HELD_OUT_BASELINE", baseline, definition.heldOutPanel());
        if (heldOutCalibration == null)
          append(
              "HELD_OUT_CALIBRATION",
              Map.of(
                  "value",
                  definition
                      .objective()
                      .codec()
                      .encodeCalibration(definition.objective().calibrate(baselinePanel))));
        ObjectiveValue baselineValue =
            definition.objective().evaluate(baselinePanel, heldOutCalibration);
        var candidateFailure =
            selected.id == 0 ? null : evaluateQualificationPanel("HELD_OUT_SELECTED", selected);
        if (candidateFailure != null) {
          finishQualification(
              candidateFailure == EvaluationStatus.INCOMPLETE_RESULT
                      || candidateFailure == EvaluationStatus.INTERRUPTED
                  ? QualificationStatus.INCONCLUSIVE
                  : QualificationStatus.FAILED,
              QualificationComparison.UNAVAILABLE,
              null,
              baselineValue,
              null,
              "Frozen candidate has ineligible held-out evidence: " + candidateFailure,
              true);
        } else {
          var selectedPanel =
              selected.id == 0
                  ? baselinePanel
                  : completedPanel("HELD_OUT_SELECTED", selected, definition.heldOutPanel());
          var selectedValue =
              selected.id == 0
                  ? baselineValue
                  : definition.objective().evaluate(selectedPanel, heldOutCalibration);
          int comparison = definition.objective().compare(selectedValue, baselineValue);
          var direction =
              comparison > 0
                  ? QualificationComparison.BETTER
                  : comparison < 0 ? QualificationComparison.WORSE : QualificationComparison.EQUAL;
          ExactRational feasible =
              feasibility(
                  selected.id == 0 ? "HELD_OUT_BASELINE" : "HELD_OUT_SELECTED",
                  selected,
                  definition.heldOutPanel());
          var status =
              feasible.compareTo(ExactRational.of(1)) < 0
                  ? QualificationStatus.NO_FULLY_FEASIBLE_CANDIDATE
                  : comparison > 0
                      ? QualificationStatus.IMPROVED_ON_HELD_OUT_PANEL
                      : QualificationStatus.NO_HELD_OUT_IMPROVEMENT;
          finishQualification(
              status,
              direction,
              feasible,
              baselineValue,
              selectedValue,
              "Evidence applies to the declared held-out panel; it is not an optimality or significance certificate.",
              true);
        }
      }
    } catch (PausedWork pause) {
      finishQualification(
          QualificationStatus.INCONCLUSIVE,
          QualificationComparison.UNAVAILABLE,
          null,
          null,
          null,
          "Qualification interrupted; pending cells can resume while selection remains frozen.",
          false);
      pause();
    } catch (RuntimeException | Error failure) {
      operationFailure = failure;
      try {
        finishQualification(
            QualificationStatus.FAILED,
            QualificationComparison.UNAVAILABLE,
            null,
            null,
            null,
            failure.getClass().getName() + ": " + failure.getMessage(),
            true);
      } catch (RuntimeException persistenceFailure) {
        failure.addSuppressed(persistenceFailure);
      }
      fail(failure);
      throw failure;
    } finally {
      endOperation();
    }
    return qualification;
  }

  private EvaluationStatus evaluateQualificationPanel(String stage, Trial trial) {
    for (String problem : definition.heldOutPanel().problemIds()) {
      for (long seed : definition.heldOutPanel().seeds()) {
        var record =
            evaluateCell(
                stage,
                trial.id,
                trial.identity,
                trial.config,
                problem,
                seed,
                definition.solveDuration());
        if (!record.eligible()) return record.status();
      }
    }
    return null;
  }

  private ExactRational feasibility(String stage, Trial trial, ProblemPanel panel) {
    long count = 0;
    for (String problem : panel.problemIds()) {
      for (long seed : panel.seeds()) {
        var record =
            terminalCells.get(
                evaluationKey(stage, trial.identity, problem, seed, definition.solveDuration()));
        if (record != null && record.eligible() && record.score().isFeasible()) count++;
      }
    }
    return new ExactRational(BigInteger.valueOf(count), BigInteger.valueOf(panel.size()));
  }

  private void finishQualification(
      QualificationStatus status,
      QualificationComparison comparison,
      ExactRational feasible,
      ObjectiveValue baselineValue,
      ObjectiveValue selectedValue,
      String detail,
      boolean terminal) {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("status", status.name());
    payload.put("comparison", comparison.name());
    payload.put("selectedTrialId", Long.toString(selectedTrialId));
    payload.put("feasibilityRate", feasible == null ? null : feasible.toString());
    payload.put(
        "baselineObjective",
        baselineValue == null ? null : definition.objective().codec().encodeValue(baselineValue));
    payload.put(
        "selectedObjective",
        selectedValue == null ? null : definition.objective().codec().encodeValue(selectedValue));
    payload.put("detail", detail);
    payload.put("terminal", terminal);
    append("QUALIFICATION", payload);
    if (terminal) setState(StudyState.FINISHED, null);
  }

  @Override
  public ExportResult exportSelected(Path destination) {
    if (selectedTrialId == null)
      throw new IllegalStateException("Export requires a frozen selection; call qualify first.");
    beginOperation("export");
    try {
      checkAdmission();
      var selected = trials.get(selectedTrialId);
      var result =
          StudyExporter.export(
              destination,
              store,
              definition,
              snapshot(),
              qualification,
              observations,
              progression,
              readArtifact(selected.config));
      append(
          "EXPORTED",
          Map.of(
              "selectionTrialId",
              Long.toString(selectedTrialId),
              "destination",
              destination.toAbsolutePath().normalize().toString(),
              "digests",
              result.artifactDigests()));
      return result;
    } catch (RuntimeException | Error failure) {
      operationFailure = failure;
      throw failure;
    } finally {
      endOperation();
    }
  }

  @Override
  public void requestStop() {
    synchronized (controlState) {
      if (closed) return;
      stopRequested.set(true);
      if (operating && !hasEndedActiveWork(state)) state = StudyState.STOPPING;
    }
  }

  @Override
  public StudyResult<Score_> snapshot() {
    var snapshot = committedSnapshot;
    return new StudyResult<>(
        snapshot.studyId(),
        state,
        snapshot.trainingStatus(),
        snapshot.trials(),
        snapshot.incumbent(),
        snapshot.frozenSelectionTrialId(),
        activeEvaluationId(),
        snapshot.measuredActiveNanos(),
        snapshot.admissionChargedNanos(),
        snapshot.interruptedCostUnknown(),
        snapshot.stopReason());
  }

  @Override
  public void close() {
    requestStop();
    boolean interrupted = false;
    synchronized (lifecycle) {
      if (closed) {
        while (!closeCompleted) {
          try {
            lifecycle.wait();
          } catch (InterruptedException e) {
            interrupted = true;
          }
        }
        if (interrupted) Thread.currentThread().interrupt();
        return;
      }
      if (operating && Thread.currentThread() == operationThread) {
        throw new IllegalStateException("close cannot wait for the operation that is invoking it.");
      }
      while (operating) {
        try {
          lifecycle.wait();
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
      closed = true;
      heartbeatExecutor.shutdown();
    }
    // A heartbeat may be waiting for lifecycle: never wait for its executor under that monitor.
    try {
      boolean terminated = false;
      while (!terminated) {
        try {
          terminated = heartbeatExecutor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          interrupted = true;
        }
      }
      store.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      synchronized (lifecycle) {
        closeCompleted = true;
        lifecycle.notifyAll();
      }
      if (interrupted) Thread.currentThread().interrupt();
    }
  }

  private void beginOperation(String kind) {
    synchronized (lifecycle) {
      if (closed) throw new IllegalStateException("Study is closed.");
      if (operating)
        throw new IllegalStateException(
            "Study operations run serially; another operation is active.");
      if (state == StudyState.FAILED && !kind.equals("export") && !kind.equals("setup"))
        throw new IllegalStateException("Study failed: " + stopReason);
      operating = true;
      operationFailure = null;
      operationThread = Thread.currentThread();
      activeOperationId = "session-" + session + "/" + kind + "-" + (++operationSequence);
      activeOperationStarted = nanoClock.getAsLong();
      try {
        append(
            "ACTIVE_STARTED",
            Map.of(
                "operationId",
                activeOperationId,
                "kind",
                kind,
                "startedAt",
                Instant.now().toString()));
      } catch (RuntimeException | Error failure) {
        operating = false;
        activeOperationId = null;
        operationThread = null;
        throw failure;
      }
    }
  }

  private void endOperation() {
    synchronized (lifecycle) {
      try {
        if (activeOperationId != null) {
          Throwable finalizationFailure = null;
          try {
            // The cache describes this journal position. The trailing accounting commit remains
            // authoritative and charges projection construction, validation and persistence too.
            checkpoint();
          } catch (RuntimeException | Error failure) {
            finalizationFailure = failure;
          }
          try {
            long elapsed = Math.max(0, nanoClock.getAsLong() - activeOperationStarted);
            append(
                "ACTIVE_COMPLETED",
                Map.of(
                    "operationId",
                    activeOperationId,
                    "elapsedNanos",
                    Long.toString(elapsed),
                    "admissionNanos",
                    Long.toString(elapsed),
                    "unknownCost",
                    false));
            activeOperationId = null;
            updateSnapshot();
          } catch (RuntimeException | Error failure) {
            if (finalizationFailure == null) finalizationFailure = failure;
            else if (finalizationFailure != failure) finalizationFailure.addSuppressed(failure);
          }
          if (finalizationFailure instanceof RuntimeException failure) throw failure;
          if (finalizationFailure instanceof Error failure) throw failure;
        }
      } catch (RuntimeException | Error finalizationFailure) {
        publishState(StudyState.FAILED);
        if (operationFailure != null) {
          if (operationFailure != finalizationFailure)
            operationFailure.addSuppressed(finalizationFailure);
        } else throw finalizationFailure;
      } finally {
        activeOperationId = null;
        operating = false;
        operationThread = null;
        lifecycle.notifyAll();
      }
    }
  }

  private void heartbeat() {
    synchronized (lifecycle) {
      if (!operating || activeOperationId == null || closed) return;
      try {
        var evaluation = activeEvaluation;
        long elapsed = Math.max(0, nanoClock.getAsLong() - activeOperationStarted);
        var payload = new LinkedHashMap<String, Object>();
        payload.put("operationId", activeOperationId);
        payload.put("elapsedNanos", Long.toString(elapsed));
        payload.put("evaluationId", evaluation == null ? null : evaluation.id());
        payload.put(
            "evaluationElapsedNanos",
            evaluation == null
                ? "0"
                : Long.toString(Math.max(0, nanoClock.getAsLong() - evaluation.startedNanos())));
        payload.put(
            "reservedNanos", Long.toString(evaluation == null ? 0 : evaluation.reservedNanos()));
        // Heartbeats cannot mutate coordinator collections while an evaluation is active.
        store.append("ACTIVE_HEARTBEAT", payload);
        if (currentAdmissionNanos() >= limits.activeTimeLimit().toNanos()) {
          stopReason = "ACTIVE_TIME_LIMIT";
          requestStop();
        }
      } catch (IOException e) {
        heartbeatFailure = new UncheckedIOException(e);
        requestStop();
      } catch (RuntimeException e) {
        heartbeatFailure = e;
        requestStop();
      }
    }
  }

  private boolean shouldStop() {
    if (stopRequested.get()) return true;
    if (currentAdmissionNanos() >= limits.activeTimeLimit().toNanos()) {
      stopReason = "ACTIVE_TIME_LIMIT";
      requestStop();
      return true;
    }
    return false;
  }

  private void checkAdmission() {
    if (heartbeatFailure != null) throw heartbeatFailure;
    if (shouldStop()) throw new PausedWork();
  }

  private long currentMeasuredNanos() {
    return saturatingAdd(
        measuredNanos,
        activeOperationId == null
            ? 0
            : Math.max(0, nanoClock.getAsLong() - activeOperationStarted));
  }

  private long currentAdmissionNanos() {
    return saturatingAdd(
        admissionNanos,
        activeOperationId == null
            ? 0
            : Math.max(0, nanoClock.getAsLong() - activeOperationStarted));
  }

  private static long saturatingAdd(long left, long right) {
    return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
  }

  private void pause() {
    for (var trial : trials.values()) {
      if (!trial.terminal() && trial.status != TrialStatus.INTERRUPTED) {
        append("TRIAL_INTERRUPTED", Map.of("trialId", Long.toString(trial.id)));
      }
    }
    setState(StudyState.PAUSED, stopReason == null ? "STOP_REQUESTED" : stopReason);
  }

  private void fail(Throwable failure) {
    try {
      setState(StudyState.FAILED, failure.getClass().getName() + ": " + failure.getMessage());
    } catch (RuntimeException persistenceFailure) {
      failure.addSuppressed(persistenceFailure);
    }
  }

  private void setState(StudyState next, String reason) {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("state", next.name());
    payload.put("reason", reason);
    append("STATE", payload);
  }

  private void checkpointTerminalProgress() {
    int terminalCount = progression.size();
    // Recovery reduces all transitions before setup begins owning an operation. Its finalization
    // writes the reconstructed cache; live search amortizes cumulative projections geometrically.
    if (operating && terminalCount > 0 && (terminalCount & (terminalCount - 1)) == 0) {
      checkpoint();
    }
  }

  private void checkpoint() {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("projectionVersion", "1");
    payload.put("recoveryAuthority", "journal");
    payload.put("state", state.name());
    payload.put("stopReason", stopReason);
    payload.put("session", Long.toString(session));
    payload.put("operationSequence", Long.toString(operationSequence));
    payload.put("preflightComplete", preflightComplete);
    payload.put("warmupCompleteForSession", warmed);
    payload.put("samplerState", samplerState.toMap());
    payload.put("candidateCount", Integer.toString(Math.max(0, trials.size() - 1)));
    payload.put("reservedIdentities", reservedIdentities.stream().sorted().toList());
    payload.put("trials", trials.values().stream().map(this::checkpointTrial).toList());
    payload.put(
        "calibration",
        calibration == null ? null : definition.objective().codec().encodeCalibration(calibration));
    payload.put(
        "heldOutCalibration",
        heldOutCalibration == null
            ? null
            : definition.objective().codec().encodeCalibration(heldOutCalibration));
    payload.put("pendingAttempts", new java.util.TreeMap<>(startedAttempts));
    var encodedNextAttempts = new java.util.TreeMap<String, Object>();
    nextAttempts.forEach((key, value) -> encodedNextAttempts.put(key, value.toString()));
    payload.put("nextAttempts", encodedNextAttempts);
    payload.put("terminalEvaluationIds", terminalCells.keySet().stream().sorted().toList());
    payload.put(
        "completedHeldOutEvaluationIds",
        terminalCells.values().stream()
            .filter(record -> record.stage().startsWith("HELD_OUT_"))
            .map(EvaluationRecord::evaluationId)
            .sorted()
            .toList());
    payload.put("maximumCandidateTrials", Long.toString(limits.maximumCandidateTrials()));
    payload.put("activeTimeLimitNanos", Long.toString(limits.activeTimeLimit().toNanos()));
    payload.put("measuredActiveNanos", Long.toString(currentMeasuredNanos()));
    payload.put("admissionChargedNanos", Long.toString(currentAdmissionNanos()));
    payload.put("interruptedCostUnknown", unknownCost);
    payload.put("activeOperationId", activeOperationId);
    payload.put("activeEvaluationId", activeEvaluationId());
    payload.put(
        "frozenSelectionTrialId", selectedTrialId == null ? null : selectedTrialId.toString());
    payload.put("qualificationTerminal", qualificationTerminal);
    payload.put("qualification", checkpointQualification());
    try {
      synchronized (store) {
        // References and the latest durable heartbeat are projected under the same writer lock
        // as the checkpoint commit. Recovery continues to replay the entire authoritative journal.
        var records = store.records();
        payload.put(
            "committedAttempts",
            records.stream()
                .filter(record -> record.kind().equals("EVALUATION_COMPLETED"))
                .map(JournalRecord::payload)
                .toList());
        payload.put(
            "attemptDiagnostics",
            records.stream()
                .filter(
                    record ->
                        record.kind().equals("EVALUATION_RECOVERED")
                            || record.kind().equals("EVALUATION_MEASUREMENT_FAILED"))
                .map(JournalRecord::payload)
                .toList());
        var accounting = new java.util.TreeMap<String, Object>();
        records.stream()
            .filter(
                record ->
                    record.kind().equals("ACTIVE_STARTED")
                        || record.kind().equals("ACTIVE_HEARTBEAT")
                        || record.kind().equals("ACTIVE_COMPLETED")
                        || record.kind().equals("ACTIVE_INTERRUPTED"))
            .forEach(
                record ->
                    accounting.put((String) record.payload().get("operationId"), record.payload()));
        payload.put("operationAccounting", accounting);
        store.checkpoint(payload);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Map<String, Object> checkpointTrial(Trial trial) {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("trialId", Long.toString(trial.id));
    payload.put("candidateIdentity", trial.identity);
    payload.put("assignment", trial.assignment == null ? null : trial.assignment.toMap());
    payload.put("config", trial.config == null ? null : trial.config.toMap());
    payload.put("status", trial.status.name());
    payload.put(
        "objective",
        trial.objective == null
            ? null
            : definition.objective().codec().encodeValue(trial.objective));
    payload.put("failure", trial.failure);
    return payload;
  }

  private Map<String, Object> checkpointQualification() {
    if (qualification == null) return null;
    var payload = new LinkedHashMap<String, Object>();
    payload.put("status", qualification.status().name());
    payload.put("comparison", qualification.comparison().name());
    payload.put("selectedTrialId", Long.toString(qualification.selectedTrialId()));
    payload.put(
        "feasibilityRate",
        qualification.feasibilityRate() == null
            ? null
            : qualification.feasibilityRate().toString());
    payload.put(
        "baselineObjective",
        qualification.baselineObjective() == null
            ? null
            : definition.objective().codec().encodeValue(qualification.baselineObjective()));
    payload.put(
        "selectedObjective",
        qualification.selectedObjective() == null
            ? null
            : definition.objective().codec().encodeValue(qualification.selectedObjective()));
    payload.put("detail", qualification.detail());
    return payload;
  }

  private long consecutiveCandidateFailures() {
    var completed = new ArrayList<>(trials.values());
    long count = 0;
    for (int index = completed.size() - 1;
        index > 0 && completed.get(index).status == TrialStatus.FAILED;
        index--) count++;
    return count;
  }

  private Trial incumbent() {
    Trial incumbent = null;
    for (var trial : trials.values()) {
      if (trial.status != TrialStatus.COMPLETE || trial.objective == null) continue;
      if (incumbent == null
          || definition.objective().compare(trial.objective, incumbent.objective) > 0)
        incumbent = trial;
    }
    return incumbent;
  }

  private List<EvaluationRecord<Score_>> trialEvaluations(long id) {
    return observations.stream()
        .filter(record -> record.trialId() == id && record.stage().equals("TRAINING"))
        .toList();
  }

  private TrialResult<Score_> trialSnapshot(Trial trial) {
    return new TrialResult<>(
        trial.id,
        trial.identity,
        trial.assignment,
        trial.status,
        trial.objective,
        trialEvaluations(trial.id),
        trial.failure);
  }

  private void updateSnapshot() {
    Trial incumbent = incumbent();
    TrainingStatus trainingStatus =
        incumbent == null
            ? TrainingStatus.NO_ELIGIBLE_INCUMBENT
            : feasibility("TRAINING", incumbent, definition.trainingPanel())
                        .compareTo(ExactRational.of(1))
                    < 0
                ? TrainingStatus.NO_FULLY_FEASIBLE_CANDIDATE
                : incumbent.id == 0
                    ? TrainingStatus.NO_IMPROVEMENT_FOUND
                    : TrainingStatus.IMPROVEMENT_FOUND;
    committedSnapshot =
        new StudyResult<>(
            (String) manifest.value().get("studyId"),
            state,
            trainingStatus,
            trials.values().stream().map(this::trialSnapshot).toList(),
            incumbent == null ? null : trialSnapshot(incumbent),
            selectedTrialId,
            activeEvaluationId(),
            measuredNanos,
            admissionNanos,
            unknownCost,
            stopReason);
  }

  private ArtifactReference storeConfiguration(SolverConfig config) {
    var writer = new StringWriter();
    config.writeXml(writer);
    return writeArtifact(writer.toString().getBytes(StandardCharsets.UTF_8), "xml");
  }

  private String activeEvaluationId() {
    var evaluation = activeEvaluation;
    return evaluation == null ? null : evaluation.id();
  }

  private SolverConfig readConfiguration(ArtifactReference reference) {
    var config =
        SolverConfig.createFromXmlReader(
            new StringReader(new String(readArtifact(reference), StandardCharsets.UTF_8)));
    return FrozenSolverConfig.capture(config, definition.runtimeReconstructionProvider())
        .freshConfig();
  }

  private ArtifactReference writeArtifact(byte[] content, String suffix) {
    try {
      return store.writeArtifact(content, suffix);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Map<String, Object> readJsonArtifact(ArtifactReference reference) {
    try {
      return StoreJson.readObject(readArtifact(reference));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void markStopping() {
    synchronized (controlState) {
      if (!hasEndedActiveWork(state)) state = StudyState.STOPPING;
    }
  }

  private static boolean hasEndedActiveWork(StudyState value) {
    return value == StudyState.PAUSED
        || value == StudyState.SEARCH_FINISHED
        || value == StudyState.FINISHED
        || value == StudyState.FAILED;
  }

  private void publishState(StudyState value) {
    // Keep this lock independent of lifecycle/store monitors: requestStop never waits for I/O.
    synchronized (controlState) {
      state = value;
    }
  }

  private byte[] readArtifact(ArtifactReference reference) {
    try {
      return store.readArtifact(reference);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void append(String kind, Map<String, Object> payload) {
    try {
      replay(store.append(kind, payload));
      updateSnapshot();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }

  private static long number(Map<String, Object> value, String name) {
    return Long.parseLong((String) value.get(name));
  }

  private static ArtifactReference reference(Object value) {
    var map = map(value);
    return new ArtifactReference((String) map.get("digest"), (String) map.get("suffix"));
  }

  private void replay(JournalRecord record) {
    var payload = record.payload();
    switch (record.kind()) {
      case "PROCESS_STARTED" -> {
        session = number(payload, "session");
        warmed = false;
      }
      case "LIMITS" -> {
        if (limits.maximumCandidateTrials() < number(payload, "maximumCandidateTrials")
            || limits.activeTimeLimit().toNanos() < number(payload, "activeTimeNanos")) {
          throw new IllegalArgumentException(
              "Resume limits may increase but cannot reduce previously declared limits.");
        }
      }
      case "BASELINE_RESERVED", "PROPOSED" -> {
        long id = number(payload, "trialId");
        if (trials.containsKey(id))
          throw new IllegalStateException("Duplicate committed trial (" + id + ").");
        var trial =
            new Trial(
                id,
                (String) payload.get("candidateIdentity"),
                payload.get("assignment") == null
                    ? null
                    : ParameterAssignment.fromMap(map(payload.get("assignment"))),
                payload.get("config") == null ? null : reference(payload.get("config")),
                (String) payload.get("materializationFailure"));
        trials.put(id, trial);
        if (!reservedIdentities.add(trial.identity))
          throw new IllegalStateException("Duplicate candidate reservation.");
        if (payload.containsKey("samplerState"))
          samplerState = SamplerState.fromMap(map(payload.get("samplerState")));
      }
      case "SAMPLING_STOP" -> samplerState = SamplerState.fromMap(map(payload.get("samplerState")));
      case "TRIAL_RUNNING" -> trials.get(number(payload, "trialId")).status = TrialStatus.RUNNING;
      case "TRIAL_INTERRUPTED" ->
          trials.get(number(payload, "trialId")).status = TrialStatus.INTERRUPTED;
      case "TRIAL_TERMINAL" -> {
        var trial = trials.get(number(payload, "trialId"));
        if (trial.terminal())
          throw new IllegalStateException("Duplicate terminal trial commit (" + trial.id + ").");
        trial.status = TrialStatus.valueOf((String) payload.get("status"));
        trial.objective =
            payload.get("objective") == null
                ? null
                : definition.objective().codec().decodeValue((String) payload.get("objective"));
        trial.failure = (String) payload.get("failure");
        progression.add(payload);
      }
      case "PREFLIGHT_COMPLETE" -> preflightComplete = true;
      case "WARMUP_COMPLETE" -> {
        if (number(payload, "session") == session) warmed = true;
      }
      case "CALIBRATION" ->
          calibration =
              definition.objective().codec().decodeCalibration((String) payload.get("value"));
      case "HELD_OUT_CALIBRATION" ->
          heldOutCalibration =
              definition.objective().codec().decodeCalibration((String) payload.get("value"));
      case "STATE" -> {
        publishState(StudyState.valueOf((String) payload.get("state")));
        stopReason = (String) payload.get("reason");
      }
      case "EVALUATION_STARTED" -> {
        String key = (String) payload.get("evaluationId");
        if (terminalCells.containsKey(key) || startedAttempts.putIfAbsent(key, payload) != null) {
          throw new IllegalStateException("Evaluation already started or terminal (" + key + ").");
        }
      }
      case "EVALUATION_COMPLETED" -> {
        String key = (String) payload.get("evaluationId");
        var observation = readJsonArtifact(reference(payload.get("observation")));
        var outcome = EvaluationOutcome.fromMap(map(observation.get("outcome")), scoreParser);
        long attempt = number(observation, "attempt");
        var started = startedAttempts.get(key);
        if (started == null
            || !key.equals(observation.get("evaluationId"))
            || attempt != number(payload, "attempt")
            || attempt != number(started, "attempt")) {
          throw new IllegalStateException(
              "Evaluation artifact does not match its started attempt.");
        }
        long knownCost =
            observation.containsKey("attemptElapsedNanos")
                ? number(observation, "attemptElapsedNanos")
                : saturatingAdd(
                    Math.max(0, outcome.solveElapsedNanos()),
                    saturatingAdd(
                        Math.max(0, outcome.preparationElapsedNanos()),
                        Math.max(0, outcome.assessmentElapsedNanos())));
        committedOperationCosts.merge(
            (String) observation.get("operationId"), knownCost, DefaultGreybayStudy::saturatingAdd);
        var result =
            new EvaluationRecord<>(
                key,
                (String) observation.get("stage"),
                number(observation, "trialId"),
                (String) observation.get("problemId"),
                number(observation, "solverSeed"),
                attempt,
                outcome.status(),
                outcome.score(),
                outcome.missingRequiredAssignmentCount(),
                outcome.selectedObserverNanos(),
                outcome.selectedSolverMillis(),
                outcome.solveElapsedNanos(),
                outcome.preparationElapsedNanos(),
                outcome.assessmentElapsedNanos(),
                outcome.verification(),
                outcome.failureType(),
                outcome.failureMessage());
        if (startedAttempts.remove(key) == null)
          throw new IllegalStateException("Terminal evaluation has no start: " + key);
        nextAttempts.put(key, attempt + 1);
        observations.add(result);
        if (outcome.status() != EvaluationStatus.INTERRUPTED
            && terminalCells.putIfAbsent(key, result) != null) {
          throw new IllegalStateException("Duplicate terminal evaluation (" + key + ").");
        }
      }
      case "EVALUATION_RECOVERED", "EVALUATION_MEASUREMENT_FAILED" -> {
        if (payload.containsKey("attemptElapsedNanos")) {
          committedOperationCosts.merge(
              (String) payload.get("operationId"),
              number(payload, "attemptElapsedNanos"),
              DefaultGreybayStudy::saturatingAdd);
        }
        String key = (String) payload.get("evaluationId");
        startedAttempts.remove(key);
        nextAttempts.put(key, number(payload, "attempt") + 1);
      }
      case "ACTIVE_STARTED", "ACTIVE_HEARTBEAT" ->
          operations.put((String) payload.get("operationId"), payload);
      case "ACTIVE_COMPLETED", "ACTIVE_INTERRUPTED" -> {
        String id = (String) payload.get("operationId");
        var previous = operations.put(id, payload);
        if (previous != null && previous.containsKey("admissionNanos")) {
          throw new IllegalStateException("Duplicate operation accounting commit (" + id + ").");
        }
        measuredNanos = saturatingAdd(measuredNanos, number(payload, "elapsedNanos"));
        admissionNanos = saturatingAdd(admissionNanos, number(payload, "admissionNanos"));
        unknownCost |= Boolean.TRUE.equals(payload.get("unknownCost"));
      }
      case "SELECTION_FROZEN" -> {
        long selected = number(payload, "trialId");
        if (selectedTrialId != null)
          throw new IllegalStateException("Selection was frozen more than once.");
        selectedTrialId = selected;
      }
      case "QUALIFICATION" -> {
        qualificationTerminal = Boolean.TRUE.equals(payload.get("terminal"));
        if (qualificationTerminal) publishState(StudyState.FINISHED);
        qualification =
            new QualificationResult<>(
                QualificationStatus.valueOf((String) payload.get("status")),
                QualificationComparison.valueOf((String) payload.get("comparison")),
                number(payload, "selectedTrialId"),
                payload.get("feasibilityRate") == null
                    ? null
                    : ExactRational.parse((String) payload.get("feasibilityRate")),
                payload.get("baselineObjective") == null
                    ? null
                    : definition
                        .objective()
                        .codec()
                        .decodeValue((String) payload.get("baselineObjective")),
                payload.get("selectedObjective") == null
                    ? null
                    : definition
                        .objective()
                        .codec()
                        .decodeValue((String) payload.get("selectedObjective")),
                observations.stream()
                    .filter(value -> value.stage().startsWith("HELD_OUT_"))
                    .toList(),
                (String) payload.get("detail"));
      }
      default -> {
        /* Store checkpoints and diagnostic/export events do not alter recovered search decisions. */
      }
    }
  }

  private void recoverInterruptedWork(String openingOperationId) {
    String setupPrefix = "session-" + session + "/setup";
    if (operations.keySet().stream().noneMatch(id -> id.startsWith(setupPrefix))) {
      // The manifest may be the only committed file. No elapsed-time evidence survived.
      append(
          "ACTIVE_INTERRUPTED",
          Map.of(
              "operationId",
              setupPrefix + "-before-tracking",
              "elapsedNanos",
              "0",
              "admissionNanos",
              "0",
              "unknownCost",
              true));
    }
    for (var entry : new ArrayList<>(operations.entrySet())) {
      if (entry.getKey().equals(openingOperationId)) continue;
      var operation = entry.getValue();
      if (operation.containsKey("admissionNanos")) continue;
      long elapsed = operation.containsKey("elapsedNanos") ? number(operation, "elapsedNanos") : 0;
      long extraCharge = 0;
      long completedCost = committedOperationCosts.getOrDefault(entry.getKey(), 0L);
      long pendingElapsed = 0;
      for (var attempt : startedAttempts.values()) {
        if (!entry.getKey().equals(attempt.get("operationId"))) continue;
        long evaluationElapsed =
            Objects.equals(operation.get("evaluationId"), attempt.get("evaluationId"))
                ? number(operation, "evaluationElapsedNanos")
                : 0;
        pendingElapsed = Math.max(pendingElapsed, evaluationElapsed);
        extraCharge =
            Math.max(
                extraCharge, Math.max(0, number(attempt, "reservedNanos") - evaluationElapsed));
      }
      elapsed = Math.max(elapsed, saturatingAdd(completedCost, pendingElapsed));
      append(
          "ACTIVE_INTERRUPTED",
          Map.of(
              "operationId",
              entry.getKey(),
              "elapsedNanos",
              Long.toString(elapsed),
              "admissionNanos",
              Long.toString(saturatingAdd(elapsed, extraCharge)),
              "unknownCost",
              true));
    }
    for (var attempt : new ArrayList<>(startedAttempts.values())) {
      append(
          "EVALUATION_RECOVERED",
          Map.of(
              "evaluationId",
              attempt.get("evaluationId"),
              "attempt",
              attempt.get("attempt"),
              "status",
              "INTERRUPTED",
              "reason",
              "Process ended before terminal commit."));
    }
    for (var trial : trials.values()) {
      if (!trial.terminal()) {
        append("TRIAL_INTERRUPTED", Map.of("trialId", Long.toString(trial.id)));
        if (calibration != null || trial.id != 0) recoverTerminalTrial(trial);
      }
    }
    if (state != StudyState.FINISHED && state != StudyState.FAILED)
      setState(StudyState.PAUSED, "EXPLICIT_RESUME");
  }

  private void recoverTerminalTrial(Trial trial) {
    var records =
        trialEvaluations(trial.id).stream()
            .filter(record -> record.status() != EvaluationStatus.INTERRUPTED)
            .toList();
    var failure = records.stream().filter(record -> !record.eligible()).findFirst();
    if (failure.isPresent()) {
      var record = failure.get();
      terminateTrial(
          trial,
          record.status() == EvaluationStatus.FAILED ? TrialStatus.FAILED : TrialStatus.INELIGIBLE,
          record.status() + ": " + record.failureMessage());
    } else if (records.size() == definition.trainingPanel().size() && calibration != null) {
      completeTrial(
          trial,
          definition
              .objective()
              .evaluate(
                  completedPanel("TRAINING", trial, definition.trainingPanel()), calibration));
    }
  }

  /** Owns setup heartbeats before a fully constructed study can expose its lifecycle. */
  private static final class OpeningHeartbeat implements AutoCloseable {
    private final String operationId;
    private final ScheduledExecutorService executor;
    private volatile RuntimeException failure;

    private OpeningHeartbeat(StudyStore store, String operationId, long started, LongSupplier clock)
        throws IOException {
      this.operationId = operationId;
      store.append(
          "ACTIVE_STARTED",
          Map.of(
              "operationId", operationId, "kind", "setup", "startedAt", Instant.now().toString()));
      executor =
          Executors.newSingleThreadScheduledExecutor(
              runnable -> {
                var thread = new Thread(runnable, "greybay-opening-heartbeat");
                thread.setDaemon(true);
                return thread;
              });
      executor.scheduleAtFixedRate(
          () -> {
            try {
              var payload = new LinkedHashMap<String, Object>();
              payload.put("operationId", operationId);
              payload.put("elapsedNanos", Long.toString(Math.max(0, clock.getAsLong() - started)));
              payload.put("evaluationId", null);
              payload.put("evaluationElapsedNanos", "0");
              payload.put("reservedNanos", "0");
              store.append("ACTIVE_HEARTBEAT", payload);
            } catch (IOException e) {
              failure = new UncheckedIOException(e);
            } catch (RuntimeException e) {
              failure = e;
            }
          },
          1,
          1,
          TimeUnit.SECONDS);
    }

    @Override
    public void close() {
      executor.shutdown();
      boolean interrupted = false;
      try {
        boolean terminated = false;
        while (!terminated) {
          try {
            terminated = executor.awaitTermination(1, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            interrupted = true;
          }
        }
      } finally {
        if (interrupted) Thread.currentThread().interrupt();
      }
    }
  }

  private record ActiveEvaluation(String id, long startedNanos, long reservedNanos) {}

  private record Materialized(SolverConfig config, String identity, String failure) {}

  private static final class PausedWork extends RuntimeException {
    private PausedWork() {
      super(null, null, false, false);
    }
  }

  private final class Trial {
    final long id;
    final String identity;
    final ParameterAssignment assignment;
    final ArtifactReference config;
    TrialStatus status = TrialStatus.PROPOSED;
    ObjectiveValue objective;
    String failure;

    Trial(
        long id,
        String identity,
        ParameterAssignment assignment,
        ArtifactReference config,
        String failure) {
      this.id = id;
      this.identity = identity;
      this.assignment = assignment;
      this.config = config;
      this.failure = failure;
    }

    boolean terminal() {
      return status == TrialStatus.COMPLETE
          || status == TrialStatus.INELIGIBLE
          || status == TrialStatus.FAILED;
    }
  }
}
