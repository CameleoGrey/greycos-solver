package greycos.solver.core.testcotwin.multiplelist.routeloading;

import static greycos.solver.core.api.score.stream.ConstraintCollectors.sum;
import static greycos.solver.core.api.score.stream.Joiners.equal;
import static greycos.solver.core.api.score.stream.Joiners.lessThan;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingSolution.Cargo;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingSolution.Visit;

public class RouteLoadingConstraintProvider implements ConstraintProvider {

  @Override
  public Constraint[] defineConstraints(ConstraintFactory factory) {
    var activeCargo = factory.forEach(Cargo.class).filter(c -> c.quantity > 0);
    var orderedPairs = activeCargo.join(activeCargo, equal(c -> c.vehicle), lessThan(c -> c.index));
    return new Constraint[] {
      factory
          .forEachIncludingUnassigned(Visit.class)
          .join(factory.forEachIncludingUnassigned(Cargo.class), equal(v -> v, c -> c.visit))
          .groupBy(
              (visit, cargo) -> visit,
              sum((visit, cargo) -> cargo.quantity == null ? 0L : cargo.quantity.longValue()))
          .penalize(
              HardSoftScore.ONE_HARD, (visit, quantity) -> Math.abs(quantity - visit.fact.demand()))
          .asConstraint("Demand conservation"),
      activeCargo
          .join(Visit.class, equal(c -> c.visit, v -> v))
          .filter((cargo, visit) -> cargo.vehicle != visit.vehicle)
          .penalize(HardSoftScore.ONE_HARD)
          .asConstraint("Cargo follows visit vehicle"),
      activeCargo
          .filter(c -> !c.vehicle.id.equals(c.compartment.vehicleId()))
          .penalize(HardSoftScore.ONE_HARD)
          .asConstraint("Compartment belongs to vehicle"),
      activeCargo
          .filter(c -> !c.visit.fact.product().equals(c.compartment.product()))
          .penalize(HardSoftScore.ONE_HARD)
          .asConstraint("Product compatibility"),
      activeCargo
          .groupBy(c -> c.compartment, sum(c -> c.quantity.longValue()))
          .filter((compartment, quantity) -> quantity > compartment.capacity())
          .penalize(
              HardSoftScore.ONE_HARD, (compartment, quantity) -> quantity - compartment.capacity())
          .asConstraint("Departure capacity"),
      factory
          .forEach(Visit.class)
          .penalize(
              HardSoftScore.ONE_SOFT,
              visit -> {
                int x = visit.previous == null ? 0 : visit.previous.fact.x();
                int y = visit.previous == null ? 0 : visit.previous.fact.y();
                return Math.abs((long) visit.fact.x() - x) + Math.abs((long) visit.fact.y() - y);
              })
          .asConstraint("Travel to visit"),
      factory
          .forEach(Visit.class)
          .filter(visit -> visit.next == null)
          .penalize(
              HardSoftScore.ONE_SOFT,
              visit -> Math.abs((long) visit.fact.x()) + Math.abs((long) visit.fact.y()))
          .asConstraint("Return to depot"),
      orderedPairs
          .filter((first, second) -> first.fact.loadingPriority() > second.fact.loadingPriority())
          .penalize(HardSoftScore.ONE_SOFT)
          .asConstraint("Loading preference"),
      orderedPairs
          .filter((first, second) -> first.compartment.equals(second.compartment))
          .join(Visit.class, equal((first, second) -> first.visit, visit -> visit))
          .join(Visit.class, equal((first, second, firstVisit) -> second.visit, visit -> visit))
          .filter(
              (first, second, firstVisit, secondVisit) ->
                  firstVisit.vehicle == first.vehicle
                      && secondVisit.vehicle == first.vehicle
                      && firstVisit.index < secondVisit.index)
          .penalize(
              HardSoftScore.ONE_SOFT,
              (first, second, firstVisit, secondVisit) -> 3L * second.quantity)
          .asConstraint("Unload obstruction")
    };
  }
}
