package greycos.solver.greybay.impl.sampling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import greycos.solver.greybay.config.Activation;
import greycos.solver.greybay.config.CategoricalDimension;
import greycos.solver.greybay.config.FixedDimension;
import greycos.solver.greybay.config.IntegerDimension;
import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.config.RealDimension;
import greycos.solver.greybay.config.SearchSpace;

import org.junit.jupiter.api.Test;

class FiniteSpaceTest {
  @Test
  void limitSizedGridStopsAtFirstUnreservedAssignment() {
    var space =
        new SearchSpace(
            List.of(
                new CategoricalDimension("template", List.of("A")),
                new IntegerDimension("x", 0, 99_999, 1)));
    var visited = new ArrayList<ParameterAssignment>();
    assertThat(
            FiniteSpace.allMatch(
                space,
                "A",
                assignment -> {
                  visited.add(assignment);
                  return false;
                }))
        .isFalse();
    assertThat(visited).containsExactly(ParameterAssignment.of(Map.of("template", "A", "x", 0)));
  }

  @Test
  void oversizedGridDoesNotResolveAnyAssignment() {
    var space = new SearchSpace(List.of(new IntegerDimension("x", 0, 100_000, 1)));
    assertThat(FiniteSpace.allMatch(space, null, FiniteSpaceTest::unexpectedAssignment)).isFalse();
  }

  @Test
  void conditionalCardinalityUsesTheWholeSelectedSpaceBeforeResolution() {
    var dimensions =
        List.of(
            new CategoricalDimension("template", List.of("A", "B", "C")),
            new IntegerDimension("x", 0, 49_999, 1)
                .withActivation(Activation.in("template", java.util.Set.of("A", "B"))));
    var oversized = new SearchSpace(List.copyOf(dimensions));
    assertThat(FiniteSpace.allMatch(oversized, null, FiniteSpaceTest::unexpectedAssignment))
        .isFalse();
    var visited = new ArrayList<ParameterAssignment>();
    assertThat(
            FiniteSpace.allMatch(
                oversized,
                "C",
                assignment -> {
                  visited.add(assignment);
                  return true;
                }))
        .isTrue();
    assertThat(visited).containsExactly(ParameterAssignment.of(Map.of("template", "C")));

    var bounded =
        new SearchSpace(
            List.of(new CategoricalDimension("template", List.of("A", "B")), dimensions.getLast()));
    visited.clear();
    assertThat(
            FiniteSpace.allMatch(
                bounded,
                null,
                assignment -> {
                  visited.add(assignment);
                  return false;
                }))
        .isFalse();
    assertThat(visited).containsExactly(ParameterAssignment.of(Map.of("template", "A", "x", 0)));
  }

  @Test
  void integerProductsRespectTheGlobalLimit() {
    var bounded =
        new SearchSpace(
            List.of(new IntegerDimension("x", 0, 99, 1), new IntegerDimension("y", 0, 999, 1)));
    var visited = new ArrayList<ParameterAssignment>();
    assertThat(
            FiniteSpace.allMatch(
                bounded,
                null,
                assignment -> {
                  visited.add(assignment);
                  return false;
                }))
        .isFalse();
    assertThat(visited).containsExactly(ParameterAssignment.of(Map.of("x", 0, "y", 0)));
    var oversized =
        new SearchSpace(
            List.of(new IntegerDimension("x", 0, 99, 1), new IntegerDimension("y", 0, 1_000, 1)));
    assertThat(FiniteSpace.allMatch(oversized, null, FiniteSpaceTest::unexpectedAssignment))
        .isFalse();
  }

  @Test
  void continuousBranchPreventsResolutionWithoutBlockingFiniteRoot() {
    var space =
        new SearchSpace(
            List.of(
                new CategoricalDimension("template", List.of("finite", "continuous")),
                new RealDimension("x", 0, 1)
                    .withActivation(Activation.when("template", "continuous")),
                new RealDimension("inactive", 0, 1).withActivation(new Activation(List.of()))));
    assertThat(FiniteSpace.allMatch(space, null, FiniteSpaceTest::unexpectedAssignment)).isFalse();
    assertThat(FiniteSpace.allMatch(space, "continuous", FiniteSpaceTest::unexpectedAssignment))
        .isFalse();
    var visited = new ArrayList<ParameterAssignment>();
    assertThat(
            FiniteSpace.allMatch(
                space,
                "finite",
                assignment -> {
                  visited.add(assignment);
                  return true;
                }))
        .isTrue();
    assertThat(visited).containsExactly(ParameterAssignment.of(Map.of("template", "finite")));
  }

  @Test
  void traversalPreservesConditionalEnumerationOrderAndAliasExhaustion() {
    var space =
        new SearchSpace(
            List.of(
                new CategoricalDimension("template", List.of("A", "B")),
                new CategoricalDimension(
                    "child", List.of("on", "off"), Activation.when("template", "A")),
                new IntegerDimension("x", 2, 4, 2).withActivation(Activation.when("child", "on")),
                new FixedDimension("fixed", 7).withActivation(Activation.when("template", "B"))));
    var expected =
        List.of(
            ParameterAssignment.of(Map.of("template", "A", "child", "on", "x", 2)),
            ParameterAssignment.of(Map.of("template", "A", "child", "on", "x", 4)),
            ParameterAssignment.of(Map.of("template", "A", "child", "off")),
            ParameterAssignment.of(Map.of("template", "B", "fixed", 7)));
    assertThat(FiniteSpace.enumerate(space, null)).containsExactlyElementsOf(expected);
    var visited = new ArrayList<ParameterAssignment>();
    assertThat(
            FiniteSpace.allMatch(
                space,
                null,
                assignment -> {
                  visited.add(assignment);
                  // Both x values materialize the same reserved configuration.
                  return assignment.contains("x");
                }))
        .isFalse();
    assertThat(visited).containsExactlyElementsOf(expected.subList(0, 3));
    visited.clear();
    assertThat(
            FiniteSpace.allMatch(
                space,
                null,
                assignment -> {
                  visited.add(assignment);
                  return true;
                }))
        .isTrue();
    assertThat(visited).containsExactlyElementsOf(expected);
  }

  private static boolean unexpectedAssignment(ParameterAssignment assignment) {
    throw new AssertionError("Must establish the finite bound before resolving " + assignment);
  }
}
