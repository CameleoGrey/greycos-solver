package greycos.solver.core.impl.cotwin.variable.declarative;

import static greycos.solver.core.impl.cotwin.variable.declarative.GraphStructure.ARBITRARY;
import static greycos.solver.core.impl.cotwin.variable.declarative.GraphStructure.ARBITRARY_SINGLE_ENTITY_AT_MOST_ONE_DIRECTIONAL_PARENT_TYPE;
import static greycos.solver.core.impl.cotwin.variable.declarative.GraphStructure.EMPTY;
import static greycos.solver.core.impl.cotwin.variable.declarative.GraphStructure.SINGLE_DIRECTIONAL_PARENT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.shadow.concurrent.TestdataConcurrentSolution;
import greycos.solver.core.testcotwin.shadow.concurrent.TestdataConcurrentValue;
import greycos.solver.core.testcotwin.shadow.extended.TestdataDeclarativeExtendedBaseValue;
import greycos.solver.core.testcotwin.shadow.extended.TestdataDeclarativeExtendedSolution;
import greycos.solver.core.testcotwin.shadow.extended.TestdataDeclarativeExtendedSubclassValue;
import greycos.solver.core.testcotwin.shadow.follower.TestdataFollowerEntity;
import greycos.solver.core.testcotwin.shadow.follower.TestdataFollowerSolution;
import greycos.solver.core.testcotwin.shadow.multi_directional_parent.TestdataMultiDirectionConcurrentEntity;
import greycos.solver.core.testcotwin.shadow.multi_directional_parent.TestdataMultiDirectionConcurrentSolution;
import greycos.solver.core.testcotwin.shadow.multi_directional_parent.TestdataMultiDirectionConcurrentValue;
import greycos.solver.core.testcotwin.shadow.multi_entity.TestdataMultiEntityDependencyEntity;
import greycos.solver.core.testcotwin.shadow.multi_entity.TestdataMultiEntityDependencySolution;
import greycos.solver.core.testcotwin.shadow.multi_entity.TestdataMultiEntityDependencyValue;
import greycos.solver.core.testcotwin.shadow.simple_list.TestdataDeclarativeSimpleListSolution;
import greycos.solver.core.testcotwin.shadow.simple_list.TestdataDeclarativeSimpleListValue;

import org.junit.jupiter.api.Test;

class GraphStructureTest {
  @Test
  void emptySimpleListStructure() {
    assertThat(
            GraphStructure.determineGraphStructure(
                TestdataDeclarativeSimpleListSolution.buildSolutionDescriptor()))
        .hasFieldOrPropertyWithValue("structure", EMPTY);
  }

  @Test
  void simpleListStructure() {
    var entity = new TestdataDeclarativeSimpleListValue();
    assertThat(
            GraphStructure.determineGraphStructure(
                TestdataDeclarativeSimpleListSolution.buildSolutionDescriptor(), entity))
        .hasFieldOrPropertyWithValue("structure", SINGLE_DIRECTIONAL_PARENT)
        .hasFieldOrPropertyWithValue("direction", ParentVariableType.PREVIOUS);
  }

  @Test
  void extendedSimpleListStructure() {
    var entity = new TestdataDeclarativeExtendedSubclassValue();
    assertThat(
            GraphStructure.determineGraphStructure(
                TestdataDeclarativeExtendedSolution.buildSolutionDescriptor(), entity))
        .hasFieldOrPropertyWithValue("structure", SINGLE_DIRECTIONAL_PARENT)
        .hasFieldOrPropertyWithValue("direction", ParentVariableType.PREVIOUS);
  }

  @Test
  void extendedSimpleListStructureWithoutDeclarativeEntities() {
    var entity = new TestdataDeclarativeExtendedBaseValue();
    assertThat(
            GraphStructure.determineGraphStructure(
                TestdataDeclarativeExtendedSolution.buildSolutionDescriptor(), entity))
        .hasFieldOrPropertyWithValue("structure", EMPTY);
  }

  @Test
  void concurrentValuesStructureWithoutGroups() {
    var value1 = new TestdataConcurrentValue("v1");
    var value2 = new TestdataConcurrentValue("v2");
    value2.setConcurrentValueGroup(Collections.emptyList());
    assertThat(
            GraphStructure.determineGraphStructure(
                TestdataConcurrentSolution.buildSolutionDescriptor(), value1, value2))
        .hasFieldOrPropertyWithValue("structure", SINGLE_DIRECTIONAL_PARENT)
        .hasFieldOrPropertyWithValue("direction", ParentVariableType.PREVIOUS);
  }

  @Test
  void concurrentValuesStructureWithGroups() {
    var value1 = new TestdataConcurrentValue("v1");
    var value2 = new TestdataConcurrentValue("v2");
    var group = List.of(value1, value2);
    value2.setConcurrentValueGroup(group);
    assertThat(
            GraphStructure.determineGraphStructure(
                TestdataConcurrentSolution.buildSolutionDescriptor(), value1, value2))
        .hasFieldOrPropertyWithValue(
            "structure", ARBITRARY_SINGLE_ENTITY_AT_MOST_ONE_DIRECTIONAL_PARENT_TYPE);
  }

  @Test
  void followerStructure() {
    var entity = new TestdataFollowerEntity();
    assertThat(
            GraphStructure.determineGraphStructure(
                TestdataFollowerSolution.buildSolutionDescriptor(), entity))
        .hasFieldOrPropertyWithValue(
            "structure", ARBITRARY_SINGLE_ENTITY_AT_MOST_ONE_DIRECTIONAL_PARENT_TYPE);
  }

  @Test
  void multiEntity() {
    var entity = new TestdataMultiEntityDependencyEntity();
    var value = new TestdataMultiEntityDependencyValue();
    assertThat(
            GraphStructure.determineGraphStructure(
                TestdataMultiEntityDependencySolution.buildSolutionDescriptor(), entity, value))
        .hasFieldOrPropertyWithValue("structure", ARBITRARY);
  }

  @Test
  void multiDirectionalParents() {
    var entity = new TestdataMultiDirectionConcurrentEntity();
    var value = new TestdataMultiDirectionConcurrentValue();
    value.setConcurrentValueGroup(List.of(value));
    assertThat(
            GraphStructure.determineGraphStructure(
                TestdataMultiDirectionConcurrentSolution.buildSolutionDescriptor(), entity, value))
        .hasFieldOrPropertyWithValue("structure", ARBITRARY);
  }

  @Test
  void multiDirectionalParentsEmptyGroups() {
    var entity = new TestdataMultiDirectionConcurrentEntity();
    var value = new TestdataMultiDirectionConcurrentValue();
    assertThat(
            GraphStructure.determineGraphStructure(
                TestdataMultiDirectionConcurrentSolution.buildSolutionDescriptor(), entity, value))
        .hasFieldOrPropertyWithValue("structure", SINGLE_DIRECTIONAL_PARENT)
        .hasFieldOrPropertyWithValue("direction", ParentVariableType.PREVIOUS);
  }

  @Test
  void emptyStructure() {
    assertThat(GraphStructure.determineGraphStructure(TestdataSolution.buildSolutionDescriptor()))
        .hasFieldOrPropertyWithValue("structure", EMPTY);
  }
}
