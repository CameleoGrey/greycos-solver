package ai.greycos.solver.quarkus.testcotwin.gizmo;

import java.util.Collections;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.entity.PlanningPin;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowSources;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariablesInconsistent;

/*
 *  Should have one of every annotation, even annotations that
 *  don't make sense on an entity, to make sure everything works
 *  a-ok.
 */
@PlanningEntity
public class TestDataKitchenSinkEntity {

  private String groupId;
  private Integer intVariable;

  @ShadowVariable(supplierName = "copyStringVariable")
  private String declarativeShadowVariable;

  @ShadowVariablesInconsistent private boolean isInconsistent;

  @PlanningVariable(valueRangeProviderRefs = {"names"})
  private String stringVariable;

  private boolean isPinned;

  @PlanningVariable(valueRangeProviderRefs = {"ints"})
  public Integer getIntVariable() {
    return intVariable;
  }

  public void setIntVariable(Integer val) {
    intVariable = val;
  }

  public Integer testGetIntVariable() {
    return intVariable;
  }

  public String testGetStringVariable() {
    return stringVariable;
  }

  @ShadowSources(value = "stringVariable", alignmentKey = "groupId")
  private String copyStringVariable() {
    return stringVariable;
  }

  @PlanningPin
  private boolean isPinned() {
    return isPinned;
  }

  @ValueRangeProvider(id = "ints")
  private List<Integer> myIntValueRange() {
    return Collections.singletonList(1);
  }

  @ValueRangeProvider(id = "names")
  public List<String> myStringValueRange() {
    return Collections.singletonList("A");
  }
}
