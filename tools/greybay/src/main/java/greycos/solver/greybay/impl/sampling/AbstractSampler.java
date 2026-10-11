package greycos.solver.greybay.impl.sampling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.api.sampling.CandidateProposal;
import greycos.solver.greybay.api.sampling.ProposalReason;
import greycos.solver.greybay.api.sampling.Sampler;
import greycos.solver.greybay.api.sampling.SamplerState;
import greycos.solver.greybay.api.sampling.SamplingContext;
import greycos.solver.greybay.api.sampling.SamplingStopReason;
import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.config.TpeSamplerConfig;

abstract class AbstractSampler implements Sampler {
  private static final int ROUNDS = 128;
  private final boolean adaptive;

  AbstractSampler(boolean adaptive) {
    this.adaptive = adaptive;
  }

  private String version() {
    return (adaptive ? "conditional-tpe" : "random") + "-1-splitmix64-1-" + NormalMath.VERSION;
  }

  @Override
  public ComponentIdentity identity() {
    return new ComponentIdentity(
        adaptive ? "greybay.conditional-tpe" : "greybay.random",
        version(),
        "greycos-solver-greybay");
  }

  @Override
  public SamplerState initialState(long optimizerSeed) {
    return new SamplerState(version(), optimizerSeed, 0, 0, 0, 0, java.util.Set.of());
  }

  @Override
  public CandidateProposal propose(SamplingContext context, SamplerState initial) {
    if (!initial.implementationVersion().equals(version()))
      throw new IllegalArgumentException(
          "Sampler state implementation ("
              + initial.implementationVersion()
              + ") does not match ("
              + version()
              + ").");
    if (!initial.customState().isEmpty())
      throw new IllegalArgumentException(
          "Reference samplers do not support custom sampler-state fields.");
    if (!context.space().rootChoices().containsAll(initial.exhaustedRoots()))
      throw new IllegalArgumentException(
          "Sampler state contains exhausted roots absent from the declared space.");
    var request = new Request(context, initial);
    var partition =
        adaptive ? QualityPartition.of(context) : new QualityPartition(List.of(), List.of());
    request.goodCount = partition.good().size();
    request.otherCount = partition.other().size();
    String forcedRoot = adaptive ? coverageRoot(context, request) : null;
    long terminals =
        context.history().stream().filter(observation -> !observation.calibration()).count();
    boolean startup =
        adaptive && (terminals < TpeSamplerConfig.STARTUP_TRIALS || forcedRoot != null);
    if (adaptive && !startup) request.postStartup = Math.incrementExact(request.postStartup);
    boolean exploration =
        !adaptive || !startup && request.postStartup % TpeSamplerConfig.EXPLORATION_INTERVAL == 0;
    boolean useTpe = adaptive && !startup && !exploration && partition.available();
    ProposalReason reason =
        startup
            ? ProposalReason.STARTUP
            : exploration
                ? ProposalReason.EXPLORATION
                : useTpe ? ProposalReason.TPE : ProposalReason.PRIOR_FALLBACK;
    var prior = new ConditionalModel(context.space(), List.of(), List.of());
    var selected =
        useTpe ? new ConditionalModel(context.space(), partition.good(), partition.other()) : prior;
    for (int pass = 0; pass < 2; pass++) {
      for (int round = 0; round < ROUNDS; round++) {
        request.rounds = Math.incrementExact(request.rounds);
        int count = pass == 0 && useTpe ? TpeSamplerConfig.ACQUISITION_CANDIDATES : 1;
        var draws = new ArrayList<ConditionalModel.Draw>(count);
        for (int draw = 0; draw < count; draw++) {
          request.draws = Math.incrementExact(request.draws);
          draws.add((pass == 0 ? selected : prior).draw(request.random, forcedRoot));
        }
        // TimSort is stable: exact acquisition ties retain generation order.
        draws.sort(Comparator.comparingDouble(ConditionalModel.Draw::acquisition).reversed());
        for (var draw : draws) {
          String identity = request.resolve(draw.assignment());
          if (!request.reserved.contains(identity))
            return request.admit(
                draw.assignment(), identity, pass == 0 ? reason : ProposalReason.PRIOR_FALLBACK);
        }
      }
    }
    var finite = FiniteSpace.enumerate(context.space(), forcedRoot);
    if (finite != null) {
      for (var assignment : finite) {
        String identity = request.resolve(assignment);
        if (!request.reserved.contains(identity))
          return request.admit(assignment, identity, ProposalReason.PRIOR_FALLBACK);
      }
      if (forcedRoot != null) {
        request.exhausted.add(forcedRoot);
        // Cover another root within the same top-level request, without pretending the
        // complete study is exhausted merely because this startup branch is exhausted.
        for (String root : context.space().rootChoices()) {
          if (request.exhausted.contains(root)) continue;
          var branch = FiniteSpace.enumerate(context.space(), root);
          if (branch == null) return request.stop(SamplingStopReason.SAMPLING_STALLED, reason);
          for (var assignment : branch) {
            String identity = request.resolve(assignment);
            if (!request.reserved.contains(identity))
              return request.admit(assignment, identity, ProposalReason.PRIOR_FALLBACK);
          }
          request.exhausted.add(root);
        }
      }
      // SPACE_EXHAUSTED requires a complete whole-space proof under the global limit.
      var complete = FiniteSpace.enumerate(context.space(), null);
      if (complete != null
          && complete.stream()
              .allMatch(assignment -> request.reserved.contains(request.resolve(assignment))))
        return request.stop(SamplingStopReason.SPACE_EXHAUSTED, reason);
    }
    return request.stop(SamplingStopReason.SAMPLING_STALLED, reason);
  }

  private String coverageRoot(SamplingContext context, Request request) {
    for (String root : context.space().rootChoices()) {
      if (request.exhausted.contains(root)) continue;
      long count =
          context.history().stream()
              .filter(
                  observation ->
                      !observation.calibration()
                          && (!observation.assignment().contains("template")
                              || observation.assignment().getString("template").equals(root)))
              .count();
      if (count < TpeSamplerConfig.ROOT_COVERAGE) {
        if (FiniteSpace.allMatch(
            context.space(),
            root.isEmpty() ? null : root,
            assignment -> request.reserved.contains(request.resolve(assignment)))) {
          request.exhausted.add(root);
          continue;
        }
        return root.isEmpty() ? null : root;
      }
    }
    return null;
  }

  private final class Request {
    private final SamplingContext context;
    private final SplitMix64 random;
    private final long sequence;
    private long postStartup;
    private long rounds;
    private long draws;
    private int goodCount;
    private int otherCount;
    private final HashSet<String> exhausted;
    private final HashSet<String> reserved;
    private final Map<ParameterAssignment, String> resolved = new HashMap<>();

    Request(SamplingContext context, SamplerState initial) {
      this.context = context;
      random = new SplitMix64(initial.randomState());
      sequence = Math.incrementExact(initial.requestSequence());
      postStartup = initial.postStartupRequestSequence();
      rounds = initial.roundCount();
      draws = initial.drawCount();
      exhausted = new HashSet<>(initial.exhaustedRoots());
      reserved = new HashSet<>(context.reservedIdentities());
      context.history().forEach(observation -> reserved.add(observation.candidateIdentity()));
    }

    String resolve(ParameterAssignment assignment) {
      return resolved.computeIfAbsent(
          assignment,
          key ->
              Objects.requireNonNull(context.identityResolver().apply(key), "candidate identity"));
    }

    SamplerState state() {
      return new SamplerState(
          version(), random.state(), sequence, postStartup, rounds, draws, exhausted);
    }

    CandidateProposal admit(
        ParameterAssignment assignment, String identity, ProposalReason reason) {
      return new CandidateProposal(
          assignment, identity, sequence, reason, null, goodCount, otherCount, state());
    }

    CandidateProposal stop(SamplingStopReason stop, ProposalReason reason) {
      return new CandidateProposal(
          null, null, sequence, reason, stop, goodCount, otherCount, state());
    }
  }
}
