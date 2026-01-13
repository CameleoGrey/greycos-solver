package ai.greycos.solver.spring.boot.autoconfigure.multiple.constraintprovider.cotwin;

import java.util.ArrayList;
import java.util.List;

import ai.greycos.solver.core.api.cotwin.solution.PlanningEntityCollectionProperty;
import ai.greycos.solver.core.api.cotwin.solution.PlanningScore;
import ai.greycos.solver.core.api.cotwin.solution.PlanningSolution;
import ai.greycos.solver.core.api.cotwin.solution.ProblemFactCollectionProperty;
import ai.greycos.solver.core.api.cotwin.valuerange.ValueRangeProvider;
import ai.greycos.solver.core.api.score.buildin.simple.SimpleScore;

@PlanningSolution
public class TestdataMultipleConstraintSolution {

  public static TestdataMultipleConstraintSolution generateSolution(
      int valueListSize, int entityListSize) {
    var solution = new TestdataMultipleConstraintSolution();
    var valueList = new ArrayList<String>(valueListSize);
    for (int i = 0; i < valueListSize; i++) {
      valueList.add("Generated Value " + i);
    }
    solution.setValueList(valueList);
    var entityList = new ArrayList<TestdataMultipleConstraintEntity>(entityListSize);
    for (int i = 0; i < entityListSize; i++) {
      var entity = new TestdataMultipleConstraintEntity();
      entityList.add(entity);
    }
    solution.setEntityList(entityList);
    return solution;
  }

  private List<String> valueList;
  private List<TestdataMultipleConstraintEntity> entityList;

  private SimpleScore score;

  @ValueRangeProvider(id = "valueRange")
  @ProblemFactCollectionProperty
  public List<String> getValueList() {
    return valueList;
  }

  public void setValueList(List<String> valueList) {
    this.valueList = valueList;
  }

  @PlanningEntityCollectionProperty
  public List<TestdataMultipleConstraintEntity> getEntityList() {
    return entityList;
  }

  public void setEntityList(List<TestdataMultipleConstraintEntity> entityList) {
    this.entityList = entityList;
  }

  @PlanningScore
  public SimpleScore getScore() {
    return score;
  }

  public void setScore(SimpleScore score) {
    this.score = score;
  }
}
