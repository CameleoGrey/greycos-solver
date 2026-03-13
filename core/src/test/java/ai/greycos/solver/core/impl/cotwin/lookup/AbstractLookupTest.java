package ai.greycos.solver.core.impl.cotwin.lookup;

import ai.greycos.solver.core.impl.cotwin.common.CotwinAccessType;
import ai.greycos.solver.core.impl.cotwin.common.accessor.MemberAccessorFactory;
import ai.greycos.solver.core.impl.cotwin.policy.DescriptorPolicy;

import org.junit.jupiter.api.BeforeEach;

abstract class AbstractLookupTest {

  private final LookUpStrategyType lookUpStrategyType;
  protected LookUpManager lookUpManager;

  protected AbstractLookupTest(LookUpStrategyType lookUpStrategyType) {
    this.lookUpStrategyType = lookUpStrategyType;
  }

  @BeforeEach
  void setUpLookUpManager() {
    lookUpManager = new LookUpManager(createLookupStrategyResolver(lookUpStrategyType));
  }

  protected LookUpStrategyResolver createLookupStrategyResolver(
      LookUpStrategyType lookUpStrategyType) {
    DescriptorPolicy descriptorPolicy = new DescriptorPolicy();
    descriptorPolicy.setMemberAccessorFactory(new MemberAccessorFactory());
    descriptorPolicy.setCotwinAccessType(CotwinAccessType.FORCE_REFLECTION);
    return new LookUpStrategyResolver(descriptorPolicy, lookUpStrategyType);
  }
}
