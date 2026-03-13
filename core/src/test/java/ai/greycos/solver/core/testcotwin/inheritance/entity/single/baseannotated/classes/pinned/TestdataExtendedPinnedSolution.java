package ai.greycos.solver.core.testcotwin.inheritance.entity.single.baseannotated.classes.pinned;

import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.SimpleScore;
import ai.greycos.solver.core.impl.cotwin.solution.descriptor.SolutionDescriptor;
import ai.greycos.solver.core.testcotwin.TestdataObject;
import ai.greycos.solver.core.testcotwin.TestdataValue;
import ai.greycos.solver.core.testcotwin.pinned.TestdataPinnedEntity;

@PlanningSolution
public class TestdataExtendedPinnedSolution extends TestdataObject {

  public static SolutionDescriptor<TestdataExtendedPinnedSolution> buildSolutionDescriptor() {
    return SolutionDescriptor.buildSolutionDescriptor(
        TestdataExtendedPinnedSolution.class,
        TestdataPinnedEntity.class,
        TestdataExtendedPinnedEntity.class);
  }

  private List<TestdataValue> valueList;
  private List<TestdataValue> subValueList;
  private List<TestdataPinnedEntity> entityList;

  private SimpleScore score;

  public TestdataExtendedPinnedSolution() {}

  public TestdataExtendedPinnedSolution(String code) {
    super(code);
  }

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public List<TestdataValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<TestdataValue> valueList) {
    this.valueList = valueList;
  }

  @ValueRangeProvider(id = "subValueRange")
  @ProblemFactCollectionProperty
  public List<TestdataValue> getSubValueList() {
    return subValueList;
  }

  public void setSubValueList(List<TestdataValue> subValueList) {
    this.subValueList = subValueList;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataPinnedEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataPinnedEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }

  // ************************************************************************
  // Complex methods
  // ************************************************************************

}
