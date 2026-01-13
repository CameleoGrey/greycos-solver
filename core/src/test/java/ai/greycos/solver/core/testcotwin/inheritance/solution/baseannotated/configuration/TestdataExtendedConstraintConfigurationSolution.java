package ai.greycos.solver.core.testcotwin.inheritance.solution.baseannotated.configuration;

import ai.greycos.solver.core.api.cotwin.constraintweight.ConstraintConfigurationProvider;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataSolution;

@Deprecated(forRemoval = true, since = "1.13.0")
@PlanningSolution
public class TestdataExtendedConstraintConfigurationSolution extends TestdataSolution {

  public static SolutionDescriptor<TestdataExtendedConstraintConfigurationSolution>
      buildExtendedSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataExtendedConstraintConfigurationSolution.class, TestdataEntity.class);
  }

  private TestdataExtendedConstraintConfiguration constraintConfiguration;

  public TestdataExtendedConstraintConfigurationSolution() {}

  public TestdataExtendedConstraintConfigurationSolution(String code) {
    super(code);
  }

  @ConstraintConfigurationProvider
  public TestdataExtendedConstraintConfiguration getConstraintConfiguration() {
    return constraintConfiguration;
  }

  public void setConstraintConfiguration(
      TestdataExtendedConstraintConfiguration constraintConfiguration) {
    this.constraintConfiguration = constraintConfiguration;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
