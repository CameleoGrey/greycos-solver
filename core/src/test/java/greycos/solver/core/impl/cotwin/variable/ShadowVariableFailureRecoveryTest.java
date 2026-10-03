package greycos.solver.core.impl.cotwin.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.CascadingUpdateShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;
import greycos.solver.core.impl.score.director.incremental.IncrementalScoreDirectorFactory;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Timeout(20)
class ShadowVariableFailureRecoveryTest {
  enum Backend {
    EASY,
    INCREMENTAL,
    BAVET
  }

  private static InnerScoreDirector<RecoverySolution, SimpleScore> director(
      Backend backend, EagerCalculator calculator) {
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            RecoverySolution.class, Route.class, Visit.class);
    return switch (backend) {
      case EASY ->
          new EasyScoreDirectorFactory<>(
                  descriptor,
                  solution ->
                      SimpleScore.of(solution.visits.stream().mapToInt(visit -> visit.total).sum()),
                  EnvironmentMode.NO_ASSERT)
              .buildScoreDirector();
      case INCREMENTAL ->
          new IncrementalScoreDirectorFactory<>(
                  descriptor, () -> calculator, EnvironmentMode.NO_ASSERT)
              .buildScoreDirector();
      case BAVET ->
          new BavetConstraintStreamScoreDirectorFactory<RecoverySolution, SimpleScore>(
                  descriptor,
                  constraints ->
                      new Constraint[] {
                        constraints
                            .forEach(Visit.class)
                            .reward(SimpleScore.ONE, visit -> visit.total)
                            .asConstraint("total")
                      },
                  EnvironmentMode.NO_ASSERT)
              .buildScoreDirector();
    };
  }

  @ParameterizedTest
  @EnumSource(Backend.class)
  void explicitRecoveryRebuildsBeforeRepairAndPreservesAssignments(Backend backend) {
    for (var partialMutation : List.of(false, true)) {
      var solution = RecoverySolution.generate();
      var calculator = new EagerCalculator();
      try (var director = director(backend, calculator)) {
        director.setWorkingSolution(solution);
        assertThat(director.calculateScore().raw()).isEqualTo(solution.replay());
        var route = solution.routes.getFirst();
        director.beforeListVariableChanged(route, "visits", 0, 2);
        Collections.swap(route.visits, 0, 1);
        director.afterListVariableChanged(route, "visits", 0, 2);
        var assignment = List.copyOf(route.visits);
        var failed = route.visits.getFirst();
        failed.fail = true;
        failed.partialMutation = partialMutation;
        var beforeCount = calculator.beforeCount;
        var afterCount = calculator.afterCount;
        assertThatThrownBy(director::updateShadowVariables)
            .isInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("expected callback failure");
        assertThat(director.isLastVariableUpdateSuccessful()).isFalse();
        if (backend == Backend.INCREMENTAL) {
          assertThat(calculator.beforeCount).isEqualTo(beforeCount + 1);
          assertThat(calculator.afterCount).isEqualTo(afterCount);
        }
        assertThat(failed.total).isEqualTo(partialMutation ? -37 : 3);
        var callsAfterFailure = failed.calls;
        var resetsAfterFailure = calculator.resets;
        director.updateShadowVariables();
        assertThatThrownBy(director::calculateScore)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("forceUpdateShadowVariables")
            .hasRootCauseMessage("expected callback failure");
        assertThat(failed.calls).isEqualTo(callsAfterFailure);
        assertThat(calculator.resets).isEqualTo(resetsAfterFailure);

        // A failed explicit recovery also remains failed until another explicit request.
        assertThatThrownBy(director::forceUpdateShadowVariables)
            .isInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("expected callback failure");
        var repeatedCalls = failed.calls;
        var repeatedResets = calculator.resets;
        director.updateShadowVariables();
        assertThatThrownBy(director::calculateScore)
            .isInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("expected callback failure");
        assertThat(failed.calls).isEqualTo(repeatedCalls);
        assertThat(calculator.resets).isEqualTo(repeatedResets);

        failed.fail = false;
        director.forceUpdateShadowVariables();
        assertThat(director.isLastVariableUpdateSuccessful()).isTrue();
        assertThat(route.visits).containsExactlyElementsOf(assignment);
        assertThat(route.visits).extracting(visit -> visit.duration).containsExactly(2, 1, 3);
        assertThat(route.visits).extracting(visit -> visit.total).containsExactly(2, 3, 6);
        assertThat(route.visits)
            .allSatisfy(visit -> assertThat(visit.mirror).isEqualTo(visit.total));
        assertThat(director.calculateScore().raw())
            .isEqualTo(SimpleScore.of(11))
            .isEqualTo(solution.replay());
        if (backend == Backend.INCREMENTAL) {
          assertThat(calculator.resets).isEqualTo(resetsAfterFailure + 2);
        }
      }
    }
  }

  @Test
  void backendResetFailureCannotBeRetriedByOrdinaryBarriers() {
    var solution = RecoverySolution.generate();
    var calculator = new EagerCalculator();
    try (var director = director(Backend.INCREMENTAL, calculator)) {
      director.setWorkingSolution(solution);
      var route = solution.routes.getFirst();
      director.beforeListVariableChanged(route, "visits", 0, 2);
      Collections.swap(route.visits, 0, 1);
      director.afterListVariableChanged(route, "visits", 0, 2);
      var failed = route.visits.getFirst();
      failed.fail = true;
      failed.partialMutation = true;
      assertThatThrownBy(director::updateShadowVariables).isInstanceOf(IllegalStateException.class);
      failed.fail = false;
      calculator.failReset = true;
      assertThatThrownBy(director::forceUpdateShadowVariables)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("expected reset failure");
      var resets = calculator.resets;
      var calls = failed.calls;
      director.updateShadowVariables();
      assertThatThrownBy(director::calculateScore)
          .isInstanceOf(IllegalStateException.class)
          .hasRootCauseMessage("expected reset failure");
      assertThat(calculator.resets).isEqualTo(resets);
      assertThat(failed.calls).isEqualTo(calls);
      calculator.failReset = false;
      director.forceUpdateShadowVariables();
      assertThat(calculator.resets).isEqualTo(resets + 1);
      assertThat(director.calculateScore().raw())
          .isEqualTo(solution.replay())
          .isEqualTo(SimpleScore.of(11));
    }
  }

  @PlanningSolution
  public static class RecoverySolution {
    @PlanningEntityCollectionProperty public List<Route> routes = new ArrayList<>();

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "visits")
    public List<Visit> visits = new ArrayList<>();

    @PlanningScore public SimpleScore score;

    static RecoverySolution generate() {
      var solution = new RecoverySolution();
      var route = new Route();
      solution.routes.add(route);
      for (var i = 1; i <= 3; i++) {
        var visit = new Visit();
        visit.duration = i;
        solution.visits.add(visit);
        route.visits.add(visit);
      }
      return solution;
    }

    SimpleScore replay() {
      int score = 0;
      for (var route : routes) {
        int prefix = 0;
        for (var visit : route.visits) {
          prefix += visit.duration;
          score += prefix;
        }
      }
      return SimpleScore.of(score);
    }
  }

  @PlanningEntity
  public static class Route {
    @PlanningListVariable(valueRangeProviderRefs = "visits")
    public List<Visit> visits = new ArrayList<>();
  }

  @PlanningEntity
  public static class Visit {
    @InverseRelationShadowVariable(sourceVariableName = "visits")
    public Route route;

    @PreviousElementShadowVariable(sourceVariableName = "visits")
    public Visit previous;

    @CascadingUpdateShadowVariable(targetMethodName = "update")
    public Integer total;

    @CascadingUpdateShadowVariable(targetMethodName = "update")
    public Integer mirror;

    public int duration;
    public boolean fail;
    public boolean partialMutation;
    public int calls;

    public void update() {
      calls++;
      if (fail) {
        if (partialMutation) total = -37;
        throw new IllegalStateException("expected callback failure");
      }
      total = route == null ? 0 : duration + (previous == null ? 0 : previous.total);
      mirror = total;
    }
  }

  private static class EagerCalculator
      implements IncrementalScoreCalculator<RecoverySolution, SimpleScore> {
    int total;
    int resets;
    int beforeCount;
    int afterCount;
    boolean failReset;

    public void resetWorkingSolution(RecoverySolution solution) {
      resets++;
      if (failReset) throw new IllegalStateException("expected reset failure");
      total =
          solution.visits.stream().mapToInt(visit -> visit.total == null ? 0 : visit.total).sum();
    }

    public void beforeVariableChanged(Object entity, String variable) {
      if (variable.equals("total")) {
        beforeCount++;
        var visit = (Visit) entity;
        total -= visit.total == null ? 0 : visit.total;
      }
    }

    public void afterVariableChanged(Object entity, String variable) {
      if (variable.equals("total")) {
        afterCount++;
        var visit = (Visit) entity;
        total += visit.total == null ? 0 : visit.total;
      }
    }

    public SimpleScore calculateScore() {
      return SimpleScore.of(total);
    }
  }
}
