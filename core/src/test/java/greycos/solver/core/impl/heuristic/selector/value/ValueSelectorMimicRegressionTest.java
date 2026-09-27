package greycos.solver.core.impl.heuristic.selector.value;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.value.ValueSelectorConfig;
import greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.heuristic.HeuristicConfigPolicy;
import greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import greycos.solver.core.impl.heuristic.selector.value.mimic.MimicRecordingValueSelector;
import greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.addvar.TestdataAddVarBaseEntity;
import greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.addvar.TestdataAddVarChildEntity;
import greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.addvar.TestdataAddVarSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedEntity;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedSolution;
import greycos.solver.core.testcotwin.mixed.singleentity.TestdataMixedValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValueSelectorMimicRegressionTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void mixedEntityMimicUsesItsRecordingVariable(boolean explicitVariableName) {
    var fixture = fixture();
    var config = new ValueSelectorConfig().withMimicSelectorRef("origin");
    if (explicitVariableName) {
      config.setVariableName("valueList");
    }
    var factory = ValueSelectorFactory.<TestdataMixedSolution>create(config);

    assertThat(factory.extractVariableDescriptor(fixture.policy(), fixture.entityDescriptor()))
        .isSameAs(fixture.recorder().getVariableDescriptor());
    var selector =
        factory.buildValueSelector(
            fixture.policy(),
            fixture.entityDescriptor(),
            SelectionCacheType.JUST_IN_TIME,
            SelectionOrder.RANDOM);

    assertThat(selector.getVariableDescriptor())
        .isSameAs(fixture.recorder().getVariableDescriptor());
    var recording = fixture.recorder().iterator();
    assertThat(recording.next()).isSameAs(fixture.value());
    var replay = selector.iterator(null);
    assertThat(replay.hasNext()).isTrue();
    assertThat(replay.next()).isSameAs(fixture.value());
    assertThat(replay.hasNext()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void inheritedVariableCanBeReplayedOnChildEntity(boolean explicitVariableName) {
    var solutionDescriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            TestdataAddVarSolution.class,
            TestdataAddVarBaseEntity.class,
            TestdataAddVarChildEntity.class);
    var policy = buildHeuristicConfigPolicy(solutionDescriptor);
    var entityDescriptor =
        solutionDescriptor.findEntityDescriptorOrFail(TestdataAddVarChildEntity.class);
    var variableDescriptor = entityDescriptor.getGenuineVariableDescriptor("value");
    assertThat(variableDescriptor.getEntityDescriptor()).isNotSameAs(entityDescriptor);
    var recorder =
        new MimicRecordingValueSelector<>(
            SelectorTestUtils.mockIterableValueSelector(variableDescriptor, "recordedValue"));
    policy.addValueMimicRecorder("origin", recorder);
    var config = new ValueSelectorConfig().withMimicSelectorRef("origin");
    if (explicitVariableName) {
      config.setVariableName("value");
    }
    var factory = ValueSelectorFactory.<TestdataAddVarSolution>create(config);

    assertThat(factory.extractVariableDescriptor(policy, entityDescriptor))
        .isSameAs(variableDescriptor);
    var selector =
        factory.buildValueSelector(
            policy, entityDescriptor, SelectionCacheType.JUST_IN_TIME, SelectionOrder.RANDOM);

    assertThat(selector.getVariableDescriptor()).isSameAs(variableDescriptor);
    recorder.iterator().next();
    assertThat(selector.iterator(null).next()).isEqualTo("recordedValue");
  }

  @Test
  void conflictingVariableNameIdentifiesRecorderAndBothVariables() {
    var fixture = fixture();
    var factory =
        ValueSelectorFactory.<TestdataMixedSolution>create(
            new ValueSelectorConfig()
                .withMimicSelectorRef("origin")
                .withVariableName("basicValue"));

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                factory.buildValueSelector(
                    fixture.policy(),
                    fixture.entityDescriptor(),
                    SelectionCacheType.JUST_IN_TIME,
                    SelectionOrder.RANDOM))
        .withMessageContainingAll("origin", "basicValue", "valueList");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> factory.extractVariableDescriptor(fixture.policy(), fixture.entityDescriptor()))
        .withMessageContainingAll("origin", "basicValue", "valueList");
  }

  @Test
  void missingRecorderIsReportedBeforeAmbiguousVariableName() {
    var fixture = fixture();
    var factory =
        ValueSelectorFactory.<TestdataMixedSolution>create(
            new ValueSelectorConfig().withMimicSelectorRef("missingOrigin"));

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                factory.buildValueSelector(
                    fixture.policy(),
                    fixture.entityDescriptor(),
                    SelectionCacheType.JUST_IN_TIME,
                    SelectionOrder.RANDOM))
        .withMessageContainingAll("missingOrigin", "no valueSelector with that id exists");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> factory.extractVariableDescriptor(fixture.policy(), fixture.entityDescriptor()))
        .withMessageContainingAll("missingOrigin", "no valueSelector with that id exists");
  }

  private static Fixture fixture() {
    var policy = buildHeuristicConfigPolicy(TestdataMixedSolution.buildSolutionDescriptor());
    var entityDescriptor =
        policy.getSolutionDescriptor().findEntityDescriptorOrFail(TestdataMixedEntity.class);
    var value = new TestdataMixedValue("recordedValue");
    var recorder =
        new MimicRecordingValueSelector<>(
            SelectorTestUtils.mockIterableValueSelector(
                entityDescriptor.getGenuineVariableDescriptor("valueList"), value));
    policy.addValueMimicRecorder("origin", recorder);
    return new Fixture(policy, entityDescriptor, recorder, value);
  }

  private record Fixture(
      HeuristicConfigPolicy<TestdataMixedSolution> policy,
      EntityDescriptor<TestdataMixedSolution> entityDescriptor,
      MimicRecordingValueSelector<TestdataMixedSolution> recorder,
      TestdataMixedValue value) {}
}
