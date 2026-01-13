package ai.greycos.solver.core.testcotwin.inheritance.entity.multiple.baseannotated.classes.childnot;

public class TestdataMultipleChildNotAnnotatedChildEntity
    extends TestdataMultipleChildNotAnnotatedSecondChildEntity {

  @SuppressWarnings("unused")
  public TestdataMultipleChildNotAnnotatedChildEntity() {}

  public TestdataMultipleChildNotAnnotatedChildEntity(long id) {
    super(id);
  }
}
