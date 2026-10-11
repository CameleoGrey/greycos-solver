package greycos.solver.greybay.impl.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.api.ConfigurationTemplate;
import greycos.solver.greybay.api.EvaluationStatus;
import greycos.solver.greybay.api.ProblemCapabilities;
import greycos.solver.greybay.api.StudyState;
import greycos.solver.greybay.api.TrialStatus;
import greycos.solver.greybay.api.VerificationResult;
import greycos.solver.greybay.api.objective.BalancedQualityObjective;
import greycos.solver.greybay.api.sampling.CandidateProposal;
import greycos.solver.greybay.api.sampling.ProposalReason;
import greycos.solver.greybay.api.sampling.Sampler;
import greycos.solver.greybay.api.sampling.SamplerState;
import greycos.solver.greybay.api.sampling.SamplingContext;
import greycos.solver.greybay.api.template.ReferenceTemplates;
import greycos.solver.greybay.config.Dimension;
import greycos.solver.greybay.config.FixedDimension;
import greycos.solver.greybay.config.GreybayStudyDefinition;
import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.config.ProblemPanel;
import greycos.solver.greybay.config.RealDimension;
import greycos.solver.greybay.config.SearchSpace;
import greycos.solver.greybay.config.StudyRunLimits;
import greycos.solver.greybay.config.template.MovePortfolio;
import greycos.solver.greybay.config.template.TemplateParameters;
import greycos.solver.greybay.config.template.TemplateRequirements;
import greycos.solver.greybay.examples.GreybayWorkflowExample;
import greycos.solver.greybay.examples.SchedulingExample;
import greycos.solver.greybay.impl.evaluation.EvaluationOutcome;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GreybayPipelineFailureTest {
  private static final double INVALID_FRACTION = 0.000001;
  @TempDir Path directory;

  @Test
  void invalidSampleIsCommittedAndResumeContinuesWithTheNextCandidate() {
    var definition = definition(pipeline(.3));
    var clock = new AtomicLong();
    var calls = new AtomicInteger();
    String failedIdentity;
    try (var study = open(definition, definition.runLimits(), false, clock, calls)) {
      var result = study.run();
      assertThat(result.state()).isEqualTo(StudyState.SEARCH_FINISHED);
      assertThat(result.trials()).hasSize(2);
      var failed = result.trials().get(1);
      failedIdentity = failed.candidateIdentity();
      assertThat(failed.status()).isEqualTo(TrialStatus.FAILED);
      assertThat(failed.objective()).isNull();
      assertThat(failed.evaluations()).isEmpty();
      assertThat(failed.failure()).contains("pipeline.firstFraction", "zero-millisecond");
      assertThat(calls.get()).isEqualTo(2); // Preflight and baseline only.
    }
    try (var resumed =
        open(definition, StudyRunLimits.of(2, Duration.ofMinutes(1)), true, clock, calls)) {
      var result = resumed.run();
      assertThat(result.state()).isEqualTo(StudyState.SEARCH_FINISHED);
      assertThat(result.trials()).hasSize(3);
      assertThat(result.trials().get(1).candidateIdentity()).isEqualTo(failedIdentity);
      assertThat(result.trials().get(1).status()).isEqualTo(TrialStatus.FAILED);
      assertThat(result.trials().get(2).status()).isEqualTo(TrialStatus.COMPLETE);
      assertThat(result.trials().get(2).evaluations()).hasSize(1);
      assertThat(calls.get()).isEqualTo(3);
    }
  }

  @Test
  void invalidPreflightDefaultStillStopsBeforeEvaluation() {
    var definition = definition(pipeline(INVALID_FRACTION));
    var calls = new AtomicInteger();
    try (var study = open(definition, definition.runLimits(), false, new AtomicLong(), calls)) {
      assertThatThrownBy(study::run)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Default assignment failed preflight")
          .hasMessageContaining("zero-millisecond");
      assertThat(study.snapshot().state()).isEqualTo(StudyState.FAILED);
      assertThat(calls.get()).isZero();
    }
  }

  @Test
  void sampledProtocolViolationStillStopsInsteadOfBecomingACandidateFailure() {
    var definition = definition(new HiddenTerminationTemplate(pipeline(.3)));
    var calls = new AtomicInteger();
    try (var study = open(definition, definition.runLimits(), false, new AtomicLong(), calls)) {
      assertThatThrownBy(study::run)
          .isInstanceOf(StudyProtocolException.class)
          .hasMessageContaining("hidden termination");
      assertThat(study.snapshot().state()).isEqualTo(StudyState.FAILED);
      assertThat(study.snapshot().trials()).hasSize(1);
      assertThat(calls.get()).isEqualTo(2);
    }
  }

  private DefaultGreybayStudy<SchedulingExample.Schedule, HardSoftScore> open(
      GreybayStudyDefinition<SchedulingExample.Schedule, HardSoftScore> definition,
      StudyRunLimits limits,
      boolean resume,
      AtomicLong clock,
      AtomicInteger calls) {
    return DefaultGreybayStudy.open(
        directory.resolve("study"),
        definition,
        limits,
        resume,
        (config, adapter, descriptor, seed, horizon, verifier, stop, stopping) -> {
          calls.incrementAndGet();
          clock.addAndGet(1_000_000);
          return new EvaluationOutcome<>(
              EvaluationStatus.COMPLETE,
              HardSoftScore.ZERO,
              0,
              1,
              0,
              1_000_000,
              0,
              0,
              VerificationResult.passed(),
              null,
              null,
              List.of());
        },
        clock::get);
  }

  private static GreybayStudyDefinition<SchedulingExample.Schedule, HardSoftScore> definition(
      ConfigurationTemplate template) {
    return GreybayStudyDefinition.<SchedulingExample.Schedule, HardSoftScore>builder()
        .baseSolverConfig(SchedulingExample.baseSolverConfig())
        .problemAdapter(SchedulingExample.problemAdapter())
        .trainingPanel(new ProblemPanel(List.of("train-small"), List.of(11L)))
        .heldOutPanel(new ProblemPanel(List.of("heldout-small"), List.of(101L)))
        .templates(List.of(template))
        .objective(new BalancedQualityObjective<>())
        .sampler(new FractionSampler(template))
        .optimizerSeed(17)
        .solveDuration(Duration.ofSeconds(1))
        .warmupDuration(Duration.ZERO)
        .maxTrials(1)
        .activeTimeLimit(Duration.ofMinutes(1))
        .build();
  }

  private static ConfigurationTemplate pipeline(double defaultFraction) {
    var values =
        Map.<String, Object>of(
            "gls.moves",
            "automatic",
            "gls.penaltyFactor",
            "0.125",
            "gls.sampleSize",
            8,
            "gls.focusStepLimit",
            4,
            "gls.focusPenaltyUpdateLimit",
            2,
            "gls.maxPenaltyUpdatesPerStep",
            8,
            "gls.excursionStepLimit",
            2,
            "gls.excursionRepairStepLimit",
            4,
            "gls.maxUnproductiveRounds",
            2);
    var dimensions =
        values.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .<Dimension>map(entry -> new FixedDimension(entry.getKey(), entry.getValue()))
            .toList();
    var gls =
        ReferenceTemplates.guidedLocalSearch(
            new TemplateParameters(
                new ComponentIdentity("gls", "1"),
                new SearchSpace(dimensions),
                ParameterAssignment.of(values)),
            Map.of(
                "automatic",
                MovePortfolio.automatic(
                    new ComponentIdentity("automatic", "1"), TemplateRequirements.allModels())));
    return ReferenceTemplates.pipeline(
        new ComponentIdentity("pipeline", "1"),
        gls,
        GreybayWorkflowExample.template(),
        new RealDimension("pipeline.firstFraction", INVALID_FRACTION, .9),
        defaultFraction);
  }

  private record FractionSampler(ConfigurationTemplate template) implements Sampler {
    @Override
    public ComponentIdentity identity() {
      return new ComponentIdentity("scripted-fractions", "1");
    }

    @Override
    public SamplerState initialState(long seed) {
      return new SamplerState("scripted-fractions", seed, 0, 0, 0, 0, Set.of());
    }

    @Override
    public CandidateProposal propose(SamplingContext context, SamplerState state) {
      long sequence = state.requestSequence() + 1;
      var values = new LinkedHashMap<>(template.defaultAssignment().values());
      values.put("template", template.identity().id());
      values.put("pipeline.firstFraction", sequence == 1 ? INVALID_FRACTION : .3);
      var assignment = ParameterAssignment.of(values);
      return new CandidateProposal(
          assignment,
          context.identityResolver().apply(assignment),
          sequence,
          ProposalReason.STARTUP,
          null,
          0,
          0,
          new SamplerState(
              state.implementationVersion(),
              state.randomState(),
              sequence,
              0,
              sequence,
              sequence,
              Set.of()));
    }
  }

  private record HiddenTerminationTemplate(ConfigurationTemplate delegate)
      implements ConfigurationTemplate {
    @Override
    public ComponentIdentity identity() {
      return delegate.identity();
    }

    @Override
    public SearchSpace searchSpace() {
      return delegate.searchSpace();
    }

    @Override
    public ParameterAssignment defaultAssignment() {
      return delegate.defaultAssignment();
    }

    @Override
    public void validate(ProblemCapabilities capabilities) {
      delegate.validate(capabilities);
    }

    @Override
    public SolverConfig materialize(SolverConfig base, ParameterAssignment assignment) {
      var config = delegate.materialize(base, assignment);
      if (assignment.getDouble("pipeline.firstFraction") == INVALID_FRACTION) {
        config
            .getPhaseConfigList()
            .getLast()
            .setTerminationConfig(new TerminationConfig().withBestScoreLimit("0hard/0soft"));
      }
      return config;
    }

    @Override
    public Map<Integer, Duration> approvedSearchPhaseLimits(
        SolverConfig config, Duration horizon, ParameterAssignment assignment) {
      return delegate.approvedSearchPhaseLimits(config, horizon, assignment);
    }
  }
}
