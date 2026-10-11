package greycos.solver.core.testcotwin.multiplelist.routeloading;

import java.util.List;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.CargoData;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.CompartmentData;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.Input;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.Metrics;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.VehicleData;
import greycos.solver.core.testcotwin.multiplelist.routeloading.RouteLoadingDomain.VisitData;

/** Run from the core test classpath; see README.md in this package. */
public final class RouteLoadingExample {

  private RouteLoadingExample() {}

  public static Input demoInput() {
    return new Input(
        List.of(new VehicleData("van-a"), new VehicleData("van-b")),
        List.of(
            new CompartmentData("a-cold", "van-a", "cold", 3),
            new CompartmentData("a-dry", "van-a", "dry", 3),
            new CompartmentData("b-cold", "van-b", "cold", 3),
            new CompartmentData("b-dry", "van-b", "dry", 3)),
        List.of(
            new VisitData("north", 1, 4, "cold", 2),
            new VisitData("east", 4, 1, "cold", 2),
            new VisitData("west", -3, 1, "dry", 2)),
        List.of(
            new CargoData("north-1", "north", 0),
            new CargoData("north-2", "north", 4),
            new CargoData("east-1", "east", 1),
            new CargoData("east-2", "east", 5),
            new CargoData("west-1", "west", 2),
            new CargoData("west-2", "west", 3)),
        2);
  }

  public static SolverConfig solverConfig(long seed, int stepCount) {
    if (stepCount < 1) {
      throw new IllegalArgumentException("stepCount (" + stepCount + ") must be positive.");
    }
    return new SolverConfig()
        .withSolutionClass(RouteLoadingSolution.class)
        .withEntityClasses(
            RouteLoadingSolution.Vehicle.class,
            RouteLoadingSolution.Visit.class,
            RouteLoadingSolution.Cargo.class)
        .withConstraintProviderClass(RouteLoadingConstraintProvider.class)
        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
        .withRandomSeed(seed)
        .withTerminationConfig(new TerminationConfig().withStepCountLimit(stepCount));
  }

  public static HardSoftScore score(Metrics metrics) {
    return HardSoftScore.of(-metrics.hardPenalty(), -metrics.softPenalty());
  }

  public static void main(String[] args) {
    if (args.length > 2) {
      throw new IllegalArgumentException("Usage: RouteLoadingExample [seed] [stepCount]");
    }
    long seed = args.length > 0 ? Long.parseLong(args[0]) : 0L;
    int steps = args.length > 1 ? Integer.parseInt(args[1]) : 2000;
    var input = demoInput();
    var solver =
        SolverFactory.<RouteLoadingSolution>create(solverConfig(seed, steps)).buildSolver();
    var solution = solver.solve(RouteLoadingSolution.fromInput(input));
    var plan = solution.toPlan();
    var metrics = RouteLoadingReplay.replay(input, plan);
    if (!score(metrics).equals(solution.score)) {
      throw new IllegalStateException(
          "Independent replay score ("
              + score(metrics)
              + ") differs from solver score ("
              + solution.score
              + ").");
    }
    System.out.println((metrics.feasible() ? "FEASIBLE" : "DRAFT") + " " + solution.score);
    for (var vehicle : plan.vehicles()) {
      System.out.println(
          vehicle.vehicleId() + " route=" + vehicle.visitIds() + " loading=" + vehicle.cargoIds());
    }
    for (var allocation : plan.cargo()) {
      System.out.println(allocation);
    }
    System.out.println("Independent replay: " + metrics);
  }
}
