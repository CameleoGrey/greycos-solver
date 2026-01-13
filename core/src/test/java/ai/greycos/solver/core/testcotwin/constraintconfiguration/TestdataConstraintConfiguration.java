package ai.greycos.solver.core.testcotwin.constraintconfiguration;

import ai.greycos.solver.core.api.cotwin.constraintweight.ConstraintConfiguration;
import ai.greycos.solver.core.api.cotwin.constraintweight.ConstraintWeight;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@Deprecated(forRemoval = true, since = "1.13.0")
@ConstraintConfiguration
public class TestdataConstraintConfiguration extends TestdataObject {

  private SimpleScore firstWeight = SimpleScore.of(1);
  private SimpleScore secondWeight = SimpleScore.of(20);

  public TestdataConstraintConfiguration() {
    super();
  }

  public TestdataConstraintConfiguration(String code) {
    super(code);
  }

  @ConstraintWeight("First weight")
  public SimpleScore getFirstWeight() {
    return firstWeight;
  }

  public void setFirstWeight(SimpleScore firstWeight) {
    this.firstWeight = firstWeight;
  }

  @ConstraintWeight(constraintPackage = "packageOverwrittenOnField", value = "Second weight")
  public SimpleScore getSecondWeight() {
    return secondWeight;
  }

  public void setSecondWeight(SimpleScore secondWeight) {
    this.secondWeight = secondWeight;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
