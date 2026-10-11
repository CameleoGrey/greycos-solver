package greycos.solver.core.impl.islandmodel.resumable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.solution.ProblemFactProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.score.stream.Joiners;
import greycos.solver.core.config.heuristic.selector.move.composite.UnionMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.ChangeMoveSelectorConfig;
import greycos.solver.core.config.heuristic.selector.move.generic.SwapMoveSelectorConfig;
import greycos.solver.core.config.localsearch.LocalSearchPhaseConfig;
import greycos.solver.core.config.localsearch.decider.acceptor.LocalSearchAcceptorConfig;
import greycos.solver.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.monitoring.MonitoringConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload;
import greycos.solver.core.impl.heuristic.thread.MoveThreadingWorkload.Workload;

/** Self-contained business fixtures; replay never invokes a score director or constraint stream. */
public final class ResumableSearchWorkloads {

  private ResumableSearchWorkloads() {}

  public static Workload<?> named(String name) {
    return switch (name) {
      case "basic", "list" -> MoveThreadingWorkload.named(name);
      case "timetable" -> new TimetableWorkload();
      default -> throw new IllegalArgumentException("Unknown workload (" + name + ").");
    };
  }

  public record Placement(@PlanningId int id, int period, int room) {}

  public static class PenaltyPolicy {
    @PlanningId private int id = 0;
    private long conflictWeight = 100;

    public PenaltyPolicy() {}

    public int getId() {
      return id;
    }

    public long getConflictWeight() {
      return conflictWeight;
    }

    public void setConflictWeight(long conflictWeight) {
      this.conflictWeight = conflictWeight;
    }
  }

  @PlanningEntity
  public static class Lesson {
    @PlanningId private int id;
    private int teacher;
    private int group;
    private int preferredPeriod;

    @PlanningVariable(valueRangeProviderRefs = "placements")
    private Placement placement;

    public Lesson() {}

    Lesson(int id, Placement placement) {
      this.id = id;
      teacher = id % 6;
      group = (id / 6 + id) % 8;
      preferredPeriod = (id * 7 + 3) % 12;
      this.placement = placement;
    }

    public int getId() {
      return id;
    }

    public int getTeacher() {
      return teacher;
    }

    public int getGroup() {
      return group;
    }

    public int getPreferredPeriod() {
      return preferredPeriod;
    }

    public Placement getPlacement() {
      return placement;
    }

    public void setPlacement(Placement placement) {
      this.placement = placement;
    }

    public int period() {
      return placement.period();
    }

    @Override
    public String toString() {
      return "lesson-" + id;
    }
  }

  @PlanningSolution
  public static class Timetable {
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "placements")
    private List<Placement> placements;

    @PlanningEntityCollectionProperty private List<Lesson> lessons;
    @ProblemFactProperty private PenaltyPolicy policy = new PenaltyPolicy();
    @PlanningScore private SimpleScore score;

    public Timetable() {}

    public List<Placement> getPlacements() {
      return placements;
    }

    public void setPlacements(List<Placement> placements) {
      this.placements = placements;
    }

    public List<Lesson> getLessons() {
      return lessons;
    }

    public void setLessons(List<Lesson> lessons) {
      this.lessons = lessons;
    }

    public SimpleScore getScore() {
      return score;
    }

    public void setScore(SimpleScore score) {
      this.score = score;
    }

    public PenaltyPolicy getPolicy() {
      return policy;
    }

    public void setPolicy(PenaltyPolicy policy) {
      this.policy = policy;
    }
  }

  public static class TimetableConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEachUniquePair(Lesson.class, Joiners.equal(Lesson::period))
            .join(PenaltyPolicy.class)
            .penalize(
                SimpleScore.ONE,
                (a, b, policy) ->
                    policy.conflictWeight
                        * ((a.teacher == b.teacher ? 1 : 0)
                            + (a.group == b.group ? 1 : 0)
                            + (a.placement.room() == b.placement.room() ? 1 : 0)))
            .asConstraint("Concurrent resource conflicts"),
        factory
            .forEach(Lesson.class)
            .penalize(SimpleScore.ONE, lesson -> Math.abs(lesson.period() - lesson.preferredPeriod))
            .asConstraint("Preferred period"),
        factory
            .forEach(Lesson.class)
            .groupBy(Lesson::period, ConstraintCollectors.count())
            .penalize(SimpleScore.ONE, (period, count) -> (long) count * count)
            .asConstraint("Period balance")
      };
    }
  }

  public static final class TimetableWorkload implements Workload<Timetable> {
    @Override
    public Timetable createProblem(int size) {
      if (size < 4) throw new IllegalArgumentException("Lesson count must be at least 4.");
      var problem = new Timetable();
      problem.placements = new ArrayList<>();
      for (int id = 0; id < 48; id++) problem.placements.add(new Placement(id, id / 4, id % 4));
      problem.lessons = new ArrayList<>();
      for (int id = 0; id < size; id++)
        problem.lessons.add(new Lesson(id, problem.placements.get((id * 13 + 5) % 48)));
      problem.score = recompute(problem);
      return problem;
    }

    @Override
    public SolverConfig solverConfig(
        String threads,
        long seed,
        int acceptedCount,
        TerminationConfig termination,
        EnvironmentMode mode) {
      return new SolverConfig()
          .withSolutionClass(Timetable.class)
          .withEntityClasses(Lesson.class)
          .withConstraintProviderClass(TimetableConstraints.class)
          .withRandomSeed(seed)
          .withEnvironmentMode(mode)
          .withMoveThreadCount(threads)
          .withMoveThreadBufferSize(10)
          .withMonitoringConfig(new MonitoringConfig().withSolverMetricList(List.of()))
          .withPhases(
              new LocalSearchPhaseConfig()
                  .withMoveSelectorConfig(
                      new UnionMoveSelectorConfig()
                          .withMoveSelectors(
                              new ChangeMoveSelectorConfig(), new SwapMoveSelectorConfig()))
                  .withAcceptorConfig(new LocalSearchAcceptorConfig().withLateAcceptanceSize(64))
                  .withForagerConfig(
                      new LocalSearchForagerConfig().withAcceptedCountLimit(acceptedCount))
                  .withTerminationConfig(termination));
    }

    @Override
    public SimpleScore score(Timetable solution) {
      return solution.score;
    }

    @Override
    public SimpleScore recompute(Timetable solution) {
      var ids = new HashSet<Integer>();
      long penalty = 0;
      long[] counts = new long[12];
      for (var lesson : solution.lessons) {
        var placement = lesson.placement;
        if (!ids.add(lesson.id)
            || placement == null
            || placement.id() < 0
            || placement.id() >= solution.placements.size()
            || solution.placements.get(placement.id()) != placement)
          throw new IllegalStateException("Invalid assignment for lesson (" + lesson.id + ").");
        penalty += Math.abs(placement.period() - lesson.preferredPeriod);
        counts[placement.period()]++;
      }
      for (int i = 0; i < solution.lessons.size(); i++) {
        var a = solution.lessons.get(i);
        for (int j = i + 1; j < solution.lessons.size(); j++) {
          var b = solution.lessons.get(j);
          if (a.placement.period() != b.placement.period()) continue;
          if (a.teacher == b.teacher) penalty += solution.policy.conflictWeight;
          if (a.group == b.group) penalty += solution.policy.conflictWeight;
          if (a.placement.room() == b.placement.room()) penalty += solution.policy.conflictWeight;
        }
      }
      for (long count : counts) penalty += count * count;
      return SimpleScore.of(-penalty);
    }

    @Override
    public String state(Timetable solution) {
      var out = new StringBuilder();
      solution.lessons.stream()
          .sorted((a, b) -> Integer.compare(a.id, b.id))
          .forEach(
              lesson ->
                  out.append(lesson.id).append('=').append(lesson.placement.id()).append(';'));
      return out.toString();
    }
  }
}
