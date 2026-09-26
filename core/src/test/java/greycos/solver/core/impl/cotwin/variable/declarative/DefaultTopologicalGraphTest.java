package greycos.solver.core.impl.cotwin.variable.declarative;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.BitSet;
import java.util.List;

import org.junit.jupiter.api.Test;

public class DefaultTopologicalGraphTest
    extends AbstractTopologicalGraphTest<DefaultTopologicalOrderGraph> {

  @Test
  void selfLoopIsDetectedReportedAndRemoved() {
    var graph = createTopologicalGraph(2);
    var changed = new BitSet();
    graph.addEdge(0, 1);
    assertThat(graph.commitChanges(changed)).isFalse();
    assertThat(changed.cardinality()).isZero();

    graph.addEdge(0, 0);
    assertThat(graph.commitChanges(changed)).isTrue();
    assertThat(changed.stream().toArray()).containsExactly(0);
    assertThat(graph.getLoopedComponentList()).containsExactly(List.of(0));
    var tracker = new LoopedTracker(2, new int[][] {{0}, {1}});
    assertThat(graph.isLooped(tracker, 0)).isTrue();
    assertThat(graph.isLooped(tracker, 1)).isTrue();

    graph.removeEdge(0, 0);
    changed.clear();
    tracker.clear();
    assertThat(graph.commitChanges(changed)).isFalse();
    assertThat(changed.stream().toArray()).containsExactly(0);
    assertThat(graph.getLoopedComponentList()).isEmpty();
    assertThat(graph.isLooped(tracker, 0)).isFalse();
    assertThat(graph.isLooped(tracker, 1)).isFalse();
  }

  @Override
  protected DefaultTopologicalOrderGraph createTopologicalGraph(int graphSize) {
    return new DefaultTopologicalOrderGraph(graphSize);
  }

  @Override
  protected void verifyConsistent(DefaultTopologicalOrderGraph graph) {
    // DefaultTopologicalOrderGraph is not incremental
    // and has no datastructures to keep consistent
  }

  /**
   * Get the component members as a list.
   *
   * @param graph the graph
   * @param node The node to get the component members of.
   * @return The list of nodes in that component.
   */
  @Override
  protected List<Integer> getComponentMembers(
      DefaultTopologicalOrderGraph graph, int graphSize, int node) {
    return graph.getComponent(node);
  }
}
