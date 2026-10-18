package greycos.solver.core.impl.cotwin.solution.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.ArrayList;
import java.util.List;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.api.cotwin.entity.PlanningPinToIndex;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.cotwin.variable.CascadingUpdateShadowVariable;
import greycos.solver.core.api.cotwin.variable.IndexShadowVariable;
import greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import greycos.solver.core.api.cotwin.variable.NextElementShadowVariable;
import greycos.solver.core.api.cotwin.variable.PlanningListVariable;
import greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import greycos.solver.core.api.score.SimpleScore;
import greycos.solver.core.impl.cotwin.variable.descriptor.VariableDescriptor;
import greycos.solver.core.testcotwin.TestdataSolution;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListEntity;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListFirstValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSecondValue;
import greycos.solver.core.testcotwin.multiplelist.TestdataMultipleListSolution;

import org.junit.jupiter.api.Test;

class MultipleListVariableDescriptorTest {

  @Test
  void orderedRegistryAndTargetResolution() {
    var solutionDescriptor = TestdataMultipleListSolution.buildSolutionDescriptor();
    var ownerDescriptor =
        solutionDescriptor.findEntityDescriptorOrFail(TestdataMultipleListEntity.class);
    var first = solutionDescriptor.resolveListVariableDescriptor(null, "firstValueList");
    var second =
        solutionDescriptor.resolveListVariableDescriptor(
            TestdataMultipleListEntity.class, "secondValueList");
    assertThat(solutionDescriptor.getListVariableDescriptorList()).containsExactly(first, second);
    assertThat(ownerDescriptor.getListVariableDescriptorList()).containsExactly(first, second);
    assertThat(
            solutionDescriptor.findListVariableDescriptorForValueType(
                TestdataMultipleListFirstValue.class))
        .isSameAs(first);
    assertThat(
            solutionDescriptor.findListVariableDescriptorForValueType(
                TestdataMultipleListSecondValue.class))
        .isSameAs(second);
    assertThat(solutionDescriptor.findListVariableDescriptorForValueType(String.class)).isNull();
    assertThat(solutionDescriptor.findListVariableDescriptorForValueType(Object.class)).isNull();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> solutionDescriptor.resolveListVariableDescriptor(null, null))
        .withMessageContaining("firstValueList")
        .withMessageContaining("secondValueList");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                solutionDescriptor.resolveListVariableDescriptor(
                    TestdataMultipleListEntity.class, "missing"))
        .withMessageContaining("missing")
        .withMessageContaining("Available planning list variables");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> solutionDescriptor.resolveListVariableDescriptor(String.class, null))
        .withMessageContaining(String.class.getName());
    assertThatIllegalStateException().isThrownBy(solutionDescriptor::getListVariableDescriptor);
    assertThatIllegalStateException().isThrownBy(ownerDescriptor::getListVariableDescriptor);
    assertThatIllegalStateException()
        .isThrownBy(ownerDescriptor::getEffectivePlanningPinToIndexReader);
  }

  @Test
  void basicOnlyAndSingleListConveniencesRemainValid() {
    assertThat(TestdataSolution.buildSolutionDescriptor().getListVariableDescriptorList())
        .isEmpty();
    assertThat(TestdataSolution.buildSolutionDescriptor().getListVariableDescriptor()).isNull();
    var descriptor = descriptor(BaseOwner.class);
    var list = descriptor.getListVariableDescriptor();
    assertThat(descriptor.resolveListVariableDescriptor(null, null)).isSameAs(list);
    assertThat(descriptor.resolveListVariableDescriptor(BaseOwner.class, "")).isSameAs(list);
    assertThat(descriptor.getEntityDescriptorStrict(BaseOwner.class).getListVariableDescriptor())
        .isSameAs(list);
  }

  @Test
  void inheritedDeclarationsAreCanonicalAndUnnamedPinsKeepTheirOriginalTarget() {
    var descriptor = descriptor(ChildOwner.class);
    var first = descriptor.resolveListVariableDescriptor(BaseOwner.class, null);
    var second = descriptor.resolveListVariableDescriptor(ChildOwner.class, "second");
    assertThat(descriptor.getListVariableDescriptorList()).containsExactly(first, second);
    assertThat(
            descriptor.getEntityDescriptorStrict(BaseOwner.class).getListVariableDescriptorList())
        .containsExactly(first);
    assertThat(
            descriptor.getEntityDescriptorStrict(ChildOwner.class).getListVariableDescriptorList())
        .containsExactly(first, second);
    assertThat(descriptor.resolveListVariableDescriptor(UnconfiguredChildOwner.class, "first"))
        .isSameAs(first);
    var owner = new ChildOwner();
    owner.firstPin = 1;
    owner.secondPin = 2;
    assertThat(first.getFirstUnpinnedIndex(owner)).isEqualTo(1);
    assertThat(second.getFirstUnpinnedIndex(owner)).isEqualTo(2);
    owner.firstPin = 3;
    assertThat(first.getFirstUnpinnedIndex(owner)).isEqualTo(3);
    assertThat(second.getFirstUnpinnedIndex(owner)).isEqualTo(2);
    var inheritedReader =
        descriptor
            .getEntityDescriptorStrict(ChildOwner.class)
            .getEffectivePlanningPinToIndexReader(first);
    assertThat(inheritedReader)
        .isSameAs(
            descriptor
                .getEntityDescriptorStrict(BaseOwner.class)
                .getEffectivePlanningPinToIndexReader(first));
  }

  @Test
  void fullEntityPinAppliesToEveryList() {
    var descriptor = descriptor(ChildOwner.class);
    var owner = new ChildOwner();
    owner.pinned = true;
    for (var list : descriptor.getListVariableDescriptorList()) {
      assertThat(list.isElementPinned(new MetadataSolution(), owner, 10)).isTrue();
    }
    owner.pinned = false;
    for (var list : descriptor.getListVariableDescriptorList()) {
      assertThat(list.isElementPinned(new MetadataSolution(), owner, 0)).isFalse();
    }
  }

  @Test
  void invalidPinTargetsFailWithTheirDeclarations() {
    assertThatIllegalStateException()
        .isThrownBy(() -> descriptor(UnnamedMultiplePinOwner.class))
        .withMessageContaining("without a variableName")
        .withMessageContaining("first")
        .withMessageContaining("second");
    assertThatIllegalStateException()
        .isThrownBy(() -> descriptor(UnknownPinOwner.class))
        .withMessageContaining("missing")
        .withMessageContaining("not a planning list variable");
    assertThatIllegalStateException()
        .isThrownBy(() -> descriptor(DuplicatePinOwner.class))
        .withMessageContaining("multiple @PlanningPinToIndex")
        .withMessageContaining("first");
  }

  @Test
  void sameNamedSourcesOnSeparateOwnersLinkByElementTypeAndDeduplicateInheritance() {
    var descriptor =
        descriptor(
            FirstOwner.class,
            FirstOwnerChild.class,
            SecondOwner.class,
            FirstElement.class,
            SecondElement.class);
    var first = descriptor.resolveListVariableDescriptor(FirstOwnerChild.class, "items");
    var second = descriptor.resolveListVariableDescriptor(SecondOwner.class, null);
    assertThat(descriptor.getListVariableDescriptorList()).containsExactly(first, second);
    for (var shadowName : List.of("index", "previous", "next", "owner", "cascade")) {
      assertThat(
              descriptor
                  .findEntityDescriptorOrFail(FirstElement.class)
                  .getShadowVariableDescriptor(shadowName)
                  .getSourceVariableDescriptor())
          .isSameAs(first);
      assertThat(
              descriptor
                  .findEntityDescriptorOrFail(SecondElement.class)
                  .getShadowVariableDescriptor(shadowName)
                  .getSourceVariableDescriptor())
          .isSameAs(second);
    }
    assertThatIllegalArgumentException()
        .isThrownBy(() -> descriptor.resolveListVariableDescriptor(null, "items"))
        .withMessageContaining("FirstOwner")
        .withMessageContaining("SecondOwner");
    assertThat(descriptor.findListVariableDescriptorForValueType(CommonElement.class)).isNull();
    assertThat(descriptor.findListVariableDescriptorForValueType(UnconfiguredFirstElement.class))
        .isSameAs(first);
  }

  @Test
  void equalAndAssignableElementTypesAreRejected() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> descriptor(SameTypeOwner.class))
        .withMessageContaining("overlapping or ambiguous element types")
        .withMessageContaining("first")
        .withMessageContaining("duplicate");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> descriptor(AssignableTypeOwner.class))
        .withMessageContaining(Number.class.getName())
        .withMessageContaining(Integer.class.getName());
  }

  @Test
  void possibleInterfaceIntersectionsAreRejectedWithoutRuntimeValues() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> descriptor(InterfaceOwner.class))
        .withMessageContaining("overlapping or ambiguous element types");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> descriptor(ClassInterfaceOwner.class))
        .withMessageContaining("overlapping or ambiguous element types");
  }

  @Test
  void finalTypesCanProveInterfaceDomainsDisjoint() {
    assertThat(descriptor(DisjointInterfaceOwner.class).getListVariableDescriptorList())
        .extracting(VariableDescriptor::getVariableName)
        .containsExactly("first", "second");
    assertThat(descriptor(DisjointSealedOwner.class).getListVariableDescriptorList())
        .extracting(VariableDescriptor::getVariableName)
        .containsExactly("first", "second");
  }

  @Test
  void nestedListOwnersAreRejected() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> descriptor(OuterOwner.class, InnerOwner.class))
        .withMessageContaining("Nested planning list ownership")
        .withMessageContaining("outer")
        .withMessageContaining("inner");
  }

  private static SolutionDescriptor<MetadataSolution> descriptor(Class<?>... entityClasses) {
    return SolutionDescriptor.buildSolutionDescriptor(MetadataSolution.class, entityClasses);
  }

  @PlanningSolution
  public static class MetadataSolution {
    @PlanningEntityCollectionProperty public List<Object> entities = new ArrayList<>();
    @PlanningScore public SimpleScore score;
  }

  public static final class First {}

  public static final class Second {}

  @PlanningEntity
  public static class BaseOwner {
    @PlanningListVariable(valueRangeProviderRefs = "firstRange")
    public List<First> first = new ArrayList<>();

    @ValueRangeProvider(id = "firstRange")
    public List<First> firstRange = new ArrayList<>();

    @PlanningPinToIndex public int firstPin;
    @PlanningPin public boolean pinned;
  }

  @PlanningEntity
  public static class ChildOwner extends BaseOwner {
    @PlanningListVariable(valueRangeProviderRefs = "secondRange")
    public List<Second> second = new ArrayList<>();

    @ValueRangeProvider(id = "secondRange")
    public List<Second> secondRange = new ArrayList<>();

    public int secondPin;

    @PlanningPinToIndex(variableName = "second")
    public int getSecondPin() {
      return secondPin;
    }
  }

  public static class UnconfiguredChildOwner extends ChildOwner {}

  @PlanningEntity
  public static class UnnamedMultiplePinOwner {
    @PlanningListVariable(valueRangeProviderRefs = "firstRange")
    public List<First> first = new ArrayList<>();

    @PlanningListVariable(valueRangeProviderRefs = "secondRange")
    public List<Second> second = new ArrayList<>();

    @ValueRangeProvider(id = "firstRange")
    public List<First> firstRange = new ArrayList<>();

    @ValueRangeProvider(id = "secondRange")
    public List<Second> secondRange = new ArrayList<>();

    @PlanningPinToIndex public int extraPin;
  }

  @PlanningEntity
  public static class UnknownPinOwner extends BaseOwner {
    @PlanningPinToIndex(variableName = "missing")
    public int extraPin;
  }

  @PlanningEntity
  public static class DuplicatePinOwner extends BaseOwner {
    @PlanningPinToIndex(variableName = "first")
    public int extraPin;
  }

  @PlanningEntity
  public static class SameTypeOwner extends BaseOwner {
    @PlanningListVariable(valueRangeProviderRefs = "firstRange")
    public List<First> duplicate = new ArrayList<>();
  }

  @PlanningEntity
  public static class AssignableTypeOwner {
    @PlanningListVariable(valueRangeProviderRefs = "numberRange")
    public List<Number> numbers = new ArrayList<>();

    @PlanningListVariable(valueRangeProviderRefs = "integerRange")
    public List<Integer> integers = new ArrayList<>();

    @ValueRangeProvider(id = "numberRange")
    public List<Number> numberRange = new ArrayList<>();

    @ValueRangeProvider(id = "integerRange")
    public List<Integer> integerRange = new ArrayList<>();
  }

  public interface FirstRole {}

  public interface SecondRole {}

  public static class OpenValue {}

  public sealed interface SealedFirstRole permits SealedFirst {}

  public sealed interface SealedSecondRole permits SealedSecond {}

  public static final class SealedFirst implements SealedFirstRole {}

  public static final class SealedSecond implements SealedSecondRole {}

  @PlanningEntity
  public static class InterfaceOwner {
    @PlanningListVariable(valueRangeProviderRefs = "firstRoleRange")
    public List<FirstRole> first = new ArrayList<>();

    @PlanningListVariable(valueRangeProviderRefs = "secondRoleRange")
    public List<SecondRole> second = new ArrayList<>();

    @ValueRangeProvider(id = "firstRoleRange")
    public List<FirstRole> firstRange = new ArrayList<>();

    @ValueRangeProvider(id = "secondRoleRange")
    public List<SecondRole> secondRange = new ArrayList<>();
  }

  @PlanningEntity
  public static class ClassInterfaceOwner {
    @PlanningListVariable(valueRangeProviderRefs = "firstRoleRange")
    public List<FirstRole> first = new ArrayList<>();

    @PlanningListVariable(valueRangeProviderRefs = "openValueRange")
    public List<OpenValue> second = new ArrayList<>();

    @ValueRangeProvider(id = "firstRoleRange")
    public List<FirstRole> firstRange = new ArrayList<>();

    @ValueRangeProvider(id = "openValueRange")
    public List<OpenValue> secondRange = new ArrayList<>();
  }

  @PlanningEntity
  public static final class DisjointInterfaceOwner {
    @PlanningListVariable(valueRangeProviderRefs = "firstRoleRange")
    public List<FirstRole> first = new ArrayList<>();

    @PlanningListVariable(valueRangeProviderRefs = "secondRange")
    public List<Second> second = new ArrayList<>();

    @ValueRangeProvider(id = "firstRoleRange")
    public List<FirstRole> firstRange = new ArrayList<>();

    @ValueRangeProvider(id = "secondRange")
    public List<Second> secondRange = new ArrayList<>();
  }

  @PlanningEntity
  public static class DisjointSealedOwner {
    @PlanningListVariable(valueRangeProviderRefs = "sealedFirstRange")
    public List<SealedFirstRole> first = new ArrayList<>();

    @PlanningListVariable(valueRangeProviderRefs = "sealedSecondRange")
    public List<SealedSecondRole> second = new ArrayList<>();

    @ValueRangeProvider(id = "sealedFirstRange")
    public List<SealedFirstRole> firstRange = new ArrayList<>();

    @ValueRangeProvider(id = "sealedSecondRange")
    public List<SealedSecondRole> secondRange = new ArrayList<>();
  }

  @PlanningEntity
  public static class OuterOwner {
    @PlanningListVariable(valueRangeProviderRefs = "innerRange")
    public List<InnerOwner> outer = new ArrayList<>();

    @ValueRangeProvider(id = "innerRange")
    public List<InnerOwner> innerRange = new ArrayList<>();
  }

  @PlanningEntity
  public static class InnerOwner {
    @PlanningListVariable(valueRangeProviderRefs = "secondRange")
    public List<Second> inner = new ArrayList<>();

    @ValueRangeProvider(id = "secondRange")
    public List<Second> secondRange = new ArrayList<>();
  }

  public static class CommonElement {}

  @PlanningEntity
  public static class FirstOwner {
    @PlanningListVariable(valueRangeProviderRefs = "firstElementRange")
    public List<FirstElement> items = new ArrayList<>();

    @ValueRangeProvider(id = "firstElementRange")
    public List<FirstElement> range = new ArrayList<>();
  }

  @PlanningEntity
  public static class FirstOwnerChild extends FirstOwner {}

  @PlanningEntity
  public static class SecondOwner {
    @PlanningListVariable(valueRangeProviderRefs = "secondElementRange")
    public List<SecondElement> items = new ArrayList<>();

    @ValueRangeProvider(id = "secondElementRange")
    public List<SecondElement> range = new ArrayList<>();
  }

  @PlanningEntity
  public static class FirstElement extends CommonElement {
    @IndexShadowVariable(sourceVariableName = "items")
    public Integer index;

    @PreviousElementShadowVariable(sourceVariableName = "items")
    public FirstElement previous;

    @NextElementShadowVariable(sourceVariableName = "items")
    public FirstElement next;

    @InverseRelationShadowVariable(sourceVariableName = "items")
    public FirstOwner owner;

    @CascadingUpdateShadowVariable(targetMethodName = "updateCascade")
    public Integer cascade;

    public void updateCascade() {
      cascade = index;
    }
  }

  public static class UnconfiguredFirstElement extends FirstElement {}

  @PlanningEntity
  public static class SecondElement extends CommonElement {
    @IndexShadowVariable(sourceVariableName = "items")
    public Integer index;

    @PreviousElementShadowVariable(sourceVariableName = "items")
    public SecondElement previous;

    @NextElementShadowVariable(sourceVariableName = "items")
    public SecondElement next;

    @InverseRelationShadowVariable(sourceVariableName = "items")
    public SecondOwner owner;

    @CascadingUpdateShadowVariable(targetMethodName = "updateCascade")
    public Integer cascade;

    public void updateCascade() {
      cascade = index;
    }
  }
}
