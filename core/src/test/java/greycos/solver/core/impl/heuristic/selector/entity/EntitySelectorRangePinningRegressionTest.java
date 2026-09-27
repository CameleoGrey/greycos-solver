package greycos.solver.core.impl.heuristic.selector.entity;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;

import greycos.solver.core.api.cotwin.entity.PlanningEntity;
import greycos.solver.core.api.cotwin.entity.PlanningPin;
import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import greycos.solver.core.impl.heuristic.selector.common.ValueRangeRecorderId;
import greycos.solver.core.impl.heuristic.selector.entity.mimic.MimicRecordingEntitySelector;
import greycos.solver.core.impl.heuristic.selector.value.mimic.MimicRecordingValueSelector;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.list.valuerange.pinned.TestdataListPinnedEntityProvidingEntity;
import greycos.solver.core.testcotwin.list.valuerange.pinned.TestdataListPinnedEntityProvidingSolution;
import greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingEntity;
import greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EntitySelectorRangePinningRegressionTest {

  @Test
  void basicRangeRandomSelectionRetainsPinning() {
    var solutionDescriptor =
        SolutionDescriptor.buildSolutionDescriptor(
            TestdataEntityProvidingSolution.class,
            TestdataEntityProvidingEntity.class,
            PinnedRangeEntity.class);
    var policy = buildHeuristicConfigPolicy(solutionDescriptor);
    var descriptor = solutionDescriptor.findEntityDescriptorOrFail(PinnedRangeEntity.class);
    var x = new TestdataValue("x");
    var y = new TestdataValue("y");
    var source = new PinnedRangeEntity("source", List.of(x, y), x, false);
    var pinned = new PinnedRangeEntity("pinned", List.of(x, y), y, true);
    var movable = new PinnedRangeEntity("movable", List.of(x, y), y, false);
    var solution = new TestdataEntityProvidingSolution();
    solution.setEntityList(List.of(source, pinned, movable));
    var recorder =
        new MimicRecordingEntitySelector<>(
            SelectorTestUtils.mockEntitySelector(descriptor, source));
    policy.addEntityMimicRecorder("source", recorder);
    var selector =
        EntitySelectorFactory.<TestdataEntityProvidingSolution>create(
                new EntitySelectorConfig().withEntityClass(PinnedRangeEntity.class))
            .buildEntitySelector(
                policy,
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.RANDOM,
                new ValueRangeRecorderId("source", true));
    try (var director = PlannerTestUtils.mockScoreDirector(solutionDescriptor)) {
      director.setWorkingSolution(solution);
      var scope = SelectorTestUtils.solvingStarted(selector, director, new Random(0));
      var phase = PlannerTestUtils.delegatingPhaseScope(scope);
      selector.phaseStarted(phase);
      try {
        recorder.iterator().next();
        var iterator = selector.iterator();
        boolean movableSelected = false;
        for (int i = 0; i < 50; i++) {
          assertThat(iterator.hasNext()).isTrue();
          var selected = iterator.next();
          assertThat(selected).isNotSameAs(pinned);
          movableSelected |= selected == movable;
        }
        assertThat(movableSelected).isTrue();
      } finally {
        selector.phaseEnded(phase);
        selector.solvingEnded(scope);
      }
    }
  }

  @ParameterizedTest
  @EnumSource(
      value = SelectionOrder.class,
      names = {"ORIGINAL", "RANDOM"})
  void listRangeSelectionRetainsPinning(SelectionOrder order) {
    var solutionDescriptor = TestdataListPinnedEntityProvidingSolution.buildSolutionDescriptor();
    var policy = buildHeuristicConfigPolicy(solutionDescriptor);
    var descriptor =
        solutionDescriptor.findEntityDescriptorOrFail(
            TestdataListPinnedEntityProvidingEntity.class);
    var value = new TestdataValue("value");
    var pinned = new TestdataListPinnedEntityProvidingEntity("pinned", List.of(value));
    pinned.setPinned(true);
    var movable = new TestdataListPinnedEntityProvidingEntity("movable", List.of(value));
    movable.getValueList().add(value);
    var solution = new TestdataListPinnedEntityProvidingSolution();
    solution.setEntityList(List.of(pinned, movable));
    var recorder =
        new MimicRecordingValueSelector<>(
            SelectorTestUtils.mockIterableValueSelector(
                descriptor.getListVariableDescriptor(), value));
    policy.addValueMimicRecorder("source", recorder);
    var selector =
        EntitySelectorFactory.<TestdataListPinnedEntityProvidingSolution>create(
                new EntitySelectorConfig())
            .buildEntitySelector(
                policy,
                SelectionCacheType.JUST_IN_TIME,
                order,
                new ValueRangeRecorderId("source", false));
    try (var director = PlannerTestUtils.mockScoreDirector(solutionDescriptor)) {
      director.setWorkingSolution(solution);
      var scope = SelectorTestUtils.solvingStarted(selector, director, new Random(0));
      var phase = PlannerTestUtils.delegatingPhaseScope(scope);
      selector.phaseStarted(phase);
      try {
        recorder.iterator().next();
        var iterator = selector.iterator();
        for (int i = 0; i < (order == SelectionOrder.RANDOM ? 20 : 1); i++) {
          assertThat(iterator.hasNext()).isTrue();
          assertThat(iterator.next()).isSameAs(movable);
        }
        if (order == SelectionOrder.ORIGINAL) {
          assertThat(iterator.hasNext()).isFalse();
        }
      } finally {
        selector.phaseEnded(phase);
        selector.solvingEnded(scope);
      }
    }
  }

  @PlanningEntity
  public static class PinnedRangeEntity extends TestdataEntityProvidingEntity {

    private boolean pinned;

    public PinnedRangeEntity() {}

    public PinnedRangeEntity(
        String code, List<TestdataValue> valueRange, TestdataValue value, boolean pinned) {
      super(code, valueRange, value);
      this.pinned = pinned;
    }

    @PlanningPin
    public boolean isPinned() {
      return pinned;
    }

    public void setPinned(boolean pinned) {
      this.pinned = pinned;
    }
  }
}
