package greycos.solver.core.testcotwin.multiplelist.routeloading;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Immutable business input and decisions; no solver annotations or mutable solver objects. */
public final class RouteLoadingDomain {

  private RouteLoadingDomain() {}

  public record VehicleData(String id) {}

  public record CompartmentData(String id, String vehicleId, String product, int capacity) {}

  public record VisitData(String id, int x, int y, String product, int demand) {}

  public record CargoData(String id, String visitId, int loadingPriority) {}

  public record Input(
      List<VehicleData> vehicles,
      List<CompartmentData> compartments,
      List<VisitData> visits,
      List<CargoData> cargo,
      int maximumQuantity) {
    public Input {
      vehicles = List.copyOf(vehicles);
      compartments = List.copyOf(compartments);
      visits = List.copyOf(visits);
      cargo = List.copyOf(cargo);
      var vehicleIds = index(vehicles, VehicleData::id);
      var visitIds = index(visits, VisitData::id);
      index(compartments, CompartmentData::id);
      index(cargo, CargoData::id);
      if (vehicles.isEmpty() || compartments.isEmpty() || maximumQuantity < 1) {
        throw new IllegalArgumentException(
            "Vehicles, compartments and positive maximumQuantity are required.");
      }
      for (var compartment : compartments) {
        if (!vehicleIds.containsKey(compartment.vehicleId())
            || compartment.capacity() < 1
            || compartment.product() == null
            || compartment.product().isBlank()) {
          throw new IllegalArgumentException("Invalid compartment (" + compartment + ").");
        }
      }
      for (var visit : visits) {
        if (visit.demand() < 1 || visit.product() == null || visit.product().isBlank()) {
          throw new IllegalArgumentException("Invalid visit (" + visit + ").");
        }
        if (cargo.stream().noneMatch(c -> visit.id().equals(c.visitId()))) {
          throw new IllegalArgumentException("Visit (" + visit.id() + ") has no cargo allocation.");
        }
      }
      for (var allocation : cargo) {
        if (!visitIds.containsKey(allocation.visitId())) {
          throw new IllegalArgumentException(
              "Cargo (" + allocation.id() + ") has unknown visit (" + allocation.visitId() + ").");
        }
      }
    }
  }

  public record VehiclePlan(String vehicleId, List<String> visitIds, List<String> cargoIds) {
    public VehiclePlan {
      visitIds = List.copyOf(visitIds);
      cargoIds = List.copyOf(cargoIds);
    }
  }

  public record CargoDecision(String cargoId, String compartmentId, int quantity) {}

  public record Plan(List<VehiclePlan> vehicles, List<CargoDecision> cargo) {
    public Plan {
      vehicles = List.copyOf(vehicles);
      cargo = List.copyOf(cargo);
    }
  }

  public record Metrics(
      long hardPenalty,
      long distance,
      long loadingPreferencePenalty,
      long accessPenalty,
      List<String> violations) {
    public Metrics {
      violations = List.copyOf(violations);
    }

    public boolean feasible() {
      return hardPenalty == 0;
    }

    public long softPenalty() {
      return distance + loadingPreferencePenalty + accessPenalty;
    }
  }

  public static <T> Map<String, T> index(List<T> values, Function<T, String> id) {
    var seen = new HashSet<String>();
    for (var value : values) {
      var key = id.apply(value);
      if (key == null || key.isBlank() || !seen.add(key)) {
        throw new IllegalArgumentException("Missing or duplicate ID (" + key + ").");
      }
    }
    return values.stream().collect(Collectors.toUnmodifiableMap(id, Function.identity()));
  }

  public static void requireSameIds(Set<String> actual, Set<String> expected, String description) {
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException(
          description + " IDs (" + actual + ") differ from input (" + expected + ").");
    }
  }
}
