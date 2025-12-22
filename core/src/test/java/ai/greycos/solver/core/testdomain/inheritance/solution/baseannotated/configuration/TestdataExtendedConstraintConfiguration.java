package ai.greycos.solver.core.testdomain.inheritance.solution.baseannotated.configuration;

import ai.greycos.solver.core.api.domain.constraintweight.ConstraintConfiguration;
import ai.greycos.solver.core.api.domain.constraintweight.ConstraintWeight;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;
import ai.greycos.solver.core.testdomain.constraintconfiguration.TestdataConstraintConfiguration;

@Deprecated(forRemoval = true, since = "1.13.0")
@ConstraintConfiguration
public class TestdataExtendedConstraintConfiguration extends TestdataConstraintConfiguration {

  private SimpleScore thirdWeight = SimpleScore.of(300);

  public TestdataExtendedConstraintConfiguration() {
    super();
  }

  public TestdataExtendedConstraintConfiguration(String code) {
    super(code);
  }

  @ConstraintWeight("Third weight")
  public SimpleScore getThirdWeight() {
    return thirdWeight;
  }

  public void setThirdWeight(SimpleScore thirdWeight) {
    this.thirdWeight = thirdWeight;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
