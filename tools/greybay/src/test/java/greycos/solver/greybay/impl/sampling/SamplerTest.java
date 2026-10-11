package greycos.solver.greybay.impl.sampling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import greycos.solver.greybay.api.sampling.ProposalReason;
import greycos.solver.greybay.api.sampling.SamplerState;
import greycos.solver.greybay.api.sampling.SamplingContext;
import greycos.solver.greybay.api.sampling.SamplingObservation;
import greycos.solver.greybay.api.sampling.SamplingStopReason;
import greycos.solver.greybay.config.Activation;
import greycos.solver.greybay.config.CategoricalDimension;
import greycos.solver.greybay.config.IntegerDimension;
import greycos.solver.greybay.config.NumericPrior;
import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.config.RealDimension;
import greycos.solver.greybay.config.SearchSpace;

import org.junit.jupiter.api.Test;

class SamplerTest {
  private static final Comparator<SamplingObservation> QUALITY =
      Comparator.comparingLong(observation -> Long.parseLong(observation.qualityKey()));

  private static SamplingContext context(
      SearchSpace space,
      List<SamplingObservation> history,
      Set<String> reserved,
      Function<ParameterAssignment, String> resolver) {
    return new SamplingContext(space, history, reserved, QUALITY, resolver);
  }

  private static SamplingObservation observation(
      long id, ParameterAssignment assignment, boolean eligible, long quality) {
    return new SamplingObservation(
        id,
        assignment,
        assignment.canonicalForm(),
        eligible,
        false,
        eligible ? Long.toString(quality) : null);
  }

  @Test
  void splitMixGoldenAndStateRoundTrip() {
    var random = new SplitMix64(0);
    assertThat(Long.toUnsignedString(random.nextLong(), 16)).isEqualTo("e220a8397b1dcdaf");
    assertThat(Long.toUnsignedString(random.nextLong(), 16)).isEqualTo("6e789e6aa1b965f4");
    assertThat(Long.toUnsignedString(random.nextLong(), 16)).isEqualTo("6c45d188009454f");
    var state = new SamplerState("test", Long.MIN_VALUE, 15, 3, 123, 456, Set.of("B", "A"));
    assertThat(SamplerState.fromMap(state.toMap())).isEqualTo(state);
  }

  @Test
  void customGaussianCacheStateIsDeeplyImmutableAndRoundTrips() {
    var cache = new java.util.LinkedHashMap<String, Object>();
    cache.put("present", true);
    cache.put("gaussian", Double.toHexString(0.125));
    cache.put("optional", null);
    var payload = new java.util.LinkedHashMap<String, Object>();
    payload.put("cache", new ArrayList<>(List.of(cache)));
    var state = new SamplerState("custom-1", 41, 1, 0, 1, 1, Set.of(), payload);
    cache.put("gaussian", "changed");
    payload.clear();
    assertThat(state.customState().toString()).contains("0x1.0p-3").doesNotContain("changed");
    assertThat(SamplerState.fromMap(state.toMap())).isEqualTo(state);
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new SamplerState(
                    "custom-1", 0, 0, 0, 0, 0, Set.of(), Map.of("gaussian", Double.NaN)));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new SamplerState(
                    "custom-1", 0, 0, 0, 0, 0, Set.of(), Map.of("object", new Object())));
  }

  @Test
  void priorGoldenUsesOnlyActiveCoordinates() {
    var space =
        new SearchSpace(
            List.of(
                new CategoricalDimension("template", List.of("A", "B")),
                new IntegerDimension("x", 1, 9, 2).withActivation(Activation.when("template", "A")),
                new RealDimension("y", 0.25, 4, NumericPrior.LOGARITHMIC)
                    .withActivation(Activation.when("template", "B"))));
    var sampler = new RandomSampler();
    var state = sampler.initialState(42);
    var context = context(space, List.of(), Set.of(), ParameterAssignment::canonicalForm);
    // Independent SplitMix64/prior reference: Python integer operations + the declared transforms.
    var first = sampler.propose(context, state);
    assertThat(first.assignment().getString("template")).isEqualTo("B");
    assertThat(Double.toHexString(first.assignment().getDouble("y")))
        .isEqualTo("0x1.8ed54a4b50f78p-2");
    assertThat(first.assignment().contains("x")).isFalse();
    var second = sampler.propose(context, first.nextState());
    assertThat(second.assignment())
        .isEqualTo(ParameterAssignment.of(Map.of("template", "A", "x", 3)));
    assertThat(sampler.propose(context, SamplerState.fromMap(first.nextState().toMap())))
        .isEqualTo(second);
  }

  @Test
  void independentTpeGoldenIncludesRootAndContextualDensities() {
    var space =
        new SearchSpace(
            List.of(
                new CategoricalDimension("template", List.of("A", "B")),
                new IntegerDimension("x", 0, 100, 1)
                    .withActivation(Activation.in("template", Set.of("A", "B")))));
    var history = new ArrayList<SamplingObservation>();
    for (int id = 1; id <= 20; id++)
      history.add(
          observation(
              id,
              ParameterAssignment.of(Map.of("template", id % 2 == 1 ? "A" : "B", "x", id)),
              true,
              id));
    var sampler = new TpeSampler();
    var proposal =
        sampler.propose(
            context(space, history, Set.of(), ParameterAssignment::canonicalForm),
            sampler.initialState(17));
    // Independent mpmath 1.3.0 reference, 60-digit truncated-normal integrals/inversion;
    // maximum acquisition is draw 57 (zero based), B/50. The 192 uniform draws are exact.
    assertThat(proposal.assignment())
        .isEqualTo(ParameterAssignment.of(Map.of("template", "B", "x", 50)));
    assertThat(proposal.reason()).isEqualTo(ProposalReason.TPE);
    assertThat(proposal.goodObservationCount()).isEqualTo(4);
    assertThat(proposal.otherObservationCount()).isEqualTo(16);
    assertThat(proposal.nextState().drawCount()).isEqualTo(64);
    assertThat(Long.toUnsignedString(proposal.nextState().randomState(), 16))
        .isEqualTo("a99b4b1f77dd0fd1");
  }

  @Test
  void qualityTiesRemainWholeAndBoundaryTiesPreferSmallerGoodSet() {
    var space = new SearchSpace(List.of(new IntegerDimension("x", 1, 10, 1)));
    var history = new ArrayList<SamplingObservation>();
    for (int id = 1; id <= 10; id++)
      history.add(
          observation(
              id, ParameterAssignment.of(Map.of("x", id)), true, id == 1 ? 3 : id <= 3 ? 2 : 1));
    var partition =
        QualityPartition.of(context(space, history, Set.of(), ParameterAssignment::canonicalForm));
    assertThat(partition.good()).hasSize(1);
    assertThat(partition.other()).hasSize(9);
    var tied =
        history.stream().map(row -> observation(row.trialId(), row.assignment(), true, 0)).toList();
    assertThat(
            QualityPartition.of(context(space, tied, Set.of(), ParameterAssignment::canonicalForm))
                .available())
        .isFalse();
  }

  @Test
  void startupIncludesFailedTrialsAndCoversRootsInOrder() {
    var space =
        new SearchSpace(
            List.of(
                new CategoricalDimension("template", List.of("A", "B")),
                new IntegerDimension("x", 0, 1_000, 1)));
    var history = new ArrayList<SamplingObservation>();
    var sampler = new TpeSampler();
    var state = sampler.initialState(7);
    for (int index = 0; index < 20; index++) {
      var proposal =
          sampler.propose(
              context(space, history, Set.of(), ParameterAssignment::canonicalForm), state);
      assertThat(proposal.reason()).isEqualTo(ProposalReason.STARTUP);
      if (index < 4)
        assertThat(proposal.assignment().getString("template")).isEqualTo(index < 2 ? "A" : "B");
      history.add(observation(index + 1, proposal.assignment(), false, 0));
      state = proposal.nextState();
    }
    var after =
        sampler.propose(
            context(space, history, Set.of(), ParameterAssignment::canonicalForm), state);
    assertThat(after.reason()).isEqualTo(ProposalReason.PRIOR_FALLBACK);
    assertThat(after.nextState().postStartupRequestSequence()).isEqualTo(1);
  }

  @Test
  void everyTenthRequestExploresRegardlessOfAcquisitionDrawCount() {
    var space = new SearchSpace(List.of(new IntegerDimension("x", 0, 100, 1)));
    var history = new ArrayList<SamplingObservation>();
    for (int id = 1; id <= 20; id++)
      history.add(observation(id, ParameterAssignment.of(Map.of("x", id)), true, id));
    var sampler = new TpeSampler();
    var initial = sampler.initialState(71);
    var state =
        new SamplerState(
            initial.implementationVersion(), initial.randomState(), 29, 9, 500, 32_000, Set.of());
    var proposal =
        sampler.propose(
            context(space, history, Set.of(), ParameterAssignment::canonicalForm), state);
    assertThat(proposal.reason()).isEqualTo(ProposalReason.EXPLORATION);
    assertThat(proposal.nextState().postStartupRequestSequence()).isEqualTo(10);
  }

  @Test
  void aliasesAreNotObservationsAndOnlyEnumerationProvesExhaustion() {
    var sampler = new RandomSampler();
    var finite = new SearchSpace(List.of(new IntegerDimension("x", 0, 2, 1)));
    var stop =
        sampler.propose(
            context(finite, List.of(), Set.of("alias"), ignored -> "alias"),
            sampler.initialState(1));
    assertThat(stop.stopReason()).isEqualTo(SamplingStopReason.SPACE_EXHAUSTED);
    assertThat(stop.nextState().roundCount()).isEqualTo(256);
    assertThat(stop.nextState().drawCount()).isEqualTo(256);
    var continuous = new SearchSpace(List.of(new RealDimension("x", 0, 1)));
    var stalled =
        sampler.propose(
            context(continuous, List.of(), Set.of("alias"), ignored -> "alias"),
            sampler.initialState(1));
    assertThat(stalled.stopReason()).isEqualTo(SamplingStopReason.SAMPLING_STALLED);
    assertThat(stalled.nextState().requestSequence()).isEqualTo(1);
    assertThat(
            sampler.propose(
                context(continuous, List.of(), Set.of("alias"), ignored -> "alias"),
                sampler.initialState(1)))
        .isEqualTo(stalled);
  }

  @Test
  void finiteRootAlreadyReservedByBaselineDoesNotTrapStartup() {
    var space =
        new SearchSpace(
            List.of(
                new CategoricalDimension("template", List.of("fixed", "continuous")),
                new RealDimension("x", 0, 1)
                    .withActivation(Activation.when("template", "continuous"))));
    var baseline = ParameterAssignment.of(Map.of("template", "fixed"));
    var sampler = new TpeSampler();
    var proposal =
        sampler.propose(
            context(
                space,
                List.of(),
                Set.of(baseline.canonicalForm()),
                ParameterAssignment::canonicalForm),
            sampler.initialState(1));
    assertThat(proposal.assignment().getString("template")).isEqualTo("continuous");
    assertThat(proposal.nextState().exhaustedRoots()).contains("fixed");
  }

  @Test
  void startupSkipsEveryAliasOfAnExhaustedRootWithoutAdvancingRandomState() {
    var space =
        new SearchSpace(
            List.of(
                new CategoricalDimension("template", List.of("aliased", "continuous")),
                new IntegerDimension("x", 0, 2, 1)
                    .withActivation(Activation.when("template", "aliased")),
                new RealDimension("y", 0, 1)
                    .withActivation(Activation.when("template", "continuous"))));
    var resolved = new ArrayList<ParameterAssignment>();
    var sampler = new TpeSampler();
    var proposal =
        sampler.propose(
            context(
                space,
                List.of(),
                Set.of("reserved-alias"),
                assignment -> {
                  resolved.add(assignment);
                  return assignment.getString("template").equals("aliased")
                      ? "reserved-alias"
                      : assignment.canonicalForm();
                }),
            sampler.initialState(71));
    assertThat(resolved)
        .containsExactly(
            ParameterAssignment.of(Map.of("template", "aliased", "x", 0)),
            ParameterAssignment.of(Map.of("template", "aliased", "x", 1)),
            ParameterAssignment.of(Map.of("template", "aliased", "x", 2)),
            proposal.assignment());
    assertThat(proposal.assignment().getString("template")).isEqualTo("continuous");
    assertThat(proposal.reason()).isEqualTo(ProposalReason.STARTUP);
    assertThat(proposal.nextState().exhaustedRoots()).containsExactly("aliased");
    assertThat(proposal.nextState().requestSequence()).isEqualTo(1);
    assertThat(proposal.nextState().roundCount()).isEqualTo(1);
    assertThat(proposal.nextState().drawCount()).isEqualTo(1);
    assertThat(proposal.nextState().randomState()).isEqualTo(71 + 0x9e3779b97f4a7c15L);
  }

  @Test
  void coldDimensionsHaveZeroAcquisitionAndUseTheExactPrior() {
    var space = new SearchSpace(List.of(new IntegerDimension("x", 0, 10, 1)));
    var good = List.of(observation(1, ParameterAssignment.of(Map.of("x", 5)), true, 1));
    var other =
        List.of(
            observation(2, ParameterAssignment.of(Map.of("x", 2)), true, 0),
            observation(3, ParameterAssignment.of(Map.of("x", 3)), true, 0));
    var cold = new ConditionalModel(space, good, other).draw(new SplitMix64(31), null);
    var prior = new ConditionalModel(space, List.of(), List.of()).draw(new SplitMix64(31), null);
    assertThat(cold).isEqualTo(prior);
    assertThat(cold.acquisition()).isZero();
  }

  @Test
  void independentDensitiesCannotDistinguishAnEqualityInteraction() {
    var space =
        new SearchSpace(
            List.of(
                new CategoricalDimension("x", List.of("0", "1")),
                new CategoricalDimension("y", List.of("0", "1"))));
    var good =
        List.of(
            observation(1, ParameterAssignment.of(Map.of("x", "0", "y", "0")), true, 1),
            observation(2, ParameterAssignment.of(Map.of("x", "1", "y", "1")), true, 1));
    var other =
        List.of(
            observation(3, ParameterAssignment.of(Map.of("x", "0", "y", "1")), true, 0),
            observation(4, ParameterAssignment.of(Map.of("x", "1", "y", "0")), true, 0));
    assertThat(QUALITY.compare(good.getFirst(), other.getFirst())).isPositive();

    // Both partitions contain one zero and one one in each coordinate. With the unit
    // uniform prior, each marginal is (1 + 1/2)/(2 + 1) = 1/2, and every joint
    // independent density is 1/4. This manual calculation ignores the equality interaction.
    double marginal = (1.0 + 0.5) / (2.0 + 1.0);
    double manualLogRatio = StrictMath.log(marginal * marginal / (marginal * marginal));
    assertThat(manualLogRatio).isZero();
    var model = new ConditionalModel(space, good, other);
    // Four pinned SplitMix64 seeds address all four assignments without a statistical test.
    var fixtures = Map.of(7L, "00", 1L, "11", 3L, "01", 0L, "10");
    var observedQualities = new HashSet<Integer>();
    for (var fixture : fixtures.entrySet()) {
      var draw = model.draw(new SplitMix64(fixture.getKey()), null);
      String pair = draw.assignment().getString("x") + draw.assignment().getString("y");
      assertThat(pair).isEqualTo(fixture.getValue());
      assertThat(draw.acquisition()).isEqualTo(manualLogRatio);
      observedQualities.add(pair.charAt(0) == pair.charAt(1) ? 1 : 0);
    }
    assertThat(observedQualities).containsExactlyInAnyOrder(0, 1);
  }

  @Test
  void incompatibleStateAndProtocolFailuresAreNotSwallowed() {
    var space = new SearchSpace(List.of(new IntegerDimension("x", 0, 10, 1)));
    var sampler = new TpeSampler();
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                sampler.propose(
                    context(space, List.of(), Set.of(), ParameterAssignment::canonicalForm),
                    new RandomSampler().initialState(1)));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                sampler.propose(
                    context(
                        space,
                        List.of(),
                        Set.of(),
                        ignored -> {
                          throw new IllegalArgumentException("protocol");
                        }),
                    sampler.initialState(1)))
        .withMessage("protocol");
  }

  @Test
  void tpeDuplicateRoundsCommitAllDrawsBeforeFiniteStop() {
    var space = new SearchSpace(List.of(new IntegerDimension("x", 0, 19, 1)));
    var history = new ArrayList<SamplingObservation>();
    for (int id = 1; id <= 20; id++)
      history.add(observation(id, ParameterAssignment.of(Map.of("x", id - 1)), true, id));
    var sampler = new TpeSampler();
    var stop =
        sampler.propose(
            context(space, history, Set.of(), ParameterAssignment::canonicalForm),
            sampler.initialState(19));
    assertThat(stop.stopReason()).isEqualTo(SamplingStopReason.SPACE_EXHAUSTED);
    assertThat(stop.nextState().roundCount()).isEqualTo(256);
    assertThat(stop.nextState().drawCount()).isEqualTo(128 * 64 + 128);
    assertThat(stop.nextState().postStartupRequestSequence()).isEqualTo(1);
  }

  @Test
  void rootCoverageCanExtendStartupPastTwentyTerminalFailures() {
    var roots = java.util.stream.IntStream.range(0, 11).mapToObj(index -> "root" + index).toList();
    var space =
        new SearchSpace(
            List.of(
                new CategoricalDimension("template", roots), new IntegerDimension("x", 0, 99, 1)));
    var history = new ArrayList<SamplingObservation>();
    for (int index = 0; index < 20; index++)
      history.add(
          observation(
              index + 1,
              ParameterAssignment.of(Map.of("template", roots.get(index / 2), "x", index % 2)),
              false,
              0));
    var sampler = new TpeSampler();
    var proposal =
        sampler.propose(
            context(space, history, Set.of(), ParameterAssignment::canonicalForm),
            sampler.initialState(5));
    assertThat(proposal.reason()).isEqualTo(ProposalReason.STARTUP);
    assertThat(proposal.assignment().getString("template")).isEqualTo("root10");
    assertThat(proposal.nextState().postStartupRequestSequence()).isZero();
  }

  @Test
  void failedMaterializationCanBeAdmittedOnceUsingTypedAssignmentIdentity() {
    var space = new SearchSpace(List.of(new IntegerDimension("x", 0, 1, 1)));
    Function<ParameterAssignment, String> resolver =
        assignment -> "materialization-failed:" + assignment.canonicalForm();
    var sampler = new RandomSampler();
    var first =
        sampler.propose(context(space, List.of(), Set.of(), resolver), sampler.initialState(1));
    var reserved = new HashSet<>(Set.of(first.candidateIdentity()));
    var second = sampler.propose(context(space, List.of(), reserved, resolver), first.nextState());
    assertThat(second.assignment()).isNotEqualTo(first.assignment());
    reserved.add(second.candidateIdentity());
    assertThat(
            sampler
                .propose(context(space, List.of(), reserved, resolver), second.nextState())
                .stopReason())
        .isEqualTo(SamplingStopReason.SPACE_EXHAUSTED);
  }
}
