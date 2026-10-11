package greycos.solver.core.testcotwin.multiplelist.routeloading;

import static greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.index;
import static greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.requireSameIds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.CargoData;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.CargoDecision;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.CompartmentData;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.Input;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.Metrics;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.Plan;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.VehicleData;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.VehiclePlan;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.VisitData;

/** Full business replay from original facts and IDs, independent of constraints and shadows. */
public final class RouteLoadingReplay {

  private RouteLoadingReplay() {}

  public static Metrics replay(Input input, Plan plan) {
    var vehicles = index(input.vehicles(), VehicleData::id);
    var compartments = index(input.compartments(), CompartmentData::id);
    var visits = index(input.visits(), VisitData::id);
    var cargo = index(input.cargo(), CargoData::id);
    var vehiclePlans = index(plan.vehicles(), VehiclePlan::vehicleId);
    var decisions = index(plan.cargo(), CargoDecision::cargoId);
    requireSameIds(vehiclePlans.keySet(), vehicles.keySet(), "Vehicle");
    requireSameIds(decisions.keySet(), cargo.keySet(), "Cargo decision");
    Map<String, String> visitOwner = new HashMap<>();
    Map<String, String> cargoOwner = new HashMap<>();
    Map<String, Integer> visitIndex = new HashMap<>();
    long distance = 0;
    for (var route : plan.vehicles()) {
      int x = 0;
      int y = 0;
      for (int i = 0; i < route.visitIds().size(); i++) {
        var id = route.visitIds().get(i);
        if (!visits.containsKey(id) || visitOwner.putIfAbsent(id, route.vehicleId()) != null) {
          throw new IllegalArgumentException("Unknown or duplicate routed visit (" + id + ").");
        }
        visitIndex.put(id, i);
        var visit = visits.get(id);
        distance += Math.abs((long) x - visit.x()) + Math.abs((long) y - visit.y());
        x = visit.x();
        y = visit.y();
      }
      distance += Math.abs((long) x) + Math.abs((long) y);
      for (var id : route.cargoIds()) {
        if (!cargo.containsKey(id) || cargoOwner.putIfAbsent(id, route.vehicleId()) != null) {
          throw new IllegalArgumentException("Unknown or duplicate loaded cargo (" + id + ").");
        }
      }
    }
    requireSameIds(visitOwner.keySet(), visits.keySet(), "Routed visit");
    requireSameIds(cargoOwner.keySet(), cargo.keySet(), "Loaded cargo");
    var violations = new ArrayList<String>();
    Map<String, Long> delivered = new HashMap<>();
    Map<String, Long> departureLoad = new HashMap<>();
    long hard = 0;
    for (var decision : plan.cargo()) {
      if (!compartments.containsKey(decision.compartmentId())
          || decision.quantity() < 0
          || decision.quantity() > input.maximumQuantity()) {
        throw new IllegalArgumentException("Invalid cargo decision (" + decision + ").");
      }
      var allocation = cargo.get(decision.cargoId());
      delivered.merge(allocation.visitId(), (long) decision.quantity(), Long::sum);
      if (decision.quantity() == 0) {
        continue;
      }
      var compartment = compartments.get(decision.compartmentId());
      var vehicleId = cargoOwner.get(decision.cargoId());
      if (!vehicleId.equals(visitOwner.get(allocation.visitId()))) {
        hard++;
        violations.add("Cargo " + allocation.id() + " is not on its visit's vehicle.");
      }
      if (!vehicleId.equals(compartment.vehicleId())) {
        hard++;
        violations.add("Cargo " + allocation.id() + " uses another vehicle's compartment.");
      }
      if (!compartment.product().equals(visits.get(allocation.visitId()).product())) {
        hard++;
        violations.add("Cargo " + allocation.id() + " has incompatible compartment product.");
      }
      departureLoad.merge(compartment.id(), (long) decision.quantity(), Long::sum);
    }
    for (var visit : input.visits()) {
      long difference = Math.abs(delivered.getOrDefault(visit.id(), 0L) - visit.demand());
      hard += difference;
      if (difference > 0) {
        violations.add("Demand mismatch at " + visit.id() + ": " + difference + ".");
      }
    }
    for (var compartment : input.compartments()) {
      long excess =
          Math.max(0, departureLoad.getOrDefault(compartment.id(), 0L) - compartment.capacity());
      hard += excess;
      if (excess > 0) {
        violations.add("Departure capacity exceeded at " + compartment.id() + ": " + excess + ".");
      }
    }
    long preference = 0;
    long access = 0;
    for (var route : plan.vehicles()) {
      for (int i = 0; i < route.cargoIds().size(); i++) {
        var first = cargo.get(route.cargoIds().get(i));
        var firstDecision = decisions.get(first.id());
        if (firstDecision.quantity() == 0) {
          continue;
        }
        for (int j = i + 1; j < route.cargoIds().size(); j++) {
          var second = cargo.get(route.cargoIds().get(j));
          var secondDecision = decisions.get(second.id());
          if (secondDecision.quantity() == 0) {
            continue;
          }
          if (first.loadingPriority() > second.loadingPriority()) {
            preference++;
          }
          if (firstDecision.compartmentId().equals(secondDecision.compartmentId())
              && visitOwner.get(first.visitId()).equals(route.vehicleId())
              && visitOwner.get(second.visitId()).equals(route.vehicleId())
              && visitIndex.get(first.visitId()) < visitIndex.get(second.visitId())) {
            access += 3L * secondDecision.quantity();
          }
        }
      }
      // Delivery-only loads decrease at each route event; the departure bound is the tightest.
      if (hard == 0) {
        var remaining = new HashMap<>(departureLoad);
        for (var visitId : route.visitIds()) {
          for (var id : route.cargoIds()) {
            var decision = decisions.get(id);
            if (cargo.get(id).visitId().equals(visitId)) {
              remaining.merge(decision.compartmentId(), -(long) decision.quantity(), Long::sum);
            }
          }
          for (var compartment : input.compartments()) {
            if (compartment.vehicleId().equals(route.vehicleId())) {
              long load = remaining.getOrDefault(compartment.id(), 0L);
              if (load < 0 || load > compartment.capacity()) {
                throw new IllegalStateException("Invalid replay load at " + visitId + ": " + load);
              }
            }
          }
        }
        for (var compartment : input.compartments()) {
          if (compartment.vehicleId().equals(route.vehicleId())
              && remaining.getOrDefault(compartment.id(), 0L) != 0) {
            throw new IllegalStateException("Undelivered cargo in " + compartment.id());
          }
        }
      }
    }
    return new Metrics(hard, distance, preference, access, violations);
  }
}
