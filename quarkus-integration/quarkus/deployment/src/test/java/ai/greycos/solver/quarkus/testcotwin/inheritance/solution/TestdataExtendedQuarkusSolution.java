package ai.greycos.solver.quarkus.testcotwin.inheritance.solution;

import ai.greycos.solver.quarkus.testcotwin.normal.TestdataQuarkusSolution;

public class TestdataExtendedQuarkusSolution extends TestdataQuarkusSolution {
  private String extraData;

  public TestdataExtendedQuarkusSolution() {}

  public TestdataExtendedQuarkusSolution(String extraData) {
    this.extraData = extraData;
  }

  public String getExtraData() {
    return extraData;
  }
}
