package ai.greycos.solver.core.impl.cotwin.common.accessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testcotwin.valuerange.entityproviding.parameter.TestdataEntityProvidingWithParameterEntity;
import ai.greycos.solver.core.testcotwin.valuerange.entityproviding.parameter.TestdataEntityProvidingWithParameterSolution;
import ai.greycos.solver.core.testcotwin.valuerange.entityproviding.parameter.inheritance.TestdataEntityProvidingEntityProvidingOnlyBaseAnnotatedExtendedSolution;
import ai.greycos.solver.core.testcotwin.valuerange.entityproviding.parameter.inheritance.TestdataEntityProvidingOnlyBaseAnnotatedChildEntity;
import ai.greycos.solver.core.testcotwin.valuerange.entityproviding.parameter.inheritance.TestdataEntityProvidingOnlyBaseAnnotatedSolution;
import ai.greycos.solver.core.testcotwin.valuerange.entityproviding.parameter.invalid.TestdataInvalidCountEntityProvidingWithParameterEntity;
import ai.greycos.solver.core.testcotwin.valuerange.entityproviding.parameter.invalid.TestdataInvalidTypeEntityProvidingWithParameterEntity;
import ai.greycos.solver.core.testcotwin.valuerange.parameter.invalid.TestdataInvalidParameterSolution;

import org.junit.jupiter.api.Test;

class ReflectionMethodExtendedMemberAccessorTest {

  @Test
  void methodAnnotatedEntity() throws NoSuchMethodException {
    var memberAccessor =
        new ReflectionMethodExtendedMemberAccessor(
            TestdataEntityProvidingWithParameterEntity.class.getMethod(
                "getValueRange", TestdataEntityProvidingWithParameterSolution.class));
    assertThat(memberAccessor.getName()).isEqualTo("getValueRange");
    assertThat(memberAccessor.getType()).isEqualTo(List.class);
    assertThat(memberAccessor.getAnnotation(ValueRangeProvider.class)).isNotNull();
    assertThat(memberAccessor.getGetterMethodParameterType())
        .isEqualTo(TestdataEntityProvidingWithParameterSolution.class);

    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    var e1 = new TestdataEntityProvidingWithParameterEntity("e1", List.of(v1, v2), v1);
    var s1 = new TestdataEntityProvidingWithParameterSolution("s1");

    assertThat(memberAccessor.executeGetter(e1, s1)).isEqualTo(List.of(v1, v2));
    e1.setValueRange(List.of(v2));
    assertThat(memberAccessor.executeGetter(e1, s1)).isEqualTo(List.of(v2));
  }

  @Test
  void methodAnnotatedEntityAndInheritance() throws NoSuchMethodException {
    var member =
        new ReflectionMethodExtendedMemberAccessor(
            TestdataEntityProvidingOnlyBaseAnnotatedChildEntity.class.getMethod(
                "getValueList",
                TestdataEntityProvidingEntityProvidingOnlyBaseAnnotatedExtendedSolution.class));
    assertMemberWithInheritance(
        member, TestdataEntityProvidingEntityProvidingOnlyBaseAnnotatedExtendedSolution.class);
    var otherMember =
        new ReflectionMethodExtendedMemberAccessor(
            TestdataEntityProvidingOnlyBaseAnnotatedChildEntity.class.getMethod(
                "getOtherValueList", TestdataEntityProvidingOnlyBaseAnnotatedSolution.class));
    assertMemberWithInheritance(
        otherMember, TestdataEntityProvidingOnlyBaseAnnotatedSolution.class);
  }

  void assertMemberWithInheritance(
      ReflectionMethodExtendedMemberAccessor member, Class<?> solutionClass) {

    assertThat(member.getName()).isEqualTo(member.getName());
    assertThat(member.getType()).isEqualTo(List.class);
    assertThat(member.getAnnotation(ValueRangeProvider.class)).isNotNull();
    assertThat(member.getGetterMethodParameterType()).isEqualTo(solutionClass);

    var v1 = new TestdataValue("v1");
    var v2 = new TestdataValue("v2");
    var e1 = new TestdataEntityProvidingOnlyBaseAnnotatedChildEntity("e1", v1);
    var s1 = new TestdataEntityProvidingEntityProvidingOnlyBaseAnnotatedExtendedSolution("s1");
    s1.setValueList(List.of(v1, v2));

    assertThat(member.executeGetter(e1, s1)).isEqualTo(List.of(v1, v2));
    s1.setValueList(List.of(v2));
    assertThat(member.executeGetter(e1, s1)).isEqualTo(List.of(v2));
  }

  @Test
  void invalidEntityReadMethodWithParameter() {
    assertThatCode(
            TestdataInvalidTypeEntityProvidingWithParameterEntity
                ::buildVariableDescriptorForValueRange)
        .hasMessageContaining(
            "The parameter type (ai.greycos.solver.core.testcotwin.TestdataSolution)")
        .hasMessageContaining(
            "of the method (getValueRange) must match the solution (ai.greycos.solver.core.testcotwin.valuerange.entityproviding.parameter.invalid.TestdataInvalidTypeEntityProvidingWithParameterSolution).");
    assertThatCode(
            TestdataInvalidCountEntityProvidingWithParameterEntity
                ::buildVariableDescriptorForValueRange)
        .hasMessageContaining(
            "is a public read method, but takes (2) parameters instead of zero or one");
  }

  @Test
  void invalidSolutionReadMethodWithParameter() {
    assertThatCode(TestdataInvalidParameterSolution::buildSolutionDescriptor)
        .hasMessageContaining("is a public read method, but takes (1) parameters instead of none")
        .hasMessageContaining("Maybe make the method (getValueList) take no parameters?");
  }

  @Test
  void forbiddenEntityReadWithoutParameter() {
    assertThatCode(
            () ->
                new ReflectionMethodExtendedMemberAccessor(
                        TestdataEntityProvidingWithParameterEntity.class.getMethod(
                            "getValueRange", TestdataEntityProvidingWithParameterSolution.class))
                    .executeGetter(new TestdataEntityProvidingWithParameterEntity()))
        .hasMessageContainingAll(
            "Impossible state: the method executeGetter(Object) without parameter is not supported.");
    assertThatCode(
            () ->
                new ReflectionMethodExtendedMemberAccessor(
                        TestdataEntityProvidingWithParameterEntity.class.getMethod(
                            "getValueRange", TestdataEntityProvidingWithParameterSolution.class))
                    .getGetterFunction())
        .hasMessageContainingAll(
            "Impossible state: the method getGetterFunction() is not supported.");
  }
}
