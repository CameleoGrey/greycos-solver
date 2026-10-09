package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.lookup.PlanningId;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationOperatorConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmMutationType;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmPhaseConfig;
import greycos.solver.core.config.geneticalgorithm.GeneticAlgorithmStepLoggingMode;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.config.solver.termination.TerminationConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class GeneticAlgorithmIndexedPopulationTest {

  @Test
  void realSeedingAndFullGenerationAvoidQuadraticBasicEqualityWork() {
    int populationSize = 1024;
    int attempts = 2 * populationSize - 1;
    var comparisons = new AtomicLong();
    var afterSeeding = new AtomicLong(-1);
    var afterGeneration = new AtomicLong(-1);
    var input = problem(populationSize, comparisons);
    var config =
        new SolverConfig()
            .withSolutionClass(IndexedSolution.class)
            .withEntityClasses(Owner.class, Task.class)
            .withConstraintProviderClass(IndexedConstraints.class)
            .withEnvironmentMode(EnvironmentMode.NO_ASSERT)
            .withRandomSeed(37L)
            .withPhases(
                new GeneticAlgorithmPhaseConfig()
                    .withPopulationSize(populationSize)
                    .withStepLoggingMode(GeneticAlgorithmStepLoggingMode.BEST_SCORE_IMPROVED)
                    .withMutationOperators(
                        new GeneticAlgorithmMutationOperatorConfig()
                            .withType(GeneticAlgorithmMutationType.INSERTION)
                            .withProbability(1.0))
                    .withTerminationConfig(new TerminationConfig().withStepCountLimit(attempts)));
    var solver =
        (DefaultSolver<IndexedSolution>)
            SolverFactory.<IndexedSolution>create(config).buildSolver();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void phaseStarted(AbstractPhaseScope<IndexedSolution> scope) {
            comparisons.set(0);
          }

          @Override
          public void stepStarted(AbstractStepScope<IndexedSolution> scope) {
            var step = (GeneticAlgorithmStepScope<IndexedSolution>) scope;
            if (!step.isSeeding() && afterSeeding.get() < 0) {
              afterSeeding.set(comparisons.get());
              assertThat(step.getPhaseScope().getPopulationSize()).isEqualTo(populationSize);
              assertThat(step.getPhaseScope().getDistinctPopulationSize())
                  .isGreaterThan(3 * populationSize / 4);
            }
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<IndexedSolution> scope) {
            afterGeneration.set(comparisons.get());
            var phase = (GeneticAlgorithmPhaseScope<IndexedSolution>) scope;
            assertThat(phase.getPopulationSize()).isEqualTo(populationSize);
            assertThat(phase.getDistinctPopulationSize()).isGreaterThan(populationSize / 2);
            assertThat(phase.getGeneration()).isEqualTo(1);
          }
        });

    var result = solver.solve(input);

    assertThat(solver.getMoveEvaluationCount()).isEqualTo(attempts);
    // These budgets include workspace deltas, range checks and exact bucket matches. Random basic
    // assignments make the old population/diversity scans compare about a million values while
    // seeding alone. Distinct list fingerprints skip that expensive equality work. This measures
    // user-value equality, not the number of constant-time fingerprint comparisons.
    assertThat(afterSeeding.get()).isPositive().isLessThan(20L * populationSize);
    assertThat(afterGeneration.get() - afterSeeding.get())
        .isPositive()
        .isLessThan(40L * populationSize);
    assertReplay(result);
    assertThat(input.owners.getFirst().tasks)
        .extracting(task -> task.id)
        .containsExactlyElementsOf(java.util.stream.IntStream.range(0, 24).boxed().toList());
    assertThat(input.owners.getFirst().value).isSameAs(input.values.getFirst());
  }

  private static IndexedSolution problem(int populationSize, AtomicLong comparisons) {
    var solution = new IndexedSolution();
    for (int id = 0; id < 4 * populationSize; id++) {
      solution.values.add(new CountedValue(id, comparisons));
    }
    var owner = new Owner();
    owner.id = 0;
    owner.value = solution.values.getFirst();
    solution.owners.add(owner);
    for (int id = 0; id < 24; id++) {
      var task = new Task();
      task.id = id;
      task.owner = owner;
      task.index = id;
      solution.tasks.add(task);
      owner.tasks.add(task);
    }
    return solution;
  }

  private static void assertReplay(IndexedSolution solution) {
    var seen = Collections.newSetFromMap(new IdentityHashMap<Task, Boolean>());
    int score = 0;
    for (var owner : solution.owners) {
      assertThat(solution.values).anyMatch(value -> value == owner.value);
      score += owner.value.id;
      for (int index = 0; index < owner.tasks.size(); index++) {
        var task = owner.tasks.get(index);
        assertThat(seen.add(task)).isTrue();
        assertThat(solution.tasks).anyMatch(canonical -> canonical == task);
        assertThat(task.owner).isSameAs(owner);
        assertThat(task.index).isEqualTo(index);
        score += (task.id + 1) * (index + 1);
      }
    }
    assertThat(seen).hasSize(solution.tasks.size());
    assertThat(solution.score).isEqualTo(SimpleScore.of(score));
  }

  @PlanningSolution
  public static class IndexedSolution {
    @PlanningEntityCollectionProperty public List<Owner> owners = new ArrayList<>();

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "tasks")
    public List<Task> tasks = new ArrayList<>();

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "values")
    public List<CountedValue> values = new ArrayList<>();

    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class Owner {
    @PlanningId public int id;

    @PlanningVariable(valueRangeProviderRefs = "values")
    public CountedValue value;

    @PlanningListVariable(valueRangeProviderRefs = "tasks")
    public List<Task> tasks = new ArrayList<>();
  }

  @PlanningEntity
  public static class Task {
    @PlanningId public int id;

    @InverseRelationShadowVariable(sourceVariableName = "tasks")
    public Owner owner;

    @IndexShadowVariable(sourceVariableName = "tasks")
    public Integer index;
  }

  public static final class CountedValue {
    @PlanningId public final int id;
    private final AtomicLong comparisons;

    private CountedValue(int id, AtomicLong comparisons) {
      this.id = id;
      this.comparisons = comparisons;
    }

    @Override
    public boolean equals(Object other) {
      comparisons.incrementAndGet();
      return other instanceof CountedValue value && id == value.id;
    }

    @Override
    public int hashCode() {
      return id;
    }
  }

  public static final class IndexedConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(Owner.class)
            .reward(SimpleScore.ONE, owner -> owner.value.id)
            .asConstraint("Basic reward"),
        factory
            .forEach(Task.class)
            .reward(SimpleScore.ONE, task -> (task.id + 1) * (task.index + 1))
            .asConstraint("Sequence reward")
      };
    }
  }
}
