package greycos.solver.core.testcotwin.shadow.declarative.basicinverse;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import greycos.solver.core.api.score.SimpleScore;

@PlanningSolution
public class TestdataBasicInverseSolution {

  @PlanningEntityCollectionProperty List<TestdataBasicInverseEntity> entityList;

  @PlanningEntityCollectionProperty
  @ValueRangeProvider(id = "groupRange")
  List<TestdataBasicInverseGroup> groupList;

  @ProblemFactCollectionProperty
  @ValueRangeProvider(id = "ownerRange")
  List<TestdataBasicInverseOwner> ownerList;

  @PlanningScore SimpleScore score;

  public TestdataBasicInverseSolution() {}

  public TestdataBasicInverseSolution(
      List<TestdataBasicInverseEntity> entityList,
      List<TestdataBasicInverseGroup> groupList,
      List<TestdataBasicInverseOwner> ownerList) {
    this.entityList = entityList;
    this.groupList = groupList;
    this.ownerList = ownerList;
  }

  public List<TestdataBasicInverseEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataBasicInverseEntity> entityList) {
    this.entityList = entityList;
  }

  public List<TestdataBasicInverseGroup> getGroupList() {
    return groupList;
  }

  public void setGroupList(List<TestdataBasicInverseGroup> groupList) {
    this.groupList = groupList;
  }

  public List<TestdataBasicInverseOwner> getOwnerList() {
    return ownerList;
  }

  public void setOwnerList(List<TestdataBasicInverseOwner> ownerList) {
    this.ownerList = ownerList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
