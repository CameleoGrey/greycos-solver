package greycos.solver.core.impl.cotwin.variable.declarative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SelfReferenceShadowVariableTest {

  static Stream<Arguments> graphConfigurations() {
    return Stream.of(
            GraphStructure.ARBITRARY,
            GraphStructure.ARBITRARY_SINGLE_ENTITY_AT_MOST_ONE_DIRECTIONAL_PARENT_TYPE)
        .flatMap(
            structure ->
                Stream.of(false, true)
                    .map(
                        ignoreInconsistentSolutions ->
                            Arguments.of(structure, ignoreInconsistentSolutions)));
  }

  @ParameterizedTest
  @MethodSource("graphConfigurations")
  void selfDependencyIsDetectedAndCanBeUndone(
      GraphStructure structure, boolean ignoreInconsistentSolutions) {
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(SelfSolution.class, SelfEntity.class);
    var root = new SelfEntity();
    var first = new SelfEntity();
    first.previous = root;
    var second = new SelfEntity();
    second.previous = first;
    var graph =
        DefaultShadowVariableSessionFactory.buildGraphForStructureAndDirection(
            new GraphStructure.GraphStructureAndDirection(structure, null, null),
            new DefaultShadowVariableSessionFactory.GraphDescriptor<>(
                    descriptor, ChangedVariableNotifier.empty(), root, first, second)
                .withIgnoreInconsistentSolutions(ignoreInconsistentSolutions));
    var previous = descriptor.getMetaModel().entity(SelfEntity.class).variable("previous");

    assertThat(graph.updateChanged()).isTrue();
    assertThat(graph.getVariableLoops()).isEmpty();
    assertThat(first.depth).isEqualTo(1);
    assertThat(first.twiceDepth).isEqualTo(2);
    assertThat(second.depth).isEqualTo(2);

    graph.beforeVariableChanged(previous, first);
    first.previous = first;
    graph.afterVariableChanged(previous, first);
    assertThat(graph.updateChanged()).isEqualTo(!ignoreInconsistentSolutions);
    assertThat(graph.getVariableLoops())
        .singleElement()
        .satisfies(loop -> assertThat(loop.entitySet()).containsExactly(first));
    if (!ignoreInconsistentSolutions) {
      assertThat(first.depth).isNull();
      assertThat(second.depth).isNull();
    }

    graph.beforeVariableChanged(previous, first);
    first.previous = root;
    graph.afterVariableChanged(previous, first);
    assertThat(graph.updateChanged()).isTrue();
    assertThat(graph.getVariableLoops()).isEmpty();
    assertThat(first.depth).isEqualTo(1);
    assertThat(first.twiceDepth).isEqualTo(2);
    assertThat(second.depth).isEqualTo(2);
    assertThat(second.twiceDepth).isEqualTo(4);
  }

  @ParameterizedTest
  @MethodSource("graphConfigurations")
  void initialSelfDependencyIsDetected(
      GraphStructure structure, boolean ignoreInconsistentSolutions) {
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(SelfSolution.class, SelfEntity.class);
    var entity = new SelfEntity();
    entity.previous = entity;
    var graph =
        DefaultShadowVariableSessionFactory.buildGraphForStructureAndDirection(
            new GraphStructure.GraphStructureAndDirection(structure, null, null),
            new DefaultShadowVariableSessionFactory.GraphDescriptor<>(
                    descriptor, ChangedVariableNotifier.empty(), entity)
                .withIgnoreInconsistentSolutions(ignoreInconsistentSolutions));

    assertThat(graph.updateChanged()).isEqualTo(!ignoreInconsistentSolutions);
    assertThat(graph.getVariableLoops())
        .singleElement()
        .satisfies(loop -> assertThat(loop.entitySet()).containsExactly(entity));
  }

  @ParameterizedTest
  @MethodSource("graphConfigurations")
  void distinctVariablesOnSameEntityRetainTheirActualDependencies(
      GraphStructure structure, boolean ignoreInconsistentSolutions) {
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            CrossVariableSolution.class, CrossVariableEntity.class);
    var entity = new CrossVariableEntity();
    entity.previous = entity;
    var graph =
        DefaultShadowVariableSessionFactory.buildGraphForStructureAndDirection(
            new GraphStructure.GraphStructureAndDirection(structure, null, null),
            new DefaultShadowVariableSessionFactory.GraphDescriptor<>(
                    descriptor, ChangedVariableNotifier.empty(), entity)
                .withIgnoreInconsistentSolutions(ignoreInconsistentSolutions));
    var next = descriptor.getMetaModel().entity(CrossVariableEntity.class).variable("next");

    // a depends on this entity's b, but b has no dependency yet: this is acyclic.
    assertThat(graph.updateChanged()).isTrue();
    assertThat(graph.getVariableLoops()).isEmpty();
    assertThat(entity.b).isZero();
    assertThat(entity.a).isEqualTo(1);

    // b now depends on this entity's a, closing a cycle between distinct shadows.
    graph.beforeVariableChanged(next, entity);
    entity.next = entity;
    graph.afterVariableChanged(next, entity);
    assertThat(graph.updateChanged()).isEqualTo(!ignoreInconsistentSolutions);
    assertThat(graph.getVariableLoops())
        .singleElement()
        .satisfies(
            loop -> {
              assertThat(loop.entitySet()).containsExactly(entity);
              assertThat(loop.involvedVariableSet())
                  .extracting(pair -> pair.variableName())
                  .containsExactlyInAnyOrder("a", "b");
            });

    graph.beforeVariableChanged(next, entity);
    entity.next = null;
    graph.afterVariableChanged(next, entity);
    assertThat(graph.updateChanged()).isTrue();
    assertThat(graph.getVariableLoops()).isEmpty();
    assertThat(entity.b).isZero();
    assertThat(entity.a).isEqualTo(1);
  }

  @Test
  void fixedSelfDependencyIsRejected() {
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(SelfSolution.class, SelfEntity.class);
    var entity = new SelfEntity();
    entity.fixedPrevious = entity;
    var graphDescriptor =
        new DefaultShadowVariableSessionFactory.GraphDescriptor<>(
            descriptor, ChangedVariableNotifier.empty(), entity);

    assertThatThrownBy(() -> DefaultShadowVariableSessionFactory.buildGraph(graphDescriptor))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("fixed dependency loops");
  }

  @PlanningSolution
  public static class SelfSolution {
    @PlanningEntityCollectionProperty @ValueRangeProvider public List<SelfEntity> entities;
    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class SelfEntity {
    @PlanningVariable(allowsUnassigned = true)
    public SelfEntity previous;

    @ShadowVariable(supplierName = "calculateDepth")
    public Integer depth;

    @ShadowVariable(supplierName = "calculateTwiceDepth")
    public Integer twiceDepth;

    public SelfEntity fixedPrevious;

    @ShadowVariable(supplierName = "calculateFixedDepth")
    public Integer fixedDepth;

    @ShadowSources("previous.depth")
    public Integer calculateDepth() {
      return previous == null ? 0 : previous.depth == null ? null : previous.depth + 1;
    }

    @ShadowSources("depth")
    public Integer calculateTwiceDepth() {
      return depth == null ? null : 2 * depth;
    }

    @ShadowSources("fixedPrevious.fixedDepth")
    public Integer calculateFixedDepth() {
      return fixedPrevious == null
          ? 0
          : fixedPrevious.fixedDepth == null ? null : fixedPrevious.fixedDepth + 1;
    }
  }

  @PlanningSolution
  public static class CrossVariableSolution {
    @PlanningEntityCollectionProperty @ValueRangeProvider public List<CrossVariableEntity> entities;
    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class CrossVariableEntity {
    @PlanningVariable(allowsUnassigned = true)
    public CrossVariableEntity previous;

    @PlanningVariable(allowsUnassigned = true)
    public CrossVariableEntity next;

    @ShadowVariable(supplierName = "calculateA")
    public Integer a;

    @ShadowVariable(supplierName = "calculateB")
    public Integer b;

    @ShadowSources("previous.b")
    public Integer calculateA() {
      return previous == null ? 0 : previous.b == null ? null : previous.b + 1;
    }

    @ShadowSources("next.a")
    public Integer calculateB() {
      return next == null ? 0 : next.a == null ? null : next.a + 1;
    }
  }
}
