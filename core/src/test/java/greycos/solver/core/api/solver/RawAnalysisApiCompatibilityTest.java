package greycos.solver.core.api.solver;

import org.junit.jupiter.api.Test;

class RawAnalysisApiCompatibilityTest {
  @Test
  void oldImplementationsAndBothExhaustiveEnumSwitchesRemainSourceCompatible() {
    LegacyRawAnalysisApiFixture.verifyLegacyCalls();
  }

  @Test
  void oldCustomAnalysesDoNotInventRawData() {
    RawAnalysisApiCompatibilityProbe.verifyUnavailableRawDefaults();
  }

  @Test
  void oldCustomConstraintsInheritMatchCountAvailabilityWithoutNewAbstractMethods() {
    RawAnalysisApiCompatibilityProbe.verifyLegacyMatchCountAvailability();
  }

  @Test
  void defaultHelpersWorkOnOldCustomImplementationsWithoutInventingRawJustifications() {
    RawAnalysisApiCompatibilityProbe.verifyNewHelpersOnOldImplementations();
  }

  @Test
  void rawDisabledOptionsDelegateToOldIndictmentAwareManager() {
    RawAnalysisApiCompatibilityProbe.verifyManagerDelegation();
  }

  @Test
  void rawRequestsAreUnsupportedOnOldManagerWithoutSideEffects() {
    RawAnalysisApiCompatibilityProbe.verifyUnsupportedRawRequests();
  }

  @Test
  void retainsInternalFiveArgumentConstructorWithoutClaimingPublicCompatibility() {
    LegacyRawAnalysisConstructorFixture.verifyFiveArgumentConstructor();
  }
}
