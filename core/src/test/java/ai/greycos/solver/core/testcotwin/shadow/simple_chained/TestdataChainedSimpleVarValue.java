package ai.greycos.solver.core.testcotwin.shadow.simple_chained;

import java.time.Duration;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.InverseRelationShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowSources;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;

@PlanningEntity
public class TestdataChainedSimpleVarValue {
  String id;

  @InverseRelationShadowVariable(sourceVariableName = "previous")
  TestdataChainedSimpleVarEntity next;

  Duration duration;

  @ShadowVariable(supplierName = "updateCumulativeDurationInDays")
  int cumulativeDurationInDays;

  public TestdataChainedSimpleVarValue() {}

  public TestdataChainedSimpleVarValue(String id, Duration duration) {
    this.id = id;
    this.duration = duration;
    this.cumulativeDurationInDays = (int) duration.toDays();
  }

  public TestdataChainedSimpleVarEntity getNext() {
    return next;
  }

  public void setNext(TestdataChainedSimpleVarEntity next) {
    this.next = next;
  }

  public Duration getDuration() {
    return duration;
  }

  public void setDuration(Duration duration) {
    this.duration = duration;
  }

  public int getCumulativeDurationInDays() {
    return cumulativeDurationInDays;
  }

  @ShadowSources("next.cumulativeDurationInDays")
  public int updateCumulativeDurationInDays() {
    if (next == null) {
      return (int) duration.toDays();
    } else {
      return next.getCumulativeDurationInDays() + (int) duration.toDays();
    }
  }
}
