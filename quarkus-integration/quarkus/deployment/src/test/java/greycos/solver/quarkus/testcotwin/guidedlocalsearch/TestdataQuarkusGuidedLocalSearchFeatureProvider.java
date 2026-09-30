package greycos.solver.quarkus.testcotwin.guidedlocalsearch;

import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureConsumer;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureProvider;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureSession;
import greycos.solver.core.api.localsearch.GuidedLocalSearchFeatureUpdater;
import greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusSolution;

/**
 * Provider with an empty feature set, used only to verify configuration and reflection discovery.
 */
public class TestdataQuarkusGuidedLocalSearchFeatureProvider
    implements GuidedLocalSearchFeatureProvider<TestdataQuarkusSolution, String> {

  @Override
  public void extractFeatures(
      TestdataQuarkusSolution solution, GuidedLocalSearchFeatureConsumer<String> consumer) {}

  @Override
  public GuidedLocalSearchFeatureSession<TestdataQuarkusSolution, String> newSession() {
    return new EmptySession();
  }

  public static class EmptySession
      implements GuidedLocalSearchFeatureSession<TestdataQuarkusSolution, String> {

    @Override
    public void resetWorkingSolution(TestdataQuarkusSolution workingSolution) {}

    @Override
    public void flushChanges(GuidedLocalSearchFeatureUpdater<String> updater) {}
  }
}
