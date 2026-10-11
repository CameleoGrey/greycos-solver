package greycos.solver.spring.boot.it.cotwin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPinToIndex;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.ShadowSources;
import greycos.solver.core.api.cotwin.variable.ShadowVariable;

/** Exercises the same model in the JVM application and its native image. */
public final class TestdataMultiListFixture {

  private TestdataMultiListFixture() {}

  public static void initialize(IntegrationTestSolution solution) {
    var firstElements =
        List.of(new FirstElement("f0"), new FirstElement("f1"), new FirstElement("f2"));
    var secondElements =
        List.of(
            new SecondElement("s0"),
            new SecondElement("s1"),
            new SecondElement("s2"),
            new SecondElement("s3"));
    var pinnedOwner = new Owner();
    pinnedOwner.firstList.add(firstElements.get(0));
    pinnedOwner.secondList.addAll(secondElements.subList(0, 2));
    pinnedOwner.firstPinToIndex = 1;
    pinnedOwner.secondPinToIndex = 2;
    solution.setListOwners(List.of(pinnedOwner, new Owner()));
    solution.setFirstElements(firstElements);
    solution.setSecondElements(secondElements);
  }

  public static void verify(IntegrationTestSolution problem, IntegrationTestSolution solution) {
    check(solution != problem, "The returned solution must be a clone.");
    check(solution.getListOwners().size() == 2, "Both list owners must be retained.");
    Set<FirstElement> firstSeen = Collections.newSetFromMap(new IdentityHashMap<>());
    Set<SecondElement> secondSeen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (var owner : solution.getListOwners()) {
      check(
          problem.getListOwners().stream().noneMatch(original -> original == owner),
          "List owners must be cloned.");
      for (int i = 0; i < owner.firstList.size(); i++) {
        var element = owner.firstList.get(i);
        check(firstSeen.add(element), "First-list elements must be assigned exactly once.");
        check(
            element.owner == owner && element.index == i && element.total == i + 1,
            "First-list ownership, index and declarative shadows must agree with replay.");
        check(
            element.previous == (i == 0 ? null : owner.firstList.get(i - 1))
                && element.next
                    == (i + 1 == owner.firstList.size() ? null : owner.firstList.get(i + 1)),
            "First-list neighbors must agree with replay.");
      }
      for (int i = 0; i < owner.secondList.size(); i++) {
        var element = owner.secondList.get(i);
        check(secondSeen.add(element), "Second-list elements must be assigned exactly once.");
        check(
            element.owner == owner && element.index == i && element.total == (i + 1) * 10,
            "Second-list ownership, index and declarative shadows must agree with replay.");
        check(
            element.previous == (i == 0 ? null : owner.secondList.get(i - 1))
                && element.next
                    == (i + 1 == owner.secondList.size() ? null : owner.secondList.get(i + 1)),
            "Second-list neighbors must agree with replay.");
      }
    }
    check(
        firstSeen.size() == 3 && firstSeen.containsAll(solution.getFirstElements()),
        "Every first-list value must be assigned using cloned range instances.");
    check(
        secondSeen.size() == 4 && secondSeen.containsAll(solution.getSecondElements()),
        "Every second-list value must be assigned using cloned range instances.");
    check(
        problem.getFirstElements().stream().noneMatch(firstSeen::contains)
            && problem.getSecondElements().stream().noneMatch(secondSeen::contains),
        "Both list element populations must be cloned.");
    var pinnedOwner = solution.getListOwners().get(0);
    check(pinnedOwner.firstList.get(0).id.equals("f0"), "The first-list prefix must stay pinned.");
    check(
        pinnedOwner.secondList.get(0).id.equals("s0")
            && pinnedOwner.secondList.get(1).id.equals("s1"),
        "The second-list prefix must stay pinned.");
    check(
        problem.getListOwners().get(0).firstList.size() == 1
            && problem.getListOwners().get(0).secondList.size() == 2
            && problem.getListOwners().get(1).firstList.isEmpty()
            && problem.getListOwners().get(1).secondList.isEmpty(),
        "Solving must not mutate either input planning list.");
  }

  private static void check(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }

  @PlanningEntity
  public static class Owner {
    @PlanningListVariable(valueRangeProviderRefs = "firstElements")
    private List<FirstElement> firstList = new ArrayList<>();

    private List<SecondElement> secondList = new ArrayList<>();

    @PlanningPinToIndex(variableName = "firstList")
    private int firstPinToIndex;

    private int secondPinToIndex;

    public Owner() {}

    public List<FirstElement> getFirstList() {
      return firstList;
    }

    public void setFirstList(List<FirstElement> firstList) {
      this.firstList = firstList;
    }

    public int getFirstPinToIndex() {
      return firstPinToIndex;
    }

    public void setFirstPinToIndex(int firstPinToIndex) {
      this.firstPinToIndex = firstPinToIndex;
    }

    @PlanningListVariable(valueRangeProviderRefs = "secondElements")
    public List<SecondElement> getSecondList() {
      return secondList;
    }

    public void setSecondList(List<SecondElement> secondList) {
      this.secondList = secondList;
    }

    @PlanningPinToIndex(variableName = "secondList")
    public int getSecondPinToIndex() {
      return secondPinToIndex;
    }

    public void setSecondPinToIndex(int secondPinToIndex) {
      this.secondPinToIndex = secondPinToIndex;
    }
  }

  @PlanningEntity
  public static class FirstElement {
    public String id;

    @InverseRelationShadowVariable(sourceVariableName = "firstList")
    public Owner owner;

    @IndexShadowVariable(sourceVariableName = "firstList")
    public Integer index;

    @PreviousElementShadowVariable(sourceVariableName = "firstList")
    public FirstElement previous;

    @NextElementShadowVariable(sourceVariableName = "firstList")
    public FirstElement next;

    @ShadowVariable(supplierName = "totalSupplier")
    public Integer total;

    public FirstElement() {}

    public FirstElement(String id) {
      this.id = id;
    }

    @ShadowSources("previous.total")
    public Integer totalSupplier() {
      return previous == null ? 1 : previous.total + 1;
    }

    public boolean hasInvalidShadows() {
      return owner == null || index == null || total == null || total != (index + 1) * 1;
    }
  }

  @PlanningEntity
  public static class SecondElement {
    public String id;

    @InverseRelationShadowVariable(sourceVariableName = "secondList")
    public Owner owner;

    @IndexShadowVariable(sourceVariableName = "secondList")
    public Integer index;

    @PreviousElementShadowVariable(sourceVariableName = "secondList")
    public SecondElement previous;

    @NextElementShadowVariable(sourceVariableName = "secondList")
    public SecondElement next;

    @ShadowVariable(supplierName = "totalSupplier")
    public Integer total;

    public SecondElement() {}

    public SecondElement(String id) {
      this.id = id;
    }

    @ShadowSources("previous.total")
    public Integer totalSupplier() {
      return previous == null ? 10 : previous.total + 10;
    }

    public boolean hasInvalidShadows() {
      return owner == null || index == null || total == null || total != (index + 1) * 10;
    }
  }
}
