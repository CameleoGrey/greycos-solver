package ai.greycos.solver.core.impl.heuristic;

import java.util.Random;

import ai.greycos.solver.core.config.heuristic.selector.entity.EntitySorterManner;
import ai.greycos.solver.core.config.solver.EnvironmentMode;
import ai.greycos.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.impl.solver.ClassInstanceCache;
import ai.greycos.solver.core.testdomain.TestdataSolution;

public final class HeuristicConfigPolicyTestUtils {

  public static HeuristicConfigPolicy<TestdataSolution> buildHeuristicConfigPolicy() {
    return buildHeuristicConfigPolicy(TestdataSolution.buildSolutionDescriptor());
  }

  public static <Solution_> HeuristicConfigPolicy<Solution_> buildHeuristicConfigPolicy(
      SolutionDescriptor<Solution_> solutionDescriptor) {
    return buildHeuristicConfigPolicy(solutionDescriptor, null);
  }

  public static <Solution_> HeuristicConfigPolicy<Solution_> buildHeuristicConfigPolicy(
      SolutionDescriptor<Solution_> solutionDescriptor, EntitySorterManner entitySorterManner) {
    return new HeuristicConfigPolicy.Builder<Solution_>()
        .withEnvironmentMode(EnvironmentMode.PHASE_ASSERT)
        .withRandom(new Random())
        .withSolutionDescriptor(solutionDescriptor)
        .withClassInstanceCache(ClassInstanceCache.create())
        .withEntitySorterManner(entitySorterManner)
        .build();
  }

  private HeuristicConfigPolicyTestUtils() {}
}
