package greycos.solver.benchmark.impl.ranking;

import static greycos.solver.core.testutil.PlannerAssert.assertCompareToOrder;

import java.util.Comparator;

import greycos.solver.core.api.score.HardSoftScore;
import greycos.solver.core.api.score.Score;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.score.definition.SimpleScoreDefinition;

import org.junit.jupiter.api.Test;

class ResilientScoreComparatorTest {

  @Test
  void compareTo() {
    Comparator<Score> comparator = new ResilientScoreComparator(new SimpleScoreDefinition());

    assertCompareToOrder(comparator, SimpleScore.of(-20), SimpleScore.of(-1));
    assertCompareToOrder(comparator, HardSoftScore.of(-20, -300), HardSoftScore.of(-1, -4000));
    assertCompareToOrder(
        comparator,
        SimpleScore.of(-4000),
        HardSoftScore.of(-300, -300),
        HardSoftScore.of(-20, -4000),
        SimpleScore.of(-20),
        HardSoftScore.of(-20, 4000),
        SimpleScore.of(-1));
  }
}
