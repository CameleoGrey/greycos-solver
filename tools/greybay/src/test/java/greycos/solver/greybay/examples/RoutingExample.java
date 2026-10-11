package greycos.solver.greybay.examples;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.calculator.EasyScoreCalculator;
import greycos.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.greybay.api.BusinessVerifier;
import greycos.solver.greybay.api.ComponentIdentity;
import greycos.solver.greybay.api.ProblemAdapter;
import greycos.solver.greybay.api.ProblemCapabilities;
import greycos.solver.greybay.api.ProblemDescriptor;
import greycos.solver.greybay.api.VerificationResult;

/**
 * Capacitated routing: ordered list variables, inverse/index shadows, and independent business
 * replay.
 */
public final class RoutingExample {
  private RoutingExample() {}

  public record Vehicle(String id, int capacity) {}

  public record Visit(String id, int x, int y, int demand) {}

  public record Problem(List<Vehicle> vehicles, List<Visit> visits) {
    public Problem {
      vehicles = List.copyOf(vehicles);
      visits = List.copyOf(visits);
    }

    /**
     * Domain replay resolves stable IDs and independently walks every route from and to the depot.
     */
    public BusinessScore replay(Map<String, List<String>> visitIdsByVehicleId) {
      if (!visitIdsByVehicleId
          .keySet()
          .equals(
              vehicles.stream().map(Vehicle::id).collect(java.util.stream.Collectors.toSet()))) {
        throw new IllegalArgumentException("Vehicle IDs differ from the original fleet.");
      }
      var original = new HashMap<String, Visit>();
      visits.forEach(visit -> original.put(visit.id(), visit));
      var visited = new HashSet<String>();
      long capacityExcess = 0;
      long distance = 0;
      for (var vehicle : vehicles) {
        int x = 0;
        int y = 0;
        long demand = 0;
        for (var id : visitIdsByVehicleId.get(vehicle.id())) {
          var visit = original.get(id);
          if (visit == null || !visited.add(id)) {
            throw new IllegalArgumentException("Unknown or duplicate visit ID (" + id + ").");
          }
          distance += Math.abs((long) x - visit.x()) + Math.abs((long) y - visit.y());
          x = visit.x();
          y = visit.y();
          demand += visit.demand();
        }
        distance += Math.abs((long) x) + Math.abs((long) y);
        capacityExcess += Math.max(0, demand - vehicle.capacity());
      }
      if (!visited.equals(original.keySet())) {
        throw new IllegalArgumentException(
            "Some original customer demand is absent from the routes.");
      }
      return new BusinessScore(-capacityExcess, -distance);
    }
  }

  public record BusinessScore(long hard, long soft) {}

  public static Problem problem(String id) {
    int count = ExampleData.size(id);
    int offset = id.startsWith("heldout-") ? 5 : 0;
    var vehicles =
        List.of(
            new Vehicle("vehicle-0", count),
            new Vehicle("vehicle-1", count),
            new Vehicle("vehicle-2", count));
    var visits = new ArrayList<Visit>();
    for (int i = 0; i < count; i++) {
      visits.add(
          new Visit(
              "visit-" + i,
              1 + (i * 7 + offset) % 31,
              1 + (i * 11 + offset) % 29,
              1 + (i + offset) % 3));
    }
    return new Problem(vehicles, visits);
  }

  public static SolverConfig baseSolverConfig() {
    return new SolverConfig()
        .withSolutionClass(RoutePlan.class)
        .withEntityClasses(Route.class, Stop.class)
        .withEasyScoreCalculatorClass(RouteScoreCalculator.class)
        .withPhases(
            new ConstructionHeuristicPhaseConfig(),
            new LocalSearchPhaseConfig().withLocalSearchType(LocalSearchType.LATE_ACCEPTANCE));
  }

  public static ProblemAdapter<RoutePlan> problemAdapter() {
    return new RoutingProblems();
  }

  public static RoutePlan reconstruct(
      Problem input, Map<String, List<String>> visitIdsByVehicleId) {
    input.replay(visitIdsByVehicleId);
    var plan = new RoutePlan();
    var stopsById = new LinkedHashMap<String, Stop>();
    input.visits().forEach(visit -> stopsById.put(visit.id(), new Stop(visit)));
    var routes = new ArrayList<Route>();
    for (var vehicle : input.vehicles()) {
      var route = new Route(vehicle);
      for (var visitId : visitIdsByVehicleId.get(vehicle.id())) {
        var stop = stopsById.get(visitId);
        stop.setRoute(route);
        stop.setIndex(route.getStops().size());
        route.getStops().add(stop);
      }
      routes.add(route);
    }
    plan.setRoutes(routes);
    plan.setStops(new ArrayList<>(stopsById.values()));
    return plan;
  }

  public static final class RoutingProblems
      implements ProblemAdapter<RoutePlan>, BusinessVerifier<RoutePlan> {
    @Override
    public ComponentIdentity identity() {
      return ExampleData.identity("routing-adapter-and-replay");
    }

    @Override
    public ProblemDescriptor describe(String problemId) {
      return new ProblemDescriptor(
          problemId,
          ExampleData.fingerprint(problem(problemId).toString()),
          new ProblemCapabilities(
              false,
              true,
              false,
              false,
              HardSoftScore.class.getName(),
              List.of("hard", "soft"),
              Set.of()));
    }

    @Override
    public RoutePlan loadFresh(String problemId) {
      var input = problem(problemId);
      var initial = new LinkedHashMap<String, List<String>>();
      input.vehicles().forEach(vehicle -> initial.put(vehicle.id(), new ArrayList<>()));
      for (int i = 0; i < input.visits().size(); i++) {
        initial
            .get(input.vehicles().get(i % input.vehicles().size()).id())
            .add(input.visits().get(i).id());
      }
      return reconstruct(input, initial);
    }

    @Override
    public Optional<BusinessVerifier<RoutePlan>> verifier() {
      return Optional.of(this);
    }

    @Override
    public VerificationResult verify(String problemId, RoutePlan plan) {
      var input = problem(problemId);
      var expectedVisits = new HashMap<String, Visit>();
      input.visits().forEach(visit -> expectedVisits.put(visit.id(), visit));
      var stopsById = new HashMap<String, Stop>();
      for (var stop : plan.getStops()) {
        if (stop.getVisit() == null
            || !stop.getVisit().equals(expectedVisits.get(stop.getId()))
            || stopsById.put(stop.getId(), stop) != null) {
          return VerificationResult.invalid("Visit facts or the visit ID population changed.");
        }
      }
      if (!stopsById.keySet().equals(expectedVisits.keySet())) {
        return VerificationResult.invalid("The original visit population is incomplete.");
      }
      var expectedVehicles = new HashMap<String, Vehicle>();
      input.vehicles().forEach(vehicle -> expectedVehicles.put(vehicle.id(), vehicle));
      var routesById = new LinkedHashMap<String, List<String>>();
      for (var route : plan.getRoutes()) {
        if (route.getVehicle() == null
            || !route.getVehicle().equals(expectedVehicles.get(route.getId()))) {
          return VerificationResult.invalid("Fleet facts changed.");
        }
        var ids = new ArrayList<String>();
        for (int index = 0; index < route.getStops().size(); index++) {
          var stop = route.getStops().get(index);
          if (stopsById.get(stop.getId()) != stop
              || stop.getRoute() != route
              || !Integer.valueOf(index).equals(stop.getIndex())) {
            return VerificationResult.invalid(
                "A route reference, owner shadow, or index shadow is inconsistent.");
          }
          ids.add(stop.getId());
        }
        if (routesById.put(route.getId(), List.copyOf(ids)) != null) {
          return VerificationResult.invalid("Duplicate vehicle ID.");
        }
      }
      try {
        var replayed = input.replay(routesById);
        var expected = HardSoftScore.of(replayed.hard(), replayed.soft());
        if (!expected.equals(plan.getScore())) {
          return VerificationResult.invalid(
              "Independent routing score differs: expected " + expected + ".");
        }
        return new VerificationResult(
            VerificationResult.Status.PASSED,
            "Demand, routes and shadows replayed.",
            Map.of("visitCount", Integer.toString(stopsById.size()), "score", expected.toString()));
      } catch (IllegalArgumentException exception) {
        return VerificationResult.invalid(exception.getMessage());
      }
    }
  }

  @PlanningEntity
  public static class Route {
    private Vehicle vehicle;
    private List<Stop> stops = new ArrayList<>();

    public Route() {}

    public Route(Vehicle vehicle) {
      this.vehicle = vehicle;
    }

    @PlanningId
    public String getId() {
      return vehicle.id();
    }

    public Vehicle getVehicle() {
      return vehicle;
    }

    public void setVehicle(Vehicle vehicle) {
      this.vehicle = vehicle;
    }

    @PlanningListVariable(valueRangeProviderRefs = "visits")
    public List<Stop> getStops() {
      return stops;
    }

    public void setStops(List<Stop> stops) {
      this.stops = stops;
    }
  }

  @PlanningEntity
  public static class Stop {
    private Visit visit;
    private Route route;
    private Integer index;

    public Stop() {}

    public Stop(Visit visit) {
      this.visit = visit;
    }

    @PlanningId
    public String getId() {
      return visit.id();
    }

    public Visit getVisit() {
      return visit;
    }

    public void setVisit(Visit visit) {
      this.visit = visit;
    }

    @InverseRelationShadowVariable(sourceVariableName = "stops")
    public Route getRoute() {
      return route;
    }

    public void setRoute(Route route) {
      this.route = route;
    }

    @IndexShadowVariable(sourceVariableName = "stops")
    public Integer getIndex() {
      return index;
    }

    public void setIndex(Integer index) {
      this.index = index;
    }
  }

  @PlanningSolution
  public static class RoutePlan {
    private List<Route> routes;
    private List<Stop> stops;
    private HardSoftScore score;

    public RoutePlan() {}

    @PlanningEntityCollectionProperty
    public List<Route> getRoutes() {
      return routes;
    }

    public void setRoutes(List<Route> routes) {
      this.routes = routes;
    }

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "visits")
    public List<Stop> getStops() {
      return stops;
    }

    public void setStops(List<Stop> stops) {
      this.stops = stops;
    }

    @PlanningScore
    public HardSoftScore getScore() {
      return score;
    }

    public void setScore(HardSoftScore score) {
      this.score = score;
    }
  }

  public static class RouteScoreCalculator
      implements EasyScoreCalculator<RoutePlan, HardSoftScore> {
    @Override
    public HardSoftScore calculateScore(RoutePlan plan) {
      long hard = 0;
      long soft = 0;
      for (var route : plan.getRoutes()) {
        long load = 0;
        Visit previous = null;
        for (var stop : route.getStops()) {
          var next = stop.getVisit();
          soft -=
              previous == null
                  ? Math.abs((long) next.x()) + Math.abs((long) next.y())
                  : Math.abs((long) previous.x() - next.x())
                      + Math.abs((long) previous.y() - next.y());
          load += next.demand();
          previous = next;
        }
        if (previous != null) {
          soft -= Math.abs((long) previous.x()) + Math.abs((long) previous.y());
        }
        hard -= Math.max(0, load - route.getVehicle().capacity());
      }
      return HardSoftScore.of(hard, soft);
    }
  }
}
