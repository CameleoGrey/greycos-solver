package ai.greycos.solver.core.testcotwin.shadow.method_variables;

import ai.greycos.solver.core.api.cotwin.entity.PlanningEntity;
import ai.greycos.solver.core.api.cotwin.variable.ShadowSources;
import ai.greycos.solver.core.api.cotwin.variable.ShadowVariable;

@PlanningEntity
public class TestdataDeclarativeMethodVariablesSubclassValue
    extends TestdataDeclarativeMethodVariablesBaseValue {
  String codeSum;

  public TestdataDeclarativeMethodVariablesSubclassValue() {
    super();
  }

  public TestdataDeclarativeMethodVariablesSubclassValue(String code) {
    super(code);
    codeSum = code;
  }

  @Override
  public TestdataDeclarativeMethodVariablesSubclassValue getPrevious() {
    return (TestdataDeclarativeMethodVariablesSubclassValue) previous;
  }

  @ShadowVariable(supplierName = "codeSumSupplier")
  public String getCodeSum() {
    return codeSum;
  }

  public void setCodeSum(String codeSum) {
    this.codeSum = codeSum;
  }

  @ShadowSources("previous.codeSum")
  public String codeSumSupplier() {
    if (previous == null) {
      return code;
    } else {
      return getPrevious().codeSum + code;
    }
  }
}
