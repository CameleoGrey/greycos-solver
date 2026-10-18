package greycos.solver.quarkus.it.cotwin;

import java.util.List;
import java.util.Map;

import greycos.solver.core.api.cotwin.solution.ConstraintWeightOverrides;
import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.HardSoftScore;

@PlanningSolution
public class TestdataStringLengthShadowSolution {

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

  @PlanningEntityCollectionProperty
  private List<TestdataStringLengthShadowEntityInterface> entityList;

  private List<String> valueList;

  ConstraintWeightOverrides<HardSoftScore> constraintWeightOverrides =
      ConstraintWeightOverrides.of(
          Map.of("Don't assign 2 entities the same value.", HardSoftScore.ofHard(1)));

  @PlanningScore private HardSoftScore score;

  // ************************************************************************
  // Getters/setters
  // ************************************************************************

  public List<String> getValueList() {
    return valueList;
  }

  public void setValueList(List<String> valueList) {
    this.valueList = valueList;
  }

  public List<TestdataStringLengthShadowEntityInterface> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataStringLengthShadowEntityInterface> entityList) {
    this.entityList = entityList;
  }

  public ConstraintWeightOverrides<HardSoftScore> getConstraintWeightOverrides() {
    return constraintWeightOverrides;
  }

  public void setConstraintWeightOverrides(
      ConstraintWeightOverrides<HardSoftScore> constraintWeightOverrides) {
    this.constraintWeightOverrides = constraintWeightOverrides;
  }

  public HardSoftScore getScore() {
    return score;
  }

  public void setScore(HardSoftScore score) {
    this.score = score;
  }
}
