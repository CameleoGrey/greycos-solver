package greycos.solver.core.impl.islandmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmMigration;
import greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmMigrationBatch;
import greycos.solver.core.impl.move.SolutionAssignments;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.random.DefaultRandomSource;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataEasyScoreCalculator;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class GeneticAlgorithmMigrationTransportTest {

  @Test
  void populationBatchBypassesArchiveGateAndPreservesOrder() {
    try (var fixture = new Fixture(2)) {
      fixture.scope.setBestScore(InnerScore.fullyAssigned(SimpleScore.of(100)));
      fixture.endpoint.phaseStarted();
      var entries = List.of(fixture.entry(-2), fixture.entry(-5));
      var incoming = new GeneticAlgorithmMigrationBatch<>(1, 7L, entries);
      fixture.receive(incoming);

      var received = fixture.endpoint.exchange(3L, List.of(fixture.entry(-1)));

      assertThat(received).isSameAs(incoming);
      assertThat(received.entries()).containsExactlyElementsOf(entries);
      assertThat(fixture.scope.consumePendingMove()).isNull();
      var sent = fixture.sender.tryReceive();
      assertThat(sent.getPopulationBatch().sourceIslandId()).isZero();
      assertThat(sent.getPopulationBatch().generation()).isEqualTo(3L);
      assertThat(sent.getPopulationBatch().entries()).hasSize(1);
      assertThat(sent.getMigrant()).isSameAs(fixture.scope.getBestSolution());
      fixture.endpoint.phaseEnded();
    }
  }

  @Test
  void explicitEmptyPopulationNeverFallsBackToArchive() {
    try (var fixture = new Fixture(2)) {
      fixture.endpoint.phaseStarted();
      var empty = new GeneticAlgorithmMigrationBatch<TestdataSolution>(1, 4L, List.of());
      fixture.receive(empty);

      assertThat(fixture.endpoint.exchange(2L, List.of())).isSameAs(empty);
      assertThat(fixture.sender.tryReceive().getPopulationBatch().entries()).isEmpty();
      fixture.endpoint.phaseEnded();
    }
  }

  @Test
  void incumbentMessageBecomesARecipientRebasableSingleton() {
    try (var fixture = new Fixture(2)) {
      fixture.endpoint.phaseStarted();
      var director = fixture.scope.getScoreDirector();
      var migrant = director.cloneWorkingSolution();
      migrant.getEntityList().getFirst().setValue(migrant.getValueList().getLast());
      var score =
          InnerScore.fullyAssigned(new TestdataEasyScoreCalculator().calculateScore(migrant));
      fixture.receiver.replace(new AgentUpdate<>(1, migrant, score, new BitSet()));

      var received = fixture.endpoint.exchange(2L, List.of());

      assertThat(received.sourceIslandId()).isEqualTo(1);
      assertThat(received.generation()).isZero();
      assertThat(received.entries()).hasSize(1);
      assertThat(received.entries().getFirst().score()).isEqualTo(score);
      received.entries().getFirst().assignments().rebase(director).apply(director);
      director.triggerVariableListeners();
      assertThat(director.calculateScore()).isEqualTo(score);
      assertThat(director.getWorkingSolution().getEntityList().getFirst())
          .isNotSameAs(migrant.getEntityList().getFirst());
      fixture.endpoint.phaseEnded();
    }
  }

  @Test
  void latestWholeBatchWinsWithoutCombiningDifferentArrivals() {
    try (var fixture = new Fixture(2)) {
      fixture.endpoint.phaseStarted();
      fixture.receive(new GeneticAlgorithmMigrationBatch<>(1, 2L, List.of(fixture.entry(100))));
      var latest = new GeneticAlgorithmMigrationBatch<>(1, 3L, List.of(fixture.entry(-100)));
      fixture.receive(latest);

      assertThat(fixture.endpoint.exchange(1L, List.of())).isSameAs(latest);
      assertThat(fixture.receiver.isEmpty()).isTrue();
      fixture.endpoint.phaseEnded();
    }
  }

  @Test
  void phaseBoundariesDiscardPreviousGlobalMoveAndPopulationInbox() {
    try (var fixture = new Fixture(2)) {
      fixture.scope.setPendingMove(view -> {});
      fixture.receive(new GeneticAlgorithmMigrationBatch<>(1, 2L, List.of(fixture.entry(1))));
      fixture.endpoint.phaseStarted();
      assertThat(fixture.scope.consumePendingMove()).isNull();
      assertThat(fixture.receiver.isEmpty()).isTrue();
      fixture.receive(new GeneticAlgorithmMigrationBatch<>(1, 3L, List.of(fixture.entry(2))));
      fixture.scope.setPendingMove(view -> {});
      fixture.endpoint.phaseEnded();
      fixture.endpoint.phaseEnded();
      assertThat(fixture.scope.consumePendingMove()).isNull();
      assertThat(fixture.receiver.isEmpty()).isTrue();
      assertThatThrownBy(() -> fixture.endpoint.exchange(1L, List.of()))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("active solver thread");
    }
  }

  @Test
  void generationExchangeDoesNotResetLocalSearchCadence() {
    try (var fixture = new Fixture(2)) {
      fixture.agent.checkAndPerformMigration(); // One local-search step remains.
      fixture.endpoint.phaseStarted();
      fixture.endpoint.exchange(2L, List.of(fixture.entry(1)));
      assertThat(fixture.sender.tryReceive().getPopulationBatch()).isNotNull();
      fixture.endpoint.phaseEnded();

      fixture.agent.checkAndPerformMigration();

      assertThat(fixture.sender.tryReceive().getPopulationBatch()).isNull();
    }
  }

  @Test
  void singleIslandDoesNotExportOrImportItsOwnPopulation() {
    try (var fixture = new Fixture(1)) {
      fixture.endpoint.phaseStarted();
      assertThat(fixture.endpoint.exchange(2L, List.of(fixture.entry(1)))).isNull();
      assertThat(fixture.sender.isEmpty()).isTrue();
      fixture.endpoint.phaseEnded();
    }
  }

  @Test
  void relayedOwnPopulationIsNotReinjected() {
    try (var fixture = new Fixture(3)) {
      fixture.endpoint.phaseStarted();
      fixture.receive(new GeneticAlgorithmMigrationBatch<>(0, 1L, List.of(fixture.entry(10))));
      assertThat(fixture.endpoint.exchange(2L, List.of(fixture.entry(1)))).isNull();
      assertThat(fixture.receiver.isEmpty()).isTrue();
      fixture.endpoint.phaseEnded();
    }
  }

  @Test
  void endpointPublishesBestAndRejectsForeignThread() throws Exception {
    try (var fixture = new Fixture(2)) {
      fixture.endpoint.phaseStarted();
      fixture.endpoint.publishBest();
      assertThat(fixture.global.getBestInnerScore()).isEqualTo(fixture.scope.getBestScore());
      var result =
          new FutureTask<>(
              () -> {
                assertThatThrownBy(fixture.endpoint::publishBest)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("active solver thread");
                return null;
              });
      var thread = new Thread(result);
      thread.start();
      result.get();
      thread.join();
      fixture.endpoint.phaseEnded();
    }
  }

  @Test
  void batchesCopyContainersAndCheckEnvelopeSource() {
    try (var fixture = new Fixture(2)) {
      var entries = new ArrayList<>(List.of(fixture.entry(1)));
      var batch = new GeneticAlgorithmMigrationBatch<>(1, 1L, entries);
      entries.clear();
      assertThat(batch.entries()).hasSize(1);
      assertThatThrownBy(() -> batch.entries().clear())
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(
              () ->
                  new AgentUpdate<>(
                      0,
                      fixture.scope.getBestSolution(),
                      fixture.scope.getBestScore(),
                      new BitSet(),
                      batch))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("sending agent");
    }
  }

  private static final class Fixture implements AutoCloseable {
    private final SolverScope<TestdataSolution> scope;
    private final BoundedChannel<AgentUpdate<TestdataSolution>> sender = new BoundedChannel<>(3);
    private final BoundedChannel<AgentUpdate<TestdataSolution>> receiver = new BoundedChannel<>(3);
    private final SharedGlobalState<TestdataSolution> global = new SharedGlobalState<>();
    private final IslandAgent<TestdataSolution> agent;
    private final GeneticAlgorithmMigration<TestdataSolution> endpoint;

    private Fixture(int islandCount) {
      var config =
          PlannerTestUtils.buildSolverConfig(TestdataSolution.class, TestdataEntity.class)
              .withEasyScoreCalculatorClass(TestdataEasyScoreCalculator.class);
      scope =
          ((DefaultSolver<TestdataSolution>)
                  SolverFactory.<TestdataSolution>create(config).buildSolver())
              .getSolverScope();
      scope.setInitialSolution(TestdataSolution.generateSolution(3, 3));
      scope.setBestScore(scope.getScoreDirector().calculateScore());
      agent =
          new IslandAgent<>(
              0,
              List.of(),
              scope.getBestSolution(),
              global,
              sender,
              receiver,
              IslandModelConfig.builder()
                  .withIslandCount(islandCount)
                  .withMigrationFrequency(2)
                  .build(),
              DefaultRandomSource.seeded(0L),
              scope,
              new CountDownLatch(islandCount));
      endpoint = agent.createGeneticAlgorithmMigration(new GlobalBestUpdater<>(global, 0));
    }

    private GeneticAlgorithmMigrationBatch.Entry<TestdataSolution> entry(int score) {
      var director = scope.getScoreDirector();
      return new GeneticAlgorithmMigrationBatch.Entry<>(
          SolutionAssignments.capture(
              director.getSolutionDescriptor(), director.cloneWorkingSolution()),
          InnerScore.fullyAssigned(SimpleScore.of(score)));
    }

    private void receive(GeneticAlgorithmMigrationBatch<TestdataSolution> batch) {
      receiver.replace(
          new AgentUpdate<>(
              batch.sourceIslandId(),
              scope.getBestSolution(),
              scope.getBestScore(),
              new BitSet(),
              batch));
    }

    @Override
    public void close() {
      scope.getScoreDirector().close();
    }
  }
}
