package greycos.solver.greybay.examples;

import java.util.ArrayList;
import java.util.HashMap;
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
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
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

/** Basic-variable staff scheduling with a solver-free business model and independent replay. */
public final class SchedulingExample {
  private SchedulingExample() {}

  public record Worker(String id, int capacity) {}

  public record Work(String id, int slot, int demand, String preferredWorkerId) {}

  public record Problem(List<Worker> workers, List<Work> work) {
    public Problem {
      workers = List.copyOf(workers);
      work = List.copyOf(work);
    }

    /** Replays only business IDs and immutable demand; no solver score calculator is called. */
    public BusinessScore replay(Map<String, String> workerByWorkId) {
      if (!workerByWorkId
          .keySet()
          .equals(work.stream().map(Work::id).collect(java.util.stream.Collectors.toSet()))) {
        throw new IllegalArgumentException("Every original work item must occur exactly once.");
      }
      var loadByWorkerAndSlot = new HashMap<String, Map<Integer, Long>>();
      var totalByWorker = new HashMap<String, Long>();
      workers.forEach(worker -> loadByWorkerAndSlot.put(worker.id(), new HashMap<>()));
      long preferenceCost = 0;
      for (var item : work) {
        var workerId = workerByWorkId.get(item.id());
        if (!loadByWorkerAndSlot.containsKey(workerId)) {
          throw new IllegalArgumentException(
              "Unknown or missing worker for work (" + item.id() + ").");
        }
        loadByWorkerAndSlot.get(workerId).merge(item.slot(), (long) item.demand(), Long::sum);
        totalByWorker.merge(workerId, (long) item.demand(), Long::sum);
        if (!item.preferredWorkerId().equals(workerId)) {
          preferenceCost += item.demand();
        }
      }
      long capacityExcess = 0;
      long balanceCost = 0;
      for (var worker : workers) {
        for (long load : loadByWorkerAndSlot.get(worker.id()).values()) {
          capacityExcess += Math.max(0, load - worker.capacity());
        }
        long total = totalByWorker.getOrDefault(worker.id(), 0L);
        balanceCost += total * total;
      }
      return new BusinessScore(-capacityExcess, -preferenceCost - balanceCost);
    }
  }

  public record BusinessScore(long hard, long soft) {}

  public static Problem problem(String id) {
    int count = ExampleData.size(id);
    int offset = id.startsWith("heldout-") ? 1 : 0;
    var workers =
        List.of(new Worker("worker-0", 3), new Worker("worker-1", 3), new Worker("worker-2", 3));
    var work = new ArrayList<Work>();
    for (int i = 0; i < count; i++) {
      work.add(new Work("work-" + i, i / 3, 1 + (i + offset) % 2, "worker-" + (i + offset) % 3));
    }
    return new Problem(workers, work);
  }

  public static SolverConfig baseSolverConfig() {
    return new SolverConfig()
        .withSolutionClass(Schedule.class)
        .withEntityClasses(Assignment.class)
        .withEasyScoreCalculatorClass(ScheduleScoreCalculator.class)
        .withPhases(
            new ConstructionHeuristicPhaseConfig(),
            new LocalSearchPhaseConfig().withLocalSearchType(LocalSearchType.LATE_ACCEPTANCE));
  }

  public static ProblemAdapter<Schedule> problemAdapter() {
    return new SchedulingProblems();
  }

  public static Schedule reconstruct(Problem input, Map<String, String> workerByWorkId) {
    input.replay(workerByWorkId);
    var schedule = new Schedule();
    schedule.setWorkers(new ArrayList<>(input.workers()));
    var workersById = new HashMap<String, Worker>();
    schedule.getWorkers().forEach(worker -> workersById.put(worker.id(), worker));
    var assignments = new ArrayList<Assignment>();
    for (var work : input.work()) {
      assignments.add(new Assignment(work, workersById.get(workerByWorkId.get(work.id()))));
    }
    schedule.setAssignments(assignments);
    return schedule;
  }

  public static final class SchedulingProblems
      implements ProblemAdapter<Schedule>, BusinessVerifier<Schedule> {
    @Override
    public ComponentIdentity identity() {
      return ExampleData.identity("scheduling-adapter-and-replay");
    }

    @Override
    public ProblemDescriptor describe(String problemId) {
      return new ProblemDescriptor(
          problemId,
          ExampleData.fingerprint(problem(problemId).toString()),
          new ProblemCapabilities(
              true,
              false,
              false,
              false,
              HardSoftScore.class.getName(),
              List.of("hard", "soft"),
              Set.of()));
    }

    @Override
    public Schedule loadFresh(String problemId) {
      var input = problem(problemId);
      var initial = new LinkedHashMap<String, String>();
      for (int i = 0; i < input.work().size(); i++) {
        initial.put(input.work().get(i).id(), input.workers().get(i % input.workers().size()).id());
      }
      return reconstruct(input, initial);
    }

    @Override
    public Optional<BusinessVerifier<Schedule>> verifier() {
      return Optional.of(this);
    }

    @Override
    public VerificationResult verify(String problemId, Schedule schedule) {
      var input = problem(problemId);
      if (!input.workers().equals(schedule.getWorkers())) {
        return VerificationResult.invalid("Worker facts changed.");
      }
      var originalWork = new HashMap<String, Work>();
      input.work().forEach(work -> originalWork.put(work.id(), work));
      var assignmentIds = new LinkedHashMap<String, String>();
      for (var assignment : schedule.getAssignments()) {
        if (assignment.getWork() == null
            || !assignment.getWork().equals(originalWork.get(assignment.getId()))) {
          return VerificationResult.invalid("Work facts changed or contain an unknown ID.");
        }
        if (assignment.getWorker() == null
            || schedule.getWorkers().stream()
                .noneMatch(worker -> worker == assignment.getWorker())) {
          return VerificationResult.invalid(
              "Worker assignment is missing or refers outside the fresh graph.");
        }
        if (assignmentIds.put(assignment.getId(), assignment.getWorker().id()) != null) {
          return VerificationResult.invalid("Duplicate work ID (" + assignment.getId() + ").");
        }
      }
      try {
        var replayed = input.replay(assignmentIds);
        var expected = HardSoftScore.of(replayed.hard(), replayed.soft());
        if (!expected.equals(schedule.getScore())) {
          return VerificationResult.invalid(
              "Independent scheduling score differs: expected " + expected + ".");
        }
        return new VerificationResult(
            VerificationResult.Status.PASSED,
            "Business demand and assignments replayed.",
            Map.of(
                "workCount", Integer.toString(assignmentIds.size()), "score", expected.toString()));
      } catch (IllegalArgumentException exception) {
        return VerificationResult.invalid(exception.getMessage());
      }
    }
  }

  @PlanningEntity
  public static class Assignment {
    private Work work;
    private Worker worker;

    public Assignment() {}

    public Assignment(Work work, Worker worker) {
      this.work = work;
      this.worker = worker;
    }

    @PlanningId
    public String getId() {
      return work.id();
    }

    public Work getWork() {
      return work;
    }

    public void setWork(Work work) {
      this.work = work;
    }

    @PlanningVariable(valueRangeProviderRefs = "workers")
    public Worker getWorker() {
      return worker;
    }

    public void setWorker(Worker worker) {
      this.worker = worker;
    }
  }

  @PlanningSolution
  public static class Schedule {
    private List<Worker> workers;
    private List<Assignment> assignments;
    private HardSoftScore score;

    public Schedule() {}

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "workers")
    public List<Worker> getWorkers() {
      return workers;
    }

    public void setWorkers(List<Worker> workers) {
      this.workers = workers;
    }

    @PlanningEntityCollectionProperty
    public List<Assignment> getAssignments() {
      return assignments;
    }

    public void setAssignments(List<Assignment> assignments) {
      this.assignments = assignments;
    }

    @PlanningScore
    public HardSoftScore getScore() {
      return score;
    }

    public void setScore(HardSoftScore score) {
      this.score = score;
    }
  }

  public static class ScheduleScoreCalculator
      implements EasyScoreCalculator<Schedule, HardSoftScore> {
    @Override
    public HardSoftScore calculateScore(Schedule schedule) {
      long hard = 0;
      long soft = 0;
      for (var worker : schedule.getWorkers()) {
        long total = 0;
        var slots = new HashMap<Integer, Long>();
        for (var assignment : schedule.getAssignments()) {
          if (assignment.getWorker() == worker) {
            var work = assignment.getWork();
            total += work.demand();
            slots.merge(work.slot(), (long) work.demand(), Long::sum);
            if (!worker.id().equals(work.preferredWorkerId())) {
              soft -= work.demand();
            }
          }
        }
        hard -=
            slots.values().stream().mapToLong(load -> Math.max(0, load - worker.capacity())).sum();
        soft -= total * total;
      }
      return HardSoftScore.of(hard, soft);
    }
  }
}
