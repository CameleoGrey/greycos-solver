package ai.greycos.solver.core.testcotwin.shadow.invalid;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.PreviousElementShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowSources;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariablesInconsistent;
import ai.greycos.solver.core.testcotwin.TestdataObject;

@PlanningEntity
public class TestdataInvalidDeclarativeValue extends TestdataObject {
  TestdataInvalidDeclarativeValue fact;

  List<TestdataInvalidDeclarativeValue> group;

  @PreviousElementShadowVariable(sourceVariableName = "values")
  TestdataInvalidDeclarativeValue previous;

  @ShadowVariable(supplierName = "dependencySupplier")
  TestdataInvalidDeclarativeValue dependency;

  @ShadowVariable(supplierName = "shadowSupplier")
  TestdataInvalidDeclarativeValue shadow;

  @ShadowVariablesInconsistent boolean inconsistent;

  public TestdataInvalidDeclarativeValue() {}

  public TestdataInvalidDeclarativeValue(String code) {
    super(code);
  }

  public TestdataInvalidDeclarativeValue getFact() {
    return fact;
  }

  public void setFact(TestdataInvalidDeclarativeValue fact) {
    this.fact = fact;
  }

  public List<TestdataInvalidDeclarativeValue> getGroup() {
    return group;
  }

  public void setGroup(List<TestdataInvalidDeclarativeValue> group) {
    this.group = group;
  }

  public TestdataInvalidDeclarativeValue getPrevious() {
    return previous;
  }

  public void setPrevious(TestdataInvalidDeclarativeValue previous) {
    this.previous = previous;
  }

  public TestdataInvalidDeclarativeValue getDependency() {
    return dependency;
  }

  public void setDependency(TestdataInvalidDeclarativeValue dependency) {
    this.dependency = dependency;
  }

  public TestdataInvalidDeclarativeValue getShadow() {
    return shadow;
  }

  public void setShadow(TestdataInvalidDeclarativeValue shadow) {
    this.shadow = shadow;
  }

  public boolean isInconsistent() {
    return inconsistent;
  }

  public void setInconsistent(boolean inconsistent) {
    this.inconsistent = inconsistent;
  }

  @ShadowSources("previous")
  public TestdataInvalidDeclarativeValue dependencySupplier() {
    return previous;
  }

  @ShadowSources("dependency")
  public TestdataInvalidDeclarativeValue shadowSupplier() {
    return dependency;
  }
}
