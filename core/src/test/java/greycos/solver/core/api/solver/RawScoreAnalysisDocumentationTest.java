package greycos.solver.core.api.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
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
import greycos.solver.core.api.score.analysis.MatchOccurrenceAnalysis;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintJustification;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.config.solver.SolverConfig;

import org.junit.jupiter.api.Test;

/** Executable examples for individual occurrences, association changes and multiset differences. */
class RawScoreAnalysisDocumentationTest {

  private static final RuleJustification OPEN_CHECK = new RuleJustification("Open shift check");

  private static final ScoreAnalysisOptions RAW_OPTIONS =
      new ScoreAnalysisOptions(
          ScoreAnalysisFetchPolicy.FETCH_ALL, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL, true);

  @Test
  void equalJustificationsRetainIndividualImpactsAndCounts() {
    var aliceId = new EmployeeId("alice");
    var alice = new Employee(aliceId);
    var monday = new Shift("monday", alice, 2, List.of(Check.SAFETY));
    var tuesday = new Shift("tuesday", alice, 5, List.of(Check.HANDOVER));
    var schedule = new ShiftSchedule(List.of(alice), List.of(monday, tuesday));
    var solutionManager = solutionManager();
    var legacy = solutionManager.analyze(schedule);
    assertThat(legacy.rawAnalysis()).isNull();
    assertThat(legacy.constraintMap().values())
        .singleElement()
        .satisfies(constraint -> assertThat(constraint.rawMatchCount()).isNull());

    // tag::rawScoreAnalysisOptions[]
    var options =
        new ScoreAnalysisOptions(
            ScoreAnalysisFetchPolicy.FETCH_ALL, IndictmentFetchPolicy.TERMINAL_AND_ORIGINAL, true);
    var analysis = solutionManager.analyzeWithOptions(schedule, options);
    var raw = analysis.rawAnalysis();
    var constraint = analysis.constraintMap().values().iterator().next();
    // The two equal justifications form one group, containing two original occurrences.
    assertThat(constraint.matchCount()).isOne();
    assertThat(constraint.rawMatchCount()).isEqualTo(2L);
    assertThat(constraint.matches())
        .singleElement()
        .satisfies(match -> assertThat(match.score()).isEqualTo(SimpleScore.of(-7)));
    assertThat(raw.matchOccurrences())
        .extracting(MatchOccurrenceAnalysis::matchScore)
        .containsExactlyInAnyOrder(SimpleScore.of(-2), SimpleScore.of(-5));
    // end::rawScoreAnalysisOptions[]

    assertThat(analysis.score()).isEqualTo(SimpleScore.of(-7));
    assertThat(raw.originalsFetched()).isTrue();
    assertThat(raw.matchOccurrences())
        .allSatisfy(
            row -> {
              assertThat(row.occurrenceSign()).isOne();
              assertThat(row.score()).isEqualTo(row.matchScore());
              assertThat(row.justification()).isEqualTo(OPEN_CHECK);
              assertThat(row.indictedObjects()).containsExactly(aliceId);
            });
    assertThat(raw.constraintMap().get(constraint.constraintRef()))
        .containsExactlyElementsOf(raw.matchOccurrences());
    assertThat(raw.indictmentMap().get(new EmployeeId("alice")))
        .containsExactlyElementsOf(raw.matchOccurrences());
    var mondayRow =
        raw.matchOccurrences().stream()
            .filter(row -> row.originalIndictedObjects().contains(monday))
            .findFirst()
            .orElseThrow();
    assertThat(mondayRow.originalIndictedObjects().getFirst()).isSameAs(monday);
    assertThat(raw.originalIndictmentMap().get(monday).getFirst()).isSameAs(mondayRow);
    assertThat(raw.indictmentMap().get(aliceId))
        .anySatisfy(row -> assertThat(row).isSameAs(mondayRow));
    assertThat(analysis.indictmentMap().get(aliceId).matchCount()).isEqualTo(2);
    assertThat(analysis.indictmentMap().get(aliceId).matches())
        .singleElement()
        .satisfies(match -> assertThat(match.matchCount()).isEqualTo(2));

    // tag::rawScoreJustifications[]
    var expectedJustification = new RuleJustification("Open shift check");
    assertThat(analysis.justifications(RuleJustification.class))
        .containsExactly(expectedJustification, expectedJustification);
    assertThat(raw.justifications()).containsExactly(expectedJustification, expectedJustification);
    assertThat(analysis.indictmentMap().get(aliceId).justifications(RuleJustification.class))
        .containsExactly(expectedJustification);
    // end::rawScoreJustifications[]
  }

  @Test
  void associationChangesRemainVisibleWhenGroupedTotalsCancel() {
    var alice = new Employee(new EmployeeId("alice"));
    var bob = new Employee(new EmployeeId("bob"));
    var monday = new Shift("monday", alice, 2, List.of(Check.SAFETY));
    var tuesday = new Shift("tuesday", bob, 2, List.of(Check.HANDOVER));
    var schedule = new ShiftSchedule(List.of(alice, bob), List.of(monday, tuesday));
    var solutionManager = solutionManager();
    var before = solutionManager.analyzeWithOptions(schedule, RAW_OPTIONS);
    var previousEmployees = Map.of(monday, alice.id(), tuesday, bob.id());

    // tag::rawScoreAssociationDiff[]
    monday.setEmployee(bob);
    tuesday.setEmployee(alice);
    var after =
        solutionManager.analyzeWithOptions(schedule, RAW_OPTIONS, SolutionUpdatePolicy.UPDATE_ALL);
    var change = after.diff(before);
    // Each employee and each shift still has one penalty of -2.
    assertThat(change.score()).isEqualTo(SimpleScore.ZERO);
    assertThat(change.indictmentMap()).isEmpty();
    assertThat(change.originalIndictmentMap()).isEmpty();
    // The employee-to-shift associations changed: two removals and two additions remain.
    assertThat(change.rawAnalysis().matchOccurrences()).hasSize(4);
    assertThat(change.rawAnalysis().matchOccurrences())
        .extracting(MatchOccurrenceAnalysis::occurrenceSign)
        .containsExactlyInAnyOrder(-1, -1, 1, 1);
    // end::rawScoreAssociationDiff[]

    assertThat(change.rawAnalysis().indictmentMap()).containsOnlyKeys(alice.id(), bob.id());
    assertThat(change.rawAnalysis().originalIndictmentMap()).containsOnlyKeys(monday, tuesday);
    assertThat(change.rawAnalysis().matchOccurrences())
        .allSatisfy(
            row -> {
              assertThat(row.matchScore()).isEqualTo(SimpleScore.of(-2));
              var shift = (Shift) row.originalIndictedObjects().getFirst();
              var employeeId =
                  row.occurrenceSign() == 1
                      ? shift.getEmployee().id()
                      : previousEmployees.get(shift);
              assertThat(row.indictedObjects()).containsExactly(employeeId);
              assertThat(row.score()).isEqualTo(SimpleScore.of(row.occurrenceSign() * -2));
            });
    assertThat(
            before.rawAnalysis().originalIndictmentMap().get(monday).getFirst().indictedObjects())
        .containsExactly(alice.id());
    assertThat(
            before.rawAnalysis().originalIndictmentMap().get(tuesday).getFirst().indictedObjects())
        .containsExactly(bob.id());
  }

  @Test
  void identicalOccurrencesAreComparedWithTheirMultiplicity() {
    var alice = new Employee(new EmployeeId("alice"));
    var monday = new Shift("monday", alice, 2, List.of(Check.SAFETY, Check.HANDOVER));
    var schedule = new ShiftSchedule(List.of(alice), List.of(monday));
    var solutionManager = solutionManager();
    var before = solutionManager.analyzeWithOptions(schedule, RAW_OPTIONS);
    assertThat(before.rawAnalysis().matchOccurrences()).hasSize(2);
    assertThat(before.rawAnalysis().matchOccurrences().get(0))
        .isNotSameAs(before.rawAnalysis().matchOccurrences().get(1));
    monday.setOpenChecks(List.of(Check.HANDOVER, Check.SAFETY));
    var reordered = solutionManager.analyzeWithOptions(schedule, RAW_OPTIONS);
    assertThat(reordered.diff(before).rawAnalysis().matchOccurrences()).isEmpty();

    // tag::rawScoreMultiplicityDiff[]
    // Both checks had the same justification, impact and selected/original objects.
    monday.setOpenChecks(List.of(Check.HANDOVER));
    var after = solutionManager.analyzeWithOptions(schedule, RAW_OPTIONS);
    var change = after.diff(before);
    assertThat(change.score()).isEqualTo(SimpleScore.of(2));
    assertThat(change.rawAnalysis().matchOccurrences())
        .singleElement()
        .satisfies(
            removed -> {
              assertThat(removed.matchScore()).isEqualTo(SimpleScore.of(-2));
              assertThat(removed.occurrenceSign()).isEqualTo(-1);
              assertThat(removed.score()).isEqualTo(SimpleScore.of(2));
            });
    assertThat(change.constraintMap().values())
        .singleElement()
        .satisfies(constraint -> assertThat(constraint.rawMatchCount()).isEqualTo(-1L));
    // end::rawScoreMultiplicityDiff[]

    assertThat(change.indictmentMap().get(alice.id()).matchCount()).isEqualTo(-1);
    assertThat(change.originalIndictmentMap().get(monday).matchCount()).isEqualTo(-1);
    assertThat(before.rawAnalysis().matchOccurrences()).hasSize(2);
  }

  private static SolutionManager<ShiftSchedule, SimpleScore> solutionManager() {
    SolverFactory<ShiftSchedule> solverFactory =
        SolverFactory.create(
            new SolverConfig()
                .withSolutionClass(ShiftSchedule.class)
                .withEntityClasses(Shift.class)
                .withConstraintProviderClass(ChecklistConstraintProvider.class));
    return SolutionManager.create(solverFactory);
  }

  public static class ChecklistConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {openChecks(factory)};
    }

    // tag::rawScoreChecklistConstraint[]
    Constraint openChecks(ConstraintFactory factory) {
      return factory
          .forEach(Shift.class)
          .expand(Shift::getOpenChecks)
          .flattenLast(checks -> checks)
          .penalize(SimpleScore.ONE, (shift, check) -> shift.getPenaltyPoints())
          .justifyWith((shift, check, score) -> new RuleJustification("Open shift check"))
          .indictWith((shift, check) -> List.of(shift.getEmployee().id()))
          .asConstraint("Open shift checks");
    }
    // end::rawScoreChecklistConstraint[]
  }

  public record EmployeeId(String value) {}

  public record Employee(EmployeeId id) {}

  // tag::rawScoreChecklistJustification[]
  public record RuleJustification(String rule) implements ConstraintJustification {}

  // end::rawScoreChecklistJustification[]

  public enum Check {
    SAFETY,
    HANDOVER
  }

  @PlanningEntity
  public static class Shift {

    @PlanningId private String id;

    @PlanningVariable(valueRangeProviderRefs = "employees")
    private Employee employee;

    private long penaltyPoints;
    private List<Check> openChecks;

    public Shift() {}

    public Shift(String id, Employee employee, long penaltyPoints, List<Check> openChecks) {
      this.id = id;
      this.employee = employee;
      this.penaltyPoints = penaltyPoints;
      this.openChecks = openChecks;
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

    public long getPenaltyPoints() {
      return penaltyPoints;
    }

    public List<Check> getOpenChecks() {
      return openChecks;
    }

    public void setOpenChecks(List<Check> openChecks) {
      this.openChecks = openChecks;
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
