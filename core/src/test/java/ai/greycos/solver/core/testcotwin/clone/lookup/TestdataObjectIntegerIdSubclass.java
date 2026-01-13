package ai.greycos.solver.core.testcotwin.clone.lookup;

public class TestdataObjectIntegerIdSubclass extends TestdataObjectIntegerId {

  public TestdataObjectIntegerIdSubclass(Integer id) {
    super(id);
  }

  @Override
  public String toString() {
    return "id=" + getId();
  }
}
