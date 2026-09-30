package greycos.solver.core.impl.score.definition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.score.BendableDoubleScore;
import greycos.solver.core.api.score.BendableFloatScore;
import greycos.solver.core.api.score.HardMediumSoftDoubleScore;
import greycos.solver.core.api.score.HardMediumSoftFloatScore;
import greycos.solver.core.api.score.HardSoftDoubleScore;
import greycos.solver.core.api.score.HardSoftFloatScore;
import greycos.solver.core.api.score.SimpleDoubleScore;
import greycos.solver.core.api.score.SimpleFloatScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

import org.junit.jupiter.api.Test;

class FloatingScoreDescriptorTest {

  @Test
  void planningScoreAutomaticallyDiscoversAllEightNativeDefinitions() {
    List<Class<?>> solutions =
        List.of(
            SimpleFloatSolution.class,
            SimpleDoubleSolution.class,
            HardSoftFloatSolution.class,
            HardSoftDoubleSolution.class,
            HardMediumSoftFloatSolution.class,
            HardMediumSoftDoubleSolution.class,
            BendableFloatSolution.class,
            BendableDoubleSolution.class);
    List<Class<?>> definitions =
        List.of(
            SimpleFloatScoreDefinition.class,
            SimpleDoubleScoreDefinition.class,
            HardSoftFloatScoreDefinition.class,
            HardSoftDoubleScoreDefinition.class,
            HardMediumSoftFloatScoreDefinition.class,
            HardMediumSoftDoubleScoreDefinition.class,
            BendableFloatScoreDefinition.class,
            BendableDoubleScoreDefinition.class);
    for (int i = 0; i < solutions.size(); i++) {
      var descriptor =
          SolutionDescriptor.buildSolutionDescriptor(solutions.get(i), FloatingEntity.class);
      var definition = descriptor.getScoreDefinition();
      assertThat(definition.getClass()).isEqualTo(definitions.get(i));
      assertThat(definition.getNumericType()).isEqualTo(i % 2 == 0 ? float.class : double.class);
      if (i >= 6) {
        assertThat(definition.getLevelsSize()).isEqualTo(3);
        assertThat(definition.getFeasibleLevelsSize()).isEqualTo(1);
      }
    }
  }

  @PlanningEntity
  public static class FloatingEntity {
    @PlanningVariable(valueRangeProviderRefs = "range")
    public String value;
  }

  @PlanningSolution
  public abstract static class FloatingSolution {
    @PlanningEntityCollectionProperty public List<FloatingEntity> entities;

    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "range")
    public List<String> values;
  }

  @PlanningSolution
  public static class SimpleFloatSolution extends FloatingSolution {
    @PlanningScore public SimpleFloatScore score;
  }

  @PlanningSolution
  public static class SimpleDoubleSolution extends FloatingSolution {
    @PlanningScore public SimpleDoubleScore score;
  }

  @PlanningSolution
  public static class HardSoftFloatSolution extends FloatingSolution {
    @PlanningScore public HardSoftFloatScore score;
  }

  @PlanningSolution
  public static class HardSoftDoubleSolution extends FloatingSolution {
    @PlanningScore public HardSoftDoubleScore score;
  }

  @PlanningSolution
  public static class HardMediumSoftFloatSolution extends FloatingSolution {
    @PlanningScore public HardMediumSoftFloatScore score;
  }

  @PlanningSolution
  public static class HardMediumSoftDoubleSolution extends FloatingSolution {
    @PlanningScore public HardMediumSoftDoubleScore score;
  }

  @PlanningSolution
  public static class BendableFloatSolution extends FloatingSolution {
    @PlanningScore(bendableHardLevelsSize = 1, bendableSoftLevelsSize = 2)
    public BendableFloatScore score;
  }

  @PlanningSolution
  public static class BendableDoubleSolution extends FloatingSolution {
    @PlanningScore(bendableHardLevelsSize = 1, bendableSoftLevelsSize = 2)
    public BendableDoubleScore score;
  }
}
