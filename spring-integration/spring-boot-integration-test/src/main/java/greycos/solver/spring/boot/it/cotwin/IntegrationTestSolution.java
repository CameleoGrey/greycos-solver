package greycos.solver.spring.boot.it.cotwin;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;

@PlanningSolution
public class IntegrationTestSolution {

  @PlanningEntityCollectionProperty
  private List<TestdataMultiListFixture.Owner> listOwners = List.of();

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "firstElements")
  private List<TestdataMultiListFixture.FirstElement> firstElements = List.of();

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "secondElements")
  private List<TestdataMultiListFixture.SecondElement> secondElements = List.of();

  public List<TestdataMultiListFixture.Owner> getListOwners() {
    return listOwners;
  }

  public void setListOwners(List<TestdataMultiListFixture.Owner> listOwners) {
    this.listOwners = listOwners;
  }

  public List<TestdataMultiListFixture.FirstElement> getFirstElements() {
    return firstElements;
  }

  public void setFirstElements(List<TestdataMultiListFixture.FirstElement> firstElements) {
    this.firstElements = firstElements;
  }

  public List<TestdataMultiListFixture.SecondElement> getSecondElements() {
    return secondElements;
  }

  public void setSecondElements(List<TestdataMultiListFixture.SecondElement> secondElements) {
    this.secondElements = secondElements;
  }

  @PlanningEntityCollectionProperty private List<IntegrationTestEntity> entityList;

  private List<IntegrationTestValue> valueList;

  @PlanningScore private SimpleScore score;

  public IntegrationTestSolution() {}

  public IntegrationTestSolution(
      List<IntegrationTestEntity> entityList, List<IntegrationTestValue> valueList) {
    this.entityList = entityList;
    this.valueList = valueList;
  }

  public List<IntegrationTestEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<IntegrationTestEntity> entityList) {
    this.entityList = entityList;
  }

  public List<IntegrationTestValue> getValueList() {
    return valueList;
  }

  public void setValueList(List<IntegrationTestValue> valueList) {
    this.valueList = valueList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
