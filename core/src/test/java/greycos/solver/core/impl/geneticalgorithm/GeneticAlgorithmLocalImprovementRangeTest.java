package greycos.solver.core.impl.geneticalgorithm;

import static greycos.solver.core.impl.geneticalgorithm.GeneticAlgorithmLocalImprovementIntegrationTest.phase;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRange;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeFactory;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.score.stream.Constraint;
import greycos.solver.core.api.score.stream.ConstraintFactory;
import greycos.solver.core.api.score.stream.ConstraintProvider;
import greycos.solver.core.api.solver.SolverFactory;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.config.solver.SolverConfig;
import greycos.solver.core.impl.phase.event.PhaseLifecycleListenerAdapter;
import greycos.solver.core.impl.phase.scope.AbstractPhaseScope;
import greycos.solver.core.impl.phase.scope.AbstractStepScope;
import greycos.solver.core.impl.solver.DefaultSolver;
import greycos.solver.core.testcotwin.TestdataObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class GeneticAlgorithmLocalImprovementRangeTest {

  @Test
  void enabledLocalImprovementSamplesHugeEntityRangesWhilePreservingPinsAndSingletons() {
    long size = 1L << 40;
    var input = new RangeSolution();
    input.entities =
        new ArrayList<>(
            List.of(
                new RangeEntity("movable", 0, size, false),
                new RangeEntity("pinned", size, size * 2, true),
                new RangeEntity("singleton", size * 2, size * 2 + 1, false)));
    var solver =
        (DefaultSolver<RangeSolution>)
            SolverFactory.<RangeSolution>create(
                    new SolverConfig()
                        .withSolutionClass(RangeSolution.class)
                        .withEntityClasses(RangeEntity.class)
                        .withConstraintProviderClass(RangeConstraints.class)
                        .withEnvironmentMode(EnvironmentMode.FULL_ASSERT)
                        .withRandomSeed(37L)
                        .withPhases(phase(12L, 20)))
                .buildSolver();
    var phase = new AtomicReference<GeneticAlgorithmPhaseScope<RangeSolution>>();
    solver.addPhaseLifecycleListener(
        new PhaseLifecycleListenerAdapter<>() {
          @Override
          public void stepEnded(AbstractStepScope<RangeSolution> scope) {
            assertThat(scope.getScore().raw()).isEqualTo(replay(scope.getWorkingSolution()));
          }

          @Override
          public void phaseEnded(AbstractPhaseScope<RangeSolution> scope) {
            phase.set((GeneticAlgorithmPhaseScope<RangeSolution>) scope);
          }
        });

    var result = solver.solve(input);

    assertThat(phase.get().getLocalImprovementProbeCount()).isPositive();
    assertThat(phase.get().getLocalImprovementAcceptedCount()).isPositive();
    assertThat(result.score).isEqualTo(replay(result));
    assertThat(input.entities)
        .extracting(entity -> entity.value)
        .containsExactly(0L, size, size * 2);
  }

  private static SimpleScore replay(RangeSolution solution) {
    var total = 0;
    for (var entity : solution.entities) {
      assertThat(entity.value).isGreaterThanOrEqualTo(entity.from).isLessThan(entity.to);
      if (entity.pinned || entity.to - entity.from == 1) {
        assertThat(entity.value).isEqualTo(entity.from);
      }
      total += (int) (entity.value % 1_000);
    }
    return SimpleScore.of(total);
  }

  @PlanningSolution
  public static class RangeSolution {
    @PlanningEntityCollectionProperty public List<RangeEntity> entities;
    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class RangeEntity extends TestdataObject {
    public long from;
    public long to;
    @PlanningPin public boolean pinned;

    @PlanningVariable(valueRangeProviderRefs = "range")
    public Long value;

    public RangeEntity() {}

    RangeEntity(String code, long from, long to, boolean pinned) {
      super(code);
      this.from = from;
      this.to = to;
      this.pinned = pinned;
      value = from;
    }

    @ValueRangeProvider(id = "range")
    public ValueRange<Long> getRange() {
      return ValueRangeFactory.createLongValueRange(from, to);
    }
  }

  public static final class RangeConstraints implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        factory
            .forEach(RangeEntity.class)
            .reward(SimpleScore.ONE, entity -> (int) (entity.value % 1_000))
            .asConstraint("Assigned remainder")
      };
    }
  }
}
