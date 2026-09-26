package greycos.solver.core.impl.exhaustivesearch.scope;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TreeSet;

import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.exhaustivesearch.node.comparator.AbstractNodeComparatorTest;
import greycos.solver.core.impl.exhaustivesearch.node.comparator.ScoreFirstNodeComparator;
import greycos.solver.core.impl.score.director.InnerScore;
import greycos.solver.core.impl.solver.scope.SolverScope;
import greycos.solver.core.testcotwin.TestdataSolution;

import org.junit.jupiter.api.Test;

class ExhaustiveSearchPhaseScopeTest extends AbstractNodeComparatorTest {

  @Test
  void testNodePruning() {
    var phase = new ExhaustiveSearchPhaseScope<TestdataSolution>(new SolverScope<>(), 0);
    phase.setExpandableNodeQueue(new TreeSet<>(new ScoreFirstNodeComparator(true)));
    phase.addExpandableNode(buildNode(0, "0", 0, 0));
    phase.addExpandableNode(buildNode(0, "1", 0, 0));
    phase.addExpandableNode(buildNode(0, "2", 0, 0));
    phase.setBestPessimisticBound(InnerScore.fullyAssigned(SimpleScore.of(Integer.MIN_VALUE)));
    phase.registerPessimisticBound(InnerScore.fullyAssigned(SimpleScore.ONE));
    assertThat(phase.getExpandableNodeQueue()).hasSize(1);
  }

  @Test
  void unknownBoundsSurviveTheFirstIncumbentAndQueueReplacement() {
    var phase = new ExhaustiveSearchPhaseScope<TestdataSolution>(new SolverScope<>(), 0);
    var queue = new TreeSet<>(new ScoreFirstNodeComparator<TestdataSolution>(true));
    phase.setExpandableNodeQueue((TreeSet) queue);
    var unknown =
        this.<TestdataSolution>buildNode(
            1,
            InnerScore.fullyAssigned(new SimpleScore(-1, 0)),
            (InnerScore<SimpleScore>) null,
            0,
            0);
    var dominated = this.<TestdataSolution>buildNode(1, "0", 0, 1);
    phase.addExpandableNode(unknown);
    phase.addExpandableNode(dominated);
    phase.setExpandableNodeQueue(new TreeSet<>(new ScoreFirstNodeComparator(true)));
    phase.setExpandableNodeQueue((TreeSet) queue);
    phase.registerPessimisticBound(InnerScore.fullyAssigned(SimpleScore.ONE));
    assertThat(phase.getExpandableNodeQueue()).containsExactly(unknown);
    assertThat(phase.pollExpandableNode()).isSameAs(unknown);
    assertThat(phase.pollExpandableNode()).isNull();
  }
}
