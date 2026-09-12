package greycos.solver.core.impl.alns;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.config.alns.AlnsAcceptanceType;
import greycos.solver.core.config.alns.AlnsDestroyOperatorConfig;
import greycos.solver.core.config.alns.AlnsDestroyOperatorType;
import greycos.solver.core.config.alns.AlnsPhaseConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorConfig;
import greycos.solver.core.config.alns.AlnsRepairOperatorType;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicConstraints;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.BasicWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Job;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.ListConstraints;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.ListSolution;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.ListWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Machine;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Route;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Visit;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Workload;

/** Incremental basic, cascading-list and interacting mixed ALNS workloads. */
public final class AlnsMoveThreadingWorkload {
  private AlnsMoveThreadingWorkload() {}

  public static Workload<?> named(String name) {
    return name.equals("mixed") ? new MixedWorkload() : MoveThreadingWorkload.named(name);
  }

  public static AlnsPhaseConfig phase(
      int destroyedCount, AlnsRepairOperatorType repair, TerminationConfig termination) {
    return new AlnsPhaseConfig()
        .withAcceptanceType(AlnsAcceptanceType.LATE_ACCEPTANCE)
        .withLateAcceptanceSize(64)
        .withDestroyOperators(
            new AlnsDestroyOperatorConfig()
                .withId("random")
                .withType(AlnsDestroyOperatorType.RANDOM)
                .withMinimumDestroyedCount(destroyedCount)
                .withMaximumDestroyedCount(destroyedCount))
        .withRepairOperators(new AlnsRepairOperatorConfig().withId("repair").withType(repair))
        .withTerminationConfig(termination);
  }

  public static <S> SolverConfig config(
      Workload<S> workload,
      String threads,
      long seed,
      int destroyedCount,
      AlnsRepairOperatorType repair,
      TerminationConfig termination,
      EnvironmentMode environment) {
    return workload
        .solverConfig(threads, seed, 1, termination, environment)
        .withPhases(phase(destroyedCount, repair, termination));
  }

  @PlanningSolution
  public static class MixedSolution {
    @ValueRangeProvider(id = "machines")
    @ProblemFactCollectionProperty
    private List<Machine> machines;

    @PlanningEntityCollectionProperty private List<Job> jobs;
    @PlanningEntityCollectionProperty private List<Route> routes;

    @ValueRangeProvider(id = "visits")
    @PlanningEntityCollectionProperty
    private List<Visit> visits;

    @PlanningScore private SimpleScore score;

    public MixedSolution() {}

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

  public static class MixedConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      var constraints = new ArrayList<Constraint>();
      constraints.addAll(List.of(new BasicConstraints().defineConstraints(factory)));
      constraints.addAll(List.of(new ListConstraints().defineConstraints(factory)));
      constraints.add(
          factory
              .forEach(Job.class)
              .join(
                  Visit.class,
                  Joiners.equal(job -> job.getMachine().id(), visit -> visit.getRoute().getId()))
              .penalize(
                  SimpleScore.ONE, (job, visit) -> (long) job.getUnits() * (visit.getIndex() + 1))
              .asConstraint("Machine and route interaction"));
      return constraints.toArray(Constraint[]::new);
    }
  }

  public static final class MixedWorkload implements Workload<MixedSolution> {
    private final BasicWorkload basic = new BasicWorkload();
    private final ListWorkload list = new ListWorkload();

    @Override
    public MixedSolution createProblem(int size) {
      var basicProblem = basic.createProblem(size);
      var listProblem = list.createProblem(size);
      var solution = new MixedSolution();
      solution.machines = basicProblem.getMachines();
      solution.jobs = basicProblem.getJobs();
      solution.routes = listProblem.getRoutes();
      solution.visits = listProblem.getVisits();
      solution.score = recompute(solution);
      return solution;
    }

    @Override
    public SolverConfig solverConfig(
        String threads,
        long seed,
        int acceptedCount,
        TerminationConfig termination,
        EnvironmentMode environment) {
      return new SolverConfig()
          .withSolutionClass(MixedSolution.class)
          .withEntityClasses(Job.class, Route.class, Visit.class)
          .withConstraintProviderClass(MixedConstraints.class)
          .withEnvironmentMode(environment)
          .withRandomSeed(seed)
          .withMoveThreadCount(threads)
          .withMonitoringConfig(new MonitoringConfig().withSolverMetricList(List.of()));
    }

    @Override
    public SimpleScore score(MixedSolution solution) {
      return solution.score;
    }

    private BasicSolution basicView(MixedSolution solution) {
      var view = new BasicSolution();
      view.setMachines(solution.machines);
      view.setJobs(solution.jobs);
      return view;
    }

    private ListSolution listView(MixedSolution solution) {
      var view = new ListSolution();
      view.setRoutes(solution.routes);
      view.setVisits(solution.visits);
      return view;
    }

    @Override
    public SimpleScore recompute(MixedSolution solution) {
      long score =
          basic.recompute(basicView(solution)).score() + list.recompute(listView(solution)).score();
      for (var job : solution.jobs) {
        for (var route : solution.routes) {
          if (job.getMachine().id() == route.getId()) {
            for (int index = 0; index < route.getVisits().size(); index++) {
              score -= (long) job.getUnits() * (index + 1);
            }
          }
        }
      }
      return SimpleScore.of(score);
    }

    @Override
    public String state(MixedSolution solution) {
      return basic.state(basicView(solution)) + "|" + list.state(listView(solution));
    }
  }
}
