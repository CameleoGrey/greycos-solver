package greycos.solver.core.impl.cotwin.variable.declarative;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.config.solver.EnvironmentMode;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.score.director.easy.EasyScoreDirectorFactory;

import org.junit.jupiter.api.Test;

class MultipleListNextDirectionalShadowTest {

  @Test
  void nextDirectionalGraphUsesSecondListForReorderingTransferAndUnassignment() {
    var solution = NextSolution.create();
    var descriptor = NextSolution.descriptor();
    assertThat(descriptor.getListVariableDescriptorList())
        .extracting(variable -> variable.getVariableName())
        .containsExactly("firsts", "seconds");
    var firstDescriptor = descriptor.getListVariableDescriptorList().get(0);
    var secondDescriptor = descriptor.getListVariableDescriptorList().get(1);
    var structure = GraphStructure.determineGraphStructure(descriptor, solution.seconds.toArray());
    assertThat(structure.structure()).isEqualTo(GraphStructure.SINGLE_DIRECTIONAL_PARENT);
    assertThat(structure.direction()).isEqualTo(ParentVariableType.NEXT);
    assertThat(
            descriptor
                .getEntityDescriptorStrict(NextValue.class)
                .getShadowVariableDescriptor("next")
                .getSourceVariableDescriptor())
        .isSameAs(secondDescriptor);

    var firstOwner = solution.owners.get(0);
    var secondOwner = solution.owners.get(1);
    var originalFirstLists =
        solution.owners.stream().map(owner -> List.copyOf(owner.firsts)).toList();
    try (var director =
        new EasyScoreDirectorFactory<>(
                descriptor, ignored -> SimpleScore.ZERO, EnvironmentMode.NO_ASSERT)
            .buildScoreDirector()) {
      director.setWorkingSolution(solution);
      assertThat(director.getListVariableState(firstDescriptor))
          .isNotSameAs(director.getListVariableState(secondDescriptor));
      assertSuffixTotalsAndFirstLists(solution, originalFirstLists);

      director.beforeListVariableChanged(secondDescriptor, firstOwner, 0, 4);
      Collections.reverse(firstOwner.seconds);
      director.afterListVariableChanged(secondDescriptor, firstOwner, 0, 4);
      director.updateShadowVariables();
      assertSuffixTotalsAndFirstLists(solution, originalFirstLists);

      director.beforeListVariableChanged(secondDescriptor, firstOwner, 1, 4);
      Collections.swap(firstOwner.seconds, 1, 3);
      director.afterListVariableChanged(secondDescriptor, firstOwner, 1, 4);
      director.updateShadowVariables();
      assertSuffixTotalsAndFirstLists(solution, originalFirstLists);

      // The unchanged prefix must receive the suffix change through the second list's state.
      director.beforeListVariableChanged(secondDescriptor, firstOwner, 2, 4);
      director.beforeListVariableChanged(secondDescriptor, secondOwner, 1, 2);
      var transferred = firstOwner.seconds.remove(2);
      secondOwner.seconds.add(1, transferred);
      director.afterListVariableChanged(secondDescriptor, firstOwner, 2, 3);
      director.afterListVariableChanged(secondDescriptor, secondOwner, 1, 3);
      director.updateShadowVariables();
      assertThat(transferred.owner).isSameAs(secondOwner);
      assertSuffixTotalsAndFirstLists(solution, originalFirstLists);

      var middle = secondOwner.seconds.get(1);
      director.beforeListVariableElementUnassigned(secondDescriptor, middle);
      director.beforeListVariableChanged(secondDescriptor, secondOwner, 1, 3);
      secondOwner.seconds.remove(1);
      director.afterListVariableChanged(secondDescriptor, secondOwner, 1, 2);
      director.afterListVariableElementUnassigned(secondDescriptor, middle);
      director.updateShadowVariables();
      assertSuffixTotalsAndFirstLists(solution, originalFirstLists);

      var last = firstOwner.seconds.get(2);
      director.beforeListVariableElementUnassigned(secondDescriptor, last);
      director.beforeListVariableChanged(secondDescriptor, firstOwner, 2, 3);
      firstOwner.seconds.remove(2);
      director.afterListVariableChanged(secondDescriptor, firstOwner, 2, 2);
      director.afterListVariableElementUnassigned(secondDescriptor, last);
      director.updateShadowVariables();
      assertSuffixTotalsAndFirstLists(solution, originalFirstLists);
    }
  }

  private static void assertSuffixTotalsAndFirstLists(
      NextSolution solution, List<List<FirstValue>> originalFirstLists) {
    for (var ownerIndex = 0; ownerIndex < solution.owners.size(); ownerIndex++) {
      var owner = solution.owners.get(ownerIndex);
      assertThat(owner.firsts).containsExactlyElementsOf(originalFirstLists.get(ownerIndex));
      for (var index = 0; index < owner.firsts.size(); index++) {
        assertThat(owner.firsts.get(index).index).isEqualTo(index);
      }
      var suffixTotal = 0;
      for (var index = owner.seconds.size() - 1; index >= 0; index--) {
        var value = owner.seconds.get(index);
        suffixTotal += value.amount;
        assertThat(value.total).as("suffix total at index %s", index).isEqualTo(suffixTotal);
        assertThat(value.owner).isSameAs(owner);
        assertThat(value.index).isEqualTo(index);
        assertThat(value.next)
            .isSameAs(index + 1 < owner.seconds.size() ? owner.seconds.get(index + 1) : null);
      }
    }
    for (var value : solution.seconds) {
      if (solution.owners.stream().noneMatch(owner -> owner.seconds.contains(value))) {
        assertThat(value.owner).isNull();
        assertThat(value.index).isNull();
        assertThat(value.next).isNull();
        assertThat(value.total).isNull();
      }
    }
  }

  @PlanningSolution
  public static class NextSolution {
    @PlanningEntityCollectionProperty public List<Owner> owners;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "firstRange")
    public List<FirstValue> firsts;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider(id = "secondRange")
    public List<NextValue> seconds;

    @PlanningScore public SimpleScore score;

    static SolutionDescriptor<NextSolution> descriptor() {
      return SolutionDescriptor.buildSolutionDescriptor(
          NextSolution.class, Owner.class, FirstValue.class, NextValue.class);
    }

    static NextSolution create() {
      var solution = new NextSolution();
      solution.firsts = List.of(new FirstValue(), new FirstValue(), new FirstValue());
      solution.seconds =
          List.of(
              new NextValue(2),
              new NextValue(3),
              new NextValue(5),
              new NextValue(7),
              new NextValue(11),
              new NextValue(13));
      var firstOwner = new Owner();
      firstOwner.firsts.add(solution.firsts.get(0));
      firstOwner.seconds.addAll(solution.seconds.subList(0, 4));
      var secondOwner = new Owner();
      secondOwner.firsts.addAll(solution.firsts.subList(1, 3));
      secondOwner.seconds.addAll(solution.seconds.subList(4, 6));
      solution.owners = List.of(firstOwner, secondOwner);
      return solution;
    }
  }

  @PlanningEntity
  public static class Owner {
    @PlanningListVariable(valueRangeProviderRefs = "firstRange")
    public List<FirstValue> firsts = new ArrayList<>();

    @PlanningListVariable(valueRangeProviderRefs = "secondRange", allowsUnassignedValues = true)
    public List<NextValue> seconds = new ArrayList<>();
  }

  @PlanningEntity
  public static class FirstValue {
    @IndexShadowVariable(sourceVariableName = "firsts")
    public Integer index;
  }

  @PlanningEntity
  public static class NextValue {
    public final int amount;

    @IndexShadowVariable(sourceVariableName = "seconds")
    public Integer index;

    @InverseRelationShadowVariable(sourceVariableName = "seconds")
    public Owner owner;

    @NextElementShadowVariable(sourceVariableName = "seconds")
    public NextValue next;

    @ShadowVariable(supplierName = "totalSupplier")
    public Integer total;

    public NextValue() {
      this(0);
    }

    public NextValue(int amount) {
      this.amount = amount;
    }

    @ShadowSources({"owner", "next.total"})
    public Integer totalSupplier() {
      if (owner == null || (next != null && next.total == null)) {
        return null;
      }
      return amount + (next == null ? 0 : next.total);
    }
  }
}
