package greycos.solver.core.impl.heuristic.thread;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.CascadingUpdateShadowVariable;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.list.ListSwapMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;

/**
 * Initialized, deterministic workloads shared by correctness tests and the standalone benchmark.
 */
public final class MoveThreadingWorkload {

  private MoveThreadingWorkload() {}

  public interface Workload<Solution_> {
    Solution_ createProblem(int size);

    SolverConfig solverConfig(
        String threads,
        long seed,
        int acceptedCount,
        TerminationConfig termination,
        EnvironmentMode environmentMode);

    SimpleScore score(Solution_ solution);

    /**
     * Validates identities and shadows, and computes the score without using Constraint Streams.
     */
    SimpleScore recompute(Solution_ solution);

    /** Stable assignment representation, using business IDs rather than object identity. */
    String state(Solution_ solution);
  }

  public static Workload<?> named(String name) {
    return switch (name) {
      case "basic" -> new BasicWorkload();
      case "list" -> new ListWorkload();
      default -> throw new IllegalArgumentException("Unknown workload: " + name);
    };
  }

  private static SolverConfig configure(
      Class<?> solutionClass,
      Class<? extends ConstraintProvider> providerClass,
      Class<?>[] entityClasses,
      boolean list,
      String threads,
      long seed,
      int acceptedCount,
      TerminationConfig termination,
      EnvironmentMode environmentMode) {
    var moves =
        list
            ? new UnionMoveSelectorConfig()
                .withMoveSelectors(
                    new ListChangeMoveSelectorConfig(), new ListSwapMoveSelectorConfig())
            : new UnionMoveSelectorConfig()
                .withMoveSelectors(new ChangeMoveSelectorConfig(), new SwapMoveSelectorConfig());
    return new SolverConfig()
        .withSolutionClass(solutionClass)
        .withEntityClasses(entityClasses)
        .withConstraintProviderClass(providerClass)
        .withEnvironmentMode(environmentMode)
        .withRandomSeed(seed)
        .withMoveThreadCount(threads)
        .withMoveThreadBufferSize(10)
        .withMonitoringConfig(new MonitoringConfig().withSolverMetricList(List.of()))
        .withPhases(
            new LocalSearchPhaseConfig()
                .withMoveSelectorConfig(moves)
                .withAcceptorConfig(new LocalSearchAcceptorConfig().withLateAcceptanceSize(64))
                .withForagerConfig(
                    new LocalSearchForagerConfig().withAcceptedCountLimit(acceptedCount))
                .withTerminationConfig(termination));
  }

  public static String fingerprint(String state) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }

  public record Machine(@PlanningId int id) {}

  @PlanningEntity
  public static class Job {
    @PlanningId private int id;
    private int units;
    private int preferredMachine;
    @PlanningPin private boolean pinned;

    @PlanningVariable(valueRangeProviderRefs = "machines")
    private Machine machine;

    public Job() {}

    Job(int id, int units, int preferredMachine, Machine machine) {
      this.id = id;
      this.units = units;
      this.preferredMachine = preferredMachine;
      this.machine = machine;
    }

    public int getId() {
      return id;
    }

    public int getUnits() {
      return units;
    }

    public int getPreferredMachine() {
      return preferredMachine;
    }

    public boolean isPinned() {
      return pinned;
    }

    public void setPinned(boolean pinned) {
      this.pinned = pinned;
    }

    public Machine getMachine() {
      return machine;
    }

    public void setMachine(Machine machine) {
      this.machine = machine;
    }

    @Override
    public String toString() {
      return "job-" + id;
    }
  }

  @PlanningSolution
  public static class BasicSolution {
    @ValueRangeProvider(id = "machines")
    @ProblemFactCollectionProperty
    private List<Machine> machines;

    @PlanningEntityCollectionProperty private List<Job> jobs;
    @PlanningScore private SimpleScore score;

    public BasicSolution() {}

    public List<Machine> getMachines() {
      return machines;
    }

    public void setMachines(List<Machine> machines) {
      this.machines = machines;
    }

    public List<Job> getJobs() {
      return jobs;
    }

    public void setJobs(List<Job> jobs) {
      this.jobs = jobs;
    }

    public SimpleScore getScore() {
      return score;
    }

    public void setScore(SimpleScore score) {
      this.score = score;
    }
  }

  public static class BasicConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(Job.class)
            .penalize(
                SimpleScore.ONE,
                job -> (long) Math.abs(job.machine.id() - job.preferredMachine) * job.units)
            .asConstraint("Assignment preference"),
        factory
            .forEach(Job.class)
            .groupBy(Job::getMachine, ConstraintCollectors.sum(Job::getUnits))
            .penalize(SimpleScore.ONE, (machine, load) -> load * load)
            .asConstraint("Squared machine load")
      };
    }
  }

  public static final class BasicWorkload implements Workload<BasicSolution> {
    @Override
    public BasicSolution createProblem(int size) {
      require(size >= 4, "Workload size must be at least 4.");
      var solution = new BasicSolution();
      int machineCount = Math.max(4, size / 20);
      solution.machines = new ArrayList<>();
      solution.jobs = new ArrayList<>();
      for (int i = 0; i < machineCount; i++) {
        solution.machines.add(new Machine(i));
      }
      for (int i = 0; i < size; i++) {
        solution.jobs.add(
            new Job(
                i,
                1 + (i * 17 % 19),
                (i * 7 + 3) % machineCount,
                solution.machines.get(i % machineCount)));
      }
      solution.score = recompute(solution);
      return solution;
    }

    @Override
    public SolverConfig solverConfig(
        String threads,
        long seed,
        int acceptedCount,
        TerminationConfig termination,
        EnvironmentMode environmentMode) {
      return configure(
          BasicSolution.class,
          BasicConstraints.class,
          new Class<?>[] {Job.class},
          false,
          threads,
          seed,
          acceptedCount,
          termination,
          environmentMode);
    }

    @Override
    public SimpleScore score(BasicSolution solution) {
      return solution.score;
    }

    @Override
    public SimpleScore recompute(BasicSolution solution) {
      long[] loads = new long[solution.machines.size()];
      Set<Integer> seen = new HashSet<>();
      long penalty = 0;
      for (Job job : solution.jobs) {
        require(seen.add(job.id), "Duplicate job ID: " + job.id);
        require(
            job.machine != null
                && job.machine.id() >= 0
                && job.machine.id() < loads.length
                && solution.machines.get(job.machine.id()) == job.machine,
            "Job assigned outside the canonical machine range: " + job.id);
        loads[job.machine.id()] += job.units;
        penalty += Math.abs((long) job.machine.id() - job.preferredMachine) * job.units;
      }
      for (long load : loads) {
        penalty += load * load;
      }
      return SimpleScore.of(-penalty);
    }

    @Override
    public String state(BasicSolution solution) {
      var out = new StringBuilder();
      for (Job job : solution.jobs) {
        out.append(job.id)
            .append('=')
            .append(job.machine == null ? "unassigned" : job.machine.id())
            .append(';');
      }
      return out.toString();
    }
  }

  @PlanningEntity
  public static class Route {
    @PlanningId private int id;

    @PlanningListVariable(valueRangeProviderRefs = "visits")
    private List<Visit> visits = new ArrayList<>();

    public Route() {}

    Route(int id) {
      this.id = id;
    }

    public int getId() {
      return id;
    }

    public List<Visit> getVisits() {
      return visits;
    }

    public void setVisits(List<Visit> visits) {
      this.visits = visits;
    }

    @Override
    public String toString() {
      return "route-" + id;
    }
  }

  @PlanningEntity
  public static class Visit {
    @PlanningId private int id;
    private int x;
    private int y;
    private int service;
    private long due;

    @InverseRelationShadowVariable(sourceVariableName = "visits")
    private Route route;

    @IndexShadowVariable(sourceVariableName = "visits")
    private Integer index;

    @PreviousElementShadowVariable(sourceVariableName = "visits")
    private Visit previous;

    @NextElementShadowVariable(sourceVariableName = "visits")
    private Visit next;

    @CascadingUpdateShadowVariable(targetMethodName = "updateCompletion")
    private Long completion;

    public Visit() {}

    Visit(int id) {
      this.id = id;
      x = (id * 37 + 11) % 101;
      y = (id * 53 + 7) % 103;
      service = 1 + id % 7;
      due = 100 + id % 300;
    }

    public int getId() {
      return id;
    }

    public Route getRoute() {
      return route;
    }

    public void setRoute(Route route) {
      this.route = route;
    }

    public Integer getIndex() {
      return index;
    }

    public void setIndex(Integer index) {
      this.index = index;
    }

    public Visit getPrevious() {
      return previous;
    }

    public void setPrevious(Visit previous) {
      this.previous = previous;
    }

    public Visit getNext() {
      return next;
    }

    public void setNext(Visit next) {
      this.next = next;
    }

    public Long getCompletion() {
      return completion;
    }

    public void setCompletion(Long completion) {
      this.completion = completion;
    }

    public void updateCompletion() {
      if (route == null) {
        completion = null;
      } else if (previous == null) {
        completion = (long) Math.abs(x - route.id * 3) + y + service;
      } else {
        completion =
            previous.completion + Math.abs(x - previous.x) + Math.abs(y - previous.y) + service;
      }
    }

    @Override
    public String toString() {
      return "visit-" + id;
    }
  }

  @PlanningSolution
  public static class ListSolution {
    @PlanningEntityCollectionProperty private List<Route> routes;

    @ValueRangeProvider(id = "visits")
    @PlanningEntityCollectionProperty
    private List<Visit> visits;

    @PlanningScore private SimpleScore score;

    public ListSolution() {}

    public List<Route> getRoutes() {
      return routes;
    }

    public void setRoutes(List<Route> routes) {
      this.routes = routes;
    }

    public List<Visit> getVisits() {
      return visits;
    }

    public void setVisits(List<Visit> visits) {
      this.visits = visits;
    }

    public SimpleScore getScore() {
      return score;
    }

    public void setScore(SimpleScore score) {
      this.score = score;
    }
  }

  public static class ListConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(Visit.class)
            .penalize(SimpleScore.ONE, visit -> visit.completion)
            .asConstraint("Total completion time"),
        factory
            .forEach(Visit.class)
            .filter(visit -> visit.completion > visit.due)
            .penalize(
                SimpleScore.ONE,
                visit -> {
                  long late = visit.completion - visit.due;
                  return late * late;
                })
            .asConstraint("Squared lateness")
      };
    }
  }

  public static final class ListWorkload implements Workload<ListSolution> {
    @Override
    public ListSolution createProblem(int size) {
      require(size >= 4, "Workload size must be at least 4.");
      var solution = new ListSolution();
      solution.routes = new ArrayList<>();
      solution.visits = new ArrayList<>();
      int routeCount = Math.max(2, size / 50);
      for (int i = 0; i < routeCount; i++) {
        solution.routes.add(new Route(i));
      }
      for (int i = 0; i < size; i++) {
        var visit = new Visit(i);
        solution.visits.add(visit);
        solution.routes.get(i % routeCount).visits.add(visit);
      }
      SolutionManager.updateShadowVariables(solution);
      solution.score = recompute(solution);
      return solution;
    }

    @Override
    public SolverConfig solverConfig(
        String threads,
        long seed,
        int acceptedCount,
        TerminationConfig termination,
        EnvironmentMode environmentMode) {
      return configure(
          ListSolution.class,
          ListConstraints.class,
          new Class<?>[] {Route.class, Visit.class},
          true,
          threads,
          seed,
          acceptedCount,
          termination,
          environmentMode);
    }

    @Override
    public SimpleScore score(ListSolution solution) {
      return solution.score;
    }

    @Override
    public SimpleScore recompute(ListSolution solution) {
      Set<Integer> seen = new HashSet<>();
      Set<Integer> routeIds = new HashSet<>();
      long penalty = 0;
      for (Route route : solution.routes) {
        require(routeIds.add(route.id), "Duplicate route ID: " + route.id);
        long time = 0;
        int x = route.id * 3;
        int y = 0;
        Visit previous = null;
        for (int index = 0; index < route.visits.size(); index++) {
          Visit visit = route.visits.get(index);
          require(
              visit.id >= 0
                  && visit.id < solution.visits.size()
                  && solution.visits.get(visit.id) == visit
                  && seen.add(visit.id),
              "Duplicate or noncanonical visit: " + visit.id);
          require(
              visit.route == route
                  && Integer.valueOf(index).equals(visit.index)
                  && visit.previous == previous
                  && visit.next
                      == (index + 1 < route.visits.size() ? route.visits.get(index + 1) : null),
              "Inconsistent list shadows for visit: " + visit.id);
          time += Math.abs((long) x - visit.x) + Math.abs((long) y - visit.y) + visit.service;
          require(
              Long.valueOf(time).equals(visit.completion),
              "Inconsistent cascading completion for visit: " + visit.id);
          long late = Math.max(0L, time - visit.due);
          penalty += time + late * late;
          previous = visit;
          x = visit.x;
          y = visit.y;
        }
      }
      require(seen.size() == solution.visits.size(), "Missing assigned visits.");
      return SimpleScore.of(-penalty);
    }

    @Override
    public String state(ListSolution solution) {
      var out = new StringBuilder();
      for (Route route : solution.routes) {
        out.append(route.id).append('=');
        for (Visit visit : route.visits) {
          out.append(visit.id).append('@').append(visit.completion).append(',');
        }
        out.append(';');
      }
      return out.toString();
    }
  }
}
