package greycos.solver.core.impl.cotwin.variable.declarative;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.preview.api.move.builtin.Moves;
import greycos.solver.core.preview.api.move.test.MoveTester;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ShadowVariableMigrationDocumentationTest {

  private static final LocalDate START_DATE = LocalDate.of(2026, 1, 5);
  private static final LocalDate LATER_START_DATE = LocalDate.of(2026, 1, 8);

  @Test
  void supplierReadsProblemFactFromItsSolution() {
    var firstJob = assignedJob();
    var secondJob = assignedJob();

    SolutionManager.updateShadowVariables(schedule(firstJob, 3));
    SolutionManager.updateShadowVariables(schedule(secondJob, 7));

    assertThat(firstJob.endDate).isEqualTo(LocalDate.of(2026, 1, 10));
    assertThat(secondJob.endDate).isEqualTo(LocalDate.of(2026, 1, 14));
  }

  @Test
  void eachDeclaredSourceUpdatesTheShadowAndUnassignmentClearsIt() {
    var job = assignedJob();
    var solution = schedule(job, 3);
    var metaModel =
        SolutionDescriptor.buildSolutionDescriptor(Schedule.class, Job.class).getMetaModel();
    var entity = metaModel.genuineEntity(Job.class);
    var startDateVariable = entity.basicVariable("startDate", LocalDate.class);
    var delayVariable = entity.basicVariable("delayDays", Integer.class);
    var context = MoveTester.build(metaModel).using(solution);
    assertThat(job.endDate).isEqualTo(LocalDate.of(2026, 1, 10));

    context.execute(Moves.change(startDateVariable, job, LATER_START_DATE));
    assertThat(job.endDate).isEqualTo(LocalDate.of(2026, 1, 13));

    context.execute(Moves.change(delayVariable, job, 4));
    assertThat(job.endDate).isEqualTo(LocalDate.of(2026, 1, 15));

    context.execute(Moves.change(startDateVariable, job, null));
    assertThat(job.endDate).isNull();
    context.execute(Moves.change(startDateVariable, job, LATER_START_DATE));
    assertThat(job.endDate).isEqualTo(LocalDate.of(2026, 1, 15));

    context.execute(Moves.change(delayVariable, job, null));
    assertThat(job.endDate).isNull();
    context.execute(Moves.change(delayVariable, job, 0));
    assertThat(job.endDate).isEqualTo(LocalDate.of(2026, 1, 11));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void initiallyUnassignedSourceClearsStaleShadow(boolean unassignedStartDate) {
    var job = assignedJob();
    job.endDate = LocalDate.of(2026, 1, 10);
    if (unassignedStartDate) {
      job.startDate = null;
    } else {
      job.delayDays = null;
    }

    SolutionManager.updateShadowVariables(schedule(job, 3));

    assertThat(job.endDate).isNull();
  }

  private static Job assignedJob() {
    var job = new Job();
    job.startDate = START_DATE;
    job.delayDays = 2;
    return job;
  }

  private static Schedule schedule(Job job, int durationInDays) {
    var schedule = new Schedule();
    schedule.jobs = List.of(job);
    schedule.startDateRange = List.of(START_DATE, LATER_START_DATE);
    schedule.delayRange = List.of(0, 2, 4);
    schedule.rules = new ScheduleRules(durationInDays);
    return schedule;
  }

  // Keep this model aligned with the "Accessing solution facts" documentation example.
  public record ScheduleRules(int durationInDays) {}

  @PlanningSolution
  public static class Schedule {

    @PlanningEntityCollectionProperty public List<Job> jobs;

    @ValueRangeProvider(id = "startDateRange")
    public List<LocalDate> startDateRange;

    @ValueRangeProvider(id = "delayRange")
    public List<Integer> delayRange;

    @ProblemFactProperty public ScheduleRules rules;

    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class Job {

    @PlanningVariable(valueRangeProviderRefs = "startDateRange", allowsUnassigned = true)
    public LocalDate startDate;

    @PlanningVariable(valueRangeProviderRefs = "delayRange", allowsUnassigned = true)
    public Integer delayDays;

    @ShadowVariable(supplierName = "endDateSupplier")
    public LocalDate endDate;

    @ShadowSources({"startDate", "delayDays"})
    public LocalDate endDateSupplier(Schedule schedule) {
      if (startDate == null || delayDays == null) {
        return null;
      }
      return startDate.plusDays((long) schedule.rules.durationInDays() + delayDays);
    }
  }
}
