package greycos.solver.core.impl.score.director;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.api.solver.event.EventProducerId;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.neighborhood.NeighborhoodsBasedMoveRepository;
import greycos.solver.core.impl.neighborhood.stream.DefaultMoveStreamFactory;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;
import greycos.solver.core.impl.score.director.incremental.IncrementalScoreDirectorFactory;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.change.DefaultProblemChangeDirector;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.preview.api.move.builtin.ChangeMoveProvider;
import greycos.solver.core.testcotwin.TestdataEntity;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.cascade.mixed.TestdataMixedCascadingSolution;
import greycos.solver.core.testcotwin.cascade.mixed.TestdataMixedCascadingSolution.Route;
import greycos.solver.core.testcotwin.cascade.mixed.TestdataMixedCascadingSolution.Visit;
import greycos.solver.core.testcotwin.score.lavish.TestdataLavishEntity;
import greycos.solver.core.testcotwin.score.lavish.TestdataLavishExtra;
import greycos.solver.core.testcotwin.score.lavish.TestdataLavishSolution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Timeout(20)
class WorkingSolutionStructureTest {
  @Test
  void initiallyEmptyFactTypeGetsFreshBavetSessionAtExplicitBarrier() {
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataSolution, SimpleScore>(
            TestdataSolution.buildSolutionDescriptor(),
            new ValuesProvider(),
            EnvironmentMode.NO_ASSERT);
    var solution = TestdataSolution.generateSolution(0, 0);
    try (var director = factory.buildScoreDirector()) {
      director.setWorkingSolution(solution);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.ZERO);
      var changes = new DefaultProblemChangeDirector<>(director);
      changes.addProblemFact(new TestdataValue("added"), solution.getValueList()::add);
      changes.updateShadowVariables();
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-1));
      try (var independent = factory.buildScoreDirector()) {
        independent.setWorkingSolution(solution);
        assertThat(director.calculateScore()).isEqualTo(independent.calculateScore());
      }
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = EnvironmentMode.class,
      names = {"NO_ASSERT", "PHASE_ASSERT"})
  void uninitializedBasicValuesRemainValidAcrossStructuralRefresh(EnvironmentMode mode) {
    var solution = TestdataSolution.generateUninitializedSolution(2, 1);
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataSolution, SimpleScore>(
            TestdataSolution.buildSolutionDescriptor(),
            constraints ->
                new Constraint[] {
                  constraints
                      .forEachIncludingUnassigned(TestdataEntity.class)
                      .penalize(SimpleScore.ONE)
                      .asConstraint("entities")
                },
            mode);
    try (var director = factory.createScoreDirectorBuilder().withLookUpEnabled(true).build()) {
      director.setWorkingSolution(solution);
      assertThat(director.getWorkingInitScore()).isEqualTo(-1);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-1));
      var changes = new DefaultProblemChangeDirector<>(director);
      var added = new TestdataEntity("uninitialized");
      changes.addEntity(added, solution.getEntityList()::add);
      changes.updateShadowVariables();
      assertThat(added.getValue()).isNull();
      assertThat(director.getWorkingInitScore()).isEqualTo(-2);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-2));
      changes.changeVariable(
          added, "value", entity -> entity.setValue(solution.getValueList().getFirst()));
      changes.updateShadowVariables();
      assertThat(director.getWorkingInitScore()).isEqualTo(-1);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-2));
    }
  }

  @Test
  void structuralBatchRefreshesOnceBeforeDependentVariableChange() {
    var solution = TestdataSolution.generateSolution(1, 0);
    var calculator = new CountingCalculator();
    try (var director =
        new IncrementalScoreDirectorFactory<>(
                TestdataSolution.buildSolutionDescriptor(),
                () -> calculator,
                EnvironmentMode.NO_ASSERT)
            .createScoreDirectorBuilder()
            .withLookUpEnabled(true)
            .build()) {
      director.setWorkingSolution(solution);
      var changes = new DefaultProblemChangeDirector<>(director);
      var addedValue = new TestdataValue("addedValue");
      changes.addProblemFact(addedValue, solution.getValueList()::add);
      for (var i = 0; i < 100; i++) {
        changes.addEntity(
            new TestdataEntity("added" + i, solution.getValueList().getFirst()),
            solution.getEntityList()::add);
      }
      assertThat(calculator.resets).isOne();
      var entity = solution.getEntityList().getLast();
      changes.changeVariable(entity, "value", e -> e.setValue(addedValue));
      assertThat(calculator.resets).isEqualTo(2);
      assertThat(calculator.changed).containsExactly(entity);
      changes.updateShadowVariables();
      assertThat(calculator.resets).isEqualTo(2);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(100));
      assertThat(director.getWorkingGenuineEntityCount()).isEqualTo(100);
    }
  }

  @Test
  void scoreReadFlushesRemovalAndReaddition() {
    var solution = TestdataSolution.generateSolution(1, 1);
    try (var director =
        new BavetConstraintStreamScoreDirectorFactory<TestdataSolution, SimpleScore>(
                TestdataSolution.buildSolutionDescriptor(),
                new ValuesProvider(),
                EnvironmentMode.NO_ASSERT)
            .createScoreDirectorBuilder()
            .withLookUpEnabled(true)
            .build()) {
      director.setWorkingSolution(solution);
      var changes = new DefaultProblemChangeDirector<>(director);
      var entity = solution.getEntityList().getFirst();
      changes.removeEntity(entity, solution.getEntityList()::remove);
      var oldValue = solution.getValueList().getFirst();
      changes.removeProblemFact(oldValue, solution.getValueList()::remove);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.ZERO);
      changes.addProblemFact(oldValue, solution.getValueList()::add);
      changes.addEntity(entity, solution.getEntityList()::add);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.of(-1));
      assertThat(director.lookUpWorkingObject(entity)).isSameAs(entity);
    }
  }

  @Test
  void failedRefreshDoesNotPretendStructureIsCleanAndCloseDoesNotRetry() {
    var solution = TestdataSolution.generateSolution(1, 0);
    var calculator = new CountingCalculator();
    var director =
        new IncrementalScoreDirectorFactory<>(
                TestdataSolution.buildSolutionDescriptor(),
                () -> calculator,
                EnvironmentMode.NO_ASSERT)
            .buildScoreDirector();
    director.setWorkingSolution(solution);
    var changes = new DefaultProblemChangeDirector<>(director);
    changes.addProblemFact(new TestdataValue("added"), solution.getValueList()::add);
    calculator.failReset = true;
    assertThatThrownBy(changes::updateShadowVariables)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("reset failure");
    var failedResets = calculator.resets;
    assertThatThrownBy(director::calculateScore)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("forceUpdateShadowVariables")
        .hasRootCauseMessage("reset failure");
    director.updateShadowVariables();
    assertThat(calculator.resets).isEqualTo(failedResets);
    director.close();
    assertThat(calculator.resets).isEqualTo(failedResets);
  }

  @Test
  void serialProblemChangePublishesReplayedScoreAtCallbackTime() {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataSolution.class)
            .withEntityClasses(TestdataEntity.class)
            .withConstraintProviderClass(ValuesProvider.class)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withMoveThreadCount("NONE")
            .withPhases(new ConstructionHeuristicPhaseConfig());
    var solver =
        (DefaultSolver<TestdataSolution>)
            SolverFactory.<TestdataSolution>create(config).buildSolver();
    var queued = new AtomicBoolean();
    var eventScores = new ArrayList<SimpleScore>();
    solver.addEventListener(
        event -> {
          if (event.getProducerId().equals(EventProducerId.problemChange())) {
            eventScores.add(event.getNewBestSolution().getScore());
          }
        });
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingEnded(SolverScope<TestdataSolution> scope) {
            if (!queued.getAndSet(true)) {
              solver.addProblemChange(
                  (solution, changes) ->
                      changes.addProblemFact(
                          new TestdataValue("added"), solution.getValueList()::add));
            }
          }
        });
    var result = solver.solve(TestdataSolution.generateSolution(0, 0));
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(-1));
    assertThat(eventScores).containsExactly(SimpleScore.of(-1));
  }

  @Test
  void ordinaryFactProblemChangesPublishCorrectScoresBeforeRestart() {
    var config =
        new SolverConfig()
            .withSolutionClass(TestdataLavishSolution.class)
            .withEntityClasses(TestdataLavishEntity.class)
            .withConstraintProviderClass(OrdinaryFactJoinProvider.class)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withMoveThreadCount("NONE")
            .withPhases(new ConstructionHeuristicPhaseConfig());
    var solver =
        (DefaultSolver<TestdataLavishSolution>)
            SolverFactory.<TestdataLavishSolution>create(config).buildSolver();
    var replayFactory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataLavishSolution, SimpleScore>(
            TestdataLavishSolution.buildSolutionDescriptor(),
            new OrdinaryFactJoinProvider(),
            EnvironmentMode.NO_ASSERT);
    var startedRuns = new AtomicInteger();
    var publications = new ArrayList<TestdataLavishSolution>();
    var expectedExtraCounts = List.of(1, 0, 1);
    solver.addEventListener(
        event -> {
          if (event.getProducerId().equals(EventProducerId.problemChange())) {
            var publicationIndex = publications.size();
            // This callback must precede the next run, which could otherwise repair stale scoring.
            assertThat(startedRuns).hasValue(publicationIndex + 1);
            var published = event.getNewBestSolution();
            assertThat(published.getExtraList()).hasSize(expectedExtraCounts.get(publicationIndex));
            assertOrdinaryFactPublication(replayFactory, published);
            publications.add(published);
          }
        });
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void solvingStarted(SolverScope<TestdataLavishSolution> scope) {
            startedRuns.incrementAndGet();
          }

          @Override
          public void solvingEnded(SolverScope<TestdataLavishSolution> scope) {
            var changeIndex = startedRuns.get() - 1;
            if (changeIndex < expectedExtraCounts.size()) {
              solver.addProblemChange(
                  (solution, changes) -> {
                    // Problem facts are shared by solution clones; retain earlier publications.
                    solution.setExtraList(new ArrayList<>(solution.getExtraList()));
                    if (expectedExtraCounts.get(changeIndex) == 0) {
                      changes.removeProblemFact(
                          solution.getExtraList().getFirst(), solution.getExtraList()::remove);
                    } else {
                      changes.addProblemFact(
                          new TestdataLavishExtra("extra" + changeIndex),
                          solution.getExtraList()::add);
                    }
                  });
            }
          }
        });

    var result = solver.solve(TestdataLavishSolution.generateSolution(2, 3));
    assertThat(startedRuns).hasValue(4);
    assertThat(publications).hasSize(3);
    for (var index = 0; index < publications.size(); index++) {
      var published = publications.get(index);
      assertThat(published.getExtraList()).hasSize(expectedExtraCounts.get(index));
      assertOrdinaryFactPublication(replayFactory, published);
    }
    assertThat(result.getScore()).isEqualTo(SimpleScore.of(-3));
    assertThat(solver.isEveryProblemChangeProcessed()).isTrue();
  }

  private static void assertOrdinaryFactPublication(
      BavetConstraintStreamScoreDirectorFactory<TestdataLavishSolution, SimpleScore> factory,
      TestdataLavishSolution published) {
    var publishedScore = published.getScore();
    var expectedScore =
        SimpleScore.of(-(long) published.getEntityList().size() * published.getExtraList().size());
    assertThat(publishedScore).isEqualTo(expectedScore);
    try (var independent = factory.buildScoreDirector()) {
      independent.setWorkingSolution(
          factory.getSolutionDescriptor().getSolutionCloner().cloneSolution(published));
      assertThat(independent.calculateScore().raw()).isEqualTo(publishedScore);
    }
  }

  @Test
  void retainedListAndBasicStatesAreRefreshedBeforeNewListOperations() {
    var solution = TestdataMixedCascadingSolution.generate(1, 1);
    var descriptor = TestdataMixedCascadingSolution.buildSolutionDescriptor();
    var delayDescriptor =
        descriptor.findEntityDescriptorOrFail(Visit.class).getGenuineVariableDescriptor("delay");
    try (var director =
        new EasyScoreDirectorFactory<>(
                descriptor, TestdataMixedCascadingSolution::shadowScore, EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(solution);
      var listState = director.getListVariableState(descriptor.getListVariableDescriptor());
      var basicState = director.getBasicVariableState(delayDescriptor);
      var changes = new DefaultProblemChangeDirector<>(director);
      var route = new Route();
      var visit = new Visit();
      changes.addEntity(route, solution.routes::add);
      changes.addEntity(visit, solution.visits::add);
      director.beforeListVariableElementAssigned(
          director.getSolutionDescriptor().getListVariableDescriptor(), visit);
      director.beforeListVariableChanged(route, "visits", 0, 0);
      route.visits.add(visit);
      director.afterListVariableChanged(route, "visits", 0, 1);
      director.afterListVariableElementAssigned(
          director.getSolutionDescriptor().getListVariableDescriptor(), visit);
      changes.updateShadowVariables();
      assertThat(director.getListVariableState(descriptor.getListVariableDescriptor()))
          .isSameAs(listState);
      assertThat(director.getBasicVariableState(delayDescriptor)).isSameAs(basicState);
      assertThat(listState.getInverseSingleton(visit)).isSameAs(route);
      assertThat(listState.getIndexOrFail(visit)).isZero();
      assertThat(basicState.getInverseCollection(1)).contains(visit);
      assertThat(director.calculateScore().raw()).isEqualTo(solution.replayScore());
    }
  }

  @Test
  void attachedNeighborhoodRepositorySurvivesRefreshAndCanBeReusedAfterClose() {
    var descriptor = TestdataSolution.buildSolutionDescriptor();
    var variable = descriptor.getMetaModel().genuineEntity(TestdataEntity.class).basicVariable();
    var repository =
        new NeighborhoodsBasedMoveRepository<>(
            new DefaultMoveStreamFactory<>(descriptor, EnvironmentMode.NO_ASSERT),
            List.of(new ChangeMoveProvider<>(variable)));
    var factory =
        new EasyScoreDirectorFactory<>(
            descriptor, ignored -> SimpleScore.ZERO, EnvironmentMode.NO_ASSERT);
    var solution = TestdataSolution.generateSolution(2, 0);
    try (var director = factory.buildScoreDirector()) {
      director.setWorkingSolution(solution);
      director.setMoveRepository(repository);
      var entity = new TestdataEntity("added", solution.getValueList().getFirst());
      new DefaultProblemChangeDirector<>(director).addEntity(entity, solution.getEntityList()::add);
      director.updateShadowVariables();
      var iterator = repository.iterator(new Random(0));
      assertThat(iterator.hasNext()).isTrue();
      var move = iterator.next();
      director.executeMove(move);
      assertThat(move.getPlanningEntities()).containsExactly(entity);
      assertThat(entity.getValue()).isSameAs(solution.getValueList().getLast());
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.ZERO);
      var replacement = TestdataSolution.generateSolution(2, 1);
      director.setWorkingSolution(replacement);
      var replacementMove = repository.iterator(new Random(0)).next();
      director.executeMove(replacementMove);
      assertThat(replacementMove.getPlanningEntities())
          .containsExactly(replacement.getEntityList().getFirst());
    }
    // The closed director must not leave an initialized repository attached to its old solution.
    try (var director = factory.buildScoreDirector()) {
      var replacement = TestdataSolution.generateSolution(2, 1);
      director.setWorkingSolution(replacement);
      director.setMoveRepository(repository);
      var move = repository.iterator(new Random(0)).next();
      director.executeMove(move);
      assertThat(move.getPlanningEntities())
          .containsExactly(replacement.getEntityList().getFirst());
    }
  }

  public static class ValuesProvider implements ConstraintProvider {
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory.forEach(TestdataValue.class).penalize(SimpleScore.ONE).asConstraint("values")
      };
    }
  }

  public static class OrdinaryFactJoinProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(TestdataLavishEntity.class)
            .join(TestdataLavishExtra.class)
            .penalize(SimpleScore.ONE)
            .asConstraint("ordinaryFactJoin")
      };
    }
  }

  private static class CountingCalculator
      implements IncrementalScoreCalculator<TestdataSolution, SimpleScore> {
    private TestdataSolution solution;
    private int resets;
    private boolean failReset;
    private final List<TestdataEntity> changed = new ArrayList<>();

    public void resetWorkingSolution(TestdataSolution solution) {
      resets++;
      if (failReset) throw new IllegalStateException("reset failure");
      this.solution = solution;
    }

    public void beforeVariableChanged(Object entity, String name) {
      assertThat(solution.getEntityList()).contains((TestdataEntity) entity);
    }

    public void afterVariableChanged(Object entity, String name) {
      changed.add((TestdataEntity) entity);
    }

    public SimpleScore calculateScore() {
      return SimpleScore.of(solution.getEntityList().size());
    }
  }
}
