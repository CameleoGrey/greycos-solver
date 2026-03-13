package ai.greycos.solver.core.testcotwin.mixed.singleentity.multipleprovider;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testcotwin.multivar.TestdataOtherValue;

@PlanningSolution
public class TestdataMixedMultipleProviderSolution {

  public static SolutionDescriptor<TestdataMixedMultipleProviderSolution>
      buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataMixedMultipleProviderSolution.class, TestdataMixedMultipleProviderEntity.class);
  }

  public static TestdataMixedMultipleProviderSolution generateUninitializedSolution(
      int entityListSize, int valueListSize, int otherValueListSize) {
    var solution = new TestdataMixedMultipleProviderSolution();
    var valueList = new ArrayList<TestdataValue>(valueListSize);
    var otherValueList = new ArrayList<TestdataOtherValue>(otherValueListSize);
    for (int i = 0; i < valueListSize; i++) {
      valueList.add(new TestdataValue("Generated Value " + i));
    }
    for (int i = 0; i < otherValueListSize; i++) {
      otherValueList.add(new TestdataOtherValue("Generated Other Value " + i));
    }
    solution.setOtherValueList(otherValueList);
    var entityList = new ArrayList<TestdataMixedMultipleProviderEntity>(entityListSize);
    for (int i = 0; i < entityListSize; i++) {
      var entity = new TestdataMixedMultipleProviderEntity("Entity " + i, valueList);
      entityList.add(entity);
    }
    solution.setEntityList(entityList);
    return solution;
  }

  @ValueRangeProvider @ProblemFactCollectionProperty
  private List<TestdataOtherValue> otherValueList;

  @PlanningEntityCollectionProperty private List<TestdataMixedMultipleProviderEntity> entityList;

  @PlanningScore private SimpleScore score;

  public List<TestdataOtherValue> getOtherValueList() {
    return otherValueList;
  }

  public void setOtherValueList(List<TestdataOtherValue> otherValueList) {
    this.otherValueList = otherValueList;
  }

  public List<TestdataMixedMultipleProviderEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataMixedMultipleProviderEntity> entityList) {
    this.entityList = entityList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
