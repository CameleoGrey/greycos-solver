package greycos.solver.core.impl.cotwin.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PlanningVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolutionManager;
import greycos.solver.core.testcotwin.shadow.basic.TestdataBasicVarEntity;
import greycos.solver.core.testcotwin.shadow.basic.TestdataBasicVarSolution;
import greycos.solver.core.testcotwin.shadow.basic.TestdataBasicVarValue;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

/** Relationship reconstruction regressions with independently constructed controls. */
class ShadowVariableReconstructionTest {

  @Test
  void wholeSolutionBasicReassignmentControl() {
    var oracle = basicSolution(false, false);
    SolutionManager.updateShadowVariables(oracle);
    assertBasicFinalState(oracle);
  }

  @Test
  void entityLevelBasicReassignmentMustRemoveOldInverseAndRefreshDependentShadows() {
    var oracle = basicSolution(false, false);
    SolutionManager.updateShadowVariables(oracle);
    assertBasicFinalState(oracle);

    var actual = basicSolution(true, false);
    SolutionManager.updateShadowVariables(TestdataBasicVarSolution.class, basicEntities(actual));
    assertThat(actual.getValues().get(0).getEntityList())
        .containsExactly(actual.getEntities().get(0));
    actual.getEntities().get(0).setValue(actual.getValues().get(1));
    SolutionManager.updateShadowVariables(TestdataBasicVarSolution.class, basicEntities(actual));

    SoftAssertions.assertSoftly(
        softly -> {
          softly
              .assertThat(actual.getValues().get(0).getEntityList())
              .as("old inverse A")
              .isEmpty();
          softly
              .assertThat(actual.getValues().get(1).getEntityList())
              .as("new inverse B")
              .containsExactlyInAnyOrderElementsOf(actual.getEntities());
          softly
              .assertThat(actual.getValues().stream().mapToInt(v -> v.getEntityList().size()).sum())
              .as("total inverse membership must equal two assigned entities")
              .isEqualTo(2);
          for (var i = 0; i < actual.getValues().size(); i++) {
            softly
                .assertThat(actual.getValues().get(i).getStartTime())
                .as("start time for value %s", i)
                .isEqualTo(oracle.getValues().get(i).getStartTime());
            softly
                .assertThat(actual.getValues().get(i).getEndTime())
                .as("end time for value %s", i)
                .isEqualTo(oracle.getValues().get(i).getEndTime());
          }
        });
  }

  @Test
  void wholeSolutionNullBasicAssignmentControl() {
    var oracle = basicSolution(false, true);
    SolutionManager.updateShadowVariables(oracle);
    assertThat(oracle.getEntities().get(0).getDurationInDays()).isZero();
    assertThat(oracle.getValues().get(0).getEntityList()).isEmpty();
    assertThat(oracle.getValues().get(1).getEntityList())
        .containsExactly(oracle.getEntities().get(1));
  }

  @Test
  void entityLevelNullBasicAssignmentMustMatchWholeSolutionUpdate() {
    var oracle = basicSolution(false, true);
    SolutionManager.updateShadowVariables(oracle);
    var actual = basicSolution(false, true);
    assertThatCode(
            () ->
                SolutionManager.updateShadowVariables(
                    TestdataBasicVarSolution.class, basicEntities(actual)))
        .doesNotThrowAnyException();
    assertThat(actual.getEntities().get(0).getDurationInDays())
        .isEqualTo(oracle.getEntities().get(0).getDurationInDays());
  }

  @Test
  void wholeSolutionRemovedListValueControl() {
    var oracle = listSolution(true);
    SolutionManager.updateShadowVariables(oracle);
    assertListFinalState(oracle);
  }

  @Test
  void entityLevelRemovedListValueMustClearAllFourBuiltInShadows() {
    var oracle = listSolution(true);
    SolutionManager.updateShadowVariables(oracle);
    assertListFinalState(oracle);

    var actual = listSolution(false);
    SolutionManager.updateShadowVariables(ListSolution.class, listEntities(actual));
    var removed = actual.values.get(1);
    assertThat(removed.owner).isSameAs(actual.owners.getFirst());
    assertThat(removed.index).isEqualTo(1);
    assertThat(removed.previous).isSameAs(actual.values.get(0));
    assertThat(removed.next).isSameAs(actual.values.get(2));
    actual.owners.getFirst().items.remove(removed);
    SolutionManager.updateShadowVariables(ListSolution.class, listEntities(actual));

    SoftAssertions.assertSoftly(
        softly -> {
          softly.assertThat(removed.owner).as("removed inverse").isNull();
          softly.assertThat(removed.index).as("removed index").isNull();
          softly.assertThat(removed.previous).as("removed previous").isNull();
          softly.assertThat(removed.next).as("removed next").isNull();
          softly
              .assertThat(actual.values.stream().filter(v -> v.owner != null).count())
              .as("assigned inverse count")
              .isEqualTo(2);
          softly.assertThat(actual.values.get(0).next).isSameAs(actual.values.get(2));
          softly.assertThat(actual.values.get(2).previous).isSameAs(actual.values.get(0));
          softly.assertThat(actual.values.get(2).index).isEqualTo(1);
        });
  }

  @Test
  void wholeSolutionMultipleInverseSourcesControl() {
    var oracle = dualSolution();
    SolutionManager.updateShadowVariables(oracle);
    assertDualFinalState(oracle);
  }

  @Test
  void entityLevelMultipleInverseSourcesMustUseMatchingSourceVariableName() {
    var oracle = dualSolution();
    SolutionManager.updateShadowVariables(oracle);
    assertDualFinalState(oracle);
    var actual = dualSolution();
    var entities = Stream.concat(actual.entities.stream(), actual.values.stream()).toArray();
    SolutionManager.updateShadowVariables(DualSolution.class, entities);
    assertDualFinalState(actual);
  }

  @Test
  void repeatedBasicAssignmentsPreserveCollectionsAndInputOrder() {
    var solution = dualSolution();
    var a = solution.values.get(0);
    var b = solution.values.get(1);
    var first = solution.entities.get(0);
    var second = solution.entities.get(1);
    var aList = a.primaryEntities;
    var aSet = a.secondaryEntities;
    var bList = b.primaryEntities;
    var bSet = b.secondaryEntities;
    for (var assignment : new DualValue[] {a, b, null, a}) {
      first.primary = assignment;
      first.secondary = assignment;
      for (var repeat = 0; repeat < 2; repeat++) {
        SolutionManager.updateShadowVariables(DualSolution.class, second, first, a, b, first);
        assertThat(a.primaryEntities).isSameAs(aList);
        assertThat(a.secondaryEntities).isSameAs(aSet);
        assertThat(b.primaryEntities).isSameAs(bList);
        assertThat(b.secondaryEntities).isSameAs(bSet);
        assertThat(a.primaryEntities)
            .containsExactlyElementsOf(assignment == a ? List.of(first) : List.of());
        assertThat(b.primaryEntities)
            .containsExactlyElementsOf(assignment == b ? List.of(second, first) : List.of(second));
        assertThat(a.secondaryEntities)
            .containsExactlyElementsOf(assignment == a ? List.of(second, first) : List.of(second));
        assertThat(b.secondaryEntities)
            .containsExactlyElementsOf(assignment == b ? List.of(first) : List.of());
      }
    }
  }

  @Test
  void omittedBasicTargetsAreNotMutatedEvenWhenTheirClassIsKnown() {
    var solution = dualSolution();
    var a = solution.values.get(0);
    var b = solution.values.get(1);
    var first = solution.entities.get(0);
    a.primaryEntities.add(first);
    b.primaryEntities.add(first);
    SolutionManager.updateShadowVariables(DualSolution.class, first, a);
    assertThat(a.primaryEntities).containsExactly(first);
    assertThat(b.primaryEntities).containsExactly(first);
    assertThat(b.secondaryEntities).isEmpty();
  }

  @Test
  void nullUnreferencedInverseFailsBeforeAnyCollectionIsCleared() {
    var solution = dualSolution();
    var a = solution.values.get(0);
    var b = solution.values.get(1);
    var first = solution.entities.get(0);
    a.primaryEntities.add(first);
    b.secondaryEntities = null;
    assertThatThrownBy(() -> SolutionManager.updateShadowVariables(DualSolution.class, first, a, b))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sourceVariableName variable (secondaryEntities) which is null");
    assertThat(a.primaryEntities).containsExactly(first);
  }

  @Test
  void listReorderTransferAndEmptyListsReconstructAllRelationships() {
    var solution = listSolution(false);
    var firstOwner = solution.owners.getFirst();
    var secondOwner = new ListOwner();
    solution.owners = List.of(firstOwner, secondOwner);
    var first = solution.values.get(0);
    var second = solution.values.get(1);
    var third = solution.values.get(2);
    SolutionManager.updateShadowVariables(ListSolution.class, listEntities(solution));
    Collections.reverse(firstOwner.items);
    SolutionManager.updateShadowVariables(ListSolution.class, listEntities(solution));
    assertThat(third.index).isZero();
    assertThat(third.previous).isNull();
    assertThat(third.next).isSameAs(second);
    assertThat(first.index).isEqualTo(2);
    assertThat(first.next).isNull();
    firstOwner.items.remove(second);
    secondOwner.items.add(second);
    SolutionManager.updateShadowVariables(ListSolution.class, listEntities(solution));
    assertThat(second.owner).isSameAs(secondOwner);
    assertThat(second.index).isZero();
    assertThat(second.previous).isNull();
    assertThat(second.next).isNull();
    assertThat(third.next).isSameAs(first);
    assertThat(first.previous).isSameAs(third);
    for (var repeat = 0; repeat < 2; repeat++) {
      firstOwner.items.clear();
      secondOwner.items.clear();
      SolutionManager.updateShadowVariables(ListSolution.class, listEntities(solution));
      for (var value : solution.values) {
        assertThat(value.owner).isNull();
        assertThat(value.index).isNull();
        assertThat(value.previous).isNull();
        assertThat(value.next).isNull();
      }
    }
  }

  @Test
  void listRuntimeSubtypesAndOmittedTargets() {
    var baseOwner = new ListOwner();
    var childOwner = new ListOwnerSubclass();
    var base = new ListItem();
    var child = new ListItemSubclass();
    var omitted = new ListItem();
    omitted.index = 99;
    childOwner.items.addAll(List.of(base, child, omitted));
    SolutionManager.updateShadowVariables(ListSolution.class, child, childOwner, baseOwner, base);
    assertThat(base.owner).isSameAs(childOwner);
    assertThat(child.owner).isSameAs(childOwner);
    assertThat(child.index).isEqualTo(1);
    assertThat(child.previous).isSameAs(base);
    assertThat(child.next).isSameAs(omitted);
    assertThat(omitted.owner).isNull();
    assertThat(omitted.index).isEqualTo(99);
  }

  @Test
  void inheritedVariablesAndIdenticallyNamedUnrelatedSourcesStaySeparate() {
    var a = new SameNameSourceA();
    var child = new SameNameSourceAChild();
    var b = new SameNameSourceB();
    var value = new SameNameValue();
    a.value = value;
    child.value = value;
    b.value = value;
    SolutionManager.updateShadowVariables(SameNameSolution.class, a, child, b, value);
    assertThat(value.sourcesA).containsExactly(a, child);
    assertThat(value.sourcesB).containsExactly(b);
    a.value = null;
    SolutionManager.updateShadowVariables(SameNameSolution.class, child, a, b, value);
    assertThat(value.sourcesA).containsExactly(child);
    assertThat(value.sourcesB).containsExactly(b);
  }

  private static TestdataBasicVarSolution basicSolution(boolean initial, boolean unassigned) {
    var a = new TestdataBasicVarValue("A", Duration.ofDays(2));
    var b = new TestdataBasicVarValue("B", Duration.ofDays(3));
    var e1 = new TestdataBasicVarEntity("e1", unassigned ? null : initial ? a : b);
    var e2 = new TestdataBasicVarEntity("e2", b);
    return new TestdataBasicVarSolution(List.of(e1, e2), List.of(a, b), List.of());
  }

  private static Object[] basicEntities(TestdataBasicVarSolution solution) {
    return Stream.concat(solution.getEntities().stream(), solution.getValues().stream()).toArray();
  }

  private static void assertBasicFinalState(TestdataBasicVarSolution solution) {
    assertThat(solution.getValues().get(0).getEntityList()).isEmpty();
    assertThat(solution.getValues().get(1).getEntityList())
        .containsExactlyInAnyOrderElementsOf(solution.getEntities());
    assertThat(solution.getValues().get(0).getStartTime())
        .isEqualTo(TestdataBasicVarValue.DEFAULT_TIME.plusDays(10));
    assertThat(solution.getValues().get(0).getEndTime())
        .isEqualTo(TestdataBasicVarValue.DEFAULT_TIME.plusDays(12));
    assertThat(solution.getValues().get(1).getStartTime())
        .isEqualTo(TestdataBasicVarValue.DEFAULT_TIME.plusDays(2));
    assertThat(solution.getValues().get(1).getEndTime())
        .isEqualTo(TestdataBasicVarValue.DEFAULT_TIME.plusDays(5));
  }

  private static ListSolution listSolution(boolean removed) {
    var solution = new ListSolution();
    solution.values = List.of(new ListItem(), new ListItem(), new ListItem());
    var owner = new ListOwner();
    owner.items.addAll(solution.values);
    if (removed) {
      owner.items.remove(1);
    }
    solution.owners = List.of(owner);
    return solution;
  }

  private static Object[] listEntities(ListSolution solution) {
    return Stream.concat(solution.owners.stream(), solution.values.stream()).toArray();
  }

  private static void assertListFinalState(ListSolution solution) {
    var removed = solution.values.get(1);
    assertThat(removed.owner).isNull();
    assertThat(removed.index).isNull();
    assertThat(removed.previous).isNull();
    assertThat(removed.next).isNull();
    assertThat(solution.values.get(0).owner).isSameAs(solution.owners.getFirst());
    assertThat(solution.values.get(0).index).isZero();
    assertThat(solution.values.get(0).next).isSameAs(solution.values.get(2));
    assertThat(solution.values.get(2).previous).isSameAs(solution.values.get(0));
    assertThat(solution.values.get(2).index).isEqualTo(1);
  }

  private static DualSolution dualSolution() {
    var solution = new DualSolution();
    var a = new DualValue();
    var b = new DualValue();
    var e1 = new DualEntity();
    e1.primary = a;
    e1.secondary = b;
    var e2 = new DualEntity();
    e2.primary = b;
    e2.secondary = a;
    solution.entities = List.of(e1, e2);
    solution.values = List.of(a, b);
    return solution;
  }

  private static void assertDualFinalState(DualSolution solution) {
    SoftAssertions.assertSoftly(
        softly -> {
          softly
              .assertThat(solution.values.get(0).primaryEntities)
              .as("A primary inverse")
              .containsExactly(solution.entities.get(0));
          softly
              .assertThat(solution.values.get(0).secondaryEntities)
              .as("A secondary inverse")
              .containsExactly(solution.entities.get(1));
          softly
              .assertThat(solution.values.get(1).primaryEntities)
              .as("B primary inverse")
              .containsExactly(solution.entities.get(1));
          softly
              .assertThat(solution.values.get(1).secondaryEntities)
              .as("B secondary inverse")
              .containsExactly(solution.entities.get(0));
        });
  }

  @PlanningSolution
  public static class ListSolution {
    @PlanningEntityCollectionProperty public List<ListOwner> owners;
    @PlanningEntityCollectionProperty @ValueRangeProvider public List<ListItem> values;
    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class ListOwner {
    @PlanningListVariable(allowsUnassignedValues = true)
    public List<ListItem> items = new ArrayList<>();
  }

  @PlanningEntity
  public static class ListItem {
    @InverseRelationShadowVariable(sourceVariableName = "items")
    public ListOwner owner;

    @IndexShadowVariable(sourceVariableName = "items")
    public Integer index;

    @PreviousElementShadowVariable(sourceVariableName = "items")
    public ListItem previous;

    @NextElementShadowVariable(sourceVariableName = "items")
    public ListItem next;
  }

  @PlanningSolution
  public static class DualSolution {
    @PlanningEntityCollectionProperty public List<DualEntity> entities;
    @PlanningEntityCollectionProperty @ValueRangeProvider public List<DualValue> values;
    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class DualEntity {
    @PlanningVariable public DualValue primary;
    @PlanningVariable public DualValue secondary;
  }

  @PlanningEntity
  public static class DualValue {
    @InverseRelationShadowVariable(sourceVariableName = "primary")
    public List<DualEntity> primaryEntities = new ArrayList<>();

    @InverseRelationShadowVariable(sourceVariableName = "secondary")
    public Set<DualEntity> secondaryEntities = new LinkedHashSet<>();
  }

  public static class ListOwnerSubclass extends ListOwner {}

  public static class ListItemSubclass extends ListItem {}

  @PlanningSolution
  public static class SameNameSolution {
    @PlanningEntityCollectionProperty public List<SameNameSourceA> sourcesA;
    @PlanningEntityCollectionProperty public List<SameNameSourceB> sourcesB;
    @PlanningEntityCollectionProperty @ValueRangeProvider public List<SameNameValue> values;
    @PlanningScore public SimpleScore score;
  }

  @PlanningEntity
  public static class SameNameSourceA {
    @PlanningVariable public SameNameValue value;
  }

  @PlanningEntity
  public static class SameNameSourceAChild extends SameNameSourceA {}

  @PlanningEntity
  public static class SameNameSourceB {
    @PlanningVariable public SameNameValue value;
  }

  @PlanningEntity
  public static class SameNameValue {
    @InverseRelationShadowVariable(sourceVariableName = "value")
    public List<SameNameSourceA> sourcesA = new ArrayList<>();

    @InverseRelationShadowVariable(sourceVariableName = "value")
    public List<SameNameSourceB> sourcesB = new ArrayList<>();
  }
}
