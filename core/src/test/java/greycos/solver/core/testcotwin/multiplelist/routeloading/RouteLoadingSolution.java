package greycos.solver.core.testcotwin.multiplelist.routeloading;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.IntStream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.CargoData;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.CargoDecision;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.CompartmentData;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.Input;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.Plan;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.VehiclePlan;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.VisitData;

/** Mutable solver representation. Public fields keep this test-classpath example compact. */
@PlanningSolution
public class RouteLoadingSolution {

  @PlanningEntityCollectionProperty public List<Vehicle> vehicles = new ArrayList<>();

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "visits")
  public List<Visit> visits = new ArrayList<>();

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "cargo")
  public List<Cargo> cargo = new ArrayList<>();

  @ProblemFactCollectionProperty
  @ValueRangeProvider(id = "compartments")
  public List<CompartmentData> compartments;

  @ProblemFactCollectionProperty
  @ValueRangeProvider(id = "quantities")
  public List<Integer> quantities;

  @PlanningScore public HardSoftScore score;

  public static RouteLoadingSolution fromInput(Input input) {
    var solution = new RouteLoadingSolution();
    solution.compartments = input.compartments();
    solution.quantities = IntStream.rangeClosed(0, input.maximumQuantity()).boxed().toList();
    var visitMap = new HashMap<String, Visit>();
    for (var fact : input.vehicles()) {
      solution.vehicles.add(new Vehicle(fact.id()));
    }
    for (var fact : input.visits()) {
      var visit = new Visit(fact);
      solution.visits.add(visit);
      visitMap.put(fact.id(), visit);
    }
    for (var fact : input.cargo()) {
      solution.cargo.add(new Cargo(fact, visitMap.get(fact.visitId())));
    }
    return solution;
  }

  /** Reconstruct decisions from genuine state only; replay uses the original input snapshot. */
  public Plan toPlan() {
    var plans =
        vehicles.stream()
            .map(
                vehicle ->
                    new VehiclePlan(
                        vehicle.id,
                        vehicle.visits.stream().map(visit -> visit.id).toList(),
                        vehicle.loadingOrder.stream().map(allocation -> allocation.id).toList()))
            .toList();
    var decisions =
        cargo.stream()
            .map(
                allocation -> {
                  if (allocation.compartment == null || allocation.quantity == null) {
                    throw new IllegalStateException(
                        "Cargo (" + allocation.id + ") is not fully initialized.");
                  }
                  return new CargoDecision(
                      allocation.id, allocation.compartment.id(), allocation.quantity);
                })
            .toList();
    return new Plan(plans, decisions);
  }

  @PlanningEntity
  public static class Vehicle {
    @PlanningId public String id;

    @PlanningListVariable(valueRangeProviderRefs = "visits")
    public List<Visit> visits = new ArrayList<>();

    @PlanningListVariable(valueRangeProviderRefs = "cargo")
    public List<Cargo> loadingOrder = new ArrayList<>();

    public Vehicle() {}

    public Vehicle(String id) {
      this.id = id;
    }
  }

  @PlanningEntity
  public static class Visit {
    @PlanningId public String id;
    public VisitData fact;

    @InverseRelationShadowVariable(sourceVariableName = "visits")
    public Vehicle vehicle;

    @IndexShadowVariable(sourceVariableName = "visits")
    public Integer index;

    @PreviousElementShadowVariable(sourceVariableName = "visits")
    public Visit previous;

    @NextElementShadowVariable(sourceVariableName = "visits")
    public Visit next;

    public Visit() {}

    public Visit(VisitData fact) {
      this.id = fact.id();
      this.fact = fact;
    }
  }

  @PlanningEntity
  public static class Cargo {
    @PlanningId public String id;
    public CargoData fact;
    public Visit visit;

    @PlanningVariable(valueRangeProviderRefs = "quantities")
    public Integer quantity;

    @PlanningVariable(valueRangeProviderRefs = "compartments")
    public CompartmentData compartment;

    @InverseRelationShadowVariable(sourceVariableName = "loadingOrder")
    public Vehicle vehicle;

    @IndexShadowVariable(sourceVariableName = "loadingOrder")
    public Integer index;

    @PreviousElementShadowVariable(sourceVariableName = "loadingOrder")
    public Cargo previous;

    @NextElementShadowVariable(sourceVariableName = "loadingOrder")
    public Cargo next;

    public Cargo() {}

    public Cargo(CargoData fact, Visit visit) {
      this.id = fact.id();
      this.fact = fact;
      this.visit = visit;
    }
  }
}
