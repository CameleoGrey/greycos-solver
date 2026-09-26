package greycos.solver.core.impl.cotwin.variable.declarative;

import static org.assertj.core.api.Assertions.assertThat;

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
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class NullableFixedSourceShadowVariableTest {

  static Stream<Arguments> graphConfigurations() {
    return Stream.of(
            GraphStructure.ARBITRARY,
            GraphStructure.ARBITRARY_SINGLE_ENTITY_AT_MOST_ONE_DIRECTIONAL_PARENT_TYPE)
        .flatMap(
            structure ->
                Stream.of(false, true)
                    .map(ignoreInconsistent -> Arguments.of(structure, ignoreInconsistent)));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void initializesNullableFixedSourcesWhenSettingWorkingSolution(boolean allowInconsistent) {
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(TestSolution.class, TestEntity.class);
    var first = new TestEntity();
    var second = new TestEntity();
    second.dependency = first;
    var solution = new TestSolution();
    solution.entities = List.of(first, second);

    try (var director =
        new EasyScoreDirectorFactory<>(descriptor, s -> SimpleScore.ZERO, EnvironmentMode.NO_ASSERT)
            .createScoreDirectorBuilder()
            .withForceAllowInconsistentSolutions(allowInconsistent)
            .build()) {
      director.setWorkingSolution(solution);
      assertThat(first.bar).isEqualTo(7);
      assertThat(second.bar).isEqualTo(1);
      assertThat(first.fooCalls).isEqualTo(1);
      assertThat(first.barCalls).isEqualTo(1);
      assertThat(second.fooCalls).isEqualTo(1);
      assertThat(second.barCalls).isEqualTo(1);

      director.updateShadowVariables();
      assertThat(first.barCalls).isEqualTo(1);
      assertThat(second.barCalls).isEqualTo(1);

      // Replacing the working solution must initialize the new graph even if fields are stale.
      first.bar = null;
      director.setWorkingSolution(solution);
      assertThat(first.bar).isEqualTo(7);
      assertThat(first.barCalls).isEqualTo(2);
      assertThat(second.barCalls).isEqualTo(2);
    }
  }

  @ParameterizedTest
  @MethodSource("graphConfigurations")
  void initializationRunsOnceAndLaterUpdatesPreserveIncrementalityAndLoops(
      GraphStructure structure, boolean ignoreInconsistent) {
    var descriptor =
        SolutionDescriptor.buildSolutionDescriptor(TestSolution.class, TestEntity.class);
    var first = new TestEntity();
    var second = new TestEntity();
    var unrelated = new TestEntity();
    second.dependency = first;
    var graph =
        DefaultShadowVariableSessionFactory.buildGraphForStructureAndDirection(
            new GraphStructure.GraphStructureAndDirection(structure, null, null),
            new DefaultShadowVariableSessionFactory.GraphDescriptor<>(
                    descriptor, ChangedVariableNotifier.empty(), first, second, unrelated)
                .withIgnoreInconsistentSolutions(ignoreInconsistent));
    var previous = descriptor.getMetaModel().entity(TestEntity.class).variable("previous");

    assertThat(graph.updateChanged()).isTrue();
    assertThat(first.bar).isEqualTo(7);
    assertThat(second.bar).isEqualTo(1);
    assertThat(unrelated.bar).isEqualTo(7);
    for (var entity : List.of(first, second, unrelated)) {
      assertThat(entity.fooCalls).isEqualTo(1);
      assertThat(entity.barCalls).isEqualTo(1);
    }
    assertThat(graph.updateChanged()).isTrue();
    assertThat(first.barCalls).isEqualTo(1);

    graph.beforeVariableChanged(previous, second);
    second.previous = unrelated;
    graph.afterVariableChanged(previous, second);
    assertThat(graph.updateChanged()).isTrue();
    assertThat(second.foo).isEqualTo(8);
    assertThat(second.fooCalls).isEqualTo(2);
    assertThat(second.barCalls).isEqualTo(1);
    assertThat(first.fooCalls).isEqualTo(1);
    assertThat(unrelated.fooCalls).isEqualTo(1);

    // first.foo -> second.bar -> first.foo is a real cycle; constant bar nodes stay independent.
    graph.beforeVariableChanged(previous, first);
    first.previous = second;
    graph.afterVariableChanged(previous, first);
    assertThat(graph.updateChanged()).isEqualTo(!ignoreInconsistent);
    assertThat(graph.getVariableLoops()).hasSize(1);
    if (!ignoreInconsistent) {
      assertThat(first.foo).isNull();
      assertThat(second.bar).isNull();
    }

    graph.beforeVariableChanged(previous, first);
    first.previous = null;
    graph.afterVariableChanged(previous, first);
    assertThat(graph.updateChanged()).isTrue();
    assertThat(graph.getVariableLoops()).isEmpty();
    assertThat(first.foo).isEqualTo(1);
    assertThat(second.bar).isEqualTo(1);
    assertThat(second.foo).isEqualTo(8);
    assertThat(first.barCalls).isEqualTo(1);
    assertThat(unrelated.fooCalls).isEqualTo(1);
    assertThat(unrelated.barCalls).isEqualTo(1);
  }

  @PlanningSolution
  public static class TestSolution {
    @PlanningEntityCollectionProperty @ValueRangeProvider public List<TestEntity> entities;
    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class TestEntity {
    @PlanningVariable(allowsUnassigned = true)
    public TestEntity previous;

    public TestEntity dependency;

    @ShadowVariable(supplierName = "fooSupplier")
    public Integer foo;

    @ShadowVariable(supplierName = "barSupplier")
    public Integer bar;

    public int fooCalls;
    public int barCalls;

    @ShadowSources("previous.bar")
    public Integer fooSupplier() {
      fooCalls++;
      return previous == null ? 1 : previous.bar == null ? null : previous.bar + 1;
    }

    @ShadowSources("dependency.foo")
    public Integer barSupplier() {
      barCalls++;
      return dependency == null ? Integer.valueOf(7) : dependency.foo;
    }
  }
}
