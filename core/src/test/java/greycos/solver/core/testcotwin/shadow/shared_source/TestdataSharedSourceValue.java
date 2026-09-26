package greycos.solver.core.testcotwin.shadow.shared_source;

import greycos.solver.core.testcotwin.TestdataObject;

public class TestdataSharedSourceValue extends TestdataObject {

  int startTime;
  int duration;

  public TestdataSharedSourceValue() {}

  public TestdataSharedSourceValue(String code, int startTime, int duration) {
    super(code);
    this.startTime = startTime;
    this.duration = duration;
  }

  public int getStartTime() {
    return startTime;
  }

  public int getDuration() {
    return duration;
  }
}
