package greycos.solver.core.impl.geneticalgorithm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GeneticAlgorithmPopulationDiversityTest {

  @ParameterizedTest
  @CsvSource({"128, 1", "1024, 1", "128, 4096"})
  void seedingComparesEachDistinctPairOnlyOnce(int populationSize, int genomeSize) {
    var diversity = new GeneticAlgorithmPopulationDiversity();
    var comparisons = new AtomicLong();
    for (int i = 0; i < populationSize; i++) {
      // A long shared prefix makes needless recounts expensive even at the default population.
      var values = new Object[genomeSize];
      values[genomeSize - 1] = new CountedValue(i, comparisons);
      diversity.add(new GeneticAlgorithmGenome(values));
      assertThat(diversity.size()).isEqualTo(i + 1);
      assertThat(comparisons.get()).isEqualTo((long) i * (i + 1) / 2);
    }
    assertThat(comparisons.get()).isEqualTo((long) populationSize * (populationSize - 1) / 2);
  }

  @Test
  void equalAssignmentsShareARepresentativeAndReadingSizeDoesNotRecount() {
    var diversity = new GeneticAlgorithmPopulationDiversity();
    var comparisons = new AtomicLong();
    var first = new GeneticAlgorithmGenome(new Object[] {null, new CountedValue(1, comparisons)});
    diversity.add(first);
    diversity.add(first);
    diversity.add(
        new GeneticAlgorithmGenome(new Object[] {null, new CountedValue(1, comparisons)}));
    diversity.add(
        new GeneticAlgorithmGenome(new Object[] {null, new CountedValue(2, comparisons)}));
    assertThat(diversity.size()).isEqualTo(2);
    long comparisonsBeforeReads = comparisons.get();
    for (int i = 0; i < 100; i++) {
      assertThat(diversity.size()).isEqualTo(2);
    }
    assertThat(comparisons.get()).isEqualTo(comparisonsBeforeReads);
  }

  @Test
  void rebuildingCountsOnlyTheReplacementPopulation() {
    var diversity = new GeneticAlgorithmPopulationDiversity();
    var first = new GeneticAlgorithmGenome(new Object[] {1});
    var second = new GeneticAlgorithmGenome(new Object[] {2});
    diversity.add(first);
    diversity.add(second);
    assertThat(diversity.size()).isEqualTo(2);

    diversity.clear();
    assertThat(diversity.size()).isZero();
    diversity.add(second);
    diversity.add(new GeneticAlgorithmGenome(new Object[] {2}));
    assertThat(diversity.size()).isEqualTo(1);
    diversity.add(first);
    assertThat(diversity.size()).isEqualTo(2);
  }

  private record CountedValue(int id, AtomicLong comparisons) {
    @Override
    public boolean equals(Object other) {
      comparisons.incrementAndGet();
      return other instanceof CountedValue value && id == value.id;
    }

    @Override
    public int hashCode() {
      return Integer.hashCode(id);
    }
  }
}
