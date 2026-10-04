package greycos.solver.core.preview.api.move.builtin;

import static org.assertj.core.api.Assertions.assertThat;

import greycos.solver.core.preview.api.neighborhood.test.NeighborhoodTester;
import greycos.solver.core.testcotwin.pinned.entityvalue.TestdataPinnedValueEntity;
import greycos.solver.core.testcotwin.pinned.entityvalue.TestdataPinnedValueSolution;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PinnedEntityValueMoveProviderTest {

  @ParameterizedTest
  @CsvSource({
    "solutionValue,false,false",
    "entityValue,false,false",
    "nullableSolutionValue,false,false",
    "nullableEntityValue,false,false",
    "nullableSolutionValue,true,false",
    "nullableEntityValue,true,false",
    "nullableSolutionValue,true,true",
    "nullableEntityValue,true,true"
  })
  void changeIncludesPinnedDestinationsButExcludesPinnedSources(
      String variableName, boolean crossingNull, boolean initiallyUnassigned) {
    var descriptor = TestdataPinnedValueSolution.buildSolutionDescriptor();
    var variableDescriptor =
        descriptor
            .findEntityDescriptorOrFail(TestdataPinnedValueEntity.class)
            .getGenuineVariableDescriptor(variableName);
    var model = descriptor.getMetaModel();
    var variable =
        model
            .genuineEntity(TestdataPinnedValueEntity.class)
            .basicVariable(variableName, TestdataPinnedValueEntity.class);
    var solution = TestdataPinnedValueSolution.generateSolution();
    var first = solution.getEntityList().getFirst();
    var pinned = solution.getEntityList().get(1);
    var outsideRange = solution.getEntityList().get(2);
    if (initiallyUnassigned) {
      variableDescriptor.setValue(first, null);
      variableDescriptor.setValue(pinned, null);
    }
    var originalValue = variableDescriptor.<TestdataPinnedValueEntity>getValue(first);
    var pinnedOriginalValue = variableDescriptor.<TestdataPinnedValueEntity>getValue(pinned);
    var context =
        NeighborhoodTester.build(new ChangeMoveProvider<>(variable, crossingNull), model)
            .using(solution);
    var toPinned = Moves.change(variable, first, pinned);

    context.producesAllOf(toPinned);
    context.producesNoneOf(
        Moves.change(variable, first, outsideRange),
        Moves.change(variable, first, initiallyUnassigned ? null : first),
        Moves.change(variable, pinned, first),
        Moves.change(variable, pinned, pinned),
        Moves.change(variable, outsideRange, pinned));
    if (crossingNull) {
      context.producesAllOf(Moves.change(variable, first, initiallyUnassigned ? first : null));
      context.producesNoneOf(Moves.change(variable, pinned, null));
    } else if (variable.allowsUnassigned()) {
      context.producesNoneOf(Moves.change(variable, first, null));
    }

    var generated = context.getMovesAsStream().limit(1_000).filter(toPinned::equals).findFirst();
    assertThat(generated).as("Move to the pinned entity is generated").isPresent();
    context
        .getMoveTestContext()
        .executeTemporarily(
            generated.orElseThrow(),
            view -> {
              assertThat(variableDescriptor.<TestdataPinnedValueEntity>getValue(first))
                  .isSameAs(pinned);
              assertThat(variableDescriptor.<TestdataPinnedValueEntity>getValue(pinned))
                  .isSameAs(pinnedOriginalValue);
              assertThat(variableDescriptor.<TestdataPinnedValueEntity>getValue(outsideRange))
                  .isSameAs(first);
            });
    assertThat(variableDescriptor.<TestdataPinnedValueEntity>getValue(first))
        .isSameAs(originalValue);
    assertThat(variableDescriptor.<TestdataPinnedValueEntity>getValue(pinned))
        .isSameAs(pinnedOriginalValue);
    assertThat(variableDescriptor.<TestdataPinnedValueEntity>getValue(outsideRange))
        .isSameAs(first);
  }

  @ParameterizedTest
  @ValueSource(strings = {"nullableSolutionValue", "nullableEntityValue"})
  void changeWithoutCrossingNullStillExcludesUnassignedSources(String variableName) {
    var descriptor = TestdataPinnedValueSolution.buildSolutionDescriptor();
    var variableDescriptor =
        descriptor
            .findEntityDescriptorOrFail(TestdataPinnedValueEntity.class)
            .getGenuineVariableDescriptor(variableName);
    var model = descriptor.getMetaModel();
    var variable =
        model
            .genuineEntity(TestdataPinnedValueEntity.class)
            .basicVariable(variableName, TestdataPinnedValueEntity.class);
    var solution = TestdataPinnedValueSolution.generateSolution();
    variableDescriptor.setValue(solution.getEntityList().getFirst(), null);

    var context =
        NeighborhoodTester.build(new ChangeMoveProvider<>(variable, false), model).using(solution);
    assertThat(context.getMovesAsIterator()).isExhausted();
  }

  @ParameterizedTest
  @ValueSource(strings = {"nullableSolutionValue", "nullableEntityValue"})
  void assignIncludesPinnedDestinationsButExcludesPinnedSources(String variableName) {
    var descriptor = TestdataPinnedValueSolution.buildSolutionDescriptor();
    var variableDescriptor =
        descriptor
            .findEntityDescriptorOrFail(TestdataPinnedValueEntity.class)
            .getGenuineVariableDescriptor(variableName);
    var model = descriptor.getMetaModel();
    var variable =
        model
            .genuineEntity(TestdataPinnedValueEntity.class)
            .basicVariable(variableName, TestdataPinnedValueEntity.class);
    var solution = TestdataPinnedValueSolution.generateSolution();
    var first = solution.getEntityList().getFirst();
    var pinned = solution.getEntityList().get(1);
    var outsideRange = solution.getEntityList().get(2);
    variableDescriptor.setValue(first, null);
    variableDescriptor.setValue(pinned, null);
    var context =
        NeighborhoodTester.build(new AssignMoveProvider<>(variable), model).using(solution);
    var toPinned = Moves.change(variable, first, pinned);

    context.producesAllOf(toPinned, Moves.change(variable, first, first));
    context.producesNoneOf(
        Moves.change(variable, first, outsideRange),
        Moves.change(variable, first, null),
        Moves.change(variable, pinned, first),
        Moves.change(variable, pinned, pinned),
        Moves.change(variable, outsideRange, pinned));

    var generated = context.getMovesAsStream().limit(1_000).filter(toPinned::equals).findFirst();
    assertThat(generated).as("Assignment to the pinned entity is generated").isPresent();
    context
        .getMoveTestContext()
        .executeTemporarily(
            generated.orElseThrow(),
            view -> {
              assertThat(variableDescriptor.<TestdataPinnedValueEntity>getValue(first))
                  .isSameAs(pinned);
              assertThat(variableDescriptor.<TestdataPinnedValueEntity>getValue(pinned)).isNull();
              assertThat(variableDescriptor.<TestdataPinnedValueEntity>getValue(outsideRange))
                  .isSameAs(first);
            });
    assertThat(variableDescriptor.<TestdataPinnedValueEntity>getValue(first)).isNull();
    assertThat(variableDescriptor.<TestdataPinnedValueEntity>getValue(pinned)).isNull();
    assertThat(variableDescriptor.<TestdataPinnedValueEntity>getValue(outsideRange))
        .isSameAs(first);
  }
}
