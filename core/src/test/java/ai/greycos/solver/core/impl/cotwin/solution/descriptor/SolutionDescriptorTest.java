package ai.greycos.solver.core.impl.cotwin.solution.descriptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.impl.cotwin.valuerange.descriptor.ValueRangeDescriptor;
import ai.greycos.solver.core.impl.cotwin.variable.descriptor.BasicVariableDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testcotwin.collection.TestdataArrayBasedSolution;
import ai.greycos.solver.core.testcotwin.collection.TestdataSetBasedSolution;
import ai.greycos.solver.core.testcotwin.immutable.enumeration.TestdataEnumSolution;
import ai.greycos.solver.core.testcotwin.immutable.record.TestdataRecordSolution;
import ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.childtoo.TestdataBothAnnotatedExtendedSolution;
import ai.greycos.solver.core.testcotwin.invalid.badfactcollection.TestdataBadFactCollectionSolution;
import ai.greycos.solver.core.testcotwin.invalid.constraintweightoverrides.TestdataInvalidConstraintWeightOverridesSolution;
import ai.greycos.solver.core.testcotwin.invalid.entityannotatedasproblemfact.TestdataEntityAnnotatedAsProblemFactArraySolution;
import ai.greycos.solver.core.testcotwin.invalid.entityannotatedasproblemfact.TestdataEntityAnnotatedAsProblemFactCollectionSolution;
import ai.greycos.solver.core.testcotwin.invalid.entityannotatedasproblemfact.TestdataEntityAnnotatedAsProblemFactSolution;
import ai.greycos.solver.core.testcotwin.invalid.nosolution.TestdataNoSolution;
import ai.greycos.solver.core.testcotwin.mixed.multientity.TestdataMixedMultiEntitySolution;
import ai.greycos.solver.core.testcotwin.reflect.generic.TestdataGenericEntity;
import ai.greycos.solver.core.testcotwin.reflect.generic.TestdataGenericSolution;
import ai.greycos.solver.core.testcotwin.shadow.missing.TestdataDeclarativeMissingSupplierSolution;
import ai.greycos.solver.core.testcotwin.solutionproperties.TestdataNoProblemFactPropertySolution;
import ai.greycos.solver.core.testcotwin.solutionproperties.TestdataProblemFactPropertySolution;
import ai.greycos.solver.core.testcotwin.solutionproperties.TestdataReadMethodProblemFactCollectionPropertySolution;
import ai.greycos.solver.core.testcotwin.solutionproperties.TestdataWildcardSolution;
import ai.greycos.solver.core.testcotwin.solutionproperties.invalid.TestdataDuplicatePlanningEntityCollectionPropertySolution;
import ai.greycos.solver.core.testcotwin.solutionproperties.invalid.TestdataDuplicatePlanningScorePropertySolution;
import ai.greycos.solver.core.testcotwin.solutionproperties.invalid.TestdataDuplicateProblemFactCollectionPropertySolution;
import ai.greycos.solver.core.testcotwin.solutionproperties.invalid.TestdataMissingScorePropertySolution;
import ai.greycos.solver.core.testcotwin.solutionproperties.invalid.TestdataProblemFactCollectionPropertyWithArgumentSolution;
import ai.greycos.solver.core.testcotwin.solutionproperties.invalid.TestdataProblemFactIsPlanningEntityCollectionPropertySolution;
import ai.greycos.solver.core.testcotwin.solutionproperties.invalid.TestdataUnsupportedWildcardSolution;
import ai.greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;

class SolutionDescriptorTest {

  // ************************************************************************
  // Problem fact and planning entity properties
  // ************************************************************************

  @Test
  void problemFactProperty() {
    var solutionDescriptor = TestdataProblemFactPropertySolution.buildSolutionDescriptor();
    assertThat(solutionDescriptor.getProblemFactMemberAccessorMap())
        .containsOnlyKeys("extraObject");
    assertThat(solutionDescriptor.getProblemFactCollectionMemberAccessorMap())
        .containsOnlyKeys("valueList", "otherProblemFactList");
  }

  @Test
  void readMethodProblemFactCollectionProperty() {
    var solutionDescriptor =
        TestdataReadMethodProblemFactCollectionPropertySolution.buildSolutionDescriptor();
    assertThat(solutionDescriptor.getProblemFactMemberAccessorMap()).isEmpty();
    assertThat(solutionDescriptor.getProblemFactCollectionMemberAccessorMap())
        .containsOnlyKeys("valueList", "createProblemFacts");
  }

  @Test
  void problemFactCollectionPropertyWithArgument() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            TestdataProblemFactCollectionPropertyWithArgumentSolution::buildSolutionDescriptor)
        .withMessageContaining("is a public read method, but takes (1) parameters instead of none");
  }

  @Test
  void duplicateProblemFactCollectionProperty() {
    assertThatIllegalStateException()
        .isThrownBy(
            TestdataDuplicateProblemFactCollectionPropertySolution::buildSolutionDescriptor);
  }

  @Test
  void duplicatePlanningEntityCollectionProperty() {
    assertThatIllegalStateException()
        .isThrownBy(
            TestdataDuplicatePlanningEntityCollectionPropertySolution::buildSolutionDescriptor);
  }

  @Test
  void duplicatePlanningScorePropertyProperty() {
    assertThatIllegalStateException()
        .isThrownBy(TestdataDuplicatePlanningScorePropertySolution::buildSolutionDescriptor);
  }

  @Test
  void missingPlanningScorePropertyProperty() {
    assertThatIllegalStateException()
        .isThrownBy(TestdataMissingScorePropertySolution::buildSolutionDescriptor);
  }

  @Test
  void problemFactIsPlanningEntityCollectionProperty() {
    assertThatIllegalStateException()
        .isThrownBy(
            TestdataProblemFactIsPlanningEntityCollectionPropertySolution::buildSolutionDescriptor);
  }

  @Test
  void planningEntityIsProblemFactProperty() {
    assertThatIllegalStateException()
        .isThrownBy(TestdataEntityAnnotatedAsProblemFactSolution::buildSolutionDescriptor);
  }

  @Test
  void planningEntityIsProblemFactCollectionProperty() {
    assertThatIllegalStateException()
        .isThrownBy(
            TestdataEntityAnnotatedAsProblemFactCollectionSolution::buildSolutionDescriptor);
  }

  @Test
  void planningEntityIsProblemFactArrayProperty() {
    assertThatIllegalStateException()
        .isThrownBy(TestdataEntityAnnotatedAsProblemFactArraySolution::buildSolutionDescriptor);
  }

  @Test
  void wildcardProblemFactAndEntityProperties() {
    var solutionDescriptor = TestdataWildcardSolution.buildSolutionDescriptor();
    assertThat(solutionDescriptor.getProblemFactMemberAccessorMap()).isEmpty();
    assertThat(solutionDescriptor.getProblemFactCollectionMemberAccessorMap())
        .containsOnlyKeys("extendsValueList", "supersValueList");
    assertThat(solutionDescriptor.getEntityMemberAccessorMap()).isEmpty();
    assertThat(solutionDescriptor.getEntityCollectionMemberAccessorMap())
        .containsOnlyKeys("extendsEntityList");
  }

  @Test
  void wildcardSupersEntityListProperty() {
    var solverFactory =
        PlannerTestUtils.buildSolverFactory(
            TestdataUnsupportedWildcardSolution.class, TestdataEntity.class);
    var solver = solverFactory.buildSolver();
    var solution = new TestdataUnsupportedWildcardSolution();
    solution.setValueList(Arrays.asList(new TestdataValue("v1")));
    solution.setSupersEntityList(Arrays.asList(new TestdataEntity("e1"), new TestdataValue("v2")));
    // TODO Ideally, this already fails fast on buildSolverFactory
    assertThatIllegalArgumentException().isThrownBy(() -> solver.solve(solution));
  }

  @Test
  void noProblemFactPropertyWithEasyScoreCalculation() {
    var solverFactory =
        PlannerTestUtils.buildSolverFactory(
            TestdataNoProblemFactPropertySolution.class, TestdataEntity.class);
    assertThatCode(solverFactory::buildSolver).doesNotThrowAnyException();
  }

  @Test
  void extended() {
    var solutionDescriptor = TestdataBothAnnotatedExtendedSolution.buildSolutionDescriptor();
    assertThat(solutionDescriptor.getProblemFactMemberAccessorMap()).isEmpty();
    assertThat(solutionDescriptor.getProblemFactCollectionMemberAccessorMap())
        .containsOnlyKeys("valueList", "subValueList");
    assertThat(solutionDescriptor.getEntityMemberAccessorMap())
        .containsOnlyKeys("entity", "subEntity");
    assertThat(solutionDescriptor.getEntityCollectionMemberAccessorMap())
        .containsOnlyKeys(
            "entityList", "subEntityList", "rawEntityList", "entityList", "objectEntityList");
  }

  @Test
  void setProperties() {
    var solutionDescriptor = TestdataSetBasedSolution.buildSolutionDescriptor();
    assertThat(solutionDescriptor.getProblemFactMemberAccessorMap()).isEmpty();
    assertThat(solutionDescriptor.getProblemFactCollectionMemberAccessorMap())
        .containsOnlyKeys("valueSet");
    assertThat(solutionDescriptor.getEntityMemberAccessorMap()).isEmpty();
    assertThat(solutionDescriptor.getEntityCollectionMemberAccessorMap())
        .containsOnlyKeys("entitySet");
  }

  @Test
  void arrayProperties() {
    var solutionDescriptor = TestdataArrayBasedSolution.buildSolutionDescriptor();
    assertThat(solutionDescriptor.getProblemFactMemberAccessorMap()).isEmpty();
    assertThat(solutionDescriptor.getProblemFactCollectionMemberAccessorMap())
        .containsOnlyKeys("values");
    assertThat(solutionDescriptor.getEntityMemberAccessorMap()).isEmpty();
    assertThat(solutionDescriptor.getEntityCollectionMemberAccessorMap())
        .containsOnlyKeys("entities");
  }

  @Test
  void generic() {
    var solutionDescriptor = TestdataGenericSolution.buildSolutionDescriptor();

    assertThat(solutionDescriptor.getProblemFactCollectionMemberAccessorMap())
        .containsOnlyKeys("valueList", "complexGenericValueList", "subTypeValueList");
    assertThat(solutionDescriptor.getEntityCollectionMemberAccessorMap())
        .containsOnlyKeys("entityList");

    assertThat(
            solutionDescriptor
                .findEntityDescriptor(TestdataGenericEntity.class)
                .getVariableDescriptorMap())
        .containsOnlyKeys("value", "subTypeValue", "complexGenericValue");
  }

  @Test
  void testImmutableClass() {
    assertThatCode(TestdataRecordSolution::buildSolutionDescriptor)
        .hasMessageContaining("cannot be a record as it needs to be mutable.");
    assertThatCode(TestdataEnumSolution::buildSolutionDescriptor)
        .hasMessageContaining("cannot be an enum as it needs to be mutable.");
  }

  @Test
  void testMultipleConstraintWeights() {
    assertThatCode(TestdataInvalidConstraintWeightOverridesSolution::buildSolutionDescriptor)
        .hasMessageContaining("has more than one field")
        .hasMessageContaining(
            "of type interface ai.greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides");
  }

  @Test
  void testNoSolution() {
    assertThatCode(TestdataNoSolution::buildSolutionDescriptor)
        .hasMessageContaining(
            "is not annotated with @PlanningSolution but defines annotated members");
  }

  @Test
  void testBadFactCollection() {
    assertThatCode(TestdataBadFactCollectionSolution::buildSolutionDescriptor)
        .hasMessageContaining("that does not return a Collection or an array.");
  }

  @Test
  void missingDeclarativeSupplierName() {
    assertThatCode(TestdataDeclarativeMissingSupplierSolution::buildSolutionDescriptor)
        .hasMessageContaining(
            "The @ShadowVariable annotated field (durationInDays) in class (ai.greycos.solver.core.testcotwin.shadow.missing.TestdataDeclarativeMissingSupplierEntity) does not have a setter method and is not public.");
  }

  @Test
  void testOrdinalId() {
    var solutionDescriptor = TestdataMixedMultiEntitySolution.buildSolutionDescriptor();
    assertThat(solutionDescriptor.getEntityDescriptors().stream().map(EntityDescriptor::getOrdinal))
        .hasSameElementsAs(List.of(0, 1));
    var allIds = new ArrayList<Integer>();
    assertThat(solutionDescriptor.getValueRangeDescriptorCount()).isEqualTo(3);
    allIds.addAll(
        solutionDescriptor.getBasicVariableDescriptorList().stream()
            .map(BasicVariableDescriptor::getValueRangeDescriptor)
            .mapToInt(ValueRangeDescriptor::getOrdinal)
            .boxed()
            .toList());
    allIds.add(
        solutionDescriptor.getListVariableDescriptor().getValueRangeDescriptor().getOrdinal());
    assertThat(allIds).containsExactlyInAnyOrder(0, 1, 2);
  }
}
