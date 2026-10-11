package greycos.solver.math.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.List;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.math.benchmark.MathQualificationSupport.AllocationMeter;
import greycos.solver.math.examples.AssignmentExample;
import greycos.solver.math.examples.KnapsackExample;
import greycos.solver.math.examples.ResourceAllocationExample;

import org.junit.jupiter.api.Test;

class MathSolveQualificationTest {
  @Test
  void tinyReferenceOptimaAreIndependentlyEnumerated() {
    assertThat(MathSolveQualification.enumerateOptimum(KnapsackExample.model()))
        .isEqualTo(BigInteger.valueOf(12));
    assertThat(MathSolveQualification.enumerateOptimum(ResourceAllocationExample.model()))
        .isEqualTo(BigInteger.valueOf(26));
    assertThat(MathSolveQualification.enumerateOptimum(AssignmentExample.model()))
        .isEqualTo(BigInteger.valueOf(3));
  }

  @Test
  void oneScalarEvaluationCannotReachTheFourBitDistantAssignmentOptimum() {
    var allocation = new AllocationMeter();
    for (var variant : MathScoringBenchmark.variants("all")) {
      var measurement =
          MathSolveQualification.run(
              AssignmentExample.model(), variant, 1, 7, "hill", "declared", allocation);
      assertThat(measurement.score()).isEqualTo(HardSoftScore.of(0, -17));
      assertThat(measurement.moves()).isEqualTo(1);
      assertThat(measurement.firstFeasibleMillis()).isZero();
    }
  }

  @Test
  void pairedSamplerAndScoringVariantsReturnMatchingFixedWorkIncumbents() {
    var allocation = new AllocationMeter();
    for (var model :
        List.of(
            KnapsackExample.model(),
            ResourceAllocationExample.model(),
            AssignmentExample.model())) {
      var reference =
          MathSolveQualification.run(model, "generic", 32, 19, "late", "upper", allocation);
      for (var variant : List.of("handwritten", "full")) {
        var actual =
            MathSolveQualification.run(model, variant, 32, 19, "late", "upper", allocation);
        assertThat(actual.score()).isEqualTo(reference.score());
        assertThat(actual.values()).containsExactly(reference.values());
        assertThat(actual.moves()).isEqualTo(reference.moves());
      }
    }
  }
}
