package greycos.solver.core.impl.cotwin.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.move.VariableChangeRecordingScoreDirector;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;
import greycos.solver.core.impl.score.director.incremental.IncrementalScoreDirectorFactory;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;
import greycos.solver.core.testcotwin.cascade.mixed.TestdataMixedCascadingSolution;
import greycos.solver.core.testcotwin.cascade.mixed.TestdataMixedCascadingSolution.Route;
import greycos.solver.core.testcotwin.cascade.mixed.TestdataMixedCascadingSolution.Visit;
import greycos.solver.core.testcotwin.cascade.single.TestdataSingleCascadingSolution;
import greycos.solver.core.testcotwin.shadow.inverserelation.TestdataInverseRelationEntity;
import greycos.solver.core.testcotwin.shadow.inverserelation.TestdataInverseRelationSolution;
import greycos.solver.core.testcotwin.shadow.inverserelation.TestdataInverseRelationValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class VariableSupportCascadingTest {
  @Test
  void forcedRefreshIncludesUnassignedValuesAndEveryCascadeGroup() {
    var solution = TestdataMixedCascadingSolution.generate(1, 1);
    solution.routes.getFirst().visits.clear();
    var visit = solution.visits.getFirst();
    visit.total = 99;
    visit.mirroredTotal = 99;
    visit.independent = 99;
    try (var director =
        new EasyScoreDirectorFactory<>(
                TestdataMixedCascadingSolution.buildSolutionDescriptor(),
                TestdataMixedCascadingSolution::shadowScore,
                EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(solution);
      assertThat(director.calculateScore().raw()).isEqualTo(solution.replayScore());
      assertThat(visit.total).isZero();
      assertThat(visit.mirroredTotal).isZero();
      assertThat(visit.independent).isZero();
      assertThat(visit.calls).isOne();
      assertThat(visit.independentCalls).isOne();
      director.updateShadowVariables();
      assertThat(visit.calls).isOne();
    }
  }

  @Test
  void basicAndDeclarativeChangesUpdateCascadeAndRecordedUndo() {
    var solution = TestdataMixedCascadingSolution.generate(1, 3);
    var factory =
        new BavetConstraintStreamScoreDirectorFactory<TestdataMixedCascadingSolution, SimpleScore>(
            TestdataMixedCascadingSolution.buildSolutionDescriptor(),
            constraints ->
                new Constraint[] {
                  constraints
                      .forEachIncludingUnassigned(Visit.class)
                      .reward(SimpleScore.ONE, v -> v.total)
                      .asConstraint("total")
                },
            EnvironmentMode.PHASE_ASSERT);
    try (var director = factory.buildScoreDirector()) {
      director.setWorkingSolution(solution);
      var original = director.calculateScore();
      var recording =
          new VariableChangeRecordingScoreDirector<TestdataMixedCascadingSolution, SimpleScore>(
              director);
      var visit = solution.visits.getFirst();
      recording.beforeVariableChanged(visit, "delay");
      visit.delay = 2;
      recording.afterVariableChanged(visit, "delay");
      recording.updateShadowVariables();
      assertThat(director.calculateScore().raw()).isEqualTo(solution.replayScore());
      assertThat(solution.visits).extracting(v -> v.total).containsExactly(2, 3, 4);
      assertThat(solution.visits).allSatisfy(v -> assertThat(v.mirroredTotal).isEqualTo(v.total));
      recording.undoChanges();
      assertThat(director.calculateScore()).isEqualTo(original);
      assertThat(director.calculateScore().raw()).isEqualTo(solution.replayScore());
    }
  }

  @Test
  void changedSuffixDoesNotScanUnrelatedRoutesOrPrefix() {
    var solution = TestdataMixedCascadingSolution.generate(2, 1000);
    try (var director =
        new EasyScoreDirectorFactory<>(
                TestdataMixedCascadingSolution.buildSolutionDescriptor(),
                TestdataMixedCascadingSolution::shadowScore,
                EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(solution);
      solution.visits.forEach(
          v -> {
            v.calls = 0;
            v.independentCalls = 0;
          });
      var visit = solution.routes.getFirst().visits.get(998);
      director.beforeVariableChanged(visit, "delay");
      visit.delay = 2;
      director.afterVariableChanged(visit, "delay");
      director.updateShadowVariables();
      assertThat(director.calculateScore().raw()).isEqualTo(solution.replayScore());
      assertThat(solution.routes.getFirst().visits.subList(0, 998))
          .allSatisfy(v -> assertThat(v.calls).isZero());
      assertThat(solution.routes.get(1).visits).allSatisfy(v -> assertThat(v.calls).isZero());
      assertThat(solution.routes.getFirst().visits.subList(998, 1000))
          .allSatisfy(v -> assertThat(v.calls).isOne());
      director.updateShadowVariables();
      assertThat(visit.calls).isOne();
    }
  }

  @Test
  void resetDiscardsPendingUpdatesForPreviousSolution() {
    var oldSolution = TestdataMixedCascadingSolution.generate(1, 2);
    var newSolution = TestdataMixedCascadingSolution.generate(1, 1);
    try (var director =
        new EasyScoreDirectorFactory<>(
                TestdataMixedCascadingSolution.buildSolutionDescriptor(),
                TestdataMixedCascadingSolution::shadowScore,
                EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(oldSolution);
      var visit = oldSolution.visits.getFirst();
      director.beforeVariableChanged(visit, "delay");
      visit.delay = 2;
      director.afterVariableChanged(visit, "delay");
      var oldCalls = visit.calls;
      director.setWorkingSolution(newSolution);
      assertThat(director.calculateScore().raw()).isEqualTo(newSolution.replayScore());
      assertThat(visit.calls).isEqualTo(oldCalls);
    }
  }

  @Test
  void forcedRefreshAlsoVisitsShadowOnlyUnassignedElements() {
    var solution = TestdataSingleCascadingSolution.generateUninitializedSolution(1, 1);
    var value = solution.getValueList().getFirst();
    value.setCascadeValue(99);
    try (var director =
        new EasyScoreDirectorFactory<>(
                TestdataSingleCascadingSolution.buildSolutionDescriptor(),
                ignored -> SimpleScore.ZERO,
                EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(solution);
      assertThat(value.getCascadeValue()).isNull();
    }
  }

  @Test
  void separateForcedRangesConvergeWithoutScanningTheGap() {
    var solution = TestdataMixedCascadingSolution.generate(1, 1000);
    try (var director =
        new EasyScoreDirectorFactory<>(
                TestdataMixedCascadingSolution.buildSolutionDescriptor(),
                TestdataMixedCascadingSolution::shadowScore,
                EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(solution);
      solution.visits.forEach(v -> v.calls = 0);
      for (var index : List.of(10, 900, 10)) {
        var visit = solution.visits.get(index);
        director.beforeVariableChanged(visit, "delay");
        director.afterVariableChanged(visit, "delay");
      }
      director.updateShadowVariables();
      assertThat(solution.visits.stream().mapToInt(v -> v.calls).sum()).isEqualTo(4);
      assertThat(director.calculateScore().raw()).isEqualTo(solution.replayScore());
    }
  }

  @Test
  void unassignmentThenReassignmentUsesFinalOwnerForEveryCascadeGroup() {
    var solution = TestdataMixedCascadingSolution.generate(2, 1);
    try (var director =
        new EasyScoreDirectorFactory<>(
                TestdataMixedCascadingSolution.buildSolutionDescriptor(),
                TestdataMixedCascadingSolution::shadowScore,
                EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(solution);
      var source = solution.routes.getFirst();
      var destination = solution.routes.getLast();
      var visit = source.visits.getFirst();
      director.beforeListVariableElementUnassigned(
          director.getSolutionDescriptor().getListVariableDescriptor(), visit);
      director.beforeListVariableChanged(source, "visits", 0, 1);
      source.visits.clear();
      director.afterListVariableChanged(source, "visits", 0, 0);
      director.afterListVariableElementUnassigned(
          director.getSolutionDescriptor().getListVariableDescriptor(), visit);
      director.beforeListVariableElementAssigned(
          director.getSolutionDescriptor().getListVariableDescriptor(), visit);
      director.beforeListVariableChanged(destination, "visits", 1, 1);
      destination.visits.add(visit);
      director.afterListVariableChanged(destination, "visits", 1, 2);
      director.afterListVariableElementAssigned(
          director.getSolutionDescriptor().getListVariableDescriptor(), visit);
      director.updateShadowVariables();
      assertThat(visit.route).isSameAs(destination);
      assertThat(visit.total).isEqualTo(2);
      assertThat(visit.mirroredTotal).isEqualTo(2);
      assertThat(visit.independent).isOne();
      assertThat(director.calculateScore().raw()).isEqualTo(solution.replayScore());
    }
  }

  @Test
  void callbackFailureDropsQueuedReferencesAndRequiresFullRefresh() {
    var solution = TestdataMixedCascadingSolution.generate(1, 3);
    try (var director =
        new EasyScoreDirectorFactory<>(
                TestdataMixedCascadingSolution.buildSolutionDescriptor(),
                TestdataMixedCascadingSolution::shadowScore,
                EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(solution);
      var visit = solution.visits.getFirst();
      director.beforeVariableChanged(visit, "delay");
      visit.delay = 2;
      director.afterVariableChanged(visit, "delay");
      visit.failUpdate = true;
      assertThatThrownBy(director::updateShadowVariables).isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(director::calculateScore)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("shadow variables might be stale");
      visit.failUpdate = false;
      director.updateShadowVariables();
      assertThat(director.isLastVariableUpdateSuccessful()).isFalse();
      director.forceUpdateShadowVariables();
      assertThat(director.isLastVariableUpdateSuccessful()).isTrue();
      assertThat(director.calculateScore().raw()).isEqualTo(solution.replayScore());
      var originalCalls = visit.calls;
      director.setWorkingSolution(TestdataMixedCascadingSolution.generate(1, 1));
      assertThat(visit.calls).isEqualTo(originalCalls);
    }
  }

  @Test
  void listShadowSpanIncludesShiftedIndexSuffixAndChangedPredecessor() {
    var solution = TestdataMixedCascadingSolution.generate(2, 8);
    var calculator = new InitializedCalculator();
    try (var director =
        new IncrementalScoreDirectorFactory<>(
                TestdataMixedCascadingSolution.buildSolutionDescriptor(),
                () -> calculator,
                EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(solution);
      calculator.events.clear();
      solution.visits.forEach(v -> v.positionCalls = 0);
      var source = solution.routes.getLast();
      var destination = solution.routes.getFirst();
      director.beforeListVariableChanged(source, "visits", 7, 8);
      var moved = source.visits.remove(7);
      director.afterListVariableChanged(source, "visits", 7, 7);
      director.beforeListVariableChanged(destination, "visits", 0, 0);
      destination.visits.addFirst(moved);
      director.afterListVariableChanged(destination, "visits", 0, 1);
      director.updateShadowVariables();
      for (var route : solution.routes) {
        for (var i = 0; i < route.visits.size(); i++) {
          var visit = route.visits.get(i);
          assertThat(visit.index).isEqualTo(i);
          assertThat(visit.position)
              .isEqualTo(
                  (i < 5 ? 0 : i) + (i == 0 ? 100 : 0) + (i + 1 == route.visits.size() ? 1000 : 0));
        }
      }
      // Only source's changed terminal neighbor is updated; destination's entire index suffix is
      // forced.
      assertThat(source.visits.subList(0, 6)).allSatisfy(v -> assertThat(v.positionCalls).isZero());
      assertThat(source.visits.getLast().positionCalls).isOne();
      assertThat(destination.visits).allSatisfy(v -> assertThat(v.positionCalls).isOne());
      for (var variable : List.of("index", "route", "previous", "next")) {
        var beforeCount =
            calculator.events.stream().filter(e -> e.equals("before:" + variable)).count();
        var afterCount =
            calculator.events.stream().filter(e -> e.equals("after:" + variable)).count();
        assertThat(beforeCount).as(variable + " callbacks remain delivered").isPositive();
        assertThat(afterCount).isEqualTo(beforeCount);
      }
      assertThat(director.calculateScore().raw()).isEqualTo(solution.replayScore());
    }
  }

  @Test
  void basicSourcedInverseShadowStillSchedulesItsDistantListElement() {
    var solution = TestdataMixedCascadingSolution.generate(1, 12);
    try (var director =
        new EasyScoreDirectorFactory<>(
                TestdataMixedCascadingSolution.buildSolutionDescriptor(),
                TestdataMixedCascadingSolution::shadowScore,
                EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(solution);
      var source = solution.visits.getFirst();
      var target = solution.visits.getLast();
      director.beforeVariableChanged(source, "partner");
      source.partner = target;
      director.afterVariableChanged(source, "partner");
      director.updateShadowVariables();
      assertThat(target.inverseCount).isOne();
      assertThat(target.dependents).containsExactly(source);
      assertThat(solution.visits.subList(0, 11))
          .allSatisfy(v -> assertThat(v.inverseCount).isZero());
    }
  }

  @Test
  void ownerRoleAndStandaloneBuiltinNotificationsStillScheduleCascades() {
    var solution = TestdataMixedCascadingSolution.generate(1, 3);
    try (var director =
        new EasyScoreDirectorFactory<>(
                TestdataMixedCascadingSolution.buildSolutionDescriptor(),
                TestdataMixedCascadingSolution::shadowScore,
                EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(solution);
      var route = solution.routes.getFirst();
      director.beforeVariableChanged(route, "offset");
      route.offset = 2;
      director.afterVariableChanged(route, "offset");
      director.updateShadowVariables();
      assertThat(solution.visits).allSatisfy(v -> assertThat(v.ownerMarker).isEqualTo(2));
      var visit = solution.visits.getFirst();
      var beforeCalls = visit.calls;
      // Outside the list state's bounded update, there is no covering list event to rely on.
      director.beforeVariableChanged(visit, "index");
      director.afterVariableChanged(visit, "index");
      director.updateShadowVariables();
      assertThat(visit.calls).isEqualTo(beforeCalls + 1);
    }
  }

  @Test
  void calculatorIsInitializedBeforeNotificationsOnFirstAndReusedSolutions() {
    var calculator = new InitializedCalculator();
    try (var director =
        new IncrementalScoreDirectorFactory<>(
                TestdataMixedCascadingSolution.buildSolutionDescriptor(),
                () -> calculator,
                EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      for (var count : List.of(1, 3)) {
        var solution = TestdataMixedCascadingSolution.generate(1, count);
        calculator.events.clear();
        director.setWorkingSolution(solution);
        assertThat(calculator.events).startsWith("reset");
        assertThat(calculator.events)
            .contains("before:route", "after:route", "before:total", "after:total");
        assertThat(director.calculateScore().raw()).isEqualTo(solution.replayScore());
      }
    }
  }

  @Test
  void eagerIncrementalCacheSeesBulkInverseCollectionRepair() {
    var value = new TestdataInverseRelationValue("value");
    var entity = new TestdataInverseRelationEntity("entity", value);
    var solution = new TestdataInverseRelationSolution();
    solution.setEntityList(List.of(entity));
    solution.setValueList(List.of(value));
    var calculator =
        new IncrementalScoreCalculator<TestdataInverseRelationSolution, SimpleScore>() {
          private int count;

          @Override
          public void resetWorkingSolution(TestdataInverseRelationSolution workingSolution) {
            count =
                workingSolution.getValueList().stream().mapToInt(v -> v.getEntities().size()).sum();
          }

          @Override
          public void beforeVariableChanged(Object changed, String name) {
            if (changed instanceof TestdataInverseRelationValue inverse)
              count -= inverse.getEntities().size();
          }

          @Override
          public void afterVariableChanged(Object changed, String name) {
            if (changed instanceof TestdataInverseRelationValue inverse)
              count += inverse.getEntities().size();
          }

          @Override
          public SimpleScore calculateScore() {
            return SimpleScore.of(count);
          }
        };
    try (var director =
        new IncrementalScoreDirectorFactory<>(
                TestdataInverseRelationSolution.buildSolutionDescriptor(),
                () -> calculator,
                EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(solution);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.ONE);
      director.setWorkingSolution(solution);
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.ONE);
      director.beforeVariableChanged(entity, "value");
      entity.setValue(null);
      director.afterVariableChanged(entity, "value");
      director.updateShadowVariables();
      assertThat(director.calculateScore().raw()).isEqualTo(SimpleScore.ZERO);
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = EnvironmentMode.class,
      names = {"NO_ASSERT", "PHASE_ASSERT"})
  void serialSolverReturnsReplayedCascadeAndScore(EnvironmentMode mode) {
    var solver =
        SolverFactory.<TestdataMixedCascadingSolution>create(
                config().withEnvironmentMode(mode).withEasyScoreCalculatorClass(CascadeScore.class))
            .buildSolver();
    var result = solver.solve(TestdataMixedCascadingSolution.generate(1, 1));
    assertThat(result.score).isEqualTo(SimpleScore.ZERO);
    assertThat(result.visits.getFirst().total).isEqualTo(result.visits.getFirst().delay);
  }

  @Test
  void serialSolverInitializesIncrementalCalculatorBeforeCallbacks() {
    var solver =
        SolverFactory.<TestdataMixedCascadingSolution>create(
                config()
                    .withScoreDirectorFactory(
                        new ScoreDirectorFactoryConfig()
                            .withIncrementalScoreCalculatorClass(InitializedCalculator.class)))
            .buildSolver();
    var result = solver.solve(TestdataMixedCascadingSolution.generate(1, 1));
    assertThat(result.score).isEqualTo(result.replayScore());
  }

  private static SolverConfig config() {
    return new SolverConfig()
        .withSolutionClass(TestdataMixedCascadingSolution.class)
        .withEntityClasses(Route.class, Visit.class)
        .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
        .withMoveThreadCount("NONE")
        .withRandomSeed(0L)
        .withPhases(
            new LocalSearchPhaseConfig()
                .withLocalSearchType(LocalSearchType.HILL_CLIMBING)
                .withMoveSelectorConfig(
                    new ChangeMoveSelectorConfig()
                        .withEntitySelectorConfig(new EntitySelectorConfig(Visit.class))
                        .withValueSelectorConfig(new ValueSelectorConfig("delay"))
                        .withSelectionOrder(SelectionOrder.ORIGINAL))
                .withTerminationConfig(new TerminationConfig().withStepCountLimit(1)));
  }

  public static class CascadeScore
      implements EasyScoreCalculator<TestdataMixedCascadingSolution, SimpleScore> {
    public SimpleScore calculateScore(TestdataMixedCascadingSolution solution) {
      return SimpleScore.of(solution.visits.stream().mapToInt(v -> v.delay - v.total).sum());
    }
  }

  public static class InitializedCalculator
      implements IncrementalScoreCalculator<TestdataMixedCascadingSolution, SimpleScore> {
    private TestdataMixedCascadingSolution solution;
    final List<String> events = new ArrayList<>();

    public void resetWorkingSolution(TestdataMixedCascadingSolution solution) {
      this.solution = solution;
      events.add("reset");
    }

    public void beforeVariableChanged(Object entity, String name) {
      assertThat(Objects.requireNonNull(solution).visits).contains((Visit) entity);
      events.add("before:" + name);
    }

    public void afterVariableChanged(Object entity, String name) {
      assertThat(Objects.requireNonNull(solution).visits).contains((Visit) entity);
      events.add("after:" + name);
    }

    public SimpleScore calculateScore() {
      return solution.shadowScore();
    }
  }
}
