package greycos.solver.core.impl.heuristic.selector.entity.nearby;

import static greycos.solver.core.impl.heuristic.HeuristicConfigPolicyTestUtils.buildHeuristicConfigPolicy;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;

import greycos.solver.core.config.heuristic.selector.common.SelectionCacheType;
import greycos.solver.core.config.heuristic.selector.common.SelectionOrder;
import greycos.solver.core.config.heuristic.selector.common.nearby.NearbySelectionConfig;
import greycos.solver.core.config.heuristic.selector.entity.EntitySelectorConfig;
import greycos.solver.core.impl.heuristic.selector.SelectorTestUtils;
import greycos.solver.core.impl.heuristic.selector.common.ValueRangeRecorderId;
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelectorFactory;
import greycos.solver.core.impl.heuristic.selector.entity.mimic.MimicRecordingEntitySelector;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingEntity;
import greycos.solver.core.testcotwin.valuerange.entityproviding.TestdataEntityProvidingSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.api.Test;

class NearbyEntityValueRangeRegressionTest {

  @Test
  void basicSwapChecksBothRangesBeforeTheCapAndRefreshesValues() {
    var policy =
        buildHeuristicConfigPolicy(TestdataEntityProvidingSolution.buildSolutionDescriptor());
    var descriptor =
        policy
            .getSolutionDescriptor()
            .findEntityDescriptorOrFail(TestdataEntityProvidingEntity.class);
    var x = new TestdataValue("x");
    var y = new TestdataValue("y");
    var source = new TestdataEntityProvidingEntity("source", List.of(x, y), x);
    var near = new TestdataEntityProvidingEntity("near", List.of(y), y);
    var far = new TestdataEntityProvidingEntity("far", List.of(x, y), y);
    var solution = new TestdataEntityProvidingSolution();
    solution.setEntityList(List.of(source, near, far));
    var recorder =
        new MimicRecordingEntitySelector<>(
            SelectorTestUtils.mockEntitySelector(descriptor, source));
    policy.addEntityMimicRecorder("source", recorder);
    var selector =
        EntitySelectorFactory.<TestdataEntityProvidingSolution>create(
                new EntitySelectorConfig()
                    .withNearbySelectionConfig(
                        new NearbySelectionConfig()
                            .withOriginEntitySelectorConfig(
                                new EntitySelectorConfig().withMimicSelectorRef("source"))
                            .withNearbyDistanceMeterClass(DistanceMeter.class)
                            .withMaxNearbySortSize(1)))
            .buildEntitySelector(
                policy,
                SelectionCacheType.JUST_IN_TIME,
                SelectionOrder.RANDOM,
                new ValueRangeRecorderId("source", true));
    var director = PlannerTestUtils.mockScoreDirector(policy.getSolutionDescriptor());
    director.setWorkingSolution(solution);
    var scope = SelectorTestUtils.solvingStarted(selector, director, new Random(0));
    var phase = PlannerTestUtils.delegatingPhaseScope(scope);
    selector.phaseStarted(phase);
    recorder.iterator().next();
    var iterator = selector.iterator();
    assertThat(iterator.next()).isSameAs(far);
    source.setValue(y);
    far.setValue(x);
    assertThat(iterator.next()).isSameAs(near);
    selector.phaseEnded(phase);
    selector.solvingEnded(scope);
    director.close();
  }

  public static class DistanceMeter
      implements NearbyDistanceMeter<TestdataEntityProvidingEntity, TestdataEntityProvidingEntity> {
    @Override
    public double getNearbyDistance(
        TestdataEntityProvidingEntity origin, TestdataEntityProvidingEntity destination) {
      return destination.getCode().equals("near") ? 0 : 10;
    }
  }
}
