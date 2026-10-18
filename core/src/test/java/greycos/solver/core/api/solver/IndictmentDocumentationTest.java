package greycos.solver.core.api.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintCollectors;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.config.solver.SolverConfig;

import org.junit.jupiter.api.Test;

/** A complete workforce example backing the indictment documentation snippets. */
class IndictmentDocumentationTest {

  @Test
  void explainEmployeeOverloadAndCompareAssignments() {
    var aliceId = new EmployeeId("alice");
    var alice = new Employee(aliceId, 2);
    var bob = new Employee(new EmployeeId("bob"), 2);
    var monday = new Shift("monday", alice);
    var tuesday = new Shift("tuesday", alice);
    var wednesday = new Shift("wednesday", alice);
    var thursday = new Shift("thursday", bob);
    var friday = new Shift("friday", bob);
    var schedule =
        new ShiftSchedule(
            List.of(alice, bob), List.of(monday, tuesday, wednesday, thursday, friday));
    SolverFactory<ShiftSchedule> solverFactory =
        SolverFactory.create(
            new SolverConfig()
                .withSolutionClass(ShiftSchedule.class)
                .withEntityClasses(Shift.class)
                .withConstraintProviderClass(WorkloadConstraintProvider.class));
    SolutionManager<ShiftSchedule, SimpleScore> solutionManager =
        SolutionManager.create(solverFactory);

    // tag::employeeIndictmentAnalysis[]
    var before =
        solutionManager.analyzeWithIndictments(
            schedule, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL);
    var aliceImpact = before.indictmentMap().get(new EmployeeId("alice"));
    var mondayImpact = before.originalIndictmentMap().get(monday);
    // Alice has three shifts and a limit of two: one grouped match with score -1.
    assertThat(before.score()).isEqualTo(SimpleScore.of(-1));
    assertThat(aliceImpact.score()).isEqualTo(SimpleScore.of(-1));
    assertThat(aliceImpact.matchCount()).isOne();
    // Every contributing shift receives the full match impact, not a fraction of it.
    assertThat(mondayImpact.score()).isEqualTo(SimpleScore.of(-1));
    assertThat(mondayImpact.matchCount()).isOne();
    // end::employeeIndictmentAnalysis[]

    assertThat(before.indictmentMap()).containsOnlyKeys(aliceId);
    assertThat(before.originalIndictmentMap()).containsOnlyKeys(monday, tuesday, wednesday);
    assertThat(before.constraintMap().values())
        .singleElement()
        .satisfies(
            constraint -> {
              assertThat(constraint.score()).isEqualTo(SimpleScore.of(-1));
              assertThat(constraint.matchCount()).isOne();
            });
    assertThat(aliceImpact.matches())
        .singleElement()
        .satisfies(
            match -> {
              assertThat(match.constraintRef().id()).isEqualTo("Employee shift limit");
              assertThat(match.justification()).isEqualTo(new EmployeeWorkload(aliceId));
              assertThat(match.score()).isEqualTo(SimpleScore.of(-1));
              assertThat(match.matchCount()).isOne();
            });
    assertThat(before.originalIndictmentMap().values())
        .allSatisfy(
            indictment -> {
              assertThat(indictment.score()).isEqualTo(SimpleScore.of(-1));
              assertThat(indictment.matchCount()).isOne();
              assertThat(indictment.matches())
                  .singleElement()
                  .satisfies(
                      match -> {
                        assertThat(match.score()).isEqualTo(SimpleScore.of(-1));
                        assertThat(match.matchCount()).isOne();
                      });
            });
    assertThat(
            before.originalIndictmentMap().values().stream()
                .map(indictment -> indictment.score())
                .reduce(SimpleScore.ZERO, SimpleScore::add))
        .isEqualTo(SimpleScore.of(-3));

    // tag::employeeIndictmentDiff[]
    friday.setEmployee(alice);
    var after =
        solutionManager.analyzeWithIndictments(
            schedule, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL);
    var change = after.diff(before); // New analysis minus old analysis.
    var aliceChange = change.indictmentMap().get(aliceId);
    assertThat(after.score()).isEqualTo(SimpleScore.of(-2));
    assertThat(change.score()).isEqualTo(SimpleScore.of(-1));
    assertThat(aliceChange.score()).isEqualTo(SimpleScore.of(-1));
    assertThat(aliceChange.matchCount()).isZero(); // The same one grouped match became worse.
    assertThat(change.originalIndictmentMap().get(friday).score()).isEqualTo(SimpleScore.of(-2));
    assertThat(change.originalIndictmentMap().get(friday).matchCount()).isOne();
    // end::employeeIndictmentDiff[]

    assertThat(after.indictmentMap()).containsOnlyKeys(aliceId);
    assertThat(after.indictmentMap().get(aliceId).matchCount()).isOne();
    assertThat(after.originalIndictmentMap()).containsOnlyKeys(monday, tuesday, wednesday, friday);
    assertThat(after.originalIndictmentMap().values())
        .allSatisfy(
            indictment -> {
              assertThat(indictment.score()).isEqualTo(SimpleScore.of(-2));
              assertThat(indictment.matchCount()).isOne();
            });
    assertThat(aliceChange.matches())
        .singleElement()
        .satisfies(
            match -> {
              assertThat(match.score()).isEqualTo(SimpleScore.of(-1));
              assertThat(match.matchCount()).isZero();
            });
    for (var shift : List.of(monday, tuesday, wednesday)) {
      var shiftChange = change.originalIndictmentMap().get(shift);
      assertThat(shiftChange.score()).isEqualTo(SimpleScore.of(-1));
      assertThat(shiftChange.matchCount()).isZero();
    }
    // The first snapshot still describes its original three contributing shifts.
    assertThat(before.originalIndictmentMap()).containsOnlyKeys(monday, tuesday, wednesday);
    assertThat(before.indictmentMap().get(aliceId).score()).isEqualTo(SimpleScore.of(-1));
  }

  public static class WorkloadConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {employeeShiftLimit(factory)};
    }

    // tag::employeeIndictmentConstraint[]
    Constraint employeeShiftLimit(ConstraintFactory factory) {
      return factory
          .forEach(Shift.class)
          .groupBy(Shift::getEmployee, ConstraintCollectors.count())
          .map((employee, count) -> new Workload(employee.id(), count, employee.maximumShifts()))
          .filter(workload -> workload.shiftCount() > workload.maximumShifts())
          .penalize(SimpleScore.ONE, workload -> workload.shiftCount() - workload.maximumShifts())
          .justifyWith((workload, score) -> new EmployeeWorkload(workload.employeeId()))
          .indictWith(workload -> List.of(workload.employeeId()))
          .asConstraint("Employee shift limit");
    }
    // end::employeeIndictmentConstraint[]
  }

  // tag::employeeIndictmentRecords[]
  public record EmployeeId(String value) {}

  public record Employee(EmployeeId id, long maximumShifts) {}

  public record Workload(EmployeeId employeeId, long shiftCount, long maximumShifts) {}

  // A stable justification lets the diff align the same employee's grouped match.
  public record EmployeeWorkload(EmployeeId employeeId) implements ConstraintJustification {}

  // end::employeeIndictmentRecords[]

  @PlanningEntity
  public static class Shift {

    @PlanningId private String id;

    @PlanningVariable(valueRangeProviderRefs = "employees")
    private Employee employee;

    public Shift() {}

    public Shift(String id, Employee employee) {
      this.id = id;
      this.employee = employee;
    }

    public String getId() {
      return id;
    }

    public Employee getEmployee() {
      return employee;
    }

    public void setEmployee(Employee employee) {
      this.employee = employee;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Shift shift && Objects.equals(id, shift.id);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(id);
    }

    @Override
    public String toString() {
      return id;
    }
  }

  @PlanningSolution
  public static class ShiftSchedule {

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "employees")
    private List<Employee> employees;

    @PlanningEntityCollectionProperty private List<Shift> shifts;

    @PlanningScore private SimpleScore score;

    public ShiftSchedule() {}

    public ShiftSchedule(List<Employee> employees, List<Shift> shifts) {
      this.employees = employees;
      this.shifts = shifts;
    }

    public List<Employee> getEmployees() {
      return employees;
    }

    public void setEmployees(List<Employee> employees) {
      this.employees = employees;
    }

    public List<Shift> getShifts() {
      return shifts;
    }

    public void setShifts(List<Shift> shifts) {
      this.shifts = shifts;
    }

    public SimpleScore getScore() {
      return score;
    }

    public void setScore(SimpleScore score) {
      this.score = score;
    }
  }
}
