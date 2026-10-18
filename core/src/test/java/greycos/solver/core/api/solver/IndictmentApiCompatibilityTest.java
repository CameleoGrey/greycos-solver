package greycos.solver.core.api.solver;

import org.junit.jupiter.api.Test;

class IndictmentApiCompatibilityTest {

  @Test
  void oldSourceIncludesExhaustiveEnumSwitchAndUnambiguousVarargsCalls() {
    LegacyIndictmentApiFixture.verifyLegacyCalls();
  }

  @Test
  void oldAnalysisReportsUnavailableViews() {
    IndictmentApiCompatibilityProbe.verifyAnalysisDefaults();
  }

  @Test
  void oldManagerDelegatesNoneAndRejectsUnsupportedRequests() {
    IndictmentApiCompatibilityProbe.verifyManagerDefaults();
  }

  @Test
  void oldRegistryPreservesItsRegistrationContract() {
    IndictmentApiCompatibilityProbe.verifyRegistryDefaults();
  }

  @Test
  void oldBuildersRejectUnsupportedAttribution() {
    IndictmentApiCompatibilityProbe.verifyBuilderDefaults();
  }

  @Test
  void assertionAdditionsAreDefaultsAndRejectUnsupportedAttribution() {
    IndictmentApiCompatibilityProbe.verifyAssertionDefaults();
  }
}
