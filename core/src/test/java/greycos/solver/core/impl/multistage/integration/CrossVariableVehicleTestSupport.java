package greycos.solver.core.impl.multistage.integration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.random.RandomGenerator;
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
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.api.solver.multistage.BasicVariableReference;
import greycos.solver.core.api.solver.multistage.CrossVariableCustomStage;
import greycos.solver.core.api.solver.multistage.CrossVariableStageProvider;
import greycos.solver.core.api.solver.multistage.ListVariableReference;
import greycos.solver.core.api.solver.multistage.MultistageStageResult;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.move.generic.CrossVariableMultistageMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.LocalSearchType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;

/** Shared planning model, providers and assertions for cross-variable integration tests. */
public final class CrossVariableVehicleTestSupport {
  static final BasicVariableReference<Vehicle, Integer> QUANTITY =
      BasicVariableReference.of(Vehicle.class, "quantity", Integer.class);
  static final ListVariableReference<Vehicle, Customer> CUSTOMERS =
      ListVariableReference.of(Vehicle.class, "customers", Customer.class);

  private CrossVariableVehicleTestSupport() {}

  static SolverConfig config(Class<? extends CrossVariableStageProvider> provider, String workers) {
    return new SolverConfig()
        .withSolutionClass(Plan.class)
        .withEntityClasses(Vehicle.class, Customer.class)
        .withConstraintProviderClass(VehicleConstraints.class)
        .withEnvironmentMode(EnvironmentMode.TRACKED_FULL_ASSERT)
        .withMoveThreadCount(workers)
        .withRandomSeed(7L)
        .withPhases(
            new LocalSearchPhaseConfig()
                .withLocalSearchType(LocalSearchType.HILL_CLIMBING)
                .withMoveSelectorConfig(selector(provider))
                .withTerminationConfig(new TerminationConfig().withStepCountLimit(3)));
  }

  static CrossVariableMultistageMoveSelectorConfig selector(
      Class<? extends CrossVariableStageProvider> provider) {
    return new CrossVariableMultistageMoveSelectorConfig()
        .withVariables(QUANTITY, CUSTOMERS)
        .withStageProviderClass(provider)
        .withSelectionOrder(SelectionOrder.ORIGINAL)
        .withCandidateCountLimit(1);
  }

  static Plan problem() {
    var solution = new Plan();
    solution.id = UUID.randomUUID().toString();
    solution.quantities = IntStream.rangeClosed(2, 16).map(i -> i * 100).boxed().toList();
    solution.customers =
        new ArrayList<>(
            List.of(
                new Customer("a", 300),
                new Customer("b", 400),
                new Customer("c", 500),
                new Customer("source-resident", 200),
                new Customer("target-resident", 200)));
    solution.vehicles =
        new ArrayList<>(
            List.of(
                new Vehicle("source", 10, 1400, solution.customers.subList(0, 4)),
                new Vehicle("target", 1, 200, solution.customers.subList(4, 5))));
    SolutionManager.updateShadowVariables(solution);
    return solution;
  }

  static List<String> assignments(Plan solution) {
    return solution.vehicles.stream()
        .map(
            vehicle ->
                vehicle.id
                    + ":"
                    + vehicle.quantity
                    + ":"
                    + vehicle.customers.stream().map(customer -> customer.id).toList())
        .toList();
  }

  /** Replays every assignment and recomputes the score without Constraint Streams. */
  static HardSoftScore independentScore(Plan solution) {
    long hard = 0;
    long soft = 0;
    for (var vehicle : solution.vehicles) {
      long demand = 0;
      for (var customer : vehicle.customers) demand += customer.demand;
      hard -= Math.abs(vehicle.quantity - demand);
      soft -= (long) vehicle.quantity * vehicle.unitCost;
    }
    return HardSoftScore.of(hard, soft);
  }

  static void verify(Plan solution) {
    verifyAssignments(solution);
    require(
        independentScore(solution).equals(solution.score),
        "Independent score differs from solver score");
  }

  static void verifyAssignments(Plan solution) {
    var assigned = new HashSet<Customer>();
    for (var vehicle : solution.vehicles) {
      require(solution.quantities.contains(vehicle.quantity), "Quantity outside its range");
      for (int index = 0; index < vehicle.customers.size(); index++) {
        var customer = vehicle.customers.get(index);
        require(assigned.add(customer), "Customer assigned twice");
        require(customer.vehicle == vehicle, "Incorrect inverse shadow");
        require(customer.index != null && customer.index == index, "Incorrect index shadow");
        require(
            customer.previous == (index == 0 ? null : vehicle.customers.get(index - 1)),
            "Incorrect previous shadow");
        require(
            customer.next
                == (index + 1 == vehicle.customers.size()
                    ? null
                    : vehicle.customers.get(index + 1)),
            "Incorrect next shadow");
      }
    }
    require(
        assigned.size() == solution.customers.size() && assigned.containsAll(solution.customers),
        "Every customer must be assigned exactly once");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  @PlanningSolution
  public static class Plan {
    public String id;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "quantityRange")
    public List<Integer> quantities;

    @PlanningEntityCollectionProperty public List<Vehicle> vehicles;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "customerRange")
    public List<Customer> customers;

    @PlanningScore public HardSoftScore score;
  }

  @PlanningEntity
  public static class Vehicle {
    @PlanningId public String id;
    public int unitCost;

    @PlanningVariable(valueRangeProviderRefs = "quantityRange")
    public Integer quantity;

    @PlanningListVariable(valueRangeProviderRefs = "customerRange")
    public List<Customer> customers = new ArrayList<>();

    public Vehicle() {}

    public Vehicle(String id, int unitCost, Integer quantity, List<Customer> customers) {
      this.id = id;
      this.unitCost = unitCost;
      this.quantity = quantity;
      this.customers = new ArrayList<>(customers);
    }
  }

  @PlanningEntity
  public static class Customer {
    @PlanningId public String id;
    public int demand;

    @InverseRelationShadowVariable(sourceVariableName = "customers")
    public Vehicle vehicle;

    @IndexShadowVariable(sourceVariableName = "customers")
    public Integer index;

    @PreviousElementShadowVariable(sourceVariableName = "customers")
    public Customer previous;

    @NextElementShadowVariable(sourceVariableName = "customers")
    public Customer next;

    public Customer() {}

    public Customer(String id, int demand) {
      this.id = id;
      this.demand = demand;
    }
  }

  public static class VehicleConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(Vehicle.class)
            .penalize(
                HardSoftScore.ONE_HARD,
                vehicle ->
                    Math.abs(
                        vehicle.quantity
                            - vehicle.customers.stream()
                                .mapToLong(customer -> customer.demand)
                                .sum()))
            .asConstraint("Quantity equals customer demand"),
        factory
            .forEach(Vehicle.class)
            .penalize(HardSoftScore.ONE_SOFT, vehicle -> (long) vehicle.quantity * vehicle.unitCost)
            .asConstraint("Vehicle cost")
      };
    }
  }

  public static class OrderedStages implements CrossVariableStageProvider<Plan, HardSoftScore> {
    protected Plan solution;

    @Override
    public void initialize(Plan solution) {
      this.solution = solution;
    }

    @Override
    public long getCandidateCount() {
      return solution.vehicles.getFirst().customers.size() > 1 ? 1 : 0;
    }

    @Override
    public List<CrossVariableCustomStage<Plan, HardSoftScore>> createStages(
        long candidateIndex, RandomGenerator random) {
      var source = solution.vehicles.getFirst();
      var target = solution.vehicles.getLast();
      var customer = source.customers.getFirst();
      return List.of(
          evaluator ->
              MultistageStageResult.apply(
                  evaluator.basic(QUANTITY).assign(source, source.quantity - customer.demand)),
          evaluator ->
              MultistageStageResult.apply(
                  evaluator.basic(QUANTITY).assign(target, target.quantity + customer.demand)),
          evaluator ->
              MultistageStageResult.apply(
                  evaluator.list(CUSTOMERS).place(customer, target, target.customers.size())));
    }

    @Override
    public void phaseEnded() {
      solution = null;
    }
  }
}
