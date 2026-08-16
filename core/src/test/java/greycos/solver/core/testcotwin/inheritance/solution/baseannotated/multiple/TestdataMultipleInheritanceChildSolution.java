package greycos.solver.core.testcotwin.inheritance.solution.baseannotated.multiple;

import java.util.List;

import greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import greycos.solver.core.api.cotwin.solution.PlanningScore;
import greycos.solver.core.api.cotwin.solution.PlanningSolution;
import greycos.solver.core.api.score.SimpleScore;

@PlanningSolution
public class TestdataMultipleInheritanceChildSolution
    extends TestdataMultipleInheritanceBaseSolution {

  @PlanningEntityCollectionProperty
  private List<? extends TestdataMultipleInheritanceEntity> entityList;

  @PlanningScore private SimpleScore score;

  public List<? extends TestdataMultipleInheritanceEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<? extends TestdataMultipleInheritanceEntity> entityList) {
    this.entityList = entityList;
  }

  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
