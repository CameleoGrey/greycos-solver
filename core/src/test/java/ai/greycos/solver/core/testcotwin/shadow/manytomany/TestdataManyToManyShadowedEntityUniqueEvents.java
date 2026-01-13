package ai.greycos.solver.core.testcotwin.shadow.manytomany;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.impl.cotwin.entity.descriptor.EntityDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningEntity
public class TestdataManyToManyShadowedEntityUniqueEvents extends TestdataManyToManyShadowedEntity {

  public static EntityDescriptor<TestdataManyToManyShadowedSolution> buildEntityDescriptor() {
    return TestdataManyToManyShadowedSolution.buildSolutionDescriptorRequiresUniqueEvents()
        .findEntityDescriptorOrFail(TestdataManyToManyShadowedEntityUniqueEvents.class);
  }

  private final List<String> composedCodeLog = new ArrayList<>();

  public TestdataManyToManyShadowedEntityUniqueEvents(
      String code, TestdataValue primaryValue, TestdataValue secondaryValue) {
    super(code, primaryValue, secondaryValue);
  }

  @Override
  public void setComposedCode(String composedCode) {
    // (2) log composedCode updates for later verification.
    composedCodeLog.add(composedCode);
    super.setComposedCode(composedCode);
  }

  public List<String> getComposedCodeLog() {
    return composedCodeLog;
  }

  public static class ComposedValuesUpdatingVariableListener
      extends TestdataManyToManyShadowedEntity.ComposedValuesUpdatingVariableListener {

    @Override
    public boolean requiresUniqueEntityEvents() {
      // (1) Override the original listener and require unique entity events.
      return true;
    }
  }
}
