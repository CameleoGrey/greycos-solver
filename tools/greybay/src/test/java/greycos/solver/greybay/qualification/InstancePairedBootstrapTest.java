package greycos.solver.greybay.qualification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import greycos.solver.greybay.api.objective.ExactRational;

import org.junit.jupiter.api.Test;

class InstancePairedBootstrapTest {
  @Test
  void keepsEveryPairedSeedTogetherWithinItsInstance() {
    // Every instance mean is exactly zero, although individual seed differences are large.
    // Resampling seed rows independently would fabricate nonzero uncertainty here.
    var pairs =
        List.of(
            pair("a", 1, 11, -100),
            pair("a", 1, 12, 100),
            pair("b", 1, 11, -200),
            pair("b", 1, 12, 200));
    var result = InstancePairedBootstrap.estimate(pairs, 9821L);
    assertThat(result.levels())
        .containsExactly(
            new InstancePairedBootstrap.Interval(
                ExactRational.ZERO, ExactRational.ZERO, ExactRational.ZERO));
    assertThat(result.resamplingSeed()).isEqualTo(9821L);
    assertThat(result.resamples()).isEqualTo(10_000);
    assertThat(result.instances().getFirst().minimumDifferences())
        .containsExactly(ExactRational.of(-100));
    assertThat(result.instances().getFirst().maximumDifferences())
        .containsExactly(ExactRational.of(100));
  }

  @Test
  void agreesWithIndependentTwoClusterDistributionAndKeepsFeasibilitySeparate() {
    // Two equiprobable clusters with means 0 and 10 give mean 5 and bootstrap support 0,5,10
    // with probabilities 1/4,1/2,1/4. Their feasibility differences have support -1,0,1.
    var pairs =
        List.of(
            new InstancePairedBootstrap.Pair(
                "a", 1, 11, true, false, List.of(ExactRational.ZERO), List.of(ExactRational.ZERO)),
            new InstancePairedBootstrap.Pair(
                "b",
                1,
                11,
                false,
                true,
                List.of(ExactRational.of(10)),
                List.of(ExactRational.ZERO)));
    var result = InstancePairedBootstrap.estimate(pairs, 314159L);
    assertThat(result.levels())
        .containsExactly(
            new InstancePairedBootstrap.Interval(
                ExactRational.of(5), ExactRational.ZERO, ExactRational.of(10)));
    assertThat(result.feasibility())
        .isEqualTo(
            new InstancePairedBootstrap.Interval(
                ExactRational.ZERO, ExactRational.of(-1), ExactRational.ONE));
    var reversed = new ArrayList<>(pairs);
    Collections.reverse(reversed);
    assertThat(InstancePairedBootstrap.estimate(reversed, 314159L)).isEqualTo(result);
  }

  @Test
  void addingRepeatedSeedMeasurementsDoesNotIncreaseTheNumberOfIndependentProblems() {
    var original = List.of(pair("a", 1, 11, -2), pair("b", 1, 11, 8));
    var replicated = new ArrayList<>(original);
    for (long optimizerSeed = 2; optimizerSeed <= 10; optimizerSeed++) {
      replicated.add(pair("a", optimizerSeed, 11, -2));
      replicated.add(pair("b", optimizerSeed, 11, 8));
    }
    var first = InstancePairedBootstrap.estimate(original, 702L);
    var second = InstancePairedBootstrap.estimate(replicated, 702L);
    assertThat(second.levels()).isEqualTo(first.levels());
    assertThat(second.feasibility()).isEqualTo(first.feasibility());
    assertThat(second.instances()).hasSize(2);
    assertThat(second.instances().getFirst().pairedSeedCount()).isEqualTo(10);
  }

  @Test
  void rejectsDuplicateOrMissingPairedCellsInsteadOfDroppingThem() {
    var pair = pair("a", 1, 11, 2);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> InstancePairedBootstrap.estimate(List.of(pair, pair), 0))
        .withMessageContaining("Duplicate paired cell");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                InstancePairedBootstrap.estimate(
                    List.of(pair, pair("a", 1, 12, 3), pair("b", 1, 11, 4)), 0))
        .withMessageContaining("same complete paired seed panel");
  }

  private static InstancePairedBootstrap.Pair pair(
      String instance, long optimizerSeed, long solverSeed, long difference) {
    return new InstancePairedBootstrap.Pair(
        instance,
        optimizerSeed,
        solverSeed,
        true,
        true,
        List.of(ExactRational.of(difference)),
        List.of(ExactRational.ZERO));
  }
}
