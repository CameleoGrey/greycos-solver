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
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.calculator.IncrementalScoreCalculator;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;
import greycos.solver.core.impl.score.director.incremental.IncrementalScoreDirectorFactory;
import greycos.solver.core.impl.score.director.stream.BavetConstraintStreamScoreDirectorFactory;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Timeout(20)
class DeclarativeShadowVariableFailureRecoveryTest {

  enum Backend {
    EASY,
    INCREMENTAL,
    BAVET
  }

  @ParameterizedTest
  @EnumSource(Backend.class)
  void explicitRecoveryPreservesAssignmentsAndAllowsFurtherListChanges(Backend backend) {
    var solution = RecoverySolution.generate(1, 2, 3);
    var calculator = new EagerCalculator();
    try (var director = director(backend, calculator)) {
      director.setWorkingSolution(solution);
      assertMatchesReplayAndFreshDirector(backend, director, solution);
      var route = solution.routes.getFirst();
      swap(director, route, 0, 1);
      var assignment = List.copyOf(route.visits);
      // The first visit updates successfully before the second supplier fails.
      var failed = route.visits.get(1);
      failed.fail = true;
      assertThatThrownBy(director::updateShadowVariables)
          .isInstanceOf(IllegalStateException.class)
          .hasRootCauseMessage("expected declarative supplier failure");
      assertThat(route.visits.getFirst().total).isEqualTo(2);
      assertThat(failed.total).isEqualTo(1);
      assertFailureQuarantined(director, calculator, solution);
      var resetsAfterFailure = calculator.resets;

      // Failed explicit repair must not allow an ordinary barrier to retry the supplier.
      assertThatThrownBy(director::forceUpdateShadowVariables)
          .isInstanceOf(IllegalStateException.class)
          .hasRootCauseMessage("expected declarative supplier failure");
      assertFailureQuarantined(director, calculator, solution);

      failed.fail = false;
      director.forceUpdateShadowVariables();
      assertThat(director.isLastVariableUpdateSuccessful()).isTrue();
      assertThat(route.visits).containsExactlyElementsOf(assignment);
      assertThat(route.visits).extracting(visit -> visit.total).containsExactly(2, 3, 6);
      assertMatchesReplayAndFreshDirector(backend, director, solution);
      if (backend == Backend.INCREMENTAL) {
        assertThat(calculator.resets).isEqualTo(resetsAfterFailure + 2);
      }

      // A throwing supplier must release the declarative-update guard before later list events.
      swap(director, route, 1, 2);
      director.updateShadowVariables();
      assertThat(director.isLastVariableUpdateSuccessful()).isTrue();
      assertThat(route.visits).extracting(visit -> visit.duration).containsExactly(2, 3, 1);
      assertThat(route.visits).extracting(visit -> visit.total).containsExactly(2, 5, 6);
      assertMatchesReplayAndFreshDirector(backend, director, solution);
    }
  }

  @ParameterizedTest
  @EnumSource(Backend.class)
  void replacingFailedSolutionDiscardsTheOldSession(Backend backend) {
    var oldSolution = RecoverySolution.generate(1, 2, 3);
    try (var director = director(backend, new EagerCalculator())) {
      director.setWorkingSolution(oldSolution);
      var oldRoute = oldSolution.routes.getFirst();
      swap(director, oldRoute, 0, 1);
      var oldAssignment = List.copyOf(oldRoute.visits);
      oldRoute.visits.get(1).fail = true;
      assertThatThrownBy(director::updateShadowVariables)
          .isInstanceOf(IllegalStateException.class)
          .hasRootCauseMessage("expected declarative supplier failure");
      var oldCalls = oldSolution.callbackCounts();

      // The old supplier keeps throwing; replacement must detach its graph before list repair.
      var replacement = RecoverySolution.generate(4, 5, 6);
      director.setWorkingSolution(replacement);
      assertThat(director.isLastVariableUpdateSuccessful()).isTrue();
      assertThat(oldSolution.callbackCounts()).isEqualTo(oldCalls);
      assertThat(oldRoute.visits).containsExactlyElementsOf(oldAssignment);
      assertMatchesReplayAndFreshDirector(backend, director, replacement);

      swap(director, replacement.routes.getFirst(), 0, 2);
      director.updateShadowVariables();
      assertMatchesReplayAndFreshDirector(backend, director, replacement);
      assertThat(oldSolution.callbackCounts()).isEqualTo(oldCalls);
      assertThat(oldRoute.visits).containsExactlyElementsOf(oldAssignment);
    }
  }

  private static void assertFailureQuarantined(
      InnerScoreDirector<RecoverySolution, SimpleScore> director,
      EagerCalculator calculator,
      RecoverySolution solution) {
    var calls = solution.callbackCounts();
    var resets = calculator.resets;
    assertThat(director.isLastVariableUpdateSuccessful()).isFalse();
    director.updateShadowVariables();
    assertThatThrownBy(director::calculateScore)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("forceUpdateShadowVariables")
        .hasRootCauseMessage("expected declarative supplier failure");
    assertThat(director.isLastVariableUpdateSuccessful()).isFalse();
    assertThat(solution.callbackCounts()).isEqualTo(calls);
    assertThat(calculator.resets).isEqualTo(resets);
  }

  private static void swap(
      InnerScoreDirector<RecoverySolution, SimpleScore> director,
      Route route,
      int firstIndex,
      int secondIndex) {
    director.beforeListVariableChanged(route, "visits", firstIndex, secondIndex + 1);
    Collections.swap(route.visits, firstIndex, secondIndex);
    director.afterListVariableChanged(route, "visits", firstIndex, secondIndex + 1);
  }

  private static void assertMatchesReplayAndFreshDirector(
      Backend backend,
      InnerScoreDirector<RecoverySolution, SimpleScore> director,
      RecoverySolution solution) {
    var route = solution.routes.getFirst();
    var prefix = 0;
    for (var i = 0; i < route.visits.size(); i++) {
      var visit = route.visits.get(i);
      prefix += visit.duration;
      assertThat(visit.route).isSameAs(route);
      assertThat(visit.previous).isSameAs(i == 0 ? null : route.visits.get(i - 1));
      assertThat(visit.total).isEqualTo(prefix);
      assertThat(visit.mirror).isEqualTo(prefix);
    }
    var score = director.calculateScore().raw();
    assertThat(score).isEqualTo(solution.replay());

    var freshSolution =
        RecoverySolution.generate(
            route.visits.stream().mapToInt(visit -> visit.duration).toArray());
    try (var freshDirector = director(backend, new EagerCalculator())) {
      freshDirector.setWorkingSolution(freshSolution);
      assertThat(score).isEqualTo(freshDirector.calculateScore().raw());
      var freshVisits = freshSolution.routes.getFirst().visits;
      assertThat(route.visits)
          .extracting(visit -> visit.total)
          .containsExactlyElementsOf(freshVisits.stream().map(visit -> visit.total).toList());
      assertThat(route.visits)
          .extracting(visit -> visit.mirror)
          .containsExactlyElementsOf(freshVisits.stream().map(visit -> visit.mirror).toList());
    }
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

  @PlanningSolution
  public static class RecoverySolution {

    @PlanningEntityCollectionProperty public List<Route> routes = new ArrayList<>();

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "visits")
    public List<Visit> visits = new ArrayList<>();

    @PlanningScore public SimpleScore score;

    static RecoverySolution generate(int... durations) {
      var solution = new RecoverySolution();
      var route = new Route();
      solution.routes.add(route);
      for (var duration : durations) {
        var visit = new Visit();
        visit.duration = duration;
        solution.visits.add(visit);
        route.visits.add(visit);
      }
      return solution;
    }

    List<Integer> callbackCounts() {
      return visits.stream().map(visit -> visit.calls).toList();
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

    @ShadowVariable(supplierName = "totalSupplier")
    public Integer total;

    @ShadowVariable(supplierName = "mirrorSupplier")
    public Integer mirror;

    public int duration;
    public boolean fail;
    public int calls;

    @ShadowSources({"route", "previous.total"})
    public Integer totalSupplier() {
      calls++;
      if (fail) {
        throw new IllegalStateException("expected declarative supplier failure");
      }
      return route == null ? 0 : duration + (previous == null ? 0 : previous.total);
    }

    @ShadowSources("total")
    public Integer mirrorSupplier() {
      return total;
    }
  }

  private static class EagerCalculator
      implements IncrementalScoreCalculator<RecoverySolution, SimpleScore> {

    int total;
    int resets;

    @Override
    public void resetWorkingSolution(RecoverySolution solution) {
      resets++;
      total =
          solution.visits.stream().mapToInt(visit -> visit.total == null ? 0 : visit.total).sum();
    }

    @Override
    public void beforeVariableChanged(Object entity, String variable) {
      if (variable.equals("total")) {
        var visit = (Visit) entity;
        total -= visit.total == null ? 0 : visit.total;
      }
    }

    @Override
    public void afterVariableChanged(Object entity, String variable) {
      if (variable.equals("total")) {
        var visit = (Visit) entity;
        total += visit.total == null ? 0 : visit.total;
      }
    }

    @Override
    public SimpleScore calculateScore() {
      return SimpleScore.of(total);
    }
  }
}
