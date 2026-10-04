package greycos.solver.core.impl.cotwin.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.CascadingUpdateShadowVariable;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.api.solver.SolutionManager;

import org.junit.jupiter.api.Test;

class ShadowVariableCascadeReconstructionTest {

  @Test
  void cascadesReadRebuiltDeclarativeShadowsAndPredecessorsInListOrder() {
    var owner = new CascadeOwner();
    var first = new CascadeItem();
    var second = new CascadeItem();
    var third = new CascadeItem();
    var unassigned = new CascadeItem();
    owner.items.addAll(List.of(first, second, third));
    // Neither the argument order nor stale relationships reflect the current list.
    first.previous = third;
    second.previous = first;
    third.previous = second;
    unassigned.index = 3;
    unassigned.previous = third;
    unassigned.owner = owner;
    var entities = new Object[] {third, unassigned, second, owner, first, first};
    for (var repeat = 1; repeat <= 2; repeat++) {
      SolutionManager.updateShadowVariables(CascadeSolution.class, entities);
      assertThat(first.derived).isEqualTo(1);
      assertThat(second.derived).isEqualTo(3);
      assertThat(third.derived).isEqualTo(6);
      assertThat(first.total).isEqualTo(1);
      assertThat(second.total).isEqualTo(4);
      assertThat(third.total).isEqualTo(10);
      assertThat(first.alternateTotal).isEqualTo(2);
      assertThat(second.alternateTotal).isEqualTo(8);
      assertThat(third.alternateTotal).isEqualTo(20);
      assertThat(unassigned.derived).isZero();
      assertThat(unassigned.total).isZero();
      assertThat(unassigned.alternateTotal).isZero();
      for (var item : List.of(first, second, third, unassigned)) {
        assertThat(item.totalUpdates).isEqualTo(repeat);
        assertThat(item.alternateUpdates).isEqualTo(repeat);
        assertThat(item.totalCopy).isEqualTo(item.total);
      }
    }
    owner.items.remove(second);
    SolutionManager.updateShadowVariables(CascadeSolution.class, entities);
    assertThat(second.owner).isNull();
    assertThat(second.total).isZero();
    assertThat(second.alternateTotal).isZero();
    assertThat(third.derived).isEqualTo(3);
    assertThat(third.total).isEqualTo(4);
    assertThat(third.alternateTotal).isEqualTo(8);
  }

  @Test
  void entityHelperDoesNotConstructSolutionsOrReadSolutionValueRanges() {
    var owner = new CascadeOwner();
    var item = new CascadeItem();
    owner.items.add(item);
    SolutionManager.updateShadowVariables(NoSolutionAccess.class, item, owner);
    assertThat(item.total).isEqualTo(1);
    assertThat(item.alternateTotal).isEqualTo(2);
  }

  @Test
  void missingDeclarativeDependencyFailsBeforeRelationshipsAreChanged() {
    var owner = new CascadeOwner();
    var missing = new CascadeItem();
    var item = new CascadeItem();
    owner.items.add(item);
    item.previous = missing;
    item.index = 99;
    assertThatThrownBy(
            () -> SolutionManager.updateShadowVariables(CascadeSolution.class, owner, item))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Found referenced entities that were not given");
    assertThat(item.index).isEqualTo(99);
    assertThat(item.previous).isSameAs(missing);
    assertThat(item.totalUpdates).isZero();
  }

  @Test
  void dependenciesIntroducedByRebuiltRelationshipsAreCheckedAgain() {
    var owner = new CascadeOwner();
    var missing = new CascadeItem();
    var item = new CascadeItem();
    owner.items.addAll(List.of(missing, item));
    missing.derived = 91;
    assertThatThrownBy(
            () -> SolutionManager.updateShadowVariables(CascadeSolution.class, owner, item))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Found referenced entities that were not given");
    assertThat(missing.owner).isNull();
    assertThat(missing.index).isNull();
    assertThat(missing.derived).isEqualTo(91);
    assertThat(missing.totalUpdates).isZero();
    assertThat(item.totalUpdates).isZero();
  }

  @PlanningSolution
  public static class CascadeSolution {
    @PlanningEntityCollectionProperty public List<CascadeOwner> owners;
    @PlanningEntityCollectionProperty @ValueRangeProvider public List<CascadeItem> items;
    @PlanningScore public SimpleScore score;
  }

  @PlanningSolution
  public static class NoSolutionAccess {
    @PlanningEntityCollectionProperty public List<CascadeOwner> owners;
    @PlanningEntityCollectionProperty public List<CascadeItem> items;
    @PlanningScore public SimpleScore score;

    public NoSolutionAccess() {
      throw new AssertionError("The entity helper must not instantiate the solution.");
    }

    @ValueRangeProvider
    public List<CascadeItem> getValueRange() {
      throw new AssertionError("The entity helper must not access solution value ranges.");
    }
  }

  @PlanningEntity
  public static class CascadeOwner {
    @PlanningListVariable(allowsUnassignedValues = true)
    public List<CascadeItem> items = new ArrayList<>();
  }

  @PlanningEntity
  public static class CascadeItem {
    @InverseRelationShadowVariable(sourceVariableName = "items")
    public CascadeOwner owner;

    @IndexShadowVariable(sourceVariableName = "items")
    public Integer index;

    @PreviousElementShadowVariable(sourceVariableName = "items")
    public CascadeItem previous;

    @ShadowVariable(supplierName = "calculateDerived")
    public int derived = -1;

    @CascadingUpdateShadowVariable(targetMethodName = "updateTotal")
    public int total = -1;

    @CascadingUpdateShadowVariable(targetMethodName = "updateTotal")
    public int totalCopy = -1;

    @CascadingUpdateShadowVariable(targetMethodName = "updateAlternate")
    public int alternateTotal = -1;

    public int totalUpdates;
    public int alternateUpdates;

    @ShadowSources({"index", "previous.derived"})
    public int calculateDerived() {
      return index == null ? 0 : index + 1 + (previous == null ? 0 : previous.derived);
    }

    public void updateTotal() {
      total = derived + (previous == null ? 0 : previous.total);
      totalCopy = total;
      totalUpdates++;
    }

    public void updateAlternate() {
      alternateTotal = 2 * derived + (previous == null ? 0 : previous.alternateTotal);
      alternateUpdates++;
    }
  }
}
