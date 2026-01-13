package ai.greycos.solver.core.testcotwin.shadow.simple_chained;

import java.time.Duration;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariable;
import ai.greycos.solver.core.api.cotwin.variable.PlanningVariableGraphType;

@PlanningEntity
public class TestdataChainedSimpleVarEntity extends TestdataChainedSimpleVarValue {
  String id;

  @PlanningVariable(graphType = PlanningVariableGraphType.CHAINED)
  TestdataChainedSimpleVarValue previous;

  public TestdataChainedSimpleVarEntity() {}

  public TestdataChainedSimpleVarEntity(String id, Duration duration) {
    super(id, duration);
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public TestdataChainedSimpleVarValue getPrevious() {
    return previous;
  }

  public void setPrevious(TestdataChainedSimpleVarValue previous) {
    this.previous = previous;
  }

  @Override
  public String toString() {
    return "TestdataChainedSimpleVarEntity{" + "id=" + id + ", previous=" + previous + '}';
  }
}
