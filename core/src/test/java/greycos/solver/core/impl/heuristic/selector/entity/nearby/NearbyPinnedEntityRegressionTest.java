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
import greycos.solver.core.impl.heuristic.selector.common.nearby.NearbyDistanceMeter;
import greycos.solver.core.impl.heuristic.selector.entity.EntitySelectorFactory;
import greycos.solver.core.impl.heuristic.selector.entity.mimic.MimicRecordingEntitySelector;
import greycos.solver.core.testcotwin.TestdataValue;
import greycos.solver.core.testcotwin.pinned.TestdataPinnedEntity;
import greycos.solver.core.testcotwin.pinned.TestdataPinnedSolution;
import greycos.solver.core.testutil.PlannerTestUtils;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NearbyPinnedEntityRegressionTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void nearestPinnedEntityDoesNotConsumeTheCap(boolean eager) {
    var policy = buildHeuristicConfigPolicy(TestdataPinnedSolution.buildSolutionDescriptor());
    var descriptor =
        policy.getSolutionDescriptor().findEntityDescriptorOrFail(TestdataPinnedEntity.class);
    var value = new TestdataValue("value");
    var source = new TestdataPinnedEntity("source", value);
    var pinned = new TestdataPinnedEntity("near", value, false, true);
    var movable = new TestdataPinnedEntity("far", value);
    var solution = new TestdataPinnedSolution();
    solution.setValueList(List.of(value));
    solution.setEntityList(List.of(source, pinned, movable));
    var recorder =
        new MimicRecordingEntitySelector<>(
            SelectorTestUtils.mockEntitySelector(descriptor, source));
    policy.addEntityMimicRecorder("source", recorder);
    var selector =
        EntitySelectorFactory.<TestdataPinnedSolution>create(
                new EntitySelectorConfig()
                    .withNearbySelectionConfig(
                        new NearbySelectionConfig()
                            .withOriginEntitySelectorConfig(
                                new EntitySelectorConfig().withMimicSelectorRef("source"))
                            .withNearbyDistanceMeterClass(DistanceMeter.class)
                            .withMaxNearbySortSize(1)
                            .withEagerInitialization(eager)))
            .buildEntitySelector(policy, SelectionCacheType.JUST_IN_TIME, SelectionOrder.RANDOM);

    try (var director = PlannerTestUtils.mockScoreDirector(policy.getSolutionDescriptor())) {
      director.setWorkingSolution(solution);
      var scope = SelectorTestUtils.solvingStarted(selector, director, new Random(0));
      var phase = PlannerTestUtils.delegatingPhaseScope(scope);
      selector.phaseStarted(phase);
      try {
        recorder.iterator().next();
        var iterator = selector.iterator();
        for (int i = 0; i < 10; i++) {
          assertThat(iterator.hasNext()).isTrue();
          assertThat(iterator.next()).isSameAs(movable);
        }
      } finally {
        selector.phaseEnded(phase);
        selector.solvingEnded(scope);
      }
    }
  }

  public static final class DistanceMeter
      implements NearbyDistanceMeter<TestdataPinnedEntity, TestdataPinnedEntity> {
    @Override
    public double getNearbyDistance(TestdataPinnedEntity origin, TestdataPinnedEntity destination) {
      return destination.getCode().equals("near") ? 0 : 10;
    }
  }
}
