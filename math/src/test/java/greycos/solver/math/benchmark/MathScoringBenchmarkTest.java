package greycos.solver.math.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.math.api.LinearExpression;
import greycos.solver.math.api.MathModel;
import greycos.solver.math.benchmark.MathQualificationSupport.AllocationMeter;

import org.junit.jupiter.api.Test;

class MathScoringBenchmarkTest {
  @Test
  void allImplementationsMatchEveryUpdateOfSparseDenseAndObjectiveFreeTraces() {
    var allocation = new AllocationMeter();
    for (int degree : new int[] {1, 5}) {
      for (var objective : new String[] {"none", "min", "max"}) {
        var workload = MathScoringBenchmark.workload(5, 7, degree, 40, objective, 19L);
        long expectedChecksum = MathScoringBenchmark.run(workload, "full", allocation).checksum();
        for (var variant : MathScoringBenchmark.variants("all")) {
          assertThat(MathScoringBenchmark.run(workload, variant, allocation).checksum())
              .as("degree=%s objective=%s variant=%s", degree, objective, variant)
              .isEqualTo(expectedChecksum);
        }
      }
    }
  }

  @Test
  void originalExpressionReplayRetainsConstantsCancellationRelationsAndObjectiveSign() {
    var builder = MathModel.builder();
    var x = builder.intVar("x", -2, 2);
    var y = builder.binaryVar("y");
    builder.initialValue(x, -1);
    builder.initialValue(y, 1);
    builder.addConstraint("upper", x.times(-3).plus(y).plus(2).le(1));
    builder.addConstraint("lower", x.plus(y.times(-2)).ge(0));
    builder.addConstraint("equal", x.plus(y).eq(2));
    builder.addConstraint("empty contradiction", LinearExpression.constant(-3).ge(0));
    builder.minimize(x.times(-4).plus(y.times(3)).minus(8).plus(x).minus(x));
    var model = builder.build();
    // x=-1,y=1: violations 5+3+2+3=13; objective 4+3-8=-1.
    assertThat(MathQualificationSupport.replay(model, new long[] {-1, 1}))
        .isEqualTo(HardSoftScore.of(-13, 1));
    var workload =
        new MathScoringBenchmark.Workload(
            model,
            new long[] {-1, 1},
            new MathScoringBenchmark.Operation[] {new MathScoringBenchmark.Operation(0, 2)},
            new HardSoftScore[] {HardSoftScore.of(-4, 13)},
            HardSoftScore.of(-13, 1),
            8,
            4,
            4);
    var allocation = new AllocationMeter();
    long expectedChecksum = MathScoringBenchmark.run(workload, "full", allocation).checksum();
    for (var variant : MathScoringBenchmark.variants("all")) {
      assertThat(MathScoringBenchmark.run(workload, variant, allocation).checksum())
          .isEqualTo(expectedChecksum);
    }
  }
}
