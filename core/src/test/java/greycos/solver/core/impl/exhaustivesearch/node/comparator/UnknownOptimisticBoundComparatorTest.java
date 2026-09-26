package greycos.solver.core.impl.exhaustivesearch.node.comparator;

import static org.assertj.core.api.Assertions.assertThat;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.exhaustivesearch.NodeExplorationType;
import greycos.solver.core.impl.score.director.InnerScore;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class UnknownOptimisticBoundComparatorTest extends AbstractNodeComparatorTest {

  @ParameterizedTest
  @EnumSource(
      value = NodeExplorationType.class,
      names = "ORIGINAL_ORDER",
      mode = EnumSource.Mode.EXCLUDE)
  void unknownBoundsSortAboveFiniteBoundsAndKeepTieBreakers(NodeExplorationType type) {
    var comparator = type.buildNodeComparator(true);
    var score = InnerScore.fullyAssigned(new SimpleScore(-1, 0));
    var finite = buildNode(1, score, InnerScore.fullyAssigned(SimpleScore.of(100)), 0, 0);
    var unknown = buildNode(1, score, (InnerScore<SimpleScore>) null, 0, 1);
    var laterUnknown = buildNode(1, score, (InnerScore<SimpleScore>) null, 0, 2);
    assertLesser(comparator, finite, unknown);
    assertLesser(comparator, laterUnknown, unknown);
    assertThat(comparator.compare(unknown, unknown)).isZero();
  }
}
