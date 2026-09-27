package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.phase.PhaseCommand;
import greycos.solver.core.config.islandmodel.IslandModelPhaseConfig;
import greycos.solver.core.config.phase.custom.CustomPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.monitoring.SolverMetricSample;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.AbstractMeterTest;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@Timeout(20)
class NestedIslandMetricsTest extends AbstractMeterTest {

  @Test
  void nestedMetersHaveDistinctOwnersAndChildCleanupKeepsParentAndSiblingMeters() throws Exception {
    var entered = new CountDownLatch(2);
    var release = List.of(new CountDownLatch(1), new CountDownLatch(1));
    var arrival = new AtomicInteger();
    PhaseCommand<TestdataSolution> command =
        context -> {
          int index = arrival.getAndIncrement();
          entered.countDown();
          try {
            if (!release.get(index).await(10, TimeUnit.SECONDS)) {
              throw new IllegalStateException("Nested metrics test did not release an island.");
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          }
        };
    var config =
        PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class)
            .withMonitoringConfig(
                new MonitoringConfig()
                    .withSolverMetricList(List.of(SolverMetric.SCORE_CALCULATION_COUNT)))
            .withPhases(
                new IslandModelPhaseConfig()
                    .withIslandCount(1)
                    .withPhaseConfigList(
                        List.of(
                            new IslandModelPhaseConfig()
                                .withIslandCount(2)
                                .withPhaseConfigList(
                                    List.of(
                                        new CustomPhaseConfig()
                                            .withCustomPhaseCommands(command))))));
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var samples = new ArrayList<SolverMetricSample>();
    solver.getSolverScope().addMetricSampleListener(samples::add);
    var registry = new SimpleMeterRegistry();
    try (var executor = Executors.newSingleThreadExecutor()) {
      Metrics.addRegistry(registry);
      try {
        var solving = executor.submit(() -> solver.solve(TestdataSolution.generateSolution(3, 3)));
        try {
          assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
          assertThat(islandIds(registry))
              .containsExactlyInAnyOrder(
                  "root",
                  "0",
                  "phase-0/island-0/phase-0/island-0",
                  "phase-0/island-0/phase-0/island-1");

          release.getFirst().countDown();
          await()
              .atMost(Duration.ofSeconds(5))
              .untilAsserted(
                  () -> {
                    var ids = islandIds(registry);
                    assertThat(ids).contains("root", "0");
                    assertThat(ids.stream().filter(id -> id.contains("/"))).hasSize(1);
                  });
          release.getLast().countDown();
          solving.get(5, TimeUnit.SECONDS);

          assertThat(islandIds(registry)).isEmpty();
          assertThat(samples.stream().filter(sample -> sample.source().contains("/phase-")))
              .isNotEmpty()
              .allSatisfy(
                  sample ->
                      assertThat(
                              sample.tags().stream()
                                  .filter(tag -> tag.getKey().equals("island.id"))
                                  .map(tag -> tag.getValue()))
                          .containsExactly(sample.source()));
        } finally {
          release.forEach(CountDownLatch::countDown);
        }
      } finally {
        Metrics.removeRegistry(registry);
      }
    } finally {
      registry.close();
    }
  }

  private static List<String> islandIds(SimpleMeterRegistry registry) {
    return registry.find(SolverMetric.SCORE_CALCULATION_COUNT.getMeterId()).gauges().stream()
        .map(gauge -> gauge.getId().getTag("island.id"))
        .toList();
  }
}
