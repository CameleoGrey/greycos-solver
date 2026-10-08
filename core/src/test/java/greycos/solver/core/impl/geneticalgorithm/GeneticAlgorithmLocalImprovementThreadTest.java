package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingEntity;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingSolution;
import greycos.solver.core.testcotwin.list.valuerange.TestdataListEntityProvidingValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class GeneticAlgorithmLocalImprovementThreadTest {

  @Test
  void deferredListSelectorFactoriesUseEachSolvingThreadsRandomSource() throws Exception {
    var buildingThread = Thread.currentThread();
    var solver =
        (DefaultSolver<TestdataListEntityProvidingSolution>)
            SolverFactory.<TestdataListEntityProvidingSolution>create(
                    new SolverConfig()
                        .withSolutionClass(TestdataListEntityProvidingSolution.class)
                        .withEntityClasses(
                            TestdataListEntityProvidingEntity.class,
                            TestdataListEntityProvidingValue.class)
                        .withConstraintProviderClass(
                            GeneticAlgorithmListIntegrationTest.RangeConstraints.class)
                        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
                        .withRandomSeed(37L)
                        .withPhases(
                            new GeneticAlgorithmPhaseConfig()
                                .withPopulationSize(4)
                                .withLocalImprovementMoveCountLimit(8L)
                                .withTerminationConfig(
                                    new TerminationConfig().withStepCountLimit(20))))
                .buildSolver();
    var completedProbes = new AtomicLong();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataListEntityProvidingSolution> scope) {
            completedProbes.set(
                ((GeneticAlgorithmPhaseScope<TestdataListEntityProvidingSolution>) scope)
                    .getLocalImprovementProbeCount());
          }
        });
    var assignments = new ArrayList<List<List<String>>>();
    var scores = new ArrayList<SimpleScore>();
    var probeCounts = new ArrayList<Long>();
    // Reuse one solver on two separate threads, both distinct from its factory/build thread.
    for (int run = 0; run < 2; run++) {
      var input = problem();
      try (var executor = Executors.newSingleThreadExecutor()) {
        var result =
            executor
                .submit(
                    () -> {
                      assertThat(Thread.currentThread()).isNotSameAs(buildingThread);
                      return solver.solve(input);
                    })
                .get(15, TimeUnit.SECONDS);
        assertThat(completedProbes.get()).isPositive();
        assertThat(result.getScore()).isEqualTo(replay(result));
        assignments.add(assignments(result));
        scores.add(result.getScore());
        probeCounts.add(completedProbes.get());
      }
      assertThat(assignments(input))
          .containsExactly(List.of("0", "1", "2", "3"), List.of("4", "5", "6", "7"));
    }
    assertThat(assignments.get(1)).isEqualTo(assignments.get(0));
    assertThat(scores.get(1)).isEqualTo(scores.get(0));
    assertThat(probeCounts.get(1)).isEqualTo(probeCounts.get(0));
  }

  private static TestdataListEntityProvidingSolution problem() {
    var values =
        IntStream.range(0, 8)
            .mapToObj(i -> new TestdataListEntityProvidingValue(Integer.toString(i)))
            .toList();
    var solution = new TestdataListEntityProvidingSolution();
    solution.setEntityList(
        new ArrayList<>(
            List.of(
                new TestdataListEntityProvidingEntity(
                    "first", values.subList(0, 6), new ArrayList<>(values.subList(0, 4))),
                new TestdataListEntityProvidingEntity(
                    "second", values.subList(2, 8), new ArrayList<>(values.subList(4, 8))))));
    return solution;
  }

  private static List<List<String>> assignments(TestdataListEntityProvidingSolution solution) {
    return solution.getEntityList().stream()
        .map(
            owner ->
                owner.getValueList().stream()
                    .map(TestdataListEntityProvidingValue::getCode)
                    .toList())
        .toList();
  }

  private static SimpleScore replay(TestdataListEntityProvidingSolution solution) {
    var seen =
        Collections.newSetFromMap(new IdentityHashMap<TestdataListEntityProvidingValue, Boolean>());
    int score = 0;
    for (var owner : solution.getEntityList()) {
      for (int index = 0; index < owner.getValueList().size(); index++) {
        var value = owner.getValueList().get(index);
        assertThat(seen.add(value)).isTrue();
        assertThat(owner.getValueRange())
            .anySatisfy(candidate -> assertThat(candidate).isSameAs(value));
        assertThat(value.getEntity()).isSameAs(owner);
        assertThat(value.getIndex()).isEqualTo(index);
        score += (Integer.parseInt(value.getCode()) + 1) * (index + 1);
      }
    }
    assertThat(seen).hasSize(8);
    return SimpleScore.of(score);
  }
}
