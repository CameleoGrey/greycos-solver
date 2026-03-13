package ai.greycos.solver.core.testcotwin.constraintweightoverrides;

import java.util.ArrayList;

import ai.greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataEntity;
import ai.greycos.solver.core.testcotwin.TestdataValue;

@PlanningSolution
public class TestdataExtendedConstraintWeightOverridesSolution
    extends TestdataConstraintWeightOverridesSolution {

  public static SolutionDescriptor<TestdataExtendedConstraintWeightOverridesSolution>
      buildExtendedSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataExtendedConstraintWeightOverridesSolution.class, TestdataEntity.class);
  }

  public static TestdataExtendedConstraintWeightOverridesSolution generateExtendedSolution(
      int valueListSize, int entityListSize) {
    var solution = new TestdataExtendedConstraintWeightOverridesSolution("Generated Solution 0");
    var valueList = new ArrayList<TestdataValue>(valueListSize);
    for (var i = 0; i < valueListSize; i++) {
      var value = new TestdataValue("Generated Value " + i);
      valueList.add(value);
    }
    solution.setValueList(valueList);
    var entityList = new ArrayList<TestdataEntity>(entityListSize);
    for (var i = 0; i < entityListSize; i++) {
      var value = valueList.get(i % valueListSize);
      var entity = new TestdataEntity("Generated Entity " + i, value);
      entityList.add(entity);
    }
    solution.setEntityList(entityList);
    solution.setConstraintWeightOverrides(ConstraintWeightOverrides.none());
    return solution;
  }

  private ConstraintWeightOverrides<SimpleScore> constraintWeightOverrides;

  public TestdataExtendedConstraintWeightOverridesSolution() {}

  public TestdataExtendedConstraintWeightOverridesSolution(String code) {
    super(code);
  }

  @Override
  public ConstraintWeightOverrides<SimpleScore> getConstraintWeightOverrides() {
    return constraintWeightOverrides;
  }

  @Override
  public void setConstraintWeightOverrides(
      ConstraintWeightOverrides<SimpleScore> constraintWeightOverrides) {
    this.constraintWeightOverrides = constraintWeightOverrides;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
