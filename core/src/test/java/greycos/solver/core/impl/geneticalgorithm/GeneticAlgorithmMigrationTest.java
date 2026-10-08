package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assertReplay;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.assignments;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.config;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.problem;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.recordPhase;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.recordSteps;
import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmIntegrationTest.solver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationOperatorConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.monitoring.SolverMetric;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.move.SolutionAssignments;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.DefaultSolverFactory;
import greycos.solver.core.impl.solver.monitoring.SolverTags;
import greycos.solver.core.impl.solver.termination.PhaseTermination;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@Timeout(30)
class GeneticAlgorithmMigrationTest {

  @ParameterizedTest
  @ValueSource(ints = {1, 3, 5})
  void exchangesOnlyCompletedGenerationsAndTiedBatchesRetainNativeMembers(int populationSize) {
    var solver =
        solver(
            config(phase(populationSize, 0.00001, 5 * populationSize))
                .withConstraintProviderClass(
                    GeneticAlgorithmGenerationPolicyTest.ConstantConstraints.class));
    var transport = new ScriptedMigration(2);
    transport.handler =
        (generation, outgoing) -> new GeneticAlgorithmMigrationBatch<>(7, generation, outgoing);
    var ga = attach(solver, transport);
    var steps = new ArrayList<GeneticAlgorithmStepScope<TestdataSolution>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<TestdataSolution> step) {
            steps.add((GeneticAlgorithmStepScope<TestdataSolution>) step);
          }
        });
    var scope = recordPhase(solver);

    solver.solve(problem(2, 8));

    assertThat(transport.generations).containsExactly(2L, 4L);
    assertThat(transport.exportSizes).containsOnly(1);
    assertThat(transport.starts).isEqualTo(1);
    assertThat(transport.ends).isEqualTo(1);
    assertThat(steps).hasSize(5 * populationSize);
    assertThat(scope.get().getGeneration()).isEqualTo(4 + (populationSize == 1 ? 1 : 0));
    assertThat(ga.getMigrationDiagnostics().admittedEntries()).isZero();
    assertThat(ga.getMigrationDiagnostics().rejectedEntries()).isEqualTo(2);
    assertThat(ga.getMigrationDiagnostics().evaluatedEntries()).isEqualTo(2);
    long freshAttempts =
        steps.stream()
            .filter(step -> step.getOutcome() == GeneticAlgorithmOutcome.EVALUATED)
            .count();
    assertThat(scope.get().getPhaseScoreCalculationCount()).isEqualTo(3 + freshAttempts);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(5L * populationSize);
  }

  @Test
  void disabledExchangePreservesStandaloneTraceAndDoesNotBuildSnapshots() {
    var plain = solver(config(phase(3, 0.0, 14)));
    var plainSteps = recordSteps(plain);
    var disabled = solver(config(phase(3, 0.0, 14)));
    var disabledSteps = recordSteps(disabled);
    var transport = new ScriptedMigration(1);
    var ga = attach(disabled, transport);

    var expected = plain.solve(problem(2, 8));
    var actual = disabled.solve(problem(2, 8));

    assertThat(assignments(actual)).containsExactlyElementsOf(assignments(expected));
    assertThat(disabledSteps)
        .extracting(GeneticAlgorithmStepScope::getCandidateScore)
        .containsExactlyElementsOf(
            plainSteps.stream().map(GeneticAlgorithmStepScope::getCandidateScore).toList());
    assertThat(transport.generations).isEmpty();
    assertThat(ga.getMigrationDiagnostics().exportedBatches()).isZero();
  }

  @Test
  void admitsDuplicateMigrantsBelowArchiveAgainstFrozenWorstSuffix() {
    var solver =
        solver(
            config(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(3)
                    .withPBestRate(0.05)
                    .withMigrationRate(1.0)
                    .withMutationRateMultiplier(32.0)
                    .withMutationOperators(mutation(GeneticAlgorithmMutationType.CHANGE))
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(6))));
    var transport = new ScriptedMigration(1);
    transport.handler =
        (generation, outgoing) -> {
          assertThat(outgoing).hasSize(3);
          int bestPopulation =
              Math.toIntExact(((SimpleScore) outgoing.getFirst().score().raw()).score());
          assertThat(bestPopulation).isLessThan(31);
          var incoming = member(solver, 2, 32, bestPopulation + 1);
          return new GeneticAlgorithmMigrationBatch<>(7, 10, List.of(incoming, incoming));
        };
    var ga = attach(solver, transport);
    var steps = recordSteps(solver);
    var input = problem(2, 32);
    input.getEntityList().forEach(entity -> entity.setValue(input.getValueList().getLast()));

    var result = solver.solve(input);

    var nextGeneration = steps.getLast();
    assertThat(nextGeneration.getGeneration()).isEqualTo(2);
    assertThat(nextGeneration.getFirstParentId()).isEqualTo(6);
    assertThat(nextGeneration.getSecondParentId()).isEqualTo(6);
    assertThat(ga.getMigrationDiagnostics().admittedEntries()).isEqualTo(2);
    assertThat(ga.getMigrationDiagnostics().committedBatches()).isEqualTo(1);
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(32));
    assertReplay(result);
  }

  @Test
  void incomingBatchIsCappedAndExportPrecedesImportWhileSessionAndSnapshotsStayStable() {
    var solver = solver(config(phase(3, 0.34, 6)));
    var transport = new ScriptedMigration(1);
    var entrySession = new AtomicReference<Object>();
    var snapshots = new ArrayList<GeneticAlgorithmMigrationBatch.Entry<TestdataSolution>>();
    var exportedAssignments = new ArrayList<List<String>>();
    transport.handler =
        (generation, outgoing) -> {
          entrySession.set(
              ((BavetConstraintStreamScoreDirector<?, ?>)
                      solver.getSolverScope().getScoreDirector())
                  .getSession());
          assertThat(outgoing).hasSize(2);
          snapshots.addAll(outgoing);
          outgoing.forEach(member -> exportedAssignments.add(snapshotValues(member)));
          var best = member(solver, 101, 8, 800);
          return new GeneticAlgorithmMigrationBatch<>(7, 10, List.of(best, best, best, best));
        };
    var ga = attach(solver, transport);
    var scope = recordPhase(solver);
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> phaseScope) {
            assertThat(
                    ((BavetConstraintStreamScoreDirector<?, ?>) phaseScope.getScoreDirector())
                        .getSession())
                .isSameAs(entrySession.get());
          }
        });

    var result = solver.solve(problem(101, 8));

    assertThat(result.getScore()).isEqualTo(SimpleScore.of(800));
    assertThat(ga.getMigrationDiagnostics().receivedEntries()).isEqualTo(3);
    assertThat(ga.getMigrationDiagnostics().admittedEntries()).isEqualTo(3);
    assertThat(scope.get().getDistinctPopulationSize()).isEqualTo(1);
    for (int i = 0; i < snapshots.size(); i++) {
      assertThat(snapshotValues(snapshots.get(i)))
          .containsExactlyElementsOf(exportedAssignments.get(i));
    }
    assertReplay(result);
  }

  @Test
  void cancellationAfterVerifiedImprovementDiscardsWholeBatchAndRestoresEntryWorkspace() {
    var solver = solver(config(phase(3, 1.0, 20)));
    var transport = new ScriptedMigration(1);
    var entryAssignments = new AtomicReference<List<String>>();
    var endedAssignments = new AtomicReference<List<String>>();
    transport.handler =
        (generation, outgoing) -> {
          entryAssignments.set(assignments(solver.getSolverScope().getWorkingSolution()));
          var best = member(solver, 101, 8, 800);
          return new GeneticAlgorithmMigrationBatch<>(7, 10, List.of(best, best));
        };
    transport.bestPublication =
        () -> {
          if (solver.getSolverScope().getBestScore().raw().equals(SimpleScore.of(800)))
            solver.terminateEarly();
        };
    var ga = attach(solver, transport);
    var steps = recordSteps(solver);
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> scope) {
            endedAssignments.set(assignments(scope.getWorkingSolution()));
          }
        });

    var result = solver.solve(problem(101, 8));

    assertThat(steps).hasSize(5);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(5);
    assertThat(ga.getMigrationDiagnostics().evaluatedEntries()).isEqualTo(1);
    assertThat(ga.getMigrationDiagnostics().admittedEntries()).isZero();
    assertThat(ga.getMigrationDiagnostics().committedBatches()).isZero();
    assertThat(endedAssignments.get()).containsExactlyElementsOf(entryAssignments.get());
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(800));
    assertReplay(result);
  }

  @Test
  void scoreMismatchIsFatalRestoresWorkspaceAndIncludesDonorContext() {
    var solver = solver(config(phase(3, 1.0, 20)));
    var transport = new ScriptedMigration(1);
    var entryAssignments = new AtomicReference<List<String>>();
    var endedAssignments = new AtomicReference<List<String>>();
    transport.handler =
        (generation, outgoing) -> {
          entryAssignments.set(assignments(solver.getSolverScope().getWorkingSolution()));
          var best = member(solver, 101, 8, 800);
          return new GeneticAlgorithmMigrationBatch<>(
              7,
              10,
              List.of(
                  new GeneticAlgorithmMigrationBatch.Entry<>(
                      best.assignments(), InnerScore.fullyAssigned(SimpleScore.ZERO))));
        };
    var ga = attach(solver, transport);
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseEnded(AbstractPhaseScope<TestdataSolution> scope) {
            endedAssignments.set(assignments(scope.getWorkingSolution()));
          }
        });

    assertThatThrownBy(() -> solver.solve(problem(101, 8)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("differs from the advertised score")
        .satisfies(
            failure ->
                assertThat(failure.getSuppressed())
                    .anySatisfy(
                        context ->
                            assertThat(context)
                                .hasMessageContaining("donor island (7)")
                                .hasMessageContaining("donor generation (10)")));

    assertThat(endedAssignments.get()).containsExactlyElementsOf(entryAssignments.get());
    assertThat(ga.getMigrationDiagnostics().admittedEntries()).isZero();
    assertThat(transport.ends).isEqualTo(1);
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(5);
  }

  @Test
  void publicationFailurePreservesOriginalExceptionAndPublishedBestWithoutCommittingPopulation() {
    var solver = solver(config(phase(3, 1.0, 20)));
    var transport = new ScriptedMigration(1);
    var failure = new IllegalStateException("publication failed");
    transport.handler =
        (generation, outgoing) ->
            new GeneticAlgorithmMigrationBatch<>(7, 10, List.of(member(solver, 101, 8, 800)));
    transport.bestPublication =
        () -> {
          if (solver.getSolverScope().getBestScore().raw().equals(SimpleScore.of(800)))
            throw failure;
        };
    var ga = attach(solver, transport);

    assertThatThrownBy(() -> solver.solve(problem(101, 8))).isSameAs(failure);

    assertThat(solver.getSolverScope().getBestScore().raw()).isEqualTo(SimpleScore.of(800));
    assertThat(ga.getMigrationDiagnostics().admittedEntries()).isZero();
    assertThat(transport.ends).isEqualTo(1);
  }

  @Test
  void solveReuseResetsGenerationAndMigrationDiagnostics() {
    var solver = solver(config(phase(3, 1.0, 14)));
    var transport = new ScriptedMigration(1);
    var ga = attach(solver, transport);

    var first = solver.solve(problem(2, 8));
    var firstDiagnostics = ga.getMigrationDiagnostics();
    var second = solver.solve(problem(2, 8));

    assertThat(ga.getMigrationDiagnostics()).isEqualTo(firstDiagnostics);
    assertThat(ga.getMigrationDiagnostics().exportedBatches()).isEqualTo(3);
    assertThat(ga.getCompletedGenerations()).isEqualTo(4);
    assertThat(transport.generations).containsExactly(1L, 2L, 3L, 1L, 2L, 3L);
    assertThat(transport.starts).isEqualTo(2);
    assertThat(transport.ends).isEqualTo(2);
    assertThat(assignments(second)).containsExactlyElementsOf(assignments(first));
    assertReplay(second);
  }

  @Test
  void terminationAtGenerationCompletionPreventsEvenExport() {
    var solver = solver(config(phase(3, 1.0, 5)));
    var transport = new ScriptedMigration(1);
    var ga = attach(solver, transport);

    assertReplay(solver.solve(problem(2, 8)));

    assertThat(ga.getCompletedGenerations()).isEqualTo(1);
    assertThat(transport.generations).isEmpty();
    assertThat(ga.getMigrationDiagnostics().exportedBatches()).isZero();
    assertThat(solver.getSolverScope().getMoveEvaluationCount()).isEqualTo(5);
  }

  @ParameterizedTest
  @ValueSource(strings = {"committed", "rolledBack", "nextStep"})
  @SuppressWarnings("unchecked")
  void migrantWorkDoesNotReplaceCompletedOffspringConstraintMetrics(String completion) {
    String tag = UUID.randomUUID().toString();
    Tags tags = SolverTags.withProblemId(tag).asTags();
    var registry = new SimpleMeterRegistry();
    try {
      Metrics.addRegistry(registry);
      try {
        var phaseConfig = phase(3, 1.0, completion.equals("nextStep") ? 6 : 20);
        var phaseReference = new AtomicReference<DefaultGeneticAlgorithmPhase<TestdataSolution>>();
        var factory =
            spy(
                new DefaultSolverFactory<TestdataSolution>(
                    config(phaseConfig)
                        .withMonitoringConfig(
                            new MonitoringConfig()
                                .withConstraintMatchMetricSampleInterval(1)
                                .withSolverMetricList(
                                    List.of(
                                        SolverMetric.MOVE_COUNT_PER_STEP,
                                        SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE,
                                        SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE)))));
        doAnswer(
                invocation -> {
                  List<DefaultGeneticAlgorithmPhase<TestdataSolution>> original =
                      (List<DefaultGeneticAlgorithmPhase<TestdataSolution>>)
                          invocation.callRealMethod();
                  var termination =
                      spy(
                          (PhaseTermination<TestdataSolution>)
                              original.getFirst().getPhaseTermination());
                  doAnswer(
                          poll ->
                              completion.equals("committed")
                                      && phaseReference
                                              .get()
                                              .getMigrationDiagnostics()
                                              .committedBatches()
                                          > 0
                                  || (boolean) poll.callRealMethod())
                      .when(termination)
                      .isPhaseTerminated(any());
                  var phase =
                      new DefaultGeneticAlgorithmPhase.Builder<TestdataSolution>(
                              0,
                              EnvironmentMode.NO_ASSERT,
                              "",
                              termination,
                              phaseConfig.resolve(),
                              invocation.getArgument(1))
                          .build();
                  phaseReference.set(phase);
                  return List.of(phase);
                })
            .when(factory)
            .buildPhaseList(any(), any(), any());
        var solver = (DefaultSolver<TestdataSolution>) factory.buildSolver();
        solver.setMonitorTags(SolverTags.withProblemId(tag));
        var transport = new ScriptedMigration(1);
        transport.handler =
            (generation, outgoing) ->
                new GeneticAlgorithmMigrationBatch<>(7, 10, List.of(member(solver, 101, 8, 800)));
        if (completion.equals("rolledBack")) {
          transport.bestPublication =
              () -> {
                if (solver.getSolverScope().getBestScore().raw().equals(SimpleScore.of(800)))
                  solver.terminateEarly();
              };
        }
        attach(solver, transport);
        var lastStepScore = new AtomicReference<Double>();
        var lastAcceptedCount = new AtomicReference<Double>();
        solver.addPhaseLifecycleListener(
            new PhaseLifecycleListenerAdapter<>() {
              @Override
              public void stepEnded(AbstractStepScope<TestdataSolution> step) {
                lastStepScore.set(
                    constraintScore(
                        registry, tags, SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE));
                lastAcceptedCount.set(
                    registry
                        .find(SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".accepted")
                        .tags(tags)
                        .gauge()
                        .value());
              }
            });

        var result = solver.solve(problem(101, 8));

        assertThat(constraintScore(registry, tags, SolverMetric.CONSTRAINT_MATCH_TOTAL_STEP_SCORE))
            .isEqualTo(lastStepScore.get());
        assertThat(constraintScore(registry, tags, SolverMetric.CONSTRAINT_MATCH_TOTAL_BEST_SCORE))
            .isEqualTo(800);
        assertThat(
                registry
                    .find(SolverMetric.MOVE_COUNT_PER_STEP.getMeterId() + ".accepted")
                    .tags(tags)
                    .gauge()
                    .value())
            .isEqualTo(lastAcceptedCount.get());
        assertThat(phaseReference.get().getMigrationDiagnostics().committedBatches())
            .isEqualTo(completion.equals("rolledBack") ? 0 : 1);
        if (completion.equals("nextStep")) assertThat(lastStepScore.get()).isEqualTo(800.0);
        else assertThat(lastStepScore.get()).isLessThan(800.0);
        assertReplay(result);
      } finally {
        Metrics.removeRegistry(registry);
        for (var meter : List.copyOf(Metrics.globalRegistry.getMeters())) {
          if (tag.equals(meter.getId().getTag("problem.id"))) Metrics.globalRegistry.remove(meter);
        }
      }
    } finally {
      registry.close();
    }
  }

  private static double constraintScore(
      SimpleMeterRegistry registry, Tags tags, SolverMetric metric) {
    var gauges = registry.find(metric.getMeterId() + ".score").tags(tags).gauges();
    assertThat(gauges).isNotEmpty();
    return gauges.stream().mapToDouble(gauge -> gauge.value()).sum();
  }

  private static GeneticAlgorithmPhaseConfig phase(int populationSize, double rate, int steps) {
    return new GeneticAlgorithmPhaseConfig()
        .withPopulationSize(populationSize)
        .withMigrationRate(rate)
        .withMutationOperators(mutation(GeneticAlgorithmMutationType.SWAP))
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(steps));
  }

  private static GeneticAlgorithmMutationOperatorConfig mutation(
      GeneticAlgorithmMutationType type) {
    return new GeneticAlgorithmMutationOperatorConfig().withType(type).withProbability(1.0);
  }

  private static DefaultGeneticAlgorithmPhase<TestdataSolution> attach(
      DefaultSolver<TestdataSolution> solver, ScriptedMigration transport) {
    var phase = (DefaultGeneticAlgorithmPhase<TestdataSolution>) solver.getPhaseList().getFirst();
    phase.setMigration(transport);
    return phase;
  }

  private static GeneticAlgorithmMigrationBatch.Entry<TestdataSolution> member(
      DefaultSolver<TestdataSolution> solver, int valueCount, int entityCount, int score) {
    var donor = problem(valueCount, entityCount);
    int remaining = score;
    for (var entity : donor.getEntityList()) {
      int value = Math.min(valueCount - 1, remaining);
      entity.setValue(donor.getValueList().get(value));
      remaining -= value;
    }
    assertThat(remaining).isZero();
    return new GeneticAlgorithmMigrationBatch.Entry<>(
        SolutionAssignments.capture(
            solver.getSolverScope().getScoreDirector().getSolutionDescriptor(), donor),
        InnerScore.fullyAssigned(SimpleScore.of(score)));
  }

  private static List<String> snapshotValues(
      GeneticAlgorithmMigrationBatch.Entry<TestdataSolution> member) {
    return member.assignments().getBasicChanges().values().stream()
        .flatMap(List::stream)
        .map(record -> ((greycos.solver.core.testcotwin.TestdataValue) record.value()).getCode())
        .toList();
  }

  private static final class ScriptedMigration
      implements GeneticAlgorithmMigration<TestdataSolution> {
    private final int frequency;
    private final List<Long> generations = new ArrayList<>();
    private final List<Integer> exportSizes = new ArrayList<>();
    private BiFunction<
            Long,
            List<GeneticAlgorithmMigrationBatch.Entry<TestdataSolution>>,
            GeneticAlgorithmMigrationBatch<TestdataSolution>>
        handler = (generation, outgoing) -> null;
    private Runnable bestPublication = () -> {};
    private int starts;
    private int ends;

    private ScriptedMigration(int frequency) {
      this.frequency = frequency;
    }

    @Override
    public int frequency() {
      return frequency;
    }

    @Override
    public void phaseStarted() {
      starts++;
    }

    @Override
    public GeneticAlgorithmMigrationBatch<TestdataSolution> exchange(
        long generation, List<GeneticAlgorithmMigrationBatch.Entry<TestdataSolution>> emigrants) {
      generations.add(generation);
      exportSizes.add(emigrants.size());
      return handler.apply(generation, emigrants);
    }

    @Override
    public void publishBest() {
      bestPublication.run();
    }

    @Override
    public void phaseEnded() {
      ends++;
    }
  }
}
