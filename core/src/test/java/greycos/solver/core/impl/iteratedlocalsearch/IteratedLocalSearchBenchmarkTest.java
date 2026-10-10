package greycos.solver.core.impl.iteratedlocalsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Map;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.iteratedlocalsearch.IteratedLocalSearchPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.alns.AlnsMoveThreadingWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Workload;
import greycos.solver.core.impl.io.jaxb.SolverConfigIO;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchBenchmark.FinalMetricSamples;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchBenchmark.MixedAdapter;
import greycos.solver.core.impl.iteratedlocalsearch.IteratedLocalSearchBenchmark.Options;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSample;
import greycos.solver.core.impl.solver.monitoring.SolverWorkSnapshot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;

class IteratedLocalSearchBenchmarkTest {
  @ParameterizedTest
  @ValueSource(strings = {"ils", "vns"})
  void optionalGentleProfileChangesOnlyPerturbationStrengths(String method) {
    var adapter = new MixedAdapter(12);
    var deep =
        IteratedLocalSearchBenchmark.configure(
            adapter, new Options(method + "-deep", 11, 10, "NONE", 1, 0, Path.of("unused")));
    var gentle =
        IteratedLocalSearchBenchmark.configure(
            adapter, new Options(method + "-gentle", 11, 10, "NONE", 1, 0, Path.of("unused")));
    var deepPhase = (IteratedLocalSearchPhaseConfig) deep.getPhaseConfigList().getFirst();
    var gentlePhase = (IteratedLocalSearchPhaseConfig) gentle.getPhaseConfigList().getFirst();
    assertThat(gentlePhase.getEpisodeCandidateAttemptLimit()).isEqualTo(40_000L);
    assertThat(gentlePhase.getPerturbationAttemptLimit()).isEqualTo(1000L);
    assertThat(gentlePhase.getLocalSearchConfig().getAcceptorConfig().getLateAcceptanceSize())
        .isEqualTo(64);
    assertThat(gentlePhase.getPerturbationStrengths())
        .containsExactlyElementsOf(
            method.equals("ils") ? java.util.List.of(2) : java.util.List.of(1, 2, 4));
    deepPhase.setPerturbationStrengths(gentlePhase.getPerturbationStrengths());
    var deepXml = new StringWriter();
    var gentleXml = new StringWriter();
    new SolverConfigIO().write(deep, deepXml);
    new SolverConfigIO().write(gentle, gentleXml);
    assertThat(gentleXml.toString()).isEqualTo(deepXml.toString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"ils", "vns"})
  void optionalDeepProfileChangesOnlyEpisodeHorizon(String method) {
    var adapter = new MixedAdapter(12);
    var large =
        IteratedLocalSearchBenchmark.configure(
            adapter, new Options(method + "-large", 11, 10, "NONE", 1, 0, Path.of("unused")));
    var deep =
        IteratedLocalSearchBenchmark.configure(
            adapter, new Options(method + "-deep", 11, 10, "NONE", 1, 0, Path.of("unused")));
    var largePhase = (IteratedLocalSearchPhaseConfig) large.getPhaseConfigList().getFirst();
    var deepPhase = (IteratedLocalSearchPhaseConfig) deep.getPhaseConfigList().getFirst();
    assertThat(largePhase.getEpisodeCandidateAttemptLimit()).isEqualTo(4000L);
    assertThat(deepPhase.getEpisodeCandidateAttemptLimit()).isEqualTo(40_000L);
    assertThat(deepPhase.getPerturbationAttemptLimit()).isEqualTo(1000L);
    assertThat(deepPhase.getLocalSearchConfig().getAcceptorConfig().getLateAcceptanceSize())
        .isEqualTo(64);
    assertThat(deepPhase.getPerturbationStrengths())
        .containsExactlyElementsOf(
            method.equals("ils") ? java.util.List.of(4) : java.util.List.of(2, 4, 8));
    largePhase.setEpisodeCandidateAttemptLimit(40_000L);
    var largeXml = new StringWriter();
    var deepXml = new StringWriter();
    new SolverConfigIO().write(large, largeXml);
    new SolverConfigIO().write(deep, deepXml);
    assertThat(deepXml.toString()).isEqualTo(largeXml.toString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"basic", "list", "mixed"})
  void finiteVndPortfolioSolvesAllModelShapes(String shape) {
    verifyVnd(AlnsMoveThreadingWorkload.named(shape), shape.equals("mixed") ? 4 : 2);
  }

  private static <S> void verifyVnd(Workload<S> workload, int expectedSelectorCount) {
    var problem = workload.createProblem(12);
    var initialScore = workload.recompute(problem);
    var config =
        workload.solverConfig("NONE", 11, 1, new TerminationConfig(), EnvironmentMode.NO_ASSERT);
    var phase =
        IteratedLocalSearchBenchmark.vndPhase(config)
            .withTerminationConfig(new TerminationConfig().withStepCountLimit(20));
    var selector = (UnionMoveSelectorConfig) phase.getMoveSelectorConfig();
    assertThat(selector.getSelectionOrder()).isEqualTo(SelectionOrder.ORIGINAL);
    assertThat(selector.getMoveSelectorList()).hasSize(expectedSelectorCount);
    config.withPhases(phase);
    var solution = SolverFactory.<S>create(config).buildSolver().solve(problem);
    assertThat(workload.recompute(solution))
        .isEqualTo(workload.score(solution))
        .isGreaterThanOrEqualTo(initialScore);
  }

  @Test
  void finalDiagnosticSamplesKeepLatestGaugePerProducerAndPhase() {
    var recorder = new FinalMetricSamples();
    var tags = Tags.of("island.id", "0", "phase.index", "1");
    var meter =
        new Meter.Id(
            SolverMetric.ITERATED_LOCAL_SEARCH_STATISTICS.getMeterId() + ".episodes",
            tags,
            null,
            null,
            Meter.Type.GAUGE);
    recorder.accept(
        new SolverMetricSample(
            SolverMetricSample.Kind.STEP,
            1,
            "island-0",
            tags,
            null,
            false,
            null,
            SolverWorkSnapshot.ZERO,
            Map.of(meter, 99.0)));
    recorder.accept(
        new SolverMetricSample(
            SolverMetricSample.Kind.FINAL,
            2,
            "island-0",
            tags,
            null,
            false,
            null,
            SolverWorkSnapshot.ZERO,
            Map.of(meter, 3.0)));
    recorder.accept(
        new SolverMetricSample(
            SolverMetricSample.Kind.FINAL,
            3,
            "island-0",
            tags,
            null,
            false,
            null,
            SolverWorkSnapshot.ZERO,
            Map.of(meter, 4.0)));
    var otherTags = Tags.of("island.id", "1", "phase.index", "1");
    var otherMeter = new Meter.Id(meter.getName(), otherTags, null, null, Meter.Type.GAUGE);
    recorder.accept(
        new SolverMetricSample(
            SolverMetricSample.Kind.FINAL,
            3,
            "island-1",
            otherTags,
            null,
            false,
            null,
            SolverWorkSnapshot.ZERO,
            Map.of(otherMeter, 7.0)));
    assertThat(recorder.rows()).hasSize(3);
    assertThat(recorder.rows().get(1))
        .startsWith("island-0\t3\t")
        .contains("phase.index=1")
        .endsWith("\t4.0");
    assertThat(recorder.rows().get(2)).startsWith("island-1\t3\t").endsWith("\t7.0");
    assertThat(IteratedLocalSearchBenchmark.monitoringConfig(false).getSolverMetricList())
        .isEmpty();
    assertThat(IteratedLocalSearchBenchmark.monitoringConfig(true).getSolverMetricList())
        .containsExactly(SolverMetric.ITERATED_LOCAL_SEARCH_STATISTICS);
  }

  @Test
  void mixedAssignmentsRoundTripUsingFreshObjectsAndIndependentScore() throws Exception {
    var adapter = new MixedAdapter(12);
    var original = adapter.load();
    // Exercise a different basic assignment and list order than the generated input.
    original.getJobs().getFirst().setMachine(original.getMachines().getLast());
    java.util.Collections.reverse(original.getRoutes().getFirst().getVisits());
    greycos.solver.core.api.solver.SolutionManager.updateShadowVariables(original);
    var encoded = adapter.assignments(original);
    var reconstructed = adapter.reconstruct(encoded);
    assertThat(reconstructed).isNotSameAs(original);
    assertThat(reconstructed.getJobs().getFirst()).isNotSameAs(original.getJobs().getFirst());
    assertThat(adapter.assignments(reconstructed)).isEqualTo(encoded);
    assertThat(adapter.replay(reconstructed)).isEqualTo(adapter.replay(original));
  }

  @Test
  void rejectsMissingAndDuplicateAssignments() {
    var adapter = new MixedAdapter(12);
    var assignments = adapter.assignments(adapter.load());
    var firstRow = assignments.substring(0, assignments.indexOf('\n') + 1);
    assertThatThrownBy(() -> adapter.reconstruct(assignments + firstRow))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate");
    assertThatThrownBy(() -> adapter.reconstruct(assignments.substring(firstRow.length())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Missing");
  }

  @Test
  void fixedAndScheduledUseSameInnerAlgorithmAndBudget() {
    var adapter = new MixedAdapter(12);
    var fixed =
        (IteratedLocalSearchPhaseConfig)
            IteratedLocalSearchBenchmark.configure(
                    adapter, new Options("ils-small", 11, 10, "NONE", 1, 0, Path.of("unused")))
                .getPhaseConfigList()
                .getFirst();
    var scheduled =
        (IteratedLocalSearchPhaseConfig)
            IteratedLocalSearchBenchmark.configure(
                    adapter, new Options("vns-small", 11, 10, "NONE", 1, 0, Path.of("unused")))
                .getPhaseConfigList()
                .getFirst();
    assertThat(fixed.getPerturbationStrengths()).containsExactly(2);
    assertThat(scheduled.getPerturbationStrengths()).containsExactly(1, 2, 4);
    assertThat(fixed.getEpisodeCandidateAttemptLimit())
        .isEqualTo(scheduled.getEpisodeCandidateAttemptLimit());
    assertThat(fixed.getLocalSearchConfig().getAcceptorConfig().getLateAcceptanceSize())
        .isEqualTo(scheduled.getLocalSearchConfig().getAcceptorConfig().getLateAcceptanceSize());
  }
}
