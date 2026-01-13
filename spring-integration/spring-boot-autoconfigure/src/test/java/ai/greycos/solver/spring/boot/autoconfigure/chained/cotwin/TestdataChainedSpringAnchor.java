package ai.greycos.solver.spring.boot.autoconfigure.chained.cotwin;

public class TestdataChainedSpringAnchor implements TestdataChainedSpringObject {

  private TestdataChainedSpringEntity next;

  // ************************************************************************
  // Getters/setters
  // ************************************************************************

  @Override
  public TestdataChainedSpringEntity getNext() {
    return next;
  }

  @Override
  public void setNext(TestdataChainedSpringEntity next) {
    this.next = next;
  }
}
