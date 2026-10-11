package greycos.solver.greybay.impl.sampling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import greycos.solver.greybay.config.Activation;
import greycos.solver.greybay.config.CategoricalDimension;
import greycos.solver.greybay.config.IntegerDimension;
import greycos.solver.greybay.config.NumericPrior;
import greycos.solver.greybay.config.ParameterAssignment;
import greycos.solver.greybay.config.RealDimension;
import greycos.solver.greybay.config.SearchSpace;

import org.junit.jupiter.api.Test;

class SearchSpaceTest {
  @Test
  void validatesConditionalTypedAssignments() {
    var space =
        new SearchSpace(
            List.of(
                new CategoricalDimension("choice", List.of("A", "B")),
                new IntegerDimension("history", 1, 10, 1)
                    .withActivation(Activation.when("choice", "A"))));
    space.validate(ParameterAssignment.of(Map.of("choice", "A", "history", 4)));
    space.validate(ParameterAssignment.of(Map.of("choice", "B")));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> space.validate(ParameterAssignment.of(Map.of("choice", "A"))));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> space.validate(ParameterAssignment.of(Map.of("choice", "B", "history", 4))));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> space.validate(ParameterAssignment.of(Map.of("choice", "A", "history", 4.0))));
  }

  @Test
  void rejectsInvalidSchemasAndUnresolvedEveryCellPrecision() {
    assertThatIllegalArgumentException().isThrownBy(() -> new IntegerDimension("bad", 1, 8, 2));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new IntegerDimension("bad", 0, 5, 1, NumericPrior.LOGARITHMIC));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new IntegerDimension("collapsed", (1L << 53) - 4, 1L << 53, 1));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new IntegerDimension(
                    "collapsedLog", (1L << 52) - 5, (1L << 52) + 5, 1, NumericPrior.LOGARITHMIC));
    assertThatIllegalArgumentException().isThrownBy(() -> new RealDimension("nan", Double.NaN, 1));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RealDimension("width", -Double.MAX_VALUE, Double.MAX_VALUE));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new CategoricalDimension("duplicate", List.of("x", "x")));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new CategoricalDimension(
                    "unreachable",
                    List.of("a", "b", "c"),
                    List.of(1.0, 1e-20, 1.0),
                    Activation.always()));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new SearchSpace(
                    List.of(
                        new CategoricalDimension("a", List.of("x"), Activation.when("b", "x")),
                        new CategoricalDimension("b", List.of("x"), Activation.when("a", "x")))));
    // A broad, exactly representable grid is accepted without iterating trillions of cells.
    assertThat(new IntegerDimension("wide", -1_000_000_000_000L, 1_000_000_000_000L, 1).gridCount())
        .isEqualTo(2_000_000_000_001L);
  }

  @Test
  void assignmentEncodingIsTypedOrderedAndRoundTripExact() {
    var left = ParameterAssignment.of(Map.of("real", -0.0, "integer", 5, "category", "5"));
    var ordered = new LinkedHashMap<String, Object>();
    ordered.put("category", "5");
    ordered.put("integer", 5L);
    ordered.put("real", 0.0);
    assertThat(left).isEqualTo(ParameterAssignment.of(ordered));
    assertThat(left.canonicalForm()).isEqualTo(ParameterAssignment.of(ordered).canonicalForm());
    assertThat(ParameterAssignment.fromMap(left.toMap())).isEqualTo(left);
    assertThat(ParameterAssignment.of(Map.of("x", 1)).canonicalForm())
        .isNotEqualTo(ParameterAssignment.of(Map.of("x", 1.0)).canonicalForm());
    assertThat(ParameterAssignment.of(Map.of("x", Math.nextUp(1.0))).toMap().toString())
        .contains("0x1.0000000000001p0");
  }

  @Test
  void combinePreservesRootOrderAndDoesNotPoolSiblingContext() {
    var local = new SearchSpace(List.of(new IntegerDimension("x", 1, 3, 1)));
    var templates = new LinkedHashMap<String, SearchSpace>();
    templates.put("B", local);
    templates.put("A", local);
    var combined = SearchSpace.combine("1", templates);
    assertThat(combined.rootChoices()).containsExactly("B", "A");
    assertThat(combined.dimensions()).hasSize(2);
    var dimension = combined.dimensions().get(1);
    assertThat(combined.contextNames(dimension)).containsExactly("template");
    assertThat(
            combined.contextKey(dimension, ParameterAssignment.of(Map.of("template", "A", "x", 2))))
        .isNotEqualTo(
            combined.contextKey(
                dimension, ParameterAssignment.of(Map.of("template", "B", "x", 2))));
    combined.validate(ParameterAssignment.of(Map.of("template", "A", "x", 2)));
  }

  @Test
  void schemaCanonicalEncodingDoesNotUseAmbiguousCollectionDisplayStrings() {
    var first = new SearchSpace("v", List.of(new CategoricalDimension("x", List.of("a, b", "c"))));
    var second = new SearchSpace("v", List.of(new CategoricalDimension("x", List.of("a", "b, c"))));
    assertThat(first.canonicalForm()).isNotEqualTo(second.canonicalForm());
    assertThat(first.canonicalForm())
        .isEqualTo(new SearchSpace("v", first.dimensions()).canonicalForm());
  }

  @Test
  void combinedSharedDimensionWaitsForEveryBranchAncestor() {
    var first =
        new SearchSpace(
            List.of(
                new CategoricalDimension("a", List.of("yes")),
                new IntegerDimension("shared", 0, 1, 1)
                    .withActivation(Activation.when("a", "yes"))));
    var second =
        new SearchSpace(
            List.of(
                new CategoricalDimension("b", List.of("yes")),
                new IntegerDimension("shared", 0, 1, 1)
                    .withActivation(Activation.when("b", "yes"))));
    var spaces = new LinkedHashMap<String, SearchSpace>();
    spaces.put("A", first);
    spaces.put("B", second);
    var combined = SearchSpace.combine("1", spaces);
    combined.validate(ParameterAssignment.of(Map.of("template", "A", "a", "yes", "shared", 1)));
    combined.validate(ParameterAssignment.of(Map.of("template", "B", "b", "yes", "shared", 1)));
    assertThat(combined.dimensions().getLast().name()).isEqualTo("shared");
  }
}
