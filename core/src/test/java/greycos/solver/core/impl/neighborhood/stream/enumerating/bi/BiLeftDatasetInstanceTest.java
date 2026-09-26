package greycos.solver.core.impl.neighborhood.stream.enumerating.bi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Random;

import greycos.solver.core.impl.bavet.common.tuple.BiTuple;
import greycos.solver.core.impl.neighborhood.stream.dataset.CachedBiDatasetInstance;
import greycos.solver.core.impl.neighborhood.stream.enumerating.common.AbstractDataset;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class BiLeftDatasetInstanceTest {

  @Test
  void lazyPerKeyIndexPreservesFlatIteratorReservations() {
    var dataset = datasetWithGaps();
    var instance = new CachedBiDatasetInstance<>(dataset);
    var iterator = instance.exhaustiveIterator(new TailFirstRandom());
    iterator.next();
    var seen = new HashSet<Integer>();
    assertThat(seen.add(iterator.b())).isTrue();
    assertThat(iterator.b()).isEqualTo(19);

    // Building the lazy index must not relocate tuples in the flat backing list.
    assertThat(instance.size(0)).isEqualTo(9);
    assertThat(instance.size(1)).isEqualTo(9);
    var bucketIterator = instance.exhaustiveIterator(0, new Random(1));
    var bucketValues = new HashSet<Integer>();
    while (bucketIterator.hasNext()) {
      assertThat(bucketValues.add(bucketIterator.next())).isTrue();
    }
    assertThat(bucketValues).hasSize(9).doesNotContain(6);

    while (iterator.hasNext()) {
      assertThat(iterator.hasNext()).isTrue();
      iterator.next();
      assertThat(seen.add(iterator.b())).as("each flat tuple is returned exactly once").isTrue();
    }
    assertThat(seen).hasSize(18).doesNotContain(1, 6);
    for (var i = 0; i < 20; i++) {
      assertThat(seen.contains(i)).isEqualTo(i != 1 && i != 6);
    }
    assertThatThrownBy(iterator::next).isInstanceOf(NoSuchElementException.class);
  }

  @Test
  void plainTraversalPreservesPendingRetirementAndInsertionOrder() {
    var dataset = datasetWithGaps();
    var iterator = dataset.retiringRandomIterator(new TailFirstRandom());
    assertThat(iterator.next().getB()).isEqualTo(19);
    var observed = new ArrayList<Integer>();
    dataset.forEach(tuple -> observed.add(tuple.getB()));
    var expected = new ArrayList<Integer>();
    for (var i = 0; i < 20; i++) {
      if (i != 1 && i != 6) {
        expected.add(i);
      }
    }
    assertThat(observed).containsExactlyElementsOf(expected);
    iterator.retire();
    assertThat(iterator.next().getB()).isEqualTo(18);
  }

  @SuppressWarnings("unchecked")
  private static BiLeftDatasetInstance<TestdataSolution, Integer, Integer> datasetWithGaps() {
    var dataset =
        new BiLeftDatasetInstance<TestdataSolution, Integer, Integer>(
            mock(AbstractDataset.class), 0);
    var tuples = new ArrayList<BiTuple<Integer, Integer>>();
    for (var i = 0; i < 20; i++) {
      var tuple = BiTuple.of(i % 2, i, 1);
      tuples.add(tuple);
      dataset.insert(tuple);
    }
    dataset.retract(tuples.get(1));
    dataset.retract(tuples.get(6));
    return dataset;
  }

  private static final class TailFirstRandom extends Random {
    private int draws;

    private TailFirstRandom() {
      super(0);
    }

    @Override
    public int nextInt(int bound) {
      return draws++ < 2 ? bound - 1 : super.nextInt(bound);
    }
  }
}
