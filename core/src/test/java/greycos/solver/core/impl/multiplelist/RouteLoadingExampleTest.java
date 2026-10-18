package greycos.solver.core.impl.multiplelist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.impl.constructionheuristic.scope.ConstructionHeuristicStepScope;
import greycos.solver.core.impl.localsearch.scope.LocalSearchStepScope;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.score.director.InnerScoreDirector;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.impl.solver.DefaultSolverFactory;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.CargoData;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.CompartmentData;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.Input;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.Plan;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.VehicleData;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.VehiclePlan;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.VisitData;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingExample;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingReplay;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingSolution;

import org.junit.jupiter.api.Test;

class RouteLoadingExampleTest {

  @Test
  void oneLocalSearchStepStillCompletesConstructionFromUninitializedDemoInput() {
    var input = RouteLoadingExample.demoInput();
    var solver =
        (DefaultSolver<RouteLoadingSolution>)
            SolverFactory.<RouteLoadingSolution>create(RouteLoadingExample.solverConfig(0L, 1))
                .buildSolver();
    var stepCounts = new int[4];
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<RouteLoadingSolution> step) {
            if (step instanceof ConstructionHeuristicStepScope<?>) {
              stepCounts[step.getPhaseScope().getPhaseIndex()]++;
            } else if (step instanceof LocalSearchStepScope<?>) {
              stepCounts[3]++;
            }
          }
        });

    var result = solver.solve(RouteLoadingSolution.fromInput(input));

    assertThat(stepCounts).containsExactly(6, 3, 6, 1);
    assertThat(result.vehicles.stream().flatMap(vehicle -> vehicle.visits.stream()))
        .containsExactlyInAnyOrderElementsOf(result.visits);
    assertThat(result.vehicles.stream().flatMap(vehicle -> vehicle.loadingOrder.stream()))
        .containsExactlyInAnyOrderElementsOf(result.cargo);
    assertThat(result.cargo)
        .allSatisfy(
            cargo -> {
              assertThat(cargo.compartment).isNotNull();
              assertThat(cargo.quantity).isNotNull();
            });
    assertShadows(result);
    assertThat(result.score)
        .isEqualTo(RouteLoadingExample.score(RouteLoadingReplay.replay(input, result.toPlan())));
  }

  @Test
  void allTinyDecisionsMatchIndependentReplayAndKnownOptimum() {
    var input = tinyInput();
    var manager =
        SolutionManager.<RouteLoadingSolution, HardSoftScore>create(
            SolverFactory.create(RouteLoadingExample.solverConfig(0L, 30)));
    HardSoftScore optimum = null;
    int feasibleCount = 0;
    int decisionCount = 0;
    for (int route = 0; route < 2; route++) {
      for (int loading = 0; loading < 2; loading++) {
        for (int firstQuantity = 0; firstQuantity <= 2; firstQuantity++) {
          for (int secondQuantity = 0; secondQuantity <= 2; secondQuantity++) {
            var solution = initialized(input);
            if (route == 1) {
              Collections.reverse(solution.vehicles.getFirst().visits);
            }
            if (loading == 1) {
              Collections.reverse(solution.vehicles.getFirst().loadingOrder);
            }
            solution.cargo.get(0).quantity = firstQuantity;
            solution.cargo.get(1).quantity = secondQuantity;
            var metrics = RouteLoadingReplay.replay(input, solution.toPlan());
            var expected = RouteLoadingExample.score(metrics);
            assertThat(manager.update(solution)).isEqualTo(expected);
            assertShadows(solution);
            decisionCount++;
            if (metrics.feasible()) {
              feasibleCount++;
              if (optimum == null || expected.compareTo(optimum) > 0) {
                optimum = expected;
              }
            }
          }
        }
      }
    }
    assertThat(decisionCount).isEqualTo(36);
    assertThat(feasibleCount).isEqualTo(4);
    assertThat(optimum).isEqualTo(HardSoftScore.of(0, -6));
  }

  @Test
  void incrementalCompoundListChangesAndBasicChangesRestoreIndependentState() {
    var input = tinyInput();
    var solution = initialized(input);
    var factory =
        new DefaultSolverFactory<RouteLoadingSolution>(RouteLoadingExample.solverConfig(0L, 30));
    try (var director = factory.<HardSoftScore>getScoreDirectorFactory().buildScoreDirector()) {
      director.setWorkingSolution(solution);
      director.triggerVariableListeners();
      var original = solution.toPlan();
      assertParity(input, solution, director);
      var owner = solution.vehicles.getFirst();
      // Both brackets are open on the same owner, for different descriptors.
      director.beforeListVariableChanged(owner, "visits", 0, 2);
      director.beforeListVariableChanged(owner, "loadingOrder", 0, 2);
      Collections.reverse(owner.visits);
      Collections.reverse(owner.loadingOrder);
      director.afterListVariableChanged(owner, "loadingOrder", 0, 2);
      director.afterListVariableChanged(owner, "visits", 0, 2);
      director.triggerVariableListeners();
      assertParity(input, solution, director);
      var cargo = solution.cargo.getFirst();
      director.beforeVariableChanged(cargo, "quantity");
      cargo.quantity = 2;
      director.afterVariableChanged(cargo, "quantity");
      director.triggerVariableListeners();
      assertParity(input, solution, director);
      director.beforeVariableChanged(cargo, "quantity");
      cargo.quantity = 1;
      director.afterVariableChanged(cargo, "quantity");
      director.beforeListVariableChanged(owner, "visits", 0, 2);
      Collections.reverse(owner.visits);
      director.afterListVariableChanged(owner, "visits", 0, 2);
      director.beforeListVariableChanged(owner, "loadingOrder", 0, 2);
      Collections.reverse(owner.loadingOrder);
      director.afterListVariableChanged(owner, "loadingOrder", 0, 2);
      director.triggerVariableListeners();
      assertThat(solution.toPlan()).isEqualTo(original);
      assertParity(input, solution, director);
    }
  }

  @Test
  void boundedSolvePreservesFeasibleInputAndReplaysReturnedClone() {
    var input = tinyInput();
    var initial = initialized(input);
    var initialPlan = initial.toPlan();
    var initialScore = RouteLoadingExample.score(RouteLoadingReplay.replay(input, initialPlan));
    var result =
        SolverFactory.<RouteLoadingSolution>create(RouteLoadingExample.solverConfig(2L, 30))
            .buildSolver()
            .solve(initial);
    var metrics = RouteLoadingReplay.replay(input, result.toPlan());
    assertThat(metrics.feasible()).isTrue();
    assertThat(result.score)
        .isEqualTo(RouteLoadingExample.score(metrics))
        .isGreaterThanOrEqualTo(initialScore);
    assertThat(result.vehicles.getFirst()).isNotSameAs(initial.vehicles.getFirst());
    assertShadows(result);
  }

  @Test
  void replayRejectsDuplicateIdsAndDoesNotTrustShadows() {
    var input = tinyInput();
    var solution = initialized(input);
    var plan = solution.toPlan();
    var expected = RouteLoadingReplay.replay(input, plan);
    solution.visits.getFirst().index = 999;
    solution.cargo.getFirst().vehicle = null;
    assertThat(RouteLoadingReplay.replay(input, solution.toPlan())).isEqualTo(expected);
    var duplicate =
        new Plan(
            List.of(new VehiclePlan("van", List.of("a", "a"), List.of("a-load", "b-load"))),
            plan.cargo());
    assertThatThrownBy(() -> RouteLoadingReplay.replay(input, duplicate))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duplicate routed visit");
  }

  @Test
  void splitLoadsCrossVehicleDependenciesCompatibilityAndCapacityAreReplayed() {
    var input = RouteLoadingExample.demoInput();
    var solution = RouteLoadingSolution.fromInput(input);
    var firstVehicle = solution.vehicles.get(0);
    var secondVehicle = solution.vehicles.get(1);
    firstVehicle.visits.add(solution.visits.get(0));
    firstVehicle.visits.add(solution.visits.get(2));
    secondVehicle.visits.add(solution.visits.get(1));
    for (var allocation : solution.cargo) {
      allocation.quantity = 1;
      boolean east = allocation.fact.visitId().equals("east");
      (east ? secondVehicle : firstVehicle).loadingOrder.add(allocation);
      allocation.compartment =
          solution.compartments.stream()
              .filter(c -> c.vehicleId().equals(east ? "van-b" : "van-a"))
              .filter(c -> c.product().equals(allocation.visit.fact.product()))
              .findFirst()
              .orElseThrow();
    }
    var factory =
        new DefaultSolverFactory<RouteLoadingSolution>(RouteLoadingExample.solverConfig(0L, 30));
    try (var director = factory.<HardSoftScore>getScoreDirectorFactory().buildScoreDirector()) {
      director.setWorkingSolution(solution);
      director.triggerVariableListeners();
      assertParity(input, solution, director);
      assertThat(director.calculateScore().raw().hardScore()).isZero();

      // Moving a visit updates constraints on both cargo allocations without changing those cargos.
      director.beforeListVariableChanged(firstVehicle, "visits", 0, 2);
      director.beforeListVariableChanged(secondVehicle, "visits", 0, 1);
      var moved = firstVehicle.visits.removeFirst();
      secondVehicle.visits.add(moved);
      director.afterListVariableChanged(firstVehicle, "visits", 0, 1);
      director.afterListVariableChanged(secondVehicle, "visits", 0, 2);
      director.triggerVariableListeners();
      assertParity(input, solution, director);
      assertThat(director.calculateScore().raw().hardScore()).isEqualTo(-2);

      director.beforeListVariableChanged(firstVehicle, "visits", 0, 1);
      director.beforeListVariableChanged(secondVehicle, "visits", 0, 2);
      secondVehicle.visits.remove(moved);
      firstVehicle.visits.addFirst(moved);
      director.afterListVariableChanged(firstVehicle, "visits", 0, 2);
      director.afterListVariableChanged(secondVehicle, "visits", 0, 1);
      var cargo = solution.cargo.getFirst();
      var originalCompartment = cargo.compartment;
      director.beforeVariableChanged(cargo, "compartment");
      cargo.compartment = solution.compartments.get(1); // Same vehicle, wrong product.
      director.afterVariableChanged(cargo, "compartment");
      director.triggerVariableListeners();
      assertParity(input, solution, director);
      assertThat(director.calculateScore().raw().hardScore()).isEqualTo(-1);

      director.beforeVariableChanged(cargo, "compartment");
      cargo.compartment = originalCompartment;
      director.afterVariableChanged(cargo, "compartment");
      for (var allocation : solution.cargo.subList(0, 2)) {
        director.beforeVariableChanged(allocation, "quantity");
        allocation.quantity = 2;
        director.afterVariableChanged(allocation, "quantity");
      }
      director.triggerVariableListeners();
      assertParity(input, solution, director);
      // Two excess demand units and one excess compartment-capacity unit.
      assertThat(director.calculateScore().raw().hardScore()).isEqualTo(-3);
    }
  }

  private static void assertParity(
      Input input,
      RouteLoadingSolution solution,
      InnerScoreDirector<RouteLoadingSolution, HardSoftScore> director) {
    assertThat(director.calculateScore().raw())
        .isEqualTo(RouteLoadingExample.score(RouteLoadingReplay.replay(input, solution.toPlan())));
    assertShadows(solution);
  }

  private static void assertShadows(RouteLoadingSolution solution) {
    for (var owner : solution.vehicles) {
      for (int i = 0; i < owner.visits.size(); i++) {
        var value = owner.visits.get(i);
        assertThat(value.vehicle).isSameAs(owner);
        assertThat(value.index).isEqualTo(i);
        assertThat(value.previous).isSameAs(i == 0 ? null : owner.visits.get(i - 1));
        assertThat(value.next)
            .isSameAs(i + 1 == owner.visits.size() ? null : owner.visits.get(i + 1));
      }
      for (int i = 0; i < owner.loadingOrder.size(); i++) {
        var value = owner.loadingOrder.get(i);
        assertThat(value.vehicle).isSameAs(owner);
        assertThat(value.index).isEqualTo(i);
        assertThat(value.previous).isSameAs(i == 0 ? null : owner.loadingOrder.get(i - 1));
        assertThat(value.next)
            .isSameAs(i + 1 == owner.loadingOrder.size() ? null : owner.loadingOrder.get(i + 1));
      }
    }
  }

  private static RouteLoadingSolution initialized(Input input) {
    var solution = RouteLoadingSolution.fromInput(input);
    solution.vehicles.getFirst().visits = new ArrayList<>(solution.visits);
    solution.vehicles.getFirst().loadingOrder = new ArrayList<>(solution.cargo);
    for (var cargo : solution.cargo) {
      cargo.quantity = 1;
      cargo.compartment = solution.compartments.getFirst();
    }
    return solution;
  }

  private static Input tinyInput() {
    return new Input(
        List.of(new VehicleData("van")),
        List.of(new CompartmentData("box", "van", "dry", 4)),
        List.of(new VisitData("a", 1, 0, "dry", 1), new VisitData("b", 3, 0, "dry", 1)),
        List.of(new CargoData("a-load", "a", 0), new CargoData("b-load", "b", 1)),
        2);
  }
}
